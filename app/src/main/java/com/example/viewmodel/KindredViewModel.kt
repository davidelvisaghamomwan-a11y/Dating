package com.example.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.auth.PendingRegistrationData
import com.example.data.CloudMediaStorageService
import com.example.data.KindredRepository
import com.example.data.SocialAndMediaRepository
import com.example.model.ActiveMemberPresenceItem
import com.example.model.BlockRecord
import com.example.model.BlockedUserItem
import com.example.model.CallAudioChunk
import com.example.model.CallSession
import com.example.model.CallVideoFrame
import com.example.model.ChatMessage
import com.example.model.DiscoveryCandidate
import com.example.model.FavoriteRecord
import com.example.model.FeedComment
import com.example.model.FeedPost
import com.example.model.FollowRecord
import com.example.model.KindredConstants
import com.example.model.MatchRecord
import com.example.model.MatchThreadItem
import com.example.model.ModerationNoticeRecord
import com.example.model.PrivateUser
import com.example.model.ProfileLikeRecord
import com.example.model.ProfileVideoItem
import com.example.model.ProfileViewRecord
import com.example.model.PublicProfile
import com.example.model.ReportRecord
import com.example.model.SocialActivityItem
import com.example.model.StoryHighlight
import com.example.model.StoryItem
import com.example.model.SuggestedFriendItem
import com.example.model.TypingStatus
import com.example.model.UiState
import com.example.model.UserPresence
import com.example.model.VideoPost
import com.example.service.GeoHashMatchingService
import com.example.service.GeoHashSpatialSnapshot
import com.example.util.GeoHashUtils
import com.example.util.LocationAndGeoHelper
import com.example.util.PreparedVideoUpload
import com.example.util.PresenceUtils
import com.example.util.ResolvedDeviceLocation
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
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
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

sealed interface KindredPushAlert {
    data class NewMessage(val matchId: String, val senderName: String, val messageText: String) : KindredPushAlert
    data class IncomingCall(
        val matchId: String,
        val callId: String,
        val callerName: String,
        val callType: String
    ) : KindredPushAlert
    data class NewFollowerAlert(val fromUid: String, val fromName: String) : KindredPushAlert
    data class ModerationNoticeAlert(val notice: ModerationNoticeRecord) : KindredPushAlert
}

data class IncomingCallAlert(
    val session: CallSession,
    val thread: MatchThreadItem
)

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
    val geoHashMeta: GeoHashContextMeta?,
    val blockedUsers: List<BlockedUserItem> = emptyList()
)

private data class SpatialQueryKey(
    val lat: Double,
    val lng: Double,
    val maxDistanceKm: Double
)

private data class BlockingSnapshot(
    val usersIBlocked: List<BlockRecord>,
    val usersWhoBlockedMe: List<BlockRecord>
) {
    val allBlockedIds: Set<String>
        get() = usersIBlocked.map { it.blockedUid }.toSet() +
            usersWhoBlockedMe.map { it.blockerUid }.toSet()
}

