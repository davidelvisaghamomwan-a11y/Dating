package com.example.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.AiIcebreakerService
import com.example.ai.IcebreakerAiUiState
import com.example.data.KindredRepository
import com.example.data.SampleProfilesProvider
import com.example.model.BlockRecord
import com.example.model.BlockedUserItem
import com.example.model.CallSession
import com.example.model.ChatMessage
import com.example.model.DiscoveryCandidate
import com.example.model.KindredConstants
import com.example.model.LivenessAnalysisResult
import com.example.model.MatchRecord
import com.example.model.MatchThreadItem
import com.example.model.PrivateUser
import com.example.model.PublicProfile
import com.example.model.SwipeRecord
import com.example.model.TypingStatus
import com.example.model.UiState
import com.example.model.UserPresence
import com.example.model.VerificationChallenge
import com.example.model.VerificationRecord
import com.example.service.GeoHashMatchingService
import com.example.service.GeoHashSpatialSnapshot
import com.example.util.GeoHashUtils
import com.example.util.LocationAndGeoHelper
import com.example.util.ResolvedDeviceLocation
import com.example.util.VerificationAnalyzer
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID

sealed interface KindredPushAlert {
    data class NewMatch(val matchId: String, val partnerName: String) : KindredPushAlert
    data class NewMessage(val matchId: String, val senderName: String, val messageText: String) : KindredPushAlert
}

data class GeoHashContextMeta(
    val centerGeoHash: String,
    val queryPrecision: Int,
    val queriedCells: List<String>
)

data class DashboardSnapshot(
    val privateUser: PrivateUser?,
    val myProfile: PublicProfile?,
    val discoveryCandidates: List<DiscoveryCandidate>,
    val searchableProfiles: List<DiscoveryCandidate> = emptyList(),
    val matches: List<MatchThreadItem>,
    val verifications: List<VerificationRecord>,
    val geoHashMeta: GeoHashContextMeta?,
    val includeDemoProfiles: Boolean = true,
    val blockedUsers: List<BlockedUserItem> = emptyList()
)

private data class SpatialQueryKey(
    val lat: Double,
    val lng: Double,
    val maxDistanceKm: Double
)

private data class BlockingSnapshot(
    val usersIBlocked: List<BlockRecord>,
    val usersWhoBlockedMe: List<BlockRecord>,
    val demoBlocked: Map<String, BlockedUserItem>
) {
    val allBlockedIds: Set<String>
        get() = usersIBlocked.map { it.blockedUid }.toSet() +
            usersWhoBlockedMe.map { it.blockerUid }.toSet() +
            demoBlocked.keys
}

