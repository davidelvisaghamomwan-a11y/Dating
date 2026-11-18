package com.example.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.KindredRepository
import com.example.data.SampleProfilesProvider
import com.example.model.BlockRecord
import com.example.model.BlockedUserItem
import com.example.model.ChatMessage
import com.example.model.DiscoveryCandidate
import com.example.model.KindredConstants
import com.example.model.LivenessAnalysisResult
import com.example.model.MatchRecord
import com.example.model.MatchThreadItem
import com.example.model.PrivateUser
import com.example.model.PublicProfile
import com.example.model.SwipeRecord
import com.example.model.UiState
import com.example.model.UserPresence
import com.example.model.VerificationChallenge
import com.example.model.VerificationRecord
import com.example.service.GeoHashMatchingService
import com.example.service.GeoHashSpatialSnapshot
import com.example.util.GeoHashUtils
import com.example.util.LocationAndGeoHelper
import com.example.util.ResolvedDeviceLocation
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
    val isEmailVerified: Boolean
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

    // Real-time GeoHash cell stream keyed by the user's location and search radius
    private val spatialGeoCellsFlow: Flow<GeoHashSpatialSnapshot?> = combine(
        privateUserFlow,
        myProfileFlow
    ) { priv, pub ->
        val lat = priv?.exactLatitude ?: pub?.coarseLatitude
        val lng = priv?.exactLongitude ?: pub?.coarseLongitude
        val dist = (priv?.maxDistanceKm ?: 100L).toDouble()
        if (lat != null && lng != null) SpatialQueryKey(lat, lng, dist) else null
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
    private val _demoBlockedUsers = MutableStateFlow<Map<String, BlockedUserItem>>(emptyMap())

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

    val dashboardState: StateFlow<UiState<DashboardSnapshot>> = combine(
        privateUserFlow,
        myProfileFlow,
        allProfilesFlow,
        spatialGeoCellsFlow,
        outgoingSwipesFlow,
        matchesFlow,
        verificationsFlow,
        _includeDemoProfiles,
        _demoSwipes,
        blockingStateFlow
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val privateUser = values[0] as PrivateUser?
        val myProfile = values[1] as PublicProfile?
        @Suppress("UNCHECKED_CAST")
        val allProfiles = values[2] as List<PublicProfile>
        val spatialSnapshot = values[3] as GeoHashSpatialSnapshot?
        @Suppress("UNCHECKED_CAST")
        val swipes = values[4] as List<SwipeRecord>
        @Suppress("UNCHECKED_CAST")
        val matches = values[5] as List<MatchRecord>
        @Suppress("UNCHECKED_CAST")
        val verifications = values[6] as List<VerificationRecord>
        val includeDemos = values[7] as Boolean
        @Suppress("UNCHECKED_CAST")
        val demoSwipes = values[8] as Map<String, String>
        val blocking = values[9] as BlockingSnapshot

        val blockedIds = blocking.allBlockedIds
        val swipedIds = swipes.map { it.toUserId }.toSet() + demoSwipes.keys
        val myLat = privateUser?.exactLatitude ?: myProfile?.coarseLatitude
        val myLng = privateUser?.exactLongitude ?: myProfile?.coarseLongitude

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

        val profileById = allProfiles.associateBy { it.userId }
        val matchItems = matches
            .filter { match ->
                val partnerId = match.participantUids.firstOrNull { it != currentUserId } ?: ""
                partnerId.isNotBlank() && partnerId !in blockedIds
            }
            .map { match ->
                val partnerId = match.participantUids.firstOrNull { it != currentUserId } ?: ""
                val partnerProfile = profileById[partnerId]
                val dist = if (myLat != null && myLng != null && partnerProfile != null) {
                    LocationAndGeoHelper.haversineDistanceKm(
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
            if (matchId.isNullOrBlank()) {
                flowOf(emptyList())
            } else {
                repository.observeMessages(matchId, currentUserId)
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

    private val _actionStatusMessage = MutableStateFlow<String?>(null)
    val actionStatusMessage: StateFlow<String?> = _actionStatusMessage.asStateFlow()

    private val _newMatchPopup = MutableStateFlow<MatchThreadItem?>(null)
    val newMatchPopup: StateFlow<MatchThreadItem?> = _newMatchPopup.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _latestLocation = MutableStateFlow<ResolvedDeviceLocation?>(null)
    val latestLocation: StateFlow<ResolvedDeviceLocation?> = _latestLocation.asStateFlow()

    private val _activeChallenge = MutableStateFlow(KindredConstants.VERIFICATION_CHALLENGES.first())
    val activeChallenge: StateFlow<VerificationChallenge> = _activeChallenge.asStateFlow()

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
        runCatching {
            FirebaseMessaging.getInstance().token
                .addOnSuccessListener { token ->
                    if (!token.isNullOrBlank()) {
                        viewModelScope.launch {
                            repository.saveFcmToken(token)
                        }
                    }
                }
                .addOnFailureListener { err ->
                    Log.w(TAG, "FCM token retrieval skipped/failed", err)
                }
        }.onFailure { err ->
            Log.w(TAG, "FirebaseMessaging unavailable in current runtime", err)
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

    fun applyResolvedLocation(resolved: ResolvedDeviceLocation) {
        _latestLocation.value = resolved
        viewModelScope.launch {
            val result = repository.updateLocationCoordinates(
                latitude = resolved.exactLatitude,
                longitude = resolved.exactLongitude,
                cityLabel = resolved.cityLabel
            )
            result.onSuccess {
                val cell = GeoHashUtils.encode(resolved.coarseLatitude, resolved.coarseLongitude, 4)
                _actionStatusMessage.value = "Location updated to ${resolved.cityLabel} (GeoHash $cell)"
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
        _includeDemoProfiles.value = true
        _actionStatusMessage.value = "Restored all 23 sample profiles"
    }

    fun swipeProfile(candidate: DiscoveryCandidate, direction: String) {
        if (SampleProfilesProvider.isDemoProfileId(candidate.profile.userId)) {
            _demoSwipes.value = _demoSwipes.value + (candidate.profile.userId to direction)
            if (direction == "LIKE") {
                _actionStatusMessage.value = "Liked ${candidate.profile.displayName} (sample profile)"
            }
            return
        }

        viewModelScope.launch {
            val result = repository.recordSwipe(candidate.profile.userId, direction)
            result.onSuccess { createdMatchId ->
                if (!createdMatchId.isNullOrBlank()) {
                    _newMatchPopup.value = MatchThreadItem(
                        match = MatchRecord(
                            id = createdMatchId,
                            participantUids = listOf(currentUserId, candidate.profile.userId).sorted(),
                            createdBy = currentUserId
                        ),
                        partnerId = candidate.profile.userId,
                        partnerProfile = candidate.profile,
                        distanceKm = candidate.distanceKm
                    )
                }
            }.onFailure { err ->
                _actionStatusMessage.value = err.localizedMessage ?: "Could not save swipe"
            }
        }
    }

    fun sendMessage(
        matchId: String,
        participantUids: List<String>,
        text: String,
        messageType: String = "TEXT"
    ) {
        viewModelScope.launch {
            val result = repository.sendMessage(matchId, participantUids, text, messageType)
            result.onFailure { err ->
                _actionStatusMessage.value = err.localizedMessage ?: "Failed to send message"
            }
        }
    }

    fun unmatch(matchId: String) {
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
        onComplete: () -> Unit = {}
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            val result = repository.updateProfileContent(
                email = userEmail,
                fallbackDisplayName = defaultDisplayName,
                bio = bio,
                interests = interests,
                profilePhotos = profilePhotos
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
    }
}