@OptIn(ExperimentalCoroutinesApi::class)
class KindredViewModel(
    private val repository: KindredRepository,
    private val geoService: GeoHashMatchingService,
    val currentUserId: String,
    val userEmail: String?,
    val defaultDisplayName: String?,
    private val socialRepo: SocialAndMediaRepository = repository.socialRepository
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
    private val matchesFlow = repository.observeMyMatches(currentUserId)
    private val usersIBlockedFlow = repository.observeUsersIBlocked(currentUserId)
        .catch { emit(emptyList()) }
        .onStart { emit(emptyList()) }
    private val usersWhoBlockedMeFlow = repository.observeUsersWhoBlockedMe(currentUserId)
        .catch { emit(emptyList()) }
        .onStart { emit(emptyList()) }

    private val _latestLocation = MutableStateFlow<ResolvedDeviceLocation?>(null)
    val latestLocation: StateFlow<ResolvedDeviceLocation?> = _latestLocation.asStateFlow()

    private var lastPersistedGeoLat: Double? = null
    private var lastPersistedGeoLng: Double? = null
    private var lastPersistedCity: String? = null
    private var lastPersistedCountry: String? = null
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

    private val blockingStateFlow: Flow<BlockingSnapshot> = combine(
        usersIBlockedFlow,
        usersWhoBlockedMeFlow
    ) { iBlocked, blockedMe ->
        BlockingSnapshot(
            usersIBlocked = iBlocked,
            usersWhoBlockedMe = blockedMe
        )
    }

    private val sortedMatchesFlow: Flow<List<MatchRecord>> = matchesFlow
        .map { remoteMatches ->
            remoteMatches
                .distinctBy { it.id }
                .sortedByDescending { it.lastMessageAt ?: it.updatedAt ?: it.createdAt ?: Timestamp(0, 0) }
        }

    val dashboardState: StateFlow<UiState<DashboardSnapshot>> = combine(
        effectiveLocationFlow,
        allProfilesFlow,
        spatialGeoCellsFlow,
        sortedMatchesFlow,
        blockingStateFlow
    ) { locationTriple, allProfiles, spatialSnapshot, matches, blocking ->
        val privateUser = locationTriple.first
        val myProfile = locationTriple.second
        val liveLocation = locationTriple.third

        val blockedIds = blocking.allBlockedIds
        val myLat = liveLocation?.exactLatitude ?: privateUser?.exactLatitude ?: myProfile?.coarseLatitude
        val myLng = liveLocation?.exactLongitude ?: privateUser?.exactLongitude ?: myProfile?.coarseLongitude
        val myInterests = myProfile?.interests ?: emptyList()

        val myGeoHash = spatialSnapshot?.centerGeoHash ?: if (myLat != null && myLng != null) {
            GeoHashUtils.encode(myLat, myLng, 7)
        } else {
            ""
        }

        val firestoreProfiles = if (spatialSnapshot != null && spatialSnapshot.profiles.isNotEmpty()) {
            val cellMap = spatialSnapshot.profiles.associateBy { it.userId }.toMutableMap()
            allProfiles.forEach { legacy ->
                if (legacy.userId !in cellMap && legacy.geohash.isBlank()) {
                    cellMap[legacy.userId] = legacy
                }
            }
            cellMap.values.filter { !it.suspended }
        } else {
            (spatialSnapshot?.profiles ?: allProfiles).filter { !it.suspended }
        }

        val candidates = geoService.rankAndFilterCandidates(
            rawProfiles = firestoreProfiles,
            currentUserId = currentUserId,
            myLat = myLat,
            myLng = myLng,
            myGeoHash = myGeoHash,
            maxDistanceKm = 500.0,
            minAge = 18,
            maxAge = 99,
            interestedIn = "Everyone",
            myInterests = myInterests,
            excludedUserIds = emptySet(),
            blockedUserIds = blockedIds
        )

        val allSearchableMap = LinkedHashMap<String, PublicProfile>().apply {
            allProfiles.forEach { put(it.userId, it) }
            firestoreProfiles.forEach { if (!containsKey(it.userId)) put(it.userId, it) }
        }
        val searchableCandidates = allSearchableMap.values
            .filter { profile ->
                profile.userId.isNotBlank() &&
                    profile.userId != currentUserId &&
                    profile.discoverable &&
                    !profile.suspended &&
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
            .sortedBy { it.distanceKm }

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

        val blockedUserItems = blocking.usersIBlocked.map { block ->
            val name = profileById[block.blockedUid]?.displayName ?: "Kindred Member"
            BlockedUserItem(
                blockedUid = block.blockedUid,
                displayName = name,
                reason = block.reason
            )
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
            geoHashMeta = geoMeta,
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

    private val allMatchCallSessionsFlow: Flow<List<CallSession>> = matchesFlow
        .map { matches -> matches.map { it.id }.distinct().sorted() }
        .distinctUntilChanged()
        .flatMapLatest { matchIds ->
            if (matchIds.isEmpty()) {
                flowOf(emptyList())
            } else {
                val perMatchFlows = matchIds.map { matchId ->
                    repository.observeMatchCalls(matchId, currentUserId)
                        .catch { error ->
                            if (error is CancellationException) throw error
                            emit(emptyList())
                        }
                }
                combine(perMatchFlows) { arrays ->
                    arrays.flatMap { it }
                        .filter { it.status == "RINGING" || it.status == "ACTIVE" }
                        .sortedByDescending { it.updatedAt ?: it.createdAt ?: Timestamp(0, 0) }
                }
            }
        }

    val globalCallSession: StateFlow<CallSession?> = allMatchCallSessionsFlow
        .map { sessions ->
            sessions.firstOrNull { it.status == "ACTIVE" }
                ?: sessions.firstOrNull { it.status == "RINGING" }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = null
        )

    val globalIncomingCallAlert: StateFlow<IncomingCallAlert?> = combine(
        allMatchCallSessionsFlow,
        matchesFlow,
        allProfilesFlow
    ) { sessions, matches, profiles ->
        val ringing = sessions.firstOrNull { it.status == "RINGING" && it.calleeUid == currentUserId }
            ?: return@combine null
        val match = matches.firstOrNull { it.id == ringing.matchId }
            ?: MatchRecord(
                id = ringing.matchId,
                participantUids = ringing.participantUids,
                createdBy = ringing.callerUid
            )
        val partnerId = ringing.callerUid.takeIf { it.isNotBlank() }
            ?: match.participantUids.firstOrNull { it != currentUserId }
            ?: ""
        val partnerProfile = profiles.firstOrNull { it.userId == partnerId }
        val thread = MatchThreadItem(
            match = match,
            partnerId = partnerId,
            partnerProfile = partnerProfile,
            distanceKm = null
        )
        IncomingCallAlert(session = ringing, thread = thread)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = null
    )

    val activeCallAudioChunks: StateFlow<List<CallAudioChunk>> = combine(
        activeMatchCallSession,
        globalCallSession
    ) { selectedCall, globalCall ->
        val call = selectedCall ?: globalCall
        call?.takeIf { it.status == "ACTIVE" }
    }
        .distinctUntilChanged { a, b -> a?.id == b?.id && a?.status == b?.status }
        .flatMapLatest { session ->
            if (session == null) {
                flowOf(emptyList())
            } else {
                repository.observeCallAudioChunks(session.matchId, session.id, currentUserId)
                    .map { chunks -> chunks.filter { it.senderUid != currentUserId } }
                    .catch { error ->
                        if (error is CancellationException) throw error
                        emit(emptyList())
                    }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = emptyList()
        )

    val activeCallVideoFrames: StateFlow<List<CallVideoFrame>> = combine(
        activeMatchCallSession,
        globalCallSession
    ) { selectedCall, globalCall ->
        val call = selectedCall ?: globalCall
        call?.takeIf { it.status == "ACTIVE" }
    }
        .distinctUntilChanged { a, b -> a?.id == b?.id && a?.status == b?.status }
        .flatMapLatest { session ->
            if (session == null) {
                flowOf(emptyList())
            } else {
                repository.observeCallVideoFrames(session.matchId, session.id, currentUserId)
                    .map { frames -> frames.filter { it.senderUid != currentUserId } }
                    .catch { error ->
                        if (error is CancellationException) throw error
                        emit(emptyList())
                    }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = emptyList()
        )

    // --- Live Presence Directory (Online Now & Recently Left) ---

    val allPresenceState: StateFlow<List<UserPresence>> = socialRepo.observeAllPresence(currentUserId)
        .catch { error ->
            if (error is CancellationException) throw error
            emit(emptyList())
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = emptyList()
        )

    val activeMembersState: StateFlow<List<ActiveMemberPresenceItem>> = combine(
        allPresenceState,
        allProfilesFlow,
        blockingStateFlow
    ) { presences, profiles, blocking ->
        val blockedIds = blocking.allBlockedIds
        val presenceByUid = presences.associateBy { it.userId }
        val now = System.currentTimeMillis()
        profiles
            .filter { p ->
                p.userId.isNotBlank() &&
                    p.userId != currentUserId &&
                    p.discoverable &&
                    !p.suspended &&
                    p.userId !in blockedIds
            }
            .mapNotNull { profile ->
                val presence = presenceByUid[profile.userId] ?: return@mapNotNull null
                if (profile.hideOnlineStatus || presence.hideOnlineStatus) return@mapNotNull null
                val display = PresenceUtils.resolvePresenceDisplay(presence, now)
                ActiveMemberPresenceItem(
                    profile = profile,
                    presence = presence,
                    isOnline = display.isOnline,
                    isRecentlyLeft = display.isRecentlyLeft,
                    statusLabel = display.label
                )
            }
            .sortedWith(
                compareByDescending<ActiveMemberPresenceItem> { it.isOnline }
                    .thenByDescending { it.isRecentlyLeft }
                    .thenByDescending { it.presence.lastSeenAt?.toDate()?.time ?: 0L }
            )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = emptyList()
    )

    // --- Social Graph: Follows, Followers, Following & Suggested Profiles ---

    val allFollowsState: StateFlow<List<FollowRecord>> = combine(
        socialRepo.observeAllFollows(currentUserId).catch { emit(emptyList()) },
        blockingStateFlow
    ) { follows, blocking ->
        val blocked = blocking.allBlockedIds
        follows.filter { it.followerUid !in blocked && it.followingUid !in blocked }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = emptyList()
    )

    val followingIdsState: StateFlow<Set<String>> = allFollowsState
        .map { list ->
            list.filter { it.followerUid == currentUserId }.map { it.followingUid }.toSet()
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = emptySet()
        )

    val followerIdsState: StateFlow<Set<String>> = allFollowsState
        .map { list ->
            list.filter { it.followingUid == currentUserId }.map { it.followerUid }.toSet()
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = emptySet()
        )

    fun isFollowingUid(uid: String): Boolean = uid in followingIdsState.value

    val suggestedProfilesState: StateFlow<List<SuggestedFriendItem>> = combine(
        dashboardState,
        followingIdsState
    ) { dashUi, followingIds ->
        val snap = (dashUi as? UiState.Success)?.data ?: return@combine emptyList()
        val myProfile = snap.myProfile
        val myInterests = myProfile?.interests.orEmpty()
        val myCity = myProfile?.cityLabel.orEmpty()
        val myCountry = myProfile?.country.orEmpty()

        snap.searchableProfiles
            .filter { c ->
                c.profile.userId !in followingIds && c.profile.userId != currentUserId
            }
            .map { candidate ->
                val p = candidate.profile
                val shared = p.interests.filter { i -> myInterests.any { m -> m.equals(i, ignoreCase = true) } }
                val sameCity = myCity.isNotBlank() && p.cityLabel.equals(myCity, ignoreCase = true)
                val sameCountry = myCountry.isNotBlank() && p.country.equals(myCountry, ignoreCase = true)
                val score = (shared.size * 20) +
                    (if (sameCity) 25 else 0) +
                    (if (sameCountry) 10 else 0) +
                    (50 - candidate.distanceKm.toInt().coerceIn(0, 50))
                SuggestedFriendItem(
                    profile = p,
                    sharedInterests = shared,
                    sameCity = sameCity,
                    sameCountry = sameCountry,
                    distanceKm = candidate.distanceKm,
                    score = score
                )
            }
            .sortedByDescending { it.score }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = emptyList()
    )

    // --- Profile Views & Favorites ---

    val profileViewsState: StateFlow<List<ProfileViewRecord>> = socialRepo.observeIncomingProfileViews(currentUserId)
        .catch { error ->
            if (error is CancellationException) throw error
            emit(emptyList())
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = emptyList()
        )

    val profileLikesState: StateFlow<List<ProfileLikeRecord>> = socialRepo.observeProfileLikes(currentUserId)
        .catch { error ->
            if (error is CancellationException) throw error
            emit(emptyList())
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = emptyList()
        )

    val favoritesState: StateFlow<List<FavoriteRecord>> = socialRepo.observeFavorites(currentUserId)
        .catch { error ->
            if (error is CancellationException) throw error
            emit(emptyList())
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = emptyList()
        )

    // --- 24h Stories & Video Feed ---

    private val _pendingDeletedStoryIds = MutableStateFlow<Set<String>>(emptySet())
    private val _pendingDeletedVideoPostIds = MutableStateFlow<Set<String>>(emptySet())
    private val _pendingDeletedProfileVideoIds = MutableStateFlow<Set<String>>(emptySet())
    private val _pendingDeletedFeedPostIds = MutableStateFlow<Set<String>>(emptySet())
    private val _storyExpiryTickMs = MutableStateFlow(System.currentTimeMillis())

    val activeStoriesState: StateFlow<List<StoryItem>> = combine(
        socialRepo.observeActiveStories(currentUserId).catch { emit(emptyList()) },
        blockingStateFlow,
        _pendingDeletedStoryIds,
        _storyExpiryTickMs
    ) { stories, blocking, deletedStoryIds, nowMs ->
        val blocked = blocking.allBlockedIds
        stories.filter {
            it.userId !in blocked &&
                it.id !in deletedStoryIds &&
                !it.isExpiredAt(nowMs)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = emptyList()
    )

    val videoFeedState: StateFlow<List<VideoPost>> = combine(
        socialRepo.observeVideoFeed(currentUserId).catch { emit(emptyList()) },
        blockingStateFlow,
        _pendingDeletedVideoPostIds
    ) { posts, blocking, deletedVideoIds ->
        val blocked = blocking.allBlockedIds
        posts.filter { it.userId !in blocked && it.id !in deletedVideoIds }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = emptyList()
    )

    // --- Social Timeline Feed Posts & Comments ---

    private val _isFeedRefreshing = MutableStateFlow(false)
    val isFeedRefreshing: StateFlow<Boolean> = _isFeedRefreshing.asStateFlow()
    private val isAutoFeedRefreshing = AtomicBoolean(false)

    fun refreshFeed() {
        if (currentUserId.isBlank()) return
        if (_isFeedRefreshing.value) return
        viewModelScope.launch {
            _isFeedRefreshing.value = true
            try {
                kotlinx.coroutines.supervisorScope {
                    val uid = currentUserId
                    val postsJob = async { socialRepo.fetchLatestFeedPosts(uid) }
                    val storiesJob = async { socialRepo.fetchLatestActiveStories(uid) }
                    val videosJob = async { socialRepo.fetchLatestVideoFeed(uid) }
                    val postsRes = postsJob.await()
                    runCatching { storiesJob.await() }
                    runCatching { videosJob.await() }
                    postsRes.onFailure { err ->
                        _actionStatusMessage.value = err.localizedMessage ?: "Unable to refresh feed from server"
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Feed refresh failed gracefully", e)
                _actionStatusMessage.value = e.localizedMessage ?: "Unable to refresh feed from server"
            } finally {
                delay(250L)
                _isFeedRefreshing.value = false
            }
        }
    }

    fun autoRefreshFeed() {
        if (currentUserId.isBlank()) return
        if (!isAutoFeedRefreshing.compareAndSet(false, true)) return
        viewModelScope.launch {
            try {
                withTimeoutOrNull(8_000L) {
                    val uid = currentUserId
                    val postsJob = async { socialRepo.fetchLatestFeedPosts(uid) }
                    val storiesJob = async { socialRepo.fetchLatestActiveStories(uid) }
                    val videosJob = async { socialRepo.fetchLatestVideoFeed(uid) }
                    postsJob.await()
                    storiesJob.await()
                    videosJob.await()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Silent background auto-refresh: never disturb the user on transient offline checks
            } finally {
                isAutoFeedRefreshing.set(false)
            }
        }
    }

    val feedPostsState: StateFlow<List<FeedPost>> = combine(
        socialRepo.observeFeedPosts(currentUserId).catch { emit(emptyList()) },
        blockingStateFlow,
        _pendingDeletedFeedPostIds
    ) { posts, blocking, deletedPostIds ->
        val blocked = blocking.allBlockedIds
        posts.filter { post ->
            val resolvedId = post.id.ifBlank { post.postId }
            post.userId !in blocked && resolvedId !in deletedPostIds
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = emptyList()
    )

    val followingFeedPostsState: StateFlow<List<FeedPost>> = combine(
        feedPostsState,
        followingIdsState
    ) { posts, followingIds ->
        val visibleIds = followingIds + currentUserId
        posts.filter { it.userId in visibleIds }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = emptyList()
    )

    private val myPostCommentsActivityFlow: Flow<List<Pair<FeedPost, List<FeedComment>>>> = feedPostsState
        .map { posts -> posts.filter { it.userId == currentUserId }.take(10) }
        .distinctUntilChanged { a, b -> a.map { it.id } == b.map { it.id } }
        .flatMapLatest { myPosts ->
            if (myPosts.isEmpty()) {
                flowOf(emptyList())
            } else {
                val perPost = myPosts.map { post ->
                    socialRepo.observePostComments(post.id, currentUserId)
                        .catch { emit(emptyList()) }
                        .map { comments -> post to comments }
                }
                combine(perPost) { it.toList() }
            }
        }

    private val myReelCommentsActivityFlow: Flow<List<Pair<VideoPost, List<FeedComment>>>> = videoFeedState
        .map { reels -> reels.filter { it.userId == currentUserId }.take(10) }
        .distinctUntilChanged { a, b -> a.map { it.id } == b.map { it.id } }
        .flatMapLatest { myReels ->
            if (myReels.isEmpty()) {
                flowOf(emptyList())
            } else {
                val perReel = myReels.map { reel ->
                    socialRepo.observeVideoPostComments(reel.id, currentUserId)
                        .catch { emit(emptyList()) }
                        .map { comments -> reel to comments }
                }
                combine(perReel) { it.toList() }
            }
        }

    val moderationNoticesState: StateFlow<List<ModerationNoticeRecord>> = socialRepo.observeModerationNotices(currentUserId)
        .catch { error ->
            if (error is CancellationException) throw error
            emit(emptyList())
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = emptyList()
        )

    val socialActivityState: StateFlow<List<SocialActivityItem>> = combine(
        allFollowsState,
        feedPostsState,
        videoFeedState,
        myPostCommentsActivityFlow,
        myReelCommentsActivityFlow,
        allProfilesFlow,
        blockingStateFlow,
        moderationNoticesState
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val follows = values[0] as List<FollowRecord>
        @Suppress("UNCHECKED_CAST")
        val posts = values[1] as List<FeedPost>
        @Suppress("UNCHECKED_CAST")
        val reels = values[2] as List<VideoPost>
        @Suppress("UNCHECKED_CAST")
        val postCommentPairs = values[3] as List<Pair<FeedPost, List<FeedComment>>>
        @Suppress("UNCHECKED_CAST")
        val reelCommentPairs = values[4] as List<Pair<VideoPost, List<FeedComment>>>
        @Suppress("UNCHECKED_CAST")
        val profiles = values[5] as List<PublicProfile>
        val blocking = values[6] as BlockingSnapshot
        @Suppress("UNCHECKED_CAST")
        val modNotices = values[7] as List<ModerationNoticeRecord>

        val blocked = blocking.allBlockedIds
        val profileMap = profiles.associateBy { it.userId }
        val items = mutableListOf<SocialActivityItem>()

        // New followers
        follows.filter { it.followingUid == currentUserId && it.followerUid != currentUserId && it.followerUid !in blocked }
            .forEach { follow ->
                val actor = profileMap[follow.followerUid]
                items += SocialActivityItem.NewFollower(
                    id = "follow_${follow.id}",
                    timestampMs = follow.createdAt?.toDate()?.time ?: 0L,
                    actorUid = follow.followerUid,
                    actorName = actor?.displayName ?: follow.followerName.ifBlank { "Kindred Member" },
                    actorPhoto = actor?.allProfilePhotos()?.firstOrNull() ?: follow.followerPhoto,
                    follow = follow
                )
            }

        // Likes on user's posts
        posts.filter { it.userId == currentUserId }
            .forEach { post ->
                val baseTs = post.createdAt?.toDate()?.time ?: 0L
                post.likedByUids.filter { it != currentUserId && it !in blocked }
                    .forEach { likerUid ->
                        val actor = profileMap[likerUid]
                        items += SocialActivityItem.PostLike(
                            id = "postlike_${post.id}_$likerUid",
                            timestampMs = baseTs,
                            actorUid = likerUid,
                            actorName = actor?.displayName ?: "Kindred Member",
                            actorPhoto = actor?.allProfilePhotos()?.firstOrNull(),
                            post = post
                        )
                    }
            }

        // Likes on user's reels
        reels.filter { it.userId == currentUserId }
            .forEach { reel ->
                val baseTs = reel.createdAt?.toDate()?.time ?: 0L
                reel.likedByUids.filter { it != currentUserId && it !in blocked }
                    .forEach { likerUid ->
                        val actor = profileMap[likerUid]
                        items += SocialActivityItem.ReelLike(
                            id = "reellike_${reel.id}_$likerUid",
                            timestampMs = baseTs,
                            actorUid = likerUid,
                            actorName = actor?.displayName ?: "Kindred Member",
                            actorPhoto = actor?.allProfilePhotos()?.firstOrNull(),
                            reel = reel
                        )
                    }
            }

        // Comments on user's posts
        postCommentPairs.forEach { (post, comments) ->
            comments.filter { it.userId != currentUserId && it.userId !in blocked }
                .forEach { comment ->
                    val actor = profileMap[comment.userId]
                    items += SocialActivityItem.PostComment(
                        id = "postcomment_${post.id}_${comment.id}",
                        timestampMs = comment.createdAt?.toDate()?.time ?: (post.createdAt?.toDate()?.time ?: 0L),
                        actorUid = comment.userId,
                        actorName = actor?.displayName ?: comment.authorName.ifBlank { "Kindred Member" },
                        actorPhoto = actor?.allProfilePhotos()?.firstOrNull() ?: comment.authorPhoto,
                        post = post,
                        comment = comment
                    )
                }
        }

        // Comments on user's reels
        reelCommentPairs.forEach { (reel, comments) ->
            comments.filter { it.userId != currentUserId && it.userId !in blocked }
                .forEach { comment ->
                    val actor = profileMap[comment.userId]
                    items += SocialActivityItem.ReelComment(
                        id = "reelcomment_${reel.id}_${comment.id}",
                        timestampMs = comment.createdAt?.toDate()?.time ?: (reel.createdAt?.toDate()?.time ?: 0L),
                        actorUid = comment.userId,
                        actorName = actor?.displayName ?: comment.authorName.ifBlank { "Kindred Member" },
                        actorPhoto = actor?.allProfilePhotos()?.firstOrNull() ?: comment.authorPhoto,
                        reel = reel,
                        comment = comment
                    )
                }
        }

        // Moderator content removal notices
        modNotices.forEach { notice ->
            items += SocialActivityItem.ModerationRemoval(
                id = "modnotice_${notice.id}",
                timestampMs = notice.createdAt?.toDate()?.time ?: System.currentTimeMillis(),
                actorUid = notice.moderatorUid,
                actorName = "Kindred Moderation",
                actorPhoto = null,
                notice = notice
            )
        }

        items.sortedByDescending { it.timestampMs }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = emptyList()
    )

    fun observeFeedComments(postId: String): Flow<List<FeedComment>> =
        socialRepo.observePostComments(postId, currentUserId).catch { emit(emptyList()) }

    // --- Admin Moderation ---

    private val _isAdminUser = MutableStateFlow(
        userEmail?.equals(KindredConstants.ADMIN_EMAIL, ignoreCase = true) == true
    )
    val isAdminUser: StateFlow<Boolean> = _isAdminUser.asStateFlow()

    val adminReportsState: StateFlow<List<ReportRecord>> = _isAdminUser
        .flatMapLatest { isAdmin ->
            socialRepo.observeAdminReports(currentUserId, isAdmin)
        }
        .catch { error ->
            if (error is CancellationException) throw error
            emit(emptyList())
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = emptyList()
        )

    val adminAllProfilesState: StateFlow<List<PublicProfile>> = _isAdminUser
        .flatMapLatest { isAdmin ->
            socialRepo.observeAllProfilesForAdmin(currentUserId, isAdmin)
        }
        .catch { error ->
            if (error is CancellationException) throw error
            emit(emptyList())
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = emptyList()
        )

    private val _actionStatusMessage = MutableStateFlow<String?>(null)
    val actionStatusMessage: StateFlow<String?> = _actionStatusMessage.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _isVideoUploading = MutableStateFlow(false)
    val isVideoUploading: StateFlow<Boolean> = _isVideoUploading.asStateFlow()

    private val _isUploadingMedia = MutableStateFlow(false)
    val isUploadingMedia: StateFlow<Boolean> = _isUploadingMedia.asStateFlow()

    private val _uploadProgress = MutableStateFlow<Float?>(null)
    val uploadProgress: StateFlow<Float?> = _uploadProgress.asStateFlow()

    private val profileBootstrapAttempted = AtomicBoolean(false)

    private val _pushAlerts = MutableSharedFlow<KindredPushAlert>(extraBufferCapacity = 16)
    val pushAlerts: SharedFlow<KindredPushAlert> = _pushAlerts.asSharedFlow()

    init {
        viewModelScope.launch {
            _isAdminUser.value = socialRepo.checkIsAdmin(userEmail)
            startExpiredStoryCleanupLoop()
        }
        refreshFeed()

        val sessionStartTimeMs = System.currentTimeMillis()

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
                        if (matches.isNotEmpty()) {
                            initialized = true
                        }
                        return@collect
                    }

                    visibleMatches.forEach { match ->
                        val partnerId = match.participantUids.firstOrNull { it != currentUserId } ?: ""
                        val partnerName = profileMap[partnerId]?.displayName ?: "Kindred Member"
                        knownMatchIds.add(match.id)

                        val latestMsgMs = match.lastMessageAt?.toDate()?.time ?: 0L
                        val previousMsgMs = knownLastMessageAt[match.id] ?: 0L
                        if (latestMsgMs <= sessionStartTimeMs) {
                            knownLastMessageAt[match.id] = maxOf(previousMsgMs, latestMsgMs)
                            return@forEach
                        }
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

        // Real-time incoming call notification emitter across all conversations
        viewModelScope.launch {
            val notifiedIncomingCallIds = mutableSetOf<String>()
            combine(
                allMatchCallSessionsFlow,
                allProfilesFlow
            ) { sessions, profiles ->
                sessions to profiles.associateBy { it.userId }
            }
                .catch { error ->
                    if (error is CancellationException) throw error
                    Log.w(TAG, "Incoming call notification watcher paused", error)
                }
                .collect { (sessions, profileMap) ->
                    val now = System.currentTimeMillis()
                    sessions
                        .filter { call ->
                            if (call.status != "RINGING" || call.calleeUid != currentUserId) return@filter false
                            val callCreatedAtMs = call.createdAt?.toDate()?.time ?: 0L
                            if (callCreatedAtMs > 0L && (now - callCreatedAtMs) > 60_000L) {
                                false
                            } else {
                                true
                            }
                        }
                        .forEach { ringingCall ->
                            if (notifiedIncomingCallIds.add(ringingCall.id)) {
                                val callerName = profileMap[ringingCall.callerUid]?.displayName ?: "Kindred Member"
                                _pushAlerts.tryEmit(
                                    KindredPushAlert.IncomingCall(
                                        matchId = ringingCall.matchId,
                                        callId = ringingCall.id,
                                        callerName = callerName,
                                        callType = ringingCall.callType
                                    )
                                )
                            }
                        }
                }
        }

        // New follower real-time alert emitter
        viewModelScope.launch {
            var initializedFollows = false
            val seenFollowIds = mutableSetOf<String>()
            combine(
                allFollowsState,
                allProfilesFlow
            ) { follows, profiles ->
                follows to profiles.associateBy { it.userId }
            }
                .catch { error ->
                    if (error is CancellationException) throw error
                    Log.w(TAG, "New follower notification watcher paused", error)
                }
                .collect { (follows, profileMap) ->
                val incomingFollows = follows.filter {
                    it.followingUid == currentUserId && it.followerUid != currentUserId
                }
                if (!initializedFollows) {
                    incomingFollows.forEach { follow ->
                        seenFollowIds += follow.id
                    }
                    if (follows.isNotEmpty()) {
                        initializedFollows = true
                    }
                    return@collect
                }
                incomingFollows.forEach { follow ->
                    val createdAtMs = follow.createdAt?.toDate()?.time ?: 0L
                    if (createdAtMs <= sessionStartTimeMs) {
                        seenFollowIds += follow.id
                        return@forEach
                    }
                    if (seenFollowIds.add(follow.id)) {
                        val name = profileMap[follow.followerUid]?.displayName
                            ?: follow.followerName.ifBlank { "Kindred Member" }
                        _pushAlerts.tryEmit(KindredPushAlert.NewFollowerAlert(follow.followerUid, name))
                    }
                }
            }
        }

        // Real-time moderation removal notice alert emitter
        viewModelScope.launch {
            val notifiedNoticeIds = mutableSetOf<String>()
            moderationNoticesState
                .catch { error ->
                    if (error is CancellationException) throw error
                    Log.w(TAG, "Moderation notice watcher paused", error)
                }
                .collect { notices ->
                    notices.filter { !it.read && it.id.isNotBlank() }
                        .forEach { notice ->
                            if (notifiedNoticeIds.add(notice.id)) {
                                _pushAlerts.tryEmit(KindredPushAlert.ModerationNoticeAlert(notice))
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
            withTimeoutOrNull(800L) {
                @Suppress("DEPRECATION")
                val token = FirebaseMessaging.getInstance().token.await()
                if (!token.isNullOrBlank()) {
                    repository.deleteFcmToken(token)
                }
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

    private fun isHideOnlinePreferred(): Boolean {
        val snap = (dashboardState.value as? UiState.Success)?.data
        return (snap?.myProfile?.hideOnlineStatus == true) || (snap?.privateUser?.hideOnlineStatus == true)
    }

    suspend fun updatePresenceNow(online: Boolean) {
        val hidden = isHideOnlinePreferred()
        withTimeoutOrNull(900L) {
            repository.updatePresence(online = online && !hidden, hideOnlineStatus = hidden)
        }
    }

    fun setPresenceOnline(online: Boolean) {
        viewModelScope.launch {
            val hidden = isHideOnlinePreferred()
            repository.updatePresence(online = online && !hidden, hideOnlineStatus = hidden)
        }
    }

    fun clearStatusMessage() {
        _actionStatusMessage.value = null
    }

    fun postStatusMessage(message: String) {
        _actionStatusMessage.value = message
    }

    fun bootstrapProfileIfMissing(
        existingProfile: PublicProfile?,
        existingPrivateUser: PrivateUser?,
        pending: PendingRegistrationData?,
        onConsumed: () -> Unit = {}
    ) {
        if (!profileBootstrapAttempted.compareAndSet(false, true)) return
        viewModelScope.launch {
            try {
                val loc = _latestLocation.value
                val lat = loc?.exactLatitude ?: existingPrivateUser?.exactLatitude ?: existingProfile?.coarseLatitude ?: 37.7749
                val lng = loc?.exactLongitude ?: existingPrivateUser?.exactLongitude ?: existingProfile?.coarseLongitude ?: -122.4194
                val city = loc?.cityLabel?.takeIf { it.isNotBlank() }
                    ?: existingProfile?.cityLabel?.takeIf { it.isNotBlank() }
                    ?: "Nearby"
                val country = loc?.countryName?.takeIf { it.isNotBlank() }
                    ?: existingProfile?.country?.takeIf { it.isNotBlank() }
                    ?: "United States"
                val resolvedName = pending?.displayName?.trim()?.takeIf { it.length >= 2 }
                    ?: existingProfile?.displayName?.trim()?.takeIf { it.length >= 2 }
                    ?: defaultDisplayName?.trim()?.takeIf { it.length >= 2 }
                    ?: userEmail?.substringBefore('@')?.trim()?.takeIf { it.length >= 2 }
                    ?: "Kindred Member"
                val resolvedAge = pending?.age?.takeIf { it in 18..99 }
                    ?: existingProfile?.age?.toInt()?.takeIf { it in 18..99 }
                    ?: 24
                val resolvedGender = pending?.gender?.takeIf { it.isNotBlank() }
                    ?: existingProfile?.gender?.takeIf { it.isNotBlank() }
                    ?: "Woman"

                val result = repository.saveProfileAndPreferences(
                    email = userEmail,
                    displayName = resolvedName.take(40),
                    age = resolvedAge,
                    gender = resolvedGender,
                    bio = existingProfile?.bio.orEmpty(),
                    cityLabel = city,
                    country = country,
                    latitude = lat,
                    longitude = lng,
                    interests = existingProfile?.interests.orEmpty(),
                    photoDataUrl = existingProfile?.photoDataUrl,
                    profilePhotos = existingProfile?.allProfilePhotos().orEmpty(),
                    discoverable = existingProfile?.discoverable ?: true,
                    interestedIn = existingPrivateUser?.interestedIn ?: "Everyone",
                    minAgePref = (existingPrivateUser?.minAgePref ?: 18L).toInt(),
                    maxAgePref = (existingPrivateUser?.maxAgePref ?: 99L).toInt(),
                    maxDistanceKm = (existingPrivateUser?.maxDistanceKm ?: 100L).toInt(),
                    dateOfBirth = existingProfile?.dateOfBirth.orEmpty(),
                    profileCompleted = true
                )
                if (result.isSuccess) {
                    onConsumed()
                } else {
                    profileBootstrapAttempted.set(false)
                }
            } catch (e: CancellationException) {
                profileBootstrapAttempted.set(false)
                throw e
            } catch (_: Exception) {
                profileBootstrapAttempted.set(false)
            }
        }
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
        val cityOrCountryChanged = (lastPersistedCity != resolved.cityLabel) ||
            (resolved.countryName.isNotBlank() && lastPersistedCountry != resolved.countryName)
        val shouldPersist = anchorLat == null ||
            cityOrCountryChanged ||
            (movedKm >= 0.5 && (now - lastGeoPersistMs) >= 30_000L)

        if (shouldPersist) {
            lastPersistedGeoLat = resolved.exactLatitude
            lastPersistedGeoLng = resolved.exactLongitude
            lastPersistedCity = resolved.cityLabel
            if (resolved.countryName.isNotBlank()) {
                lastPersistedCountry = resolved.countryName
            }
            lastGeoPersistMs = now
            viewModelScope.launch {
                repository.updateLocationCoordinates(
                    latitude = resolved.exactLatitude,
                    longitude = resolved.exactLongitude,
                    cityLabel = resolved.cityLabel,
                    countryName = resolved.countryName,
                    email = userEmail
                )
            }
        }
    }

    fun applyResolvedLocation(resolved: ResolvedDeviceLocation) {
        _latestLocation.value = resolved
        lastPersistedGeoLat = resolved.exactLatitude
        lastPersistedGeoLng = resolved.exactLongitude
        lastPersistedCity = resolved.cityLabel
        if (resolved.countryName.isNotBlank()) {
            lastPersistedCountry = resolved.countryName
        }
        lastGeoPersistMs = System.currentTimeMillis()
        viewModelScope.launch {
            val result = repository.updateLocationCoordinates(
                latitude = resolved.exactLatitude,
                longitude = resolved.exactLongitude,
                cityLabel = resolved.cityLabel,
                countryName = resolved.countryName,
                email = userEmail
            )
            result.onSuccess {
                val cell = GeoHashUtils.encode(resolved.coarseLatitude, resolved.coarseLongitude, 4)
                val locLabel = if (resolved.countryName.isNotBlank()) {
                    "${resolved.cityLabel}, ${resolved.countryName}"
                } else {
                    resolved.cityLabel
                }
                _actionStatusMessage.value = "Location verified: $locLabel (GeoHash $cell)"
            }.onFailure { err ->
                _actionStatusMessage.value = err.localizedMessage ?: "Failed to update location"
            }
        }
    }

    fun startDirectConversation(
        targetProfile: PublicProfile,
        onOpened: (matchId: String) -> Unit = {}
    ) {
        val targetId = targetProfile.userId
        if (targetId.isBlank() || targetId == currentUserId) return

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
        viewOnce: Boolean = false,
        preparedVideo: PreparedVideoUpload? = null,
        replyToMessageId: String? = null,
        replyToSenderId: String? = null,
        replyToText: String? = null,
        replyToType: String? = null
    ) {
        val hasMediaAttachment = preparedVideo != null || !mediaDataUrl.isNullOrBlank()
        viewModelScope.launch {
            if (hasMediaAttachment) {
                _isUploadingMedia.value = true
                _uploadProgress.value = 0f
            }
            try {
                withTimeout(CloudMediaStorageService.UPLOAD_TIMEOUT_MS + 5_000L) {
                    val result = repository.sendMessage(
                        matchId = matchId,
                        participantUids = participantUids,
                        text = text,
                        messageType = messageType,
                        mediaDataUrl = mediaDataUrl,
                        mediaGifId = mediaGifId,
                        mediaDurationSec = mediaDurationSec,
                        disappearingSeconds = disappearingSeconds,
                        viewOnce = viewOnce,
                        preparedVideo = preparedVideo,
                        replyToMessageId = replyToMessageId,
                        replyToSenderId = replyToSenderId,
                        replyToText = replyToText,
                        replyToType = replyToType,
                        onProgress = { progress ->
                            if (hasMediaAttachment) _uploadProgress.value = progress
                        }
                    )
                    result.onFailure { err ->
                        val msg = CloudMediaStorageService.formatUploadError(err)
                        Log.e(CloudMediaStorageService.TAG, "sendMessage failed: $msg", err)
                        _actionStatusMessage.value = msg
                    }
                }
            } catch (e: TimeoutCancellationException) {
                val msg = CloudMediaStorageService.formatUploadError(e)
                Log.e(CloudMediaStorageService.TAG, "sendMessage timed out", e)
                _actionStatusMessage.value = msg
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val msg = CloudMediaStorageService.formatUploadError(e)
                Log.e(CloudMediaStorageService.TAG, "sendMessage unexpected error: $msg", e)
                _actionStatusMessage.value = msg
            } finally {
                if (hasMediaAttachment) {
                    _uploadProgress.value = null
                    _isUploadingMedia.value = false
                }
            }
        }
    }

    fun markMessagesAsRead(matchId: String, messageIds: List<String>) {
        if (matchId.isBlank() || messageIds.isEmpty()) return
        viewModelScope.launch {
            repository.markMessagesAsRead(matchId, messageIds)
        }
    }

    fun markViewOnceOpened(matchId: String, messageId: String) {
        viewModelScope.launch {
            repository.markViewOnceOpened(matchId, messageId)
        }
    }

    fun deleteExpiredMessage(matchId: String, messageId: String) {
        viewModelScope.launch {
            repository.deleteMessage(matchId, messageId)
        }
    }

    fun deleteMessageForMe(matchId: String, messageId: String) {
        viewModelScope.launch {
            repository.deleteMessageForMe(matchId, messageId)
                .onSuccess { _actionStatusMessage.value = "Message deleted for you" }
                .onFailure { err -> _actionStatusMessage.value = err.localizedMessage ?: "Delete failed" }
        }
    }

    fun unsendMessage(matchId: String, messageId: String, isLastMessage: Boolean = false) {
        viewModelScope.launch {
            repository.unsendMessage(matchId, messageId, isLastMessage)
                .onSuccess { _actionStatusMessage.value = "Message unsent" }
                .onFailure { err -> _actionStatusMessage.value = err.localizedMessage ?: "Unsend failed" }
        }
    }

    suspend fun searchMessagesAcrossMatches(query: String): List<Pair<String, ChatMessage>> {
        val snap = (dashboardState.value as? UiState.Success)?.data ?: return emptyList()
        val matchIds = snap.matches.map { it.match.id }
        return repository.searchMessagesAcrossMatches(currentUserId, matchIds, query)
    }

    suspend fun resolveChatVideoFile(context: Context, message: ChatMessage): Result<File> {
        return socialRepo.resolveVideoToFile(
            context = context,
            cacheKey = message.id.ifBlank { "msg_video" },
            inlineDataUrl = message.mediaDataUrl,
            chunkCount = message.mediaChunkCount,
            chunksCollection = socialRepo.messageMediaChunksRef(message.matchId, message.id)
        )
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

    fun sendCallAudioChunk(session: CallSession, seq: Long, sampleRateHz: Int, audioBase64: String) {
        viewModelScope.launch {
            repository.sendCallAudioChunk(
                matchId = session.matchId,
                callId = session.id,
                participantUids = session.participantUids,
                seq = seq,
                sampleRateHz = sampleRateHz,
                audioBase64 = audioBase64
            )
        }
    }

    fun sendCallVideoFrame(session: CallSession, seq: Long, frameBase64: String) {
        viewModelScope.launch {
            repository.sendCallVideoFrame(
                matchId = session.matchId,
                callId = session.id,
                participantUids = session.participantUids,
                seq = seq,
                frameBase64 = frameBase64
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
        viewModelScope.launch {
            _isBusy.value = true
            val result = repository.unmatch(matchId)
            _isBusy.value = false
            result.onSuccess {
                if (_selectedMatchId.value == matchId) {
                    _selectedMatchId.value = null
                }
                _actionStatusMessage.value = "Conversation removed"
            }.onFailure { err ->
                _actionStatusMessage.value = err.localizedMessage ?: "Failed to remove conversation"
            }
        }
    }

    fun blockUser(
        targetUserId: String,
        displayName: String,
        reason: String? = null,
        onBlocked: () -> Unit = {}
    ) {
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
                _actionStatusMessage.value = "Blocked $displayName"
                onBlocked()
            }.onFailure { err ->
                _actionStatusMessage.value = err.localizedMessage ?: "Failed to block user"
            }
        }
    }

    fun unblockUser(targetUserId: String) {
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

    fun reportUser(
        reportedUid: String,
        displayName: String,
        reason: String,
        details: String = "",
        sourceSurface: String = "PROFILE",
        matchId: String? = null,
        alsoBlock: Boolean = false,
        onReported: () -> Unit = {}
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            val result = repository.submitReport(
                reportedUid = reportedUid,
                reason = reason,
                details = details,
                sourceSurface = sourceSurface,
                matchId = matchId
            )
            if (result.isSuccess && alsoBlock) {
                repository.blockUser(reportedUid, "Reported: $reason")
                val sorted = listOf(currentUserId, reportedUid).sorted()
                val potentialMatchId = "${sorted[0]}_${sorted[1]}"
                if (_selectedMatchId.value == potentialMatchId) {
                    _selectedMatchId.value = null
                }
            }
            _isBusy.value = false
            result.onSuccess {
                _actionStatusMessage.value = if (alsoBlock) {
                    "Reported and blocked $displayName. Thank you for keeping Kindred safe."
                } else {
                    "Report submitted for $displayName. Our safety team will review it."
                }
                onReported()
            }.onFailure { err ->
                _actionStatusMessage.value = err.localizedMessage ?: "Failed to submit report"
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
        dateOfBirth: String = "",
        profileCompleted: Boolean = true,
        onComplete: () -> Unit = {}
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            _isUploadingMedia.value = true
            if (profilePhotos.isNotEmpty() || !photoDataUrl.isNullOrBlank()) {
                _uploadProgress.value = 0f
            }
            try {
                withTimeout(CloudMediaStorageService.UPLOAD_TIMEOUT_MS + 5_000L) {
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
                        dateOfBirth = dateOfBirth,
                        profileCompleted = profileCompleted,
                        onProgress = { _uploadProgress.value = it }
                    )
                    result.onSuccess {
                        _actionStatusMessage.value = "Profile & GeoHash index saved"
                        onComplete()
                    }.onFailure { err ->
                        val msg = CloudMediaStorageService.formatUploadError(err)
                        Log.e(CloudMediaStorageService.TAG, "saveProfile failed: $msg", err)
                        _actionStatusMessage.value = msg
                    }
                }
            } catch (e: TimeoutCancellationException) {
                val msg = CloudMediaStorageService.formatUploadError(e)
                Log.e(CloudMediaStorageService.TAG, "saveProfile timed out", e)
                _actionStatusMessage.value = msg
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val msg = CloudMediaStorageService.formatUploadError(e)
                Log.e(CloudMediaStorageService.TAG, "saveProfile error: $msg", e)
                _actionStatusMessage.value = msg
            } finally {
                _uploadProgress.value = null
                _isUploadingMedia.value = false
                _isBusy.value = false
            }
        }
    }

    fun updateProfileContent(
        bio: String,
        interests: List<String>,
        profilePhotos: List<String>,
        cityLabel: String? = null,
        country: String? = null,
        displayName: String? = null,
        gender: String? = null,
        dateOfBirth: String? = null,
        computedAge: Int? = null,
        username: String? = null,
        pronouns: String? = null,
        profileLink: String? = null,
        pageInfo: String? = null,
        category: String? = null,
        onComplete: () -> Unit = {},
        onCompleteResult: ((Boolean, String?) -> Unit)? = null
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            _isUploadingMedia.value = true
            if (profilePhotos.isNotEmpty()) {
                _uploadProgress.value = 0f
            }
            try {
                withTimeout(CloudMediaStorageService.UPLOAD_TIMEOUT_MS + 5_000L) {
                    val result = repository.updateProfileContent(
                        email = userEmail,
                        fallbackDisplayName = defaultDisplayName,
                        bio = bio,
                        interests = interests,
                        profilePhotos = profilePhotos,
                        cityLabel = cityLabel,
                        country = country,
                        displayName = displayName,
                        gender = gender,
                        dateOfBirth = dateOfBirth,
                        computedAge = computedAge,
                        username = username,
                        pronouns = pronouns,
                        profileLink = profileLink,
                        pageInfo = pageInfo,
                        category = category,
                        onProgress = { _uploadProgress.value = it }
                    )
                    result.onSuccess {
                        _actionStatusMessage.value = "Profile updated in Firestore"
                        onComplete()
                        onCompleteResult?.invoke(true, null)
                    }.onFailure { err ->
                        val msg = CloudMediaStorageService.formatUploadError(err)
                        Log.e(CloudMediaStorageService.TAG, "updateProfileContent failed: $msg", err)
                        _actionStatusMessage.value = msg
                        onCompleteResult?.invoke(false, msg)
                    }
                }
            } catch (e: TimeoutCancellationException) {
                val msg = CloudMediaStorageService.formatUploadError(e)
                Log.e(CloudMediaStorageService.TAG, "updateProfileContent timed out", e)
                _actionStatusMessage.value = msg
                onCompleteResult?.invoke(false, msg)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val msg = CloudMediaStorageService.formatUploadError(e)
                Log.e(CloudMediaStorageService.TAG, "updateProfileContent error: $msg", e)
                _actionStatusMessage.value = msg
                onCompleteResult?.invoke(false, msg)
            } finally {
                _uploadProgress.value = null
                _isUploadingMedia.value = false
                _isBusy.value = false
            }
        }
    }

    fun uploadProfilePicture(
        photoDataUrl: String,
        onComplete: (Boolean, String?) -> Unit
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            _isUploadingMedia.value = true
            _uploadProgress.value = 0.05f
            val uid = currentUserId
            if (uid.isBlank()) {
                _isBusy.value = false
                _isUploadingMedia.value = false
                _actionStatusMessage.value = "Please sign in before uploading profile picture."
                onComplete(false, "User is not signed in.")
                return@launch
            }
            try {
                withTimeout(CloudMediaStorageService.UPLOAD_TIMEOUT_MS + 5_000L) {
                    val result = repository.uploadSingleProfilePicture(
                        uid = uid,
                        photoDataUrl = photoDataUrl,
                        onProgress = { fraction ->
                            _uploadProgress.value = fraction
                        }
                    )
                    result.onSuccess {
                        _uploadProgress.value = 1f
                        _actionStatusMessage.value = "Profile picture updated!"
                        onComplete(true, null)
                    }.onFailure { err ->
                        val msg = CloudMediaStorageService.formatUploadError(err)
                        Log.e(CloudMediaStorageService.TAG, "uploadProfilePicture error: $msg", err)
                        _actionStatusMessage.value = msg
                        onComplete(false, msg)
                    }
                }
            } catch (e: TimeoutCancellationException) {
                val msg = "Upload timed out. Please check your internet connection."
                _actionStatusMessage.value = msg
                onComplete(false, msg)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val msg = CloudMediaStorageService.formatUploadError(e)
                _actionStatusMessage.value = msg
                onComplete(false, msg)
            } finally {
                _isUploadingMedia.value = false
                _isBusy.value = false
                _uploadProgress.value = null
            }
        }
    }

    fun updateDiscoverable(discoverable: Boolean, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            _isBusy.value = true
            val result = repository.updateDiscoverable(discoverable)
            _isBusy.value = false
            result.onSuccess {
                _actionStatusMessage.value = if (discoverable) {
                    "Your profile is visible in Explore"
                } else {
                    "Profile hidden from Explore"
                }
                onComplete()
            }.onFailure { err ->
                _actionStatusMessage.value = err.localizedMessage ?: "Failed to update visibility"
            }
        }
    }

    // --- Privacy & Online Visibility ---

    fun updatePrivacyAndHideOnline(
        hideOnlineStatus: Boolean,
        photosVisibility: String,
        videosVisibility: String,
        infoVisibility: String
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            val result = repository.updatePrivacyAndHideOnline(
                hideOnlineStatus = hideOnlineStatus,
                photosVisibility = photosVisibility,
                videosVisibility = videosVisibility,
                infoVisibility = infoVisibility
            )
            _isBusy.value = false
            result.onSuccess {
                _actionStatusMessage.value = "Privacy & online settings updated"
            }.onFailure { err ->
                _actionStatusMessage.value = err.localizedMessage ?: "Failed to update privacy"
            }
        }
    }

    // --- Followers / Following Social Graph ---

    fun toggleFollow(targetUid: String) {
        if (targetUid.isBlank() || targetUid == currentUserId) return
        val snap = (dashboardState.value as? UiState.Success)?.data
        val currentlyFollowing = targetUid in followingIdsState.value
        viewModelScope.launch {
            if (currentlyFollowing) {
                socialRepo.unfollowUser(targetUid)
                    .onSuccess { _actionStatusMessage.value = "Unfollowed" }
                    .onFailure { _actionStatusMessage.value = it.localizedMessage ?: "Could not unfollow" }
            } else {
                socialRepo.followUser(targetUid, snap?.myProfile, defaultDisplayName)
                    .onSuccess { _actionStatusMessage.value = "Following" }
                    .onFailure { _actionStatusMessage.value = it.localizedMessage ?: "Could not follow user" }
            }
        }
    }

    fun removeFollower(followerUid: String) {
        if (followerUid.isBlank() || followerUid == currentUserId) return
        viewModelScope.launch {
            socialRepo.removeFollower(followerUid)
                .onSuccess { _actionStatusMessage.value = "Removed follower" }
                .onFailure { _actionStatusMessage.value = it.localizedMessage ?: "Could not remove follower" }
        }
    }

    // --- Favorites & Profile Views ---

    fun toggleProfileLike(targetUid: String) {
        viewModelScope.launch {
            socialRepo.toggleProfileLike(targetUid)
                .onSuccess { liked ->
                    _actionStatusMessage.value = if (liked) "Profile liked" else "Removed profile like"
                }
                .onFailure { _actionStatusMessage.value = it.localizedMessage ?: "Could not update like" }
        }
    }

    fun toggleFavorite(profile: PublicProfile) {
        viewModelScope.launch {
            socialRepo.toggleFavorite(profile)
                .onSuccess { saved ->
                    _actionStatusMessage.value = if (saved) {
                        "Saved ${profile.displayName} to Favorites"
                    } else {
                        "Removed ${profile.displayName} from Favorites"
                    }
                }
                .onFailure { _actionStatusMessage.value = it.localizedMessage ?: "Could not update favorites" }
        }
    }

    fun recordProfileView(targetUid: String) {
        if (targetUid.isBlank() || targetUid == currentUserId) return
        val snap = (dashboardState.value as? UiState.Success)?.data
        val myName = snap?.myProfile?.displayName ?: defaultDisplayName ?: "Kindred Member"
        val myPhoto = snap?.myProfile?.allProfilePhotos()?.firstOrNull()
        viewModelScope.launch {
            socialRepo.recordProfileView(targetUid, myName, myPhoto)
        }
    }

    // --- Profile Showcase Videos ---

    fun observeUserVideos(targetUserId: String): Flow<List<ProfileVideoItem>> = combine(
        socialRepo.observeUserVideos(targetUserId, currentUserId).catch { emit(emptyList()) },
        _pendingDeletedProfileVideoIds
    ) { videos, deletedIds ->
        if (deletedIds.isEmpty()) videos else videos.filter { it.id !in deletedIds }
    }

    fun uploadProfileVideo(
        caption: String,
        prepared: PreparedVideoUpload,
        onComplete: () -> Unit = {}
    ) {
        uploadProfileVideoWithResult(caption, prepared) { success, _ ->
            if (success) onComplete()
        }
    }

    fun uploadProfileVideoWithResult(
        caption: String,
        prepared: PreparedVideoUpload,
        onComplete: (Boolean, String?) -> Unit
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            _isVideoUploading.value = true
            _isUploadingMedia.value = true
            _uploadProgress.value = 0f
            try {
                withTimeout(CloudMediaStorageService.UPLOAD_TIMEOUT_MS + 5_000L) {
                    val res = socialRepo.uploadProfileVideo(caption, prepared) { progress ->
                        _uploadProgress.value = progress
                    }
                    res.onSuccess {
                        _actionStatusMessage.value = "Showcase video uploaded and saved to your profile"
                        onComplete(true, null)
                    }.onFailure { err ->
                        val msg = CloudMediaStorageService.formatUploadError(err)
                        Log.e(CloudMediaStorageService.TAG, "uploadProfileVideo failed: $msg", err)
                        _actionStatusMessage.value = msg
                        onComplete(false, msg)
                    }
                }
            } catch (e: TimeoutCancellationException) {
                val msg = CloudMediaStorageService.formatUploadError(e)
                Log.e(CloudMediaStorageService.TAG, "uploadProfileVideo timed out", e)
                _actionStatusMessage.value = msg
                onComplete(false, msg)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val msg = CloudMediaStorageService.formatUploadError(e)
                Log.e(CloudMediaStorageService.TAG, "uploadProfileVideo error: $msg", e)
                _actionStatusMessage.value = msg
                onComplete(false, msg)
            } finally {
                _uploadProgress.value = null
                _isUploadingMedia.value = false
                _isVideoUploading.value = false
                _isBusy.value = false
            }
        }
    }

    fun deleteProfileVideo(ownerUid: String, videoId: String) {
        val cleanVideoId = videoId.trim()
        if (cleanVideoId.isBlank()) return
        _pendingDeletedProfileVideoIds.update { it + cleanVideoId }
        viewModelScope.launch {
            socialRepo.deleteProfileVideo(ownerUid, cleanVideoId)
                .onSuccess { _actionStatusMessage.value = "Video deleted" }
                .onFailure { err ->
                    _pendingDeletedProfileVideoIds.update { it - cleanVideoId }
                    _actionStatusMessage.value = err.localizedMessage ?: "Delete failed"
                }
        }
    }

    suspend fun resolveProfileVideoFile(context: Context, ownerUid: String, video: ProfileVideoItem): Result<File> {
        return socialRepo.resolveVideoToFile(
            context = context,
            cacheKey = "pvid_${ownerUid}_${video.id}",
            inlineDataUrl = video.mediaDataUrl,
            chunkCount = video.chunkCount,
            chunksCollection = socialRepo.profileVideoChunksRef(ownerUid, video.id)
        )
    }

    // --- 24-Hour Stories ---

    fun postPhotoStory(
        photoDataUrl: String,
        caption: String,
        onComplete: (Boolean) -> Unit = {}
    ) {
        postPhotoStoryWithResult(photoDataUrl, caption) { success, _ ->
            onComplete(success)
        }
    }

    fun postPhotoStoryWithResult(
        photoDataUrl: String,
        caption: String,
        onComplete: (Boolean, String?) -> Unit
    ) {
        val snap = (dashboardState.value as? UiState.Success)?.data
        viewModelScope.launch {
            _isBusy.value = true
            _isUploadingMedia.value = true
            _uploadProgress.value = 0f
            try {
                withTimeout(CloudMediaStorageService.UPLOAD_TIMEOUT_MS + 5_000L) {
                    val res = socialRepo.postPhotoStory(
                        authorProfile = snap?.myProfile,
                        fallbackName = defaultDisplayName,
                        photoDataUrl = photoDataUrl,
                        caption = caption,
                        onProgress = { _uploadProgress.value = it }
                    )
                    res.onSuccess {
                        _actionStatusMessage.value = "Story shared for 24 hours!"
                        autoRefreshFeed()
                        onComplete(true, null)
                    }.onFailure { err ->
                        val msg = CloudMediaStorageService.formatUploadError(err)
                        Log.e(CloudMediaStorageService.TAG, "postPhotoStory failed: $msg", err)
                        _actionStatusMessage.value = msg
                        onComplete(false, msg)
                    }
                }
            } catch (e: TimeoutCancellationException) {
                val msg = CloudMediaStorageService.formatUploadError(e)
                Log.e(CloudMediaStorageService.TAG, "postPhotoStory timed out", e)
                _actionStatusMessage.value = msg
                onComplete(false, msg)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val msg = CloudMediaStorageService.formatUploadError(e)
                Log.e(CloudMediaStorageService.TAG, "postPhotoStory error: $msg", e)
                _actionStatusMessage.value = msg
                onComplete(false, msg)
            } finally {
                _uploadProgress.value = null
                _isUploadingMedia.value = false
                _isBusy.value = false
            }
        }
    }

    fun postVideoStory(
        prepared: PreparedVideoUpload,
        caption: String,
        onComplete: (Boolean) -> Unit = {}
    ) {
        postVideoStoryWithResult(prepared, caption) { success, _ ->
            onComplete(success)
        }
    }

    fun postVideoStoryWithResult(
        prepared: PreparedVideoUpload,
        caption: String,
        onComplete: (Boolean, String?) -> Unit
    ) {
        val snap = (dashboardState.value as? UiState.Success)?.data
        viewModelScope.launch {
            _isBusy.value = true
            _isVideoUploading.value = true
            _isUploadingMedia.value = true
            _uploadProgress.value = 0f
            try {
                withTimeout(CloudMediaStorageService.UPLOAD_TIMEOUT_MS + 5_000L) {
                    val res = socialRepo.postVideoStory(
                        authorProfile = snap?.myProfile,
                        fallbackName = defaultDisplayName,
                        prepared = prepared,
                        caption = caption,
                        onProgress = { _uploadProgress.value = it }
                    )
                    res.onSuccess {
                        _actionStatusMessage.value = "Video story shared for 24 hours!"
                        autoRefreshFeed()
                        onComplete(true, null)
                    }.onFailure { err ->
                        val msg = CloudMediaStorageService.formatUploadError(err)
                        Log.e(CloudMediaStorageService.TAG, "postVideoStory failed: $msg", err)
                        _actionStatusMessage.value = msg
                        onComplete(false, msg)
                    }
                }
            } catch (e: TimeoutCancellationException) {
                val msg = CloudMediaStorageService.formatUploadError(e)
                Log.e(CloudMediaStorageService.TAG, "postVideoStory timed out", e)
                _actionStatusMessage.value = msg
                onComplete(false, msg)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val msg = CloudMediaStorageService.formatUploadError(e)
                Log.e(CloudMediaStorageService.TAG, "postVideoStory error: $msg", e)
                _actionStatusMessage.value = msg
                onComplete(false, msg)
            } finally {
                _uploadProgress.value = null
                _isUploadingMedia.value = false
                _isVideoUploading.value = false
                _isBusy.value = false
            }
        }
    }

    private val isPurgingExpiredStories = AtomicBoolean(false)
    private var expiredStoryLoopStarted = AtomicBoolean(false)

    private fun startExpiredStoryCleanupLoop() {
        if (currentUserId.isBlank()) return
        if (!expiredStoryLoopStarted.compareAndSet(false, true)) return
        viewModelScope.launch {
            while (isActive) {
                runCatching { purgeExpiredStoriesSuspend() }
                val now = System.currentTimeMillis()
                val nextExpiryWaitMs = activeStoriesState.value
                    .mapNotNull { it.remainingTtlMs(now).takeIf { rem -> rem < Long.MAX_VALUE } }
                    .minOrNull()
                    ?.coerceIn(5_000L, 60_000L)
                    ?: 60_000L
                delay(nextExpiryWaitMs)
            }
        }
    }

    fun cleanupExpiredStoriesNow() {
        if (currentUserId.isBlank()) return
        viewModelScope.launch {
            runCatching { purgeExpiredStoriesSuspend() }
        }
    }

    private suspend fun purgeExpiredStoriesSuspend() {
        val now = System.currentTimeMillis()
        _storyExpiryTickMs.value = now
        if (!isPurgingExpiredStories.compareAndSet(false, true)) return
        try {
            socialRepo.purgeExpiredStories(
                viewerUid = currentUserId,
                includeAllUsers = _isAdminUser.value,
                nowMs = now
            )
        } finally {
            isPurgingExpiredStories.set(false)
        }
    }

    fun markStoryViewed(storyId: String) {
        viewModelScope.launch { socialRepo.markStoryViewed(storyId) }
    }

    fun deleteStory(storyId: String) {
        val cleanStoryId = storyId.trim()
        if (cleanStoryId.isBlank()) return
        _pendingDeletedStoryIds.update { it + cleanStoryId }
        viewModelScope.launch {
            socialRepo.deleteStory(cleanStoryId)
                .onSuccess {
                    _actionStatusMessage.value = "Story removed"
                    autoRefreshFeed()
                }
                .onFailure { err ->
                    _pendingDeletedStoryIds.update { it - cleanStoryId }
                    _actionStatusMessage.value = err.localizedMessage ?: "Could not remove story"
                }
        }
    }

    fun replyToStory(authorUid: String, replyText: String, onDone: (Boolean) -> Unit = {}) {
        if (authorUid.isBlank() || replyText.isBlank() || authorUid == currentUserId) return
        viewModelScope.launch {
            val matchRes = repository.createOrOpenDirectMatch(authorUid)
            matchRes.onSuccess { matchId ->
                repository.sendMessage(
                    matchId = matchId,
                    participantUids = listOf(currentUserId, authorUid).distinct(),
                    text = "Replied to story: $replyText",
                    messageType = "TEXT"
                )
                _actionStatusMessage.value = "Reply sent to DM"
                onDone(true)
            }.onFailure {
                _actionStatusMessage.value = it.localizedMessage ?: "Failed to send reply"
                onDone(false)
            }
        }
    }

    suspend fun resolveStoryVideoFile(context: Context, story: StoryItem): Result<File> {
        return socialRepo.resolveVideoToFile(
            context = context,
            cacheKey = "story_${story.id}",
            inlineDataUrl = story.mediaDataUrl,
            chunkCount = story.chunkCount,
            chunksCollection = socialRepo.storyChunksRef(story.id)
        )
    }

    // --- Story Highlights & Story Archive ---

    fun observeUserHighlights(userId: String): Flow<List<StoryHighlight>> =
        socialRepo.observeUserHighlights(userId)

    fun observeUserStoriesArchive(userId: String): Flow<List<StoryItem>> = combine(
        socialRepo.observeUserStoriesArchive(userId).catch { emit(emptyList()) },
        _pendingDeletedStoryIds
    ) { stories, deletedIds ->
        if (deletedIds.isEmpty()) stories else stories.filter { it.id !in deletedIds }
    }

    fun createStoryHighlight(
        title: String,
        coverUrl: String,
        items: List<StoryItem>,
        onDone: (Boolean) -> Unit = {}
    ) {
        createStoryHighlightWithResult(title, coverUrl, items) { success, _ ->
            onDone(success)
        }
    }

    fun createStoryHighlightWithResult(
        title: String,
        coverUrl: String,
        items: List<StoryItem>,
        onDone: (Boolean, String?) -> Unit
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            _isUploadingMedia.value = true
            _uploadProgress.value = 0f
            try {
                withTimeout(CloudMediaStorageService.UPLOAD_TIMEOUT_MS + 5_000L) {
                    val res = socialRepo.createHighlight(title, coverUrl, items) { progress ->
                        _uploadProgress.value = progress
                    }
                    res.onSuccess {
                        _actionStatusMessage.value = "Highlight created"
                        onDone(true, null)
                    }.onFailure { err ->
                        val msg = CloudMediaStorageService.formatUploadError(err)
                        Log.e(TAG, "createStoryHighlight failed: $msg", err)
                        _actionStatusMessage.value = msg
                        onDone(false, msg)
                    }
                }
            } catch (e: TimeoutCancellationException) {
                val msg = CloudMediaStorageService.formatUploadError(e)
                Log.e(TAG, "createStoryHighlight timed out", e)
                _actionStatusMessage.value = msg
                onDone(false, msg)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val msg = CloudMediaStorageService.formatUploadError(e)
                Log.e(TAG, "createStoryHighlight error: $msg", e)
                _actionStatusMessage.value = msg
                onDone(false, msg)
            } finally {
                _uploadProgress.value = null
                _isUploadingMedia.value = false
                _isBusy.value = false
            }
        }
    }

    fun deleteStoryHighlight(highlightId: String) {
        viewModelScope.launch {
            socialRepo.deleteHighlight(highlightId)
                .onSuccess { _actionStatusMessage.value = "Highlight removed" }
                .onFailure { _actionStatusMessage.value = it.localizedMessage ?: "Could not remove highlight" }
        }
    }

    // --- Video Feed ---

    fun publishVideoPost(
        caption: String,
        prepared: PreparedVideoUpload,
        onComplete: () -> Unit = {}
    ) {
        publishVideoPostWithResult(caption, prepared) { success, _ ->
            if (success) onComplete()
        }
    }

    fun publishVideoPostWithResult(
        caption: String,
        prepared: PreparedVideoUpload,
        onComplete: (Boolean, String?) -> Unit
    ) {
        val snap = (dashboardState.value as? UiState.Success)?.data
        viewModelScope.launch {
            _isBusy.value = true
            _isVideoUploading.value = true
            _isUploadingMedia.value = true
            _uploadProgress.value = 0f
            try {
                withTimeout(CloudMediaStorageService.UPLOAD_TIMEOUT_MS + 5_000L) {
                    val res = socialRepo.publishVideoPost(
                        authorProfile = snap?.myProfile,
                        fallbackName = defaultDisplayName,
                        caption = caption,
                        prepared = prepared,
                        onProgress = { _uploadProgress.value = it }
                    )
                    res.onSuccess {
                        _actionStatusMessage.value = "Video posted to Kindred Reels!"
                        autoRefreshFeed()
                        onComplete(true, null)
                    }.onFailure { err ->
                        val msg = CloudMediaStorageService.formatUploadError(err)
                        Log.e(CloudMediaStorageService.TAG, "publishVideoPost failed: $msg", err)
                        _actionStatusMessage.value = msg
                        onComplete(false, msg)
                    }
                }
            } catch (e: TimeoutCancellationException) {
                val msg = CloudMediaStorageService.formatUploadError(e)
                Log.e(CloudMediaStorageService.TAG, "publishVideoPost timed out", e)
                _actionStatusMessage.value = msg
                onComplete(false, msg)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val msg = CloudMediaStorageService.formatUploadError(e)
                Log.e(CloudMediaStorageService.TAG, "publishVideoPost error: $msg", e)
                _actionStatusMessage.value = msg
                onComplete(false, msg)
            } finally {
                _uploadProgress.value = null
                _isUploadingMedia.value = false
                _isVideoUploading.value = false
                _isBusy.value = false
            }
        }
    }

    fun toggleVideoPostLike(postId: String, currentlyLiked: Boolean) {
        viewModelScope.launch { socialRepo.toggleVideoPostLike(postId, currentlyLiked) }
    }

    fun deleteVideoPost(postId: String) {
        val cleanPostId = postId.trim()
        if (cleanPostId.isBlank()) return
        _pendingDeletedVideoPostIds.update { it + cleanPostId }
        viewModelScope.launch {
            socialRepo.deleteVideoPost(cleanPostId)
                .onSuccess {
                    _actionStatusMessage.value = "Video post deleted"
                    autoRefreshFeed()
                }
                .onFailure { err ->
                    _pendingDeletedVideoPostIds.update { it - cleanPostId }
                    _actionStatusMessage.value = err.localizedMessage ?: "Delete failed"
                }
        }
    }

    suspend fun resolveVideoPostFile(context: Context, post: VideoPost): Result<File> {
        return socialRepo.resolveVideoToFile(
            context = context,
            cacheKey = "vpost_${post.id}",
            inlineDataUrl = post.mediaDataUrl,
            chunkCount = post.chunkCount,
            chunksCollection = socialRepo.videoPostChunksRef(post.id)
        )
    }

    fun observeVideoPostComments(postId: String): Flow<List<FeedComment>> =
        socialRepo.observeVideoPostComments(postId, currentUserId)

    fun addVideoPostComment(
        postId: String,
        text: String,
        replyToComment: FeedComment? = null,
        onComplete: () -> Unit = {}
    ) {
        val snap = (dashboardState.value as? UiState.Success)?.data
        viewModelScope.launch {
            socialRepo.addVideoPostComment(postId, snap?.myProfile, defaultDisplayName, text, replyToComment)
                .onSuccess { onComplete() }
                .onFailure { _actionStatusMessage.value = it.localizedMessage ?: "Could not add comment" }
        }
    }

    fun deleteVideoPostComment(postId: String, commentId: String) {
        viewModelScope.launch {
            socialRepo.deleteVideoPostComment(postId, commentId)
                .onFailure { _actionStatusMessage.value = it.localizedMessage ?: "Could not delete comment" }
        }
    }

    // --- Social Timeline Feed Posts & Comments ---

    fun publishFeedPost(
        caption: String,
        photoDataUrl: String? = null,
        preparedVideo: PreparedVideoUpload? = null,
        onComplete: () -> Unit = {}
    ) {
        publishFeedPost(caption, photoDataUrl, emptyList(), preparedVideo, onComplete)
    }

    fun publishFeedPost(
        caption: String,
        photoDataUrl: String? = null,
        photoUrls: List<String> = emptyList(),
        preparedVideo: PreparedVideoUpload? = null,
        onComplete: () -> Unit = {}
    ) {
        publishFeedPostWithResult(caption, photoDataUrl, photoUrls, preparedVideo) { success, _ ->
            if (success) onComplete()
        }
    }

    fun publishFeedPostWithResult(
        caption: String,
        photoDataUrl: String? = null,
        photoUrls: List<String> = emptyList(),
        preparedVideo: PreparedVideoUpload? = null,
        onComplete: (Boolean, String?) -> Unit
    ) {
        val snap = (dashboardState.value as? UiState.Success)?.data
        val hasMedia = preparedVideo != null || !photoDataUrl.isNullOrBlank() || photoUrls.isNotEmpty()
        viewModelScope.launch {
            _isBusy.value = true
            _isUploadingMedia.value = true
            if (hasMedia) _uploadProgress.value = 0f
            if (preparedVideo != null) _isVideoUploading.value = true
            try {
                withTimeout(CloudMediaStorageService.UPLOAD_TIMEOUT_MS + 5_000L) {
                    val res = socialRepo.publishFeedPost(
                        authorProfile = snap?.myProfile,
                        fallbackName = defaultDisplayName,
                        caption = caption,
                        photoDataUrl = photoDataUrl,
                        photoUrls = photoUrls,
                        preparedVideo = preparedVideo,
                        onProgress = { progress ->
                            if (hasMedia) _uploadProgress.value = progress
                        }
                    )
                    res.onSuccess {
                        _actionStatusMessage.value = "Shared to your timeline!"
                        autoRefreshFeed()
                        onComplete(true, null)
                    }.onFailure { err ->
                        val msg = CloudMediaStorageService.formatUploadError(err)
                        Log.e(CloudMediaStorageService.TAG, "publishFeedPost failed: $msg", err)
                        _actionStatusMessage.value = msg
                        onComplete(false, msg)
                    }
                }
            } catch (e: TimeoutCancellationException) {
                val msg = CloudMediaStorageService.formatUploadError(e)
                Log.e(CloudMediaStorageService.TAG, "publishFeedPost timed out", e)
                _actionStatusMessage.value = msg
                onComplete(false, msg)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val msg = CloudMediaStorageService.formatUploadError(e)
                Log.e(CloudMediaStorageService.TAG, "publishFeedPost error: $msg", e)
                _actionStatusMessage.value = msg
                onComplete(false, msg)
            } finally {
                _uploadProgress.value = null
                _isUploadingMedia.value = false
                _isVideoUploading.value = false
                _isBusy.value = false
            }
        }
    }

    fun toggleFeedPostLike(postId: String, currentlyLiked: Boolean) {
        viewModelScope.launch { socialRepo.toggleFeedPostLike(postId, currentlyLiked) }
    }

    fun toggleFeedPostSave(postId: String, currentlySaved: Boolean) {
        viewModelScope.launch {
            socialRepo.toggleFeedPostSave(postId, currentlySaved)
                .onSuccess {
                    _actionStatusMessage.value = if (!currentlySaved) "Saved post" else "Removed from saved"
                }
        }
    }

    fun addFeedPostComment(
        postId: String,
        text: String,
        replyToComment: FeedComment? = null,
        onDone: () -> Unit = {}
    ) {
        val snap = (dashboardState.value as? UiState.Success)?.data
        viewModelScope.launch {
            socialRepo.addFeedPostComment(postId, snap?.myProfile, defaultDisplayName, text, replyToComment)
                .onSuccess { onDone() }
                .onFailure { _actionStatusMessage.value = it.localizedMessage ?: "Could not post comment" }
        }
    }

    fun deleteFeedPostComment(postId: String, commentId: String) {
        viewModelScope.launch {
            socialRepo.deleteFeedPostComment(postId, commentId)
                .onFailure { _actionStatusMessage.value = it.localizedMessage ?: "Delete failed" }
        }
    }

    fun toggleCommentLike(postId: String, commentId: String, currentlyLiked: Boolean) {
        viewModelScope.launch {
            socialRepo.toggleCommentLike(postId, commentId, currentlyLiked)
        }
    }

    fun deleteFeedPost(postId: String) {
        val cleanPostId = postId.trim()
        if (cleanPostId.isBlank()) return
        _pendingDeletedFeedPostIds.update { it + cleanPostId }
        viewModelScope.launch {
            socialRepo.deleteFeedPost(cleanPostId)
                .onSuccess {
                    _actionStatusMessage.value = "Post deleted"
                    autoRefreshFeed()
                }
                .onFailure { err ->
                    _pendingDeletedFeedPostIds.update { it - cleanPostId }
                    _actionStatusMessage.value = err.localizedMessage ?: "Delete failed"
                }
        }
    }

    suspend fun resolveFeedPostVideoFile(context: Context, post: FeedPost): Result<File> {
        return socialRepo.resolveVideoToFile(
            context = context,
            cacheKey = "fpost_${post.id}",
            inlineDataUrl = post.mediaDataUrl,
            chunkCount = post.chunkCount,
            chunksCollection = socialRepo.feedPostChunksRef(post.id)
        )
    }

    // --- Admin & Account Deletion ---

    fun updateReportStatus(reportId: String, status: String) {
        viewModelScope.launch {
            socialRepo.updateReportStatus(reportId, status)
                .onSuccess { _actionStatusMessage.value = "Report marked $status" }
                .onFailure { _actionStatusMessage.value = it.localizedMessage ?: "Could not update report" }
        }
    }

    fun setUserSuspended(targetUserId: String, suspended: Boolean) {
        viewModelScope.launch {
            socialRepo.setUserSuspendedByAdmin(targetUserId, suspended)
                .onSuccess {
                    _actionStatusMessage.value = if (suspended) "User suspended" else "User reinstated"
                }
                .onFailure { _actionStatusMessage.value = it.localizedMessage ?: "Action failed" }
        }
    }

    fun moderatorDeleteFeedPost(
        post: FeedPost,
        reason: String,
        details: String = "",
        relatedReportId: String? = null
    ) {
        val cleanPostId = post.id.ifBlank { post.postId }.trim()
        if (cleanPostId.isBlank()) return
        _pendingDeletedFeedPostIds.update { it + cleanPostId }
        viewModelScope.launch {
            socialRepo.moderatorDeleteFeedPost(post, reason, details, relatedReportId)
                .onSuccess {
                    _actionStatusMessage.value = "Post removed and user notified"
                    autoRefreshFeed()
                }
                .onFailure { err ->
                    _pendingDeletedFeedPostIds.update { it - cleanPostId }
                    _actionStatusMessage.value = err.localizedMessage ?: "Moderation delete failed"
                }
        }
    }

    fun moderatorDeleteVideoPost(
        reel: VideoPost,
        reason: String,
        details: String = "",
        relatedReportId: String? = null
    ) {
        val cleanPostId = reel.id.trim()
        if (cleanPostId.isBlank()) return
        _pendingDeletedVideoPostIds.update { it + cleanPostId }
        viewModelScope.launch {
            socialRepo.moderatorDeleteVideoPost(reel, reason, details, relatedReportId)
                .onSuccess {
                    _actionStatusMessage.value = "Reel removed and user notified"
                    autoRefreshFeed()
                }
                .onFailure { err ->
                    _pendingDeletedVideoPostIds.update { it - cleanPostId }
                    _actionStatusMessage.value = err.localizedMessage ?: "Moderation delete failed"
                }
        }
    }

    fun moderatorDeleteStory(
        story: StoryItem,
        reason: String,
        details: String = ""
    ) {
        val cleanStoryId = story.id.trim()
        if (cleanStoryId.isBlank()) return
        _pendingDeletedStoryIds.update { it + cleanStoryId }
        viewModelScope.launch {
            socialRepo.moderatorDeleteStory(story, reason, details)
                .onSuccess {
                    _actionStatusMessage.value = "Story removed and user notified"
                    autoRefreshFeed()
                }
                .onFailure { err ->
                    _pendingDeletedStoryIds.update { it - cleanStoryId }
                    _actionStatusMessage.value = err.localizedMessage ?: "Moderation delete failed"
                }
        }
    }

    fun markModerationNoticeRead(noticeId: String) {
        if (noticeId.isBlank()) return
        viewModelScope.launch {
            socialRepo.markModerationNoticeRead(currentUserId, noticeId)
        }
    }

    fun markAllModerationNoticesRead() {
        val unreadIds = moderationNoticesState.value.filter { !it.read && it.id.isNotBlank() }.map { it.id }
        if (unreadIds.isEmpty()) return
        viewModelScope.launch {
            socialRepo.markAllModerationNoticesRead(currentUserId, unreadIds)
        }
    }

    fun deleteAccountAndData(
        onDeleted: () -> Unit,
        onRequiresRecentLogin: () -> Unit
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            val res = socialRepo.deleteAccountAndData()
            _isBusy.value = false
            res.onSuccess {
                onDeleted()
            }.onFailure { err ->
                if (err is FirebaseAuthRecentLoginRequiredException) {
                    _actionStatusMessage.value = "For security, please sign out and sign back in right before deleting your account."
                    onRequiresRecentLogin()
                } else {
                    _actionStatusMessage.value = err.localizedMessage ?: "Failed to delete account"
                }
            }
        }
    }

    private companion object {
        const val TAG = "KindredVM"
        const val STOP_TIMEOUT_MILLIS = 5000L
    }
}