@OptIn(ExperimentalCoroutinesApi::class)
class KindredViewModel(
    private val repository: KindredRepository,
    private val geoService: GeoHashMatchingService,
    val currentUserId: String,
    val userEmail: String?,
    val defaultDisplayName: String?,
    val isEmailVerified: Boolean,
    private val aiIcebreakerService: AiIcebreakerService = AiIcebreakerService()
) : ViewModel() {

    private fun <T> Flow<T>.asFirestoreUiState(label: String): StateFlow<UiState<T>> =
        this.map<T, UiState<T>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, "Error observing $label", rootError)
                emit(UiState.Error(rootError.message ?: "Failed to load $label"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = UiState.Loading
            )

    private val privateUserFlow = repository.observePrivateUser(currentUserId)
    private val myProfileFlow = repository.observeMyPublicProfile(currentUserId)
    private val allProfilesFlow = repository.observeDiscoverableProfiles(currentUserId)
    private val outgoingSwipesFlow = repository.observeMyOutgoingSwipes(currentUserId)
    private val matchesFlow = repository.observeMyMatches(currentUserId)
    private val verificationsFlow = repository.observeVerifications(currentUserId)
    private val usersIBlockedFlow = repository.observeUsersIBlocked(currentUserId)
    private val usersWhoBlockedMeFlow = repository.observeUsersWhoBlockedMe(currentUserId)

    private val _latestLocation = MutableStateFlow<ResolvedDeviceLocation?>(null)
    val latestLocation: StateFlow<ResolvedDeviceLocation?> = _latestLocation.asStateFlow()

    private var lastPersistedGeoLat: Double? = null
    private var lastPersistedGeoLng: Double? = null
    private var lastGeoPersistMs: Long = 0L

    private val effectiveLocationFlow: Flow<Triple<PrivateUser?, PublicProfile?, ResolvedDeviceLocation?>> = combine(
        privateUserFlow,
        myProfileFlow,
        _latestLocation
    ) { priv, pub, live ->
        Triple(priv, pub, live)
    }

    // Real-time GeoHash cell stream keyed by the user's live/stored location and search radius
    private val spatialGeoCellsFlow: Flow<GeoHashSpatialSnapshot?> = effectiveLocationFlow
        .map { (priv, pub, live) ->
            val lat = live?.exactLatitude ?: priv?.exactLatitude ?: pub?.coarseLatitude
            val lng = live?.exactLongitude ?: priv?.exactLongitude ?: pub?.coarseLongitude
            val dist = (priv?.maxDistanceKm ?: 100L).toDouble()
            if (lat != null && lng != null) {
                SpatialQueryKey(
                    lat = LocationAndGeoHelper.roundToCoarse(lat),
                    lng = LocationAndGeoHelper.roundToCoarse(lng),
                    maxDistanceKm = dist
                )
            } else null
        }
        .distinctUntilChanged()
        .flatMapLatest { key ->
            if (key == null) {
                flowOf(null)
            } else {
                geoService.observeSpatialNeighborhood(
                    userId = currentUserId,
                    latitude = key.lat,
                    longitude = key.lng,
                    maxDistanceKm = key.maxDistanceKm
                )
            }
        }

    private val _includeDemoProfiles = MutableStateFlow(true)
    private val _demoSwipes = MutableStateFlow<Map<String, String>>(emptyMap())
    private val _pendingSwipes = MutableStateFlow<Map<String, String>>(emptyMap())
    private val _demoBlockedUsers = MutableStateFlow<Map<String, BlockedUserItem>>(emptyMap())
    private val _demoMatches = MutableStateFlow<List<MatchRecord>>(emptyList())
    private val _demoMessages = MutableStateFlow<Map<String, List<ChatMessage>>>(emptyMap())

    private data class UndoableSwipe(
        val targetUserId: String,
        val displayName: String,
        val isDemo: Boolean
    )

    private val _lastUndoableSwipe = MutableStateFlow<UndoableSwipe?>(null)
    val canUndoLastSwipe: StateFlow<Boolean> = _lastUndoableSwipe
        .map { it != null }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = false
        )

    private val blockingStateFlow: Flow<BlockingSnapshot> = combine(
        usersIBlockedFlow,
        usersWhoBlockedMeFlow,
        _demoBlockedUsers
    ) { iBlocked, blockedMe, demoBlocked ->
        BlockingSnapshot(
            usersIBlocked = iBlocked,
            usersWhoBlockedMe = blockedMe,
            demoBlocked = demoBlocked
        )
    }

    private val effectiveSwipedIdsFlow: Flow<Set<String>> = combine(
        outgoingSwipesFlow,
        _demoSwipes,
        _pendingSwipes
    ) { swipes, demoSwipes, pendingSwipes ->
        swipes.map { it.toUserId }.toSet() + demoSwipes.keys + pendingSwipes.keys
    }

    private val mergedMatchesFlow: Flow<List<MatchRecord>> = combine(
        matchesFlow,
        _demoMatches
    ) { remoteMatches, demoMatches ->
        (remoteMatches + demoMatches)
            .distinctBy { it.id }
            .sortedByDescending { it.lastMessageAt ?: it.updatedAt ?: it.createdAt ?: Timestamp(0, 0) }
    }

    val dashboardState: StateFlow<UiState<DashboardSnapshot>> = combine(
        effectiveLocationFlow,
        allProfilesFlow,
        spatialGeoCellsFlow,
        effectiveSwipedIdsFlow,
        mergedMatchesFlow,
        verificationsFlow,
        _includeDemoProfiles,
        blockingStateFlow
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val locationTriple = values[0] as Triple<PrivateUser?, PublicProfile?, ResolvedDeviceLocation?>
        val privateUser = locationTriple.first
        val myProfile = locationTriple.second
        val liveLocation = locationTriple.third
        @Suppress("UNCHECKED_CAST")
        val allProfiles = values[1] as List<PublicProfile>
        val spatialSnapshot = values[2] as GeoHashSpatialSnapshot?
        @Suppress("UNCHECKED_CAST")
        val swipedIds = values[3] as Set<String>
        @Suppress("UNCHECKED_CAST")
        val matches = values[4] as List<MatchRecord>
        @Suppress("UNCHECKED_CAST")
        val verifications = values[5] as List<VerificationRecord>
        val includeDemos = values[6] as Boolean
        val blocking = values[7] as BlockingSnapshot

        val blockedIds = blocking.allBlockedIds
        val myLat = liveLocation?.exactLatitude ?: privateUser?.exactLatitude ?: myProfile?.coarseLatitude
        val myLng = liveLocation?.exactLongitude ?: privateUser?.exactLongitude ?: myProfile?.coarseLongitude

        val minAge = (privateUser?.minAgePref ?: 18L).toInt()
        val maxAge = (privateUser?.maxAgePref ?: 99L).toInt()
        val maxDistKm = (privateUser?.maxDistanceKm ?: 100L).toDouble()
        val interestedIn = privateUser?.interestedIn ?: "Everyone"
        val verifiedOnly = privateUser?.verifiedOnlyFilter ?: false
        val myInterests = myProfile?.interests ?: emptyList()

        val myGeoHash = spatialSnapshot?.centerGeoHash ?: if (myLat != null && myLng != null) {
            GeoHashUtils.encode(myLat, myLng, 7)
        } else {
            ""
        }

        // Primary source: GeoHash spatial cells queried from Firestore /geoCells/{cellId}/profiles.
        // Any legacy publicProfiles without geoCells yet are merged by userId only if within GeoHash bounds.
        val firestoreProfiles = if (spatialSnapshot != null && spatialSnapshot.profiles.isNotEmpty()) {
            val cellMap = spatialSnapshot.profiles.associateBy { it.userId }.toMutableMap()
            allProfiles.forEach { legacy ->
                if (legacy.userId !in cellMap && legacy.geohash.isBlank()) {
                    cellMap[legacy.userId] = legacy
                }
            }
            cellMap.values.toList()
        } else {
            spatialSnapshot?.profiles ?: allProfiles
        }

        val demoProfiles = if (includeDemos) {
            SampleProfilesProvider.generateProfiles(
                anchorLatitude = myLat ?: 37.7749,
                anchorLongitude = myLng ?: -122.4194
            )
        } else {
            emptyList()
        }

        val combinedProfiles = LinkedHashMap<String, PublicProfile>().apply {
            firestoreProfiles.forEach { put(it.userId, it) }
            demoProfiles.forEach { if (!containsKey(it.userId)) put(it.userId, it) }
        }.values.toList()

        val candidates = geoService.rankAndFilterCandidates(
            rawProfiles = combinedProfiles,
            currentUserId = currentUserId,
            myLat = myLat,
            myLng = myLng,
            myGeoHash = myGeoHash,
            maxDistanceKm = maxDistKm,
            minAge = minAge,
            maxAge = maxAge,
            interestedIn = interestedIn,
            verifiedOnly = verifiedOnly,
            myInterests = myInterests,
            excludedUserIds = swipedIds,
            blockedUserIds = blockedIds
        )

        val allSearchableMap = LinkedHashMap<String, PublicProfile>().apply {
            allProfiles.forEach { put(it.userId, it) }
            combinedProfiles.forEach { if (!containsKey(it.userId)) put(it.userId, it) }
        }
        val searchableCandidates = allSearchableMap.values
            .filter { profile ->
                profile.userId.isNotBlank() &&
                    profile.userId != currentUserId &&
                    profile.discoverable &&
                    profile.userId !in blockedIds
            }
            .map { profile ->
                val distKm = if (myLat != null && myLng != null) {
                    LocationAndGeoHelper.calculateDistanceKm(
                        myLat,
                        myLng,
                        profile.coarseLatitude,
                        profile.coarseLongitude
                    )
                } else 0.0
                val sharedInterests = profile.interests.filter { interest ->
                    myInterests.any { mine -> mine.equals(interest, ignoreCase = true) }
                }
                DiscoveryCandidate(
                    profile = profile,
                    distanceKm = distKm,
                    sharedInterests = sharedInterests
                )
            }
            .sortedWith(
                compareBy<DiscoveryCandidate> { SampleProfilesProvider.isDemoProfileId(it.profile.userId) }
                    .thenByDescending { it.profile.isProfileVerified }
                    .thenBy { it.distanceKm }
            )

        val profileById = allSearchableMap
        val matchItems = matches
            .filter { match ->
                val partnerId = match.participantUids.firstOrNull { it != currentUserId } ?: ""
                partnerId.isNotBlank() && partnerId !in blockedIds
            }
            .map { match ->
                val partnerId = match.participantUids.firstOrNull { it != currentUserId } ?: ""
                val partnerProfile = profileById[partnerId]
                val dist = if (myLat != null && myLng != null && partnerProfile != null) {
                    LocationAndGeoHelper.calculateDistanceKm(
                        myLat,
                        myLng,
                        partnerProfile.coarseLatitude,
                        partnerProfile.coarseLongitude
                    )
                } else null
                MatchThreadItem(
                    match = match,
                    partnerId = partnerId,
                    partnerProfile = partnerProfile,
                    distanceKm = dist
                )
            }

        val blockedUserItems = buildList {
            blocking.usersIBlocked.forEach { block ->
                val name = profileById[block.blockedUid]?.displayName ?: "Kindred Member"
                add(
                    BlockedUserItem(
                        blockedUid = block.blockedUid,
                        displayName = name,
                        reason = block.reason,
                        isDemo = false
                    )
                )
            }
            blocking.demoBlocked.values.forEach { add(it) }
        }

        val geoMeta = spatialSnapshot?.let {
            GeoHashContextMeta(
                centerGeoHash = it.centerGeoHash,
                queryPrecision = it.queryPrecision,
                queriedCells = it.queriedCells
            )
        }

        DashboardSnapshot(
            privateUser = privateUser,
            myProfile = myProfile,
            discoveryCandidates = candidates,
            searchableProfiles = searchableCandidates,
            matches = matchItems,
            verifications = verifications,
            geoHashMeta = geoMeta,
            includeDemoProfiles = includeDemos,
            blockedUsers = blockedUserItems
        )
    }.asFirestoreUiState("kindred data")

    private val _selectedMatchId = MutableStateFlow<String?>(null)
    val selectedMatchId: StateFlow<String?> = _selectedMatchId.asStateFlow()

    private val _chatRetryNonce = MutableStateFlow(0)

    val activeChatMessages: StateFlow<UiState<List<ChatMessage>>> = combine(
        _selectedMatchId,
        _chatRetryNonce
    ) { matchId, _ -> matchId }
        .flatMapLatest { matchId ->
            when {
                matchId.isNullOrBlank() -> flowOf(emptyList())
                isDemoMatchId(matchId) -> _demoMessages.map { it[matchId].orEmpty() }
                else -> repository.observeMessages(matchId, currentUserId)
            }
        }
        .asFirestoreUiState("chat messages")

    val activePartnerPresence: StateFlow<UserPresence?> = combine(
        _selectedMatchId,
        matchesFlow
    ) { selectedId, matches ->
        val match = matches.firstOrNull { it.id == selectedId }
        match?.participantUids?.firstOrNull { it != currentUserId }.orEmpty()
    }
        .distinctUntilChanged()
        .flatMapLatest { partnerId ->
            if (partnerId.isBlank()) {
                flowOf(null)
            } else {
                repository.observeUserPresence(partnerId, currentUserId)
                    .catch { error ->
                        val firestoreError = generateSequence(error) { it.cause }
                            .filterIsInstance<FirebaseFirestoreException>()
                            .firstOrNull()
                        if (firestoreError == null && error is CancellationException) throw error
                        Log.w(TAG, "Error observing presence for $partnerId", firestoreError ?: error)
                        emit(null)
                    }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = null
        )

    val activePartnerTyping: StateFlow<TypingStatus?> = combine(
        _selectedMatchId,
        matchesFlow
    ) { selectedId, matches ->
        val match = matches.firstOrNull { it.id == selectedId }
        val partnerId = match?.participantUids?.firstOrNull { it != currentUserId }.orEmpty()
        (selectedId ?: "") to partnerId
    }
        .distinctUntilChanged()
        .flatMapLatest { (matchId, partnerId) ->
            if (matchId.isBlank() || partnerId.isBlank()) {
                flowOf(null)
            } else {
                repository.observePartnerTyping(matchId, partnerId, currentUserId)
                    .catch { error ->
                        val firestoreError = generateSequence(error) { it.cause }
                            .filterIsInstance<FirebaseFirestoreException>()
                            .firstOrNull()
                        if (firestoreError == null && error is CancellationException) throw error
                        Log.w(TAG, "Error observing typing for $partnerId", firestoreError ?: error)
                        emit(null)
                    }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = null
        )

    val activeMatchCallSession: StateFlow<CallSession?> = _selectedMatchId
        .flatMapLatest { matchId ->
            if (matchId.isNullOrBlank()) {
                flowOf(null)
            } else {
                repository.observeMatchCalls(matchId, currentUserId)
                    .map { calls ->
                        calls.firstOrNull { it.status == "ACTIVE" || it.status == "RINGING" }
                    }
                    .catch { error ->
                        val firestoreError = generateSequence(error) { it.cause }
                            .filterIsInstance<FirebaseFirestoreException>()
                            .firstOrNull()
                        if (firestoreError == null && error is CancellationException) throw error
                        Log.w(TAG, "Error observing calls for match=$matchId", firestoreError ?: error)
                        emit(null)
                    }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = null
        )

    private val _actionStatusMessage = MutableStateFlow<String?>(null)
    val actionStatusMessage: StateFlow<String?> = _actionStatusMessage.asStateFlow()

    private val _newMatchPopup = MutableStateFlow<MatchThreadItem?>(null)
    val newMatchPopup: StateFlow<MatchThreadItem?> = _newMatchPopup.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _activeChallenge = MutableStateFlow(KindredConstants.VERIFICATION_CHALLENGES.first())
    val activeChallenge: StateFlow<VerificationChallenge> = _activeChallenge.asStateFlow()

    private val _icebreakerAiState = MutableStateFlow<IcebreakerAiUiState>(IcebreakerAiUiState.Idle)
    val icebreakerAiState: StateFlow<IcebreakerAiUiState> = _icebreakerAiState.asStateFlow()

    private val _pushAlerts = MutableSharedFlow<KindredPushAlert>(extraBufferCapacity = 16)
    val pushAlerts: SharedFlow<KindredPushAlert> = _pushAlerts.asSharedFlow()

    init {
        viewModelScope.launch {
            var initialized = false
            val knownMatchIds = mutableSetOf<String>()
            val knownLastMessageAt = mutableMapOf<String, Long>()

            combine(
                matchesFlow,
                allProfilesFlow,
                blockingStateFlow
            ) { matches, profiles, blocking ->
                Triple(matches, profiles.associateBy { it.userId }, blocking.allBlockedIds)
            }
                .catch { error ->
                    if (error is CancellationException) throw error
                    Log.w(TAG, "Push alert stream paused", error)
                }
                .collect { (matches, profileMap, blockedIds) ->
                    val visibleMatches = matches.filter { match ->
                        val partnerId = match.participantUids.firstOrNull { it != currentUserId } ?: ""
                        partnerId.isNotBlank() && partnerId !in blockedIds
                    }

                    if (!initialized) {
                        visibleMatches.forEach { m ->
                            knownMatchIds += m.id
                            knownLastMessageAt[m.id] = m.lastMessageAt?.toDate()?.time ?: 0L
                        }
                        initialized = true
                        return@collect
                    }

                    visibleMatches.forEach { match ->
                        val partnerId = match.participantUids.firstOrNull { it != currentUserId } ?: ""
                        val partnerName = profileMap[partnerId]?.displayName ?: "Kindred Match"
                        val isBrandNewMatch = knownMatchIds.add(match.id)
                        if (isBrandNewMatch && match.createdBy != currentUserId) {
                            _pushAlerts.tryEmit(
                                KindredPushAlert.NewMatch(
                                    matchId = match.id,
                                    partnerName = partnerName
                                )
                            )
                        }

                        val latestMsgMs = match.lastMessageAt?.toDate()?.time ?: 0L
                        val previousMsgMs = knownLastMessageAt[match.id] ?: 0L
                        if (latestMsgMs > previousMsgMs) {
                            knownLastMessageAt[match.id] = latestMsgMs
                            val senderId = match.lastMessageSenderId
                            if (!senderId.isNullOrBlank() &&
                                senderId != currentUserId &&
                                _selectedMatchId.value != match.id
                            ) {
                                _pushAlerts.tryEmit(
                                    KindredPushAlert.NewMessage(
                                        matchId = match.id,
                                        senderName = partnerName,
                                        messageText = match.lastMessageText ?: "Sent you a new message"
                                    )
                                )
                            }
                        }
                    }
                }
        }
    }

    fun selectMatch(matchId: String?) {
        _selectedMatchId.value = matchId
    }

    fun syncFcmToken() {
        viewModelScope.launch {
            try {
                @Suppress("DEPRECATION")
                val token = FirebaseMessaging.getInstance().token.await()
                if (!token.isNullOrBlank()) {
                    repository.saveFcmToken(token)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "FCM token retrieval skipped/failed", e)
            }
        }
    }

    suspend fun unregisterFcmTokenOnSignOut() {
        try {
            @Suppress("DEPRECATION")
            val token = FirebaseMessaging.getInstance().token.await()
            if (!token.isNullOrBlank()) {
                repository.deleteFcmToken(token)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "FCM token cleanup skipped on sign-out", e)
        }
    }

    fun retryChatObservation() {
        _chatRetryNonce.value += 1
    }

    suspend fun updatePresenceNow(online: Boolean) {
        repository.updatePresence(online)
    }

    fun setPresenceOnline(online: Boolean) {
        viewModelScope.launch {
            repository.updatePresence(online)
        }
    }

    fun dismissNewMatchPopup() {
        _newMatchPopup.value = null
    }

    fun clearStatusMessage() {
        _actionStatusMessage.value = null
    }

    fun postStatusMessage(message: String) {
        _actionStatusMessage.value = message
    }

    fun cycleVerificationChallenge() {
        val list = KindredConstants.VERIFICATION_CHALLENGES
        val nextIndex = (list.indexOf(_activeChallenge.value) + 1) % list.size
        _activeChallenge.value = list[nextIndex]
    }

    fun onLiveLocationTick(resolved: ResolvedDeviceLocation) {
        _latestLocation.value = resolved
        val now = System.currentTimeMillis()
        val anchorLat = lastPersistedGeoLat
        val anchorLng = lastPersistedGeoLng
        val movedKm = if (anchorLat != null && anchorLng != null) {
            LocationAndGeoHelper.calculateDistanceKm(
                anchorLat,
                anchorLng,
                resolved.exactLatitude,
                resolved.exactLongitude
            )
        } else {
            Double.MAX_VALUE
        }
        if (movedKm >= 0.5 && (now - lastGeoPersistMs) >= 30_000L) {
            lastPersistedGeoLat = resolved.exactLatitude
            lastPersistedGeoLng = resolved.exactLongitude
            lastGeoPersistMs = now
            viewModelScope.launch {
                repository.updateLocationCoordinates(
                    latitude = resolved.exactLatitude,
                    longitude = resolved.exactLongitude,
                    cityLabel = resolved.cityLabel,
                    countryName = resolved.countryName
                )
            }
        }
    }

    fun applyResolvedLocation(resolved: ResolvedDeviceLocation) {
        _latestLocation.value = resolved
        lastPersistedGeoLat = resolved.exactLatitude
        lastPersistedGeoLng = resolved.exactLongitude
        lastGeoPersistMs = System.currentTimeMillis()
        viewModelScope.launch {
            val result = repository.updateLocationCoordinates(
                latitude = resolved.exactLatitude,
                longitude = resolved.exactLongitude,
                cityLabel = resolved.cityLabel,
                countryName = resolved.countryName
            )
            result.onSuccess {
                val cell = GeoHashUtils.encode(resolved.coarseLatitude, resolved.coarseLongitude, 4)
                val locLabel = if (resolved.countryName.isNotBlank()) {
                    "${resolved.cityLabel}, ${resolved.countryName}"
                } else {
                    resolved.cityLabel
                }
                _actionStatusMessage.value = "Location updated to $locLabel (GeoHash $cell)"
            }.onFailure { err ->
                _actionStatusMessage.value = err.localizedMessage ?: "Failed to update location"
            }
        }
    }

    fun toggleDemoProfiles(enabled: Boolean) {
        _includeDemoProfiles.value = enabled
        _actionStatusMessage.value = if (enabled) {
            "Showing 23 sample profiles (10W / 7M / 6NB)"
        } else {
            "Sample profiles hidden — showing live members only"
        }
    }

    fun resetDemoSwipes() {
        _demoSwipes.value = emptyMap()
        if (_lastUndoableSwipe.value?.isDemo == true) {
            _lastUndoableSwipe.value = null
        }
        _includeDemoProfiles.value = true
        _actionStatusMessage.value = "Restored all 23 sample profiles"
    }

    fun swipeProfile(candidate: DiscoveryCandidate, direction: String) {
        val targetId = candidate.profile.userId
        val targetName = candidate.profile.displayName

        if (SampleProfilesProvider.isDemoProfileId(targetId)) {
            _demoSwipes.value = _demoSwipes.value + (targetId to direction)
            _lastUndoableSwipe.value = UndoableSwipe(
                targetUserId = targetId,
                displayName = targetName,
                isDemo = true
            )
            if (direction == "LIKE") {
                _actionStatusMessage.value = "Liked $targetName (sample profile)"
            }
            return
        }

        if (targetId in _pendingSwipes.value) return
        _pendingSwipes.value = _pendingSwipes.value + (targetId to direction)
        _lastUndoableSwipe.value = UndoableSwipe(
            targetUserId = targetId,
            displayName = targetName,
            isDemo = false
        )

        viewModelScope.launch {
            val result = repository.recordSwipe(targetId, direction)
            result.onSuccess { createdMatchId ->
                if (!createdMatchId.isNullOrBlank()) {
                    // Mutual matches cannot be rewound silently after a match record is created
                    if (_lastUndoableSwipe.value?.targetUserId == targetId) {
                        _lastUndoableSwipe.value = null
                    }
                    _newMatchPopup.value = MatchThreadItem(
                        match = MatchRecord(
                            id = createdMatchId,
                            participantUids = listOf(currentUserId, targetId).sorted(),
                            createdBy = currentUserId
                        ),
                        partnerId = targetId,
                        partnerProfile = candidate.profile,
                        distanceKm = candidate.distanceKm
                    )
                }
            }.onFailure { err ->
                _pendingSwipes.value = _pendingSwipes.value - targetId
                if (_lastUndoableSwipe.value?.targetUserId == targetId) {
                    _lastUndoableSwipe.value = null
                }
                _actionStatusMessage.value = err.localizedMessage ?: "Could not save swipe"
            }
        }
    }

    fun undoLastSwipe() {
        val entry = _lastUndoableSwipe.value ?: return
        _lastUndoableSwipe.value = null

        if (entry.isDemo) {
            _demoSwipes.value = _demoSwipes.value - entry.targetUserId
            _actionStatusMessage.value = "Brought ${entry.displayName} back to your deck"
            return
        }

        val previousPending = _pendingSwipes.value[entry.targetUserId]
        _pendingSwipes.value = _pendingSwipes.value - entry.targetUserId

        viewModelScope.launch {
            val result = repository.deleteSwipe(entry.targetUserId)
            result.onSuccess {
                _actionStatusMessage.value = "Brought ${entry.displayName} back to your deck"
            }.onFailure { err ->
                if (previousPending != null) {
                    _pendingSwipes.value = _pendingSwipes.value + (entry.targetUserId to previousPending)
                }
                _actionStatusMessage.value = err.localizedMessage ?: "Could not undo swipe"
            }
        }
    }

    fun startDirectConversation(
        targetProfile: PublicProfile,
        onOpened: (matchId: String) -> Unit = {}
    ) {
        val targetId = targetProfile.userId
        if (targetId.isBlank() || targetId == currentUserId) return

        if (SampleProfilesProvider.isDemoProfileId(targetId)) {
            val matchId = "${DEMO_MATCH_PREFIX}$targetId"
            val nowTs = Timestamp.now()
            val alreadyExists = _demoMatches.value.any { it.id == matchId }
            if (!alreadyExists) {
                val sorted = listOf(currentUserId, targetId).sorted()
                val newThread = MatchRecord(
                    id = matchId,
                    participantUids = sorted,
                    createdBy = currentUserId,
                    conversationType = "DIRECT",
                    lastMessageReadByUids = listOf(currentUserId),
                    createdAt = nowTs,
                    updatedAt = nowTs
                )
                _demoMatches.value = listOf(newThread) + _demoMatches.value
            }
            _selectedMatchId.value = matchId
            onOpened(matchId)
            return
        }

        viewModelScope.launch {
            _isBusy.value = true
            val result = repository.createOrOpenDirectMatch(targetId)
            _isBusy.value = false
            result.onSuccess { matchId ->
                _selectedMatchId.value = matchId
                onOpened(matchId)
            }.onFailure { err ->
                _actionStatusMessage.value = err.localizedMessage ?: "Unable to start conversation"
            }
        }
    }

    fun sendMessage(
        matchId: String,
        participantUids: List<String>,
        text: String,
        messageType: String = "TEXT",
        mediaDataUrl: String? = null,
        mediaGifId: String? = null,
        mediaDurationSec: Long = 0L,
        disappearingSeconds: Long = 0L,
        viewOnce: Boolean = false
    ) {
        if (isDemoMatchId(matchId)) {
            val cleanText = text.trim().take(1000)
            if (cleanText.isEmpty() && messageType !in setOf("PHOTO", "VOICE", "GIF")) return
            val nowTs = Timestamp.now()
            val newMessage = ChatMessage(
                id = "demo_msg_${UUID.randomUUID().toString().take(8)}",
                matchId = matchId,
                senderId = currentUserId,
                participantUids = participantUids,
                text = cleanText,
                messageType = messageType,
                mediaDataUrl = mediaDataUrl,
                mediaGifId = mediaGifId,
                mediaDurationSec = mediaDurationSec,
                readByUids = listOf(currentUserId),
                disappearingSeconds = disappearingSeconds,
                viewOnce = viewOnce,
                createdAt = nowTs
            )
            val currentList = _demoMessages.value[matchId].orEmpty()
            _demoMessages.value = _demoMessages.value + (matchId to (currentList + newMessage))
            val preview = cleanText.ifBlank {
                when (messageType) {
                    "PHOTO" -> "📷 Photo"
                    "VOICE" -> "🎙️ Voice note"
                    "GIF" -> "🎞️ Sent a GIF"
                    else -> "Sent a message"
                }
            }.take(300)
            _demoMatches.value = _demoMatches.value.map { m ->
                if (m.id == matchId) {
                    m.copy(
                        lastMessageText = preview,
                        lastMessageSenderId = currentUserId,
                        lastMessageAt = nowTs,
                        lastMessageReadByUids = listOf(currentUserId),
                        updatedAt = nowTs
                    )
                } else m
            }
            return
        }

        viewModelScope.launch {
            val result = repository.sendMessage(
                matchId = matchId,
                participantUids = participantUids,
                text = text,
                messageType = messageType,
                mediaDataUrl = mediaDataUrl,
                mediaGifId = mediaGifId,
                mediaDurationSec = mediaDurationSec,
                disappearingSeconds = disappearingSeconds,
                viewOnce = viewOnce
            )
            result.onFailure { err ->
                _actionStatusMessage.value = err.localizedMessage ?: "Failed to send message"
            }
        }
    }

    fun markMessagesAsRead(matchId: String, messageIds: List<String>) {
        if (matchId.isBlank() || messageIds.isEmpty()) return
        if (isDemoMatchId(matchId)) {
            _demoMessages.value = _demoMessages.value.toMutableMap().apply {
                val updated = get(matchId).orEmpty().map { msg ->
                    if (msg.id in messageIds && currentUserId !in msg.readByUids) {
                        msg.copy(readByUids = msg.readByUids + currentUserId)
                    } else msg
                }
                put(matchId, updated)
            }
            _demoMatches.value = _demoMatches.value.map { m ->
                if (m.id == matchId && currentUserId !in m.lastMessageReadByUids) {
                    m.copy(lastMessageReadByUids = m.lastMessageReadByUids + currentUserId)
                } else m
            }
            return
        }
        viewModelScope.launch {
            repository.markMessagesAsRead(matchId, messageIds)
        }
    }

    fun markViewOnceOpened(matchId: String, messageId: String) {
        if (isDemoMatchId(matchId)) return
        viewModelScope.launch {
            repository.markViewOnceOpened(matchId, messageId)
        }
    }

    fun deleteExpiredMessage(matchId: String, messageId: String) {
        viewModelScope.launch {
            repository.deleteMessage(matchId, messageId)
        }
    }

    fun updateDisappearingTimer(matchId: String, ttlSeconds: Long) {
        viewModelScope.launch {
            val result = repository.updateMatchDisappearingTimer(matchId, ttlSeconds)
            result.onSuccess {
                val label = when (ttlSeconds) {
                    30L -> "30 seconds"
                    300L -> "5 minutes"
                    86400L -> "24 hours"
                    else -> "Off"
                }
                _actionStatusMessage.value = "Disappearing messages: $label"
            }.onFailure { err ->
                _actionStatusMessage.value = err.localizedMessage ?: "Failed to update disappearing timer"
            }
        }
    }

    fun setTypingStatus(matchId: String, isTyping: Boolean) {
        if (matchId.isBlank()) return
        viewModelScope.launch {
            repository.setTypingStatus(matchId, isTyping)
        }
    }

    fun generateAiIcebreakers(
        myProfile: PublicProfile?,
        partnerProfile: PublicProfile?,
        tone: String = "Balanced"
    ) {
        if (_icebreakerAiState.value is IcebreakerAiUiState.Loading) return
        _icebreakerAiState.value = IcebreakerAiUiState.Loading
        viewModelScope.launch {
            val result = aiIcebreakerService.generateAiOpeners(
                myProfile = myProfile,
                partnerProfile = partnerProfile,
                tonePreference = tone
            )
            result.onSuccess { suggestions ->
                _icebreakerAiState.value = IcebreakerAiUiState.Success(suggestions)
            }.onFailure { err ->
                _icebreakerAiState.value = IcebreakerAiUiState.Error(
                    err.localizedMessage ?: "Failed to generate AI icebreakers"
                )
            }
        }
    }

    fun resetIcebreakerAiState() {
        _icebreakerAiState.value = IcebreakerAiUiState.Idle
    }

    fun startCall(
        matchId: String,
        calleeUid: String,
        participantUids: List<String>,
        callType: String
    ) {
        viewModelScope.launch {
            val result = repository.startCall(matchId, calleeUid, participantUids, callType)
            result.onFailure { err ->
                _actionStatusMessage.value = err.localizedMessage ?: "Unable to start call"
            }
        }
    }

    fun answerCall(session: CallSession) {
        viewModelScope.launch {
            repository.updateCallSession(
                matchId = session.matchId,
                callId = session.id,
                status = "ACTIVE"
            )
        }
    }

    fun declineCall(session: CallSession) {
        viewModelScope.launch {
            repository.endCallAndLogMessage(
                matchId = session.matchId,
                callId = session.id,
                participantUids = session.participantUids,
                callType = session.callType,
                durationSeconds = 0L,
                declined = true
            )
        }
    }

    fun toggleCallMute(session: CallSession) {
        val isCaller = session.callerUid == currentUserId
        viewModelScope.launch {
            repository.updateCallSession(
                matchId = session.matchId,
                callId = session.id,
                callerMuted = if (isCaller) !session.callerMuted else null,
                calleeMuted = if (!isCaller) !session.calleeMuted else null
            )
        }
    }

    fun toggleCallCamera(session: CallSession) {
        val isCaller = session.callerUid == currentUserId
        viewModelScope.launch {
            repository.updateCallSession(
                matchId = session.matchId,
                callId = session.id,
                callerCameraOn = if (isCaller) !session.callerCameraOn else null,
                calleeCameraOn = if (!isCaller) !session.calleeCameraOn else null
            )
        }
    }

    fun endCall(session: CallSession, durationSeconds: Long) {
        viewModelScope.launch {
            repository.endCallAndLogMessage(
                matchId = session.matchId,
                callId = session.id,
                participantUids = session.participantUids,
                callType = session.callType,
                durationSeconds = durationSeconds,
                declined = false
            )
        }
    }

    fun unmatch(matchId: String) {
        if (isDemoMatchId(matchId)) {
            _demoMatches.value = _demoMatches.value.filterNot { it.id == matchId }
            _demoMessages.value = _demoMessages.value - matchId
            if (_selectedMatchId.value == matchId) {
                _selectedMatchId.value = null
            }
            _actionStatusMessage.value = "Conversation removed"
            return
        }
        viewModelScope.launch {
            _isBusy.value = true
            val result = repository.unmatch(matchId)
            _isBusy.value = false
            result.onSuccess {
                if (_selectedMatchId.value == matchId) {
                    _selectedMatchId.value = null
                }
                _actionStatusMessage.value = "Match removed"
            }.onFailure { err ->
                _actionStatusMessage.value = err.localizedMessage ?: "Failed to unmatch"
            }
        }
    }

    fun blockUser(
        targetUserId: String,
        displayName: String,
        reason: String? = null,
        onBlocked: () -> Unit = {}
    ) {
        if (SampleProfilesProvider.isDemoProfileId(targetUserId)) {
            val demoMatchId = "${DEMO_MATCH_PREFIX}$targetUserId"
            _demoMatches.value = _demoMatches.value.filterNot { it.id == demoMatchId }
            _demoMessages.value = _demoMessages.value - demoMatchId
            if (_selectedMatchId.value == demoMatchId) {
                _selectedMatchId.value = null
            }
            _demoBlockedUsers.value = _demoBlockedUsers.value + (
                targetUserId to BlockedUserItem(
                    blockedUid = targetUserId,
                    displayName = displayName,
                    reason = reason,
                    isDemo = true
                )
            )
            _actionStatusMessage.value = "Blocked $displayName"
            onBlocked()
            return
        }

        viewModelScope.launch {
            _isBusy.value = true
            val result = repository.blockUser(targetUserId, reason)
            _isBusy.value = false
            result.onSuccess {
                val sorted = listOf(currentUserId, targetUserId).sorted()
                val potentialMatchId = "${sorted[0]}_${sorted[1]}"
                if (_selectedMatchId.value == potentialMatchId) {
                    _selectedMatchId.value = null
                }
                _actionStatusMessage.value = "Blocked $displayName and excluded from matching"
                onBlocked()
            }.onFailure { err ->
                _actionStatusMessage.value = err.localizedMessage ?: "Failed to block user"
            }
        }
    }

    fun unblockUser(targetUserId: String) {
        if (SampleProfilesProvider.isDemoProfileId(targetUserId)) {
            _demoBlockedUsers.value = _demoBlockedUsers.value - targetUserId
            _actionStatusMessage.value = "User unblocked"
            return
        }

        viewModelScope.launch {
            _isBusy.value = true
            val result = repository.unblockUser(targetUserId)
            _isBusy.value = false
            result.onSuccess {
                _actionStatusMessage.value = "User unblocked"
            }.onFailure { err ->
                _actionStatusMessage.value = err.localizedMessage ?: "Failed to unblock user"
            }
        }
    }

    fun saveProfile(
        displayName: String,
        age: Int,
        gender: String,
        bio: String,
        cityLabel: String,
        country: String = "United States",
        latitude: Double,
        longitude: Double,
        interests: List<String>,
        photoDataUrl: String?,
        profilePhotos: List<String> = emptyList(),
        discoverable: Boolean,
        interestedIn: String,
        minAgePref: Int,
        maxAgePref: Int,
        maxDistanceKm: Int,
        verifiedOnlyFilter: Boolean,
        onComplete: () -> Unit = {}
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            val result = repository.saveProfileAndPreferences(
                email = userEmail,
                displayName = displayName,
                age = age,
                gender = gender,
                bio = bio,
                cityLabel = cityLabel,
                country = country,
                latitude = latitude,
                longitude = longitude,
                interests = interests,
                photoDataUrl = photoDataUrl,
                profilePhotos = profilePhotos,
                discoverable = discoverable,
                interestedIn = interestedIn,
                minAgePref = minAgePref,
                maxAgePref = maxAgePref,
                maxDistanceKm = maxDistanceKm,
                verifiedOnlyFilter = verifiedOnlyFilter
            )
            _isBusy.value = false
            result.onSuccess {
                _actionStatusMessage.value = "Profile & GeoHash index saved"
                onComplete()
            }.onFailure { err ->
                _actionStatusMessage.value = err.localizedMessage ?: "Failed to save profile"
            }
        }
    }

    fun updateProfileContent(
        bio: String,
        interests: List<String>,
        profilePhotos: List<String>,
        cityLabel: String? = null,
        country: String? = null,
        onComplete: () -> Unit = {}
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            val result = repository.updateProfileContent(
                email = userEmail,
                fallbackDisplayName = defaultDisplayName,
                bio = bio,
                interests = interests,
                profilePhotos = profilePhotos,
                cityLabel = cityLabel,
                country = country
            )
            _isBusy.value = false
            result.onSuccess {
                _actionStatusMessage.value = "Profile updated in Firestore"
                onComplete()
            }.onFailure { err ->
                _actionStatusMessage.value = err.localizedMessage ?: "Failed to update profile"
            }
        }
    }

    fun updateFilters(
        interestedIn: String,
        minAgePref: Int,
        maxAgePref: Int,
        maxDistanceKm: Int,
        verifiedOnlyFilter: Boolean
    ) {
        viewModelScope.launch {
            val result = repository.updateDiscoveryFilters(
                interestedIn = interestedIn,
                minAgePref = minAgePref,
                maxAgePref = maxAgePref,
                maxDistanceKm = maxDistanceKm,
                verifiedOnlyFilter = verifiedOnlyFilter
            )
            result.onSuccess {
                _actionStatusMessage.value = "Discovery preferences updated"
            }.onFailure { err ->
                _actionStatusMessage.value = err.localizedMessage ?: "Save your profile first to store filters"
            }
        }
    }

    fun saveSettingsPreferences(
        interestedIn: String,
        minAgePref: Int,
        maxAgePref: Int,
        maxDistanceKm: Int,
        verifiedOnlyFilter: Boolean,
        myGender: String?,
        discoverable: Boolean?,
        onComplete: () -> Unit = {}
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            val result = repository.saveSettingsPreferences(
                email = userEmail,
                interestedIn = interestedIn,
                minAgePref = minAgePref,
                maxAgePref = maxAgePref,
                maxDistanceKm = maxDistanceKm,
                verifiedOnlyFilter = verifiedOnlyFilter,
                myGender = myGender,
                discoverable = discoverable
            )
            _isBusy.value = false
            result.onSuccess {
                _actionStatusMessage.value = "Dating preferences saved to Firestore"
                onComplete()
            }.onFailure { err ->
                _actionStatusMessage.value = err.localizedMessage ?: "Failed to save dating preferences"
            }
        }
    }

    fun submitVerification(
        challenge: VerificationChallenge,
        analysis: LivenessAnalysisResult,
        locationVerified: Boolean
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            val result = repository.submitVerification(
                challenge = challenge,
                analysis = analysis,
                locationVerified = locationVerified,
                googleVerified = isEmailVerified
            )
            _isBusy.value = false
            result.onSuccess { score ->
                _actionStatusMessage.value = "Profile verified! Trust score: $score/100"
            }.onFailure { err ->
                _actionStatusMessage.value = err.localizedMessage ?: "Verification failed"
            }
        }
    }

    private companion object {
        const val TAG = "KindredVM"
        const val STOP_TIMEOUT_MILLIS = 5000L
        const val DEMO_MATCH_PREFIX = "demo_match_"

        fun isDemoMatchId(matchId: String): Boolean = matchId.startsWith(DEMO_MATCH_PREFIX)
    }
}
