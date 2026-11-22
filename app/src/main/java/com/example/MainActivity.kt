package com.example

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.auth.GoogleAuthHelper
import com.example.communication.CallRingtoneController
import com.example.data.KindredRepository
import com.example.model.FriendStatus
import com.example.model.PublicProfile
import com.example.model.UiState
import com.example.notifications.KindredBackgroundService
import com.example.notifications.KindredNotificationHelper
import com.example.security.configureAppCheck
import com.example.service.GeoHashMatchingService
import com.example.ui.components.FullscreenIncomingCallDialog
import com.example.ui.components.InAppCallOverlay
import com.example.ui.components.LocationVerificationGate
import com.example.ui.components.NewMatchCelebrationDialog
import com.example.ui.components.ProfileSearchSheet
import com.example.ui.screens.ActiveUsersScreen
import com.example.ui.screens.AdminDashboardScreen
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.DiscoverScreen
import com.example.ui.screens.EditProfileScreen
import com.example.ui.screens.FriendsHubScreen
import com.example.ui.screens.MatchesAndChatScreen
import com.example.ui.screens.ProfileScreen
import com.example.ui.screens.ProfileSetupScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.UserProfileDetailScreen
import com.example.ui.screens.VerificationScreen
import com.example.ui.screens.VideoFeedScreen
import com.example.ui.screens.WhoLikesMeScreen
import com.example.ui.theme.CoralRose
import com.example.ui.theme.KindredTheme
import com.example.update.AppReleaseInfo
import com.example.update.AppUpdateChecker
import com.example.update.UpdateCheckResult
import com.example.util.LocationAndGeoHelper
import com.example.util.PresenceUtils
import com.example.viewmodel.KindredPushAlert
import com.example.viewmodel.KindredViewModel
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

const val ROUTE_DISCOVER = "discover"
const val ROUTE_MATCHES = "matches"
const val ROUTE_VERIFY = "verify"
const val ROUTE_PROFILE = "profile"
const val ROUTE_EDIT_PROFILE = "edit_profile"
const val ROUTE_SETTINGS = "settings"
const val ROUTE_USER_PROFILE = "user_profile/{uid}"
const val ROUTE_ACTIVE_USERS = "active_users"
const val ROUTE_VIDEO_FEED = "video_feed"
const val ROUTE_WHO_LIKES_ME = "who_likes_me"
const val ROUTE_FRIENDS_HUB = "friends_hub"
const val ROUTE_ADMIN_DASHBOARD = "admin_dashboard"

private data class NavTabItem(
    val route: String,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

private val NAV_TABS = listOf(
    NavTabItem(
        route = ROUTE_DISCOVER,
        labelRes = R.string.nav_discover,
        selectedIcon = Icons.Filled.Explore,
        unselectedIcon = Icons.Outlined.Explore,
        testTag = "nav_discover_tab"
    ),
    NavTabItem(
        route = ROUTE_MATCHES,
        labelRes = R.string.nav_matches,
        selectedIcon = Icons.Filled.ChatBubble,
        unselectedIcon = Icons.Outlined.ChatBubbleOutline,
        testTag = "nav_matches_tab"
    ),
    NavTabItem(
        route = ROUTE_VERIFY,
        labelRes = R.string.nav_verify,
        selectedIcon = Icons.Filled.VerifiedUser,
        unselectedIcon = Icons.Outlined.VerifiedUser,
        testTag = "nav_verify_tab"
    ),
    NavTabItem(
        route = ROUTE_PROFILE,
        labelRes = R.string.nav_profile,
        selectedIcon = Icons.Filled.Person,
        unselectedIcon = Icons.Outlined.PersonOutline,
        testTag = "nav_profile_tab"
    )
)

internal fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { auth ->
        trySend(auth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

class MainActivity : ComponentActivity() {
    private var pendingDeepLinkMatchId by mutableStateOf<String?>(null)

    private fun extractMatchDeepLink(sourceIntent: Intent?): String? {
        return sourceIntent?.getStringExtra(KindredNotificationHelper.EXTRA_MATCH_ID)
            ?.takeIf { it.isNotBlank() }
            ?: sourceIntent?.getStringExtra("matchId")?.takeIf { it.isNotBlank() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureAppCheck(this, intent)
        enableEdgeToEdge()
        KindredNotificationHelper.ensureChannels(this)
        pendingDeepLinkMatchId = extractMatchDeepLink(intent)
        setContent {
            KindredTheme {
                val firebaseReady = remember {
                    FirebaseApp.getApps(this@MainActivity).isNotEmpty()
                }
                if (firebaseReady) {
                    AppNavigation(
                        pendingDeepLinkMatchId = pendingDeepLinkMatchId,
                        onConsumeDeepLink = { pendingDeepLinkMatchId = null }
                    )
                } else {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Firebase Configuration Missing",
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.error,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Add app/google-services.json (or set the GOOGLE_SERVICES_JSON GitHub secret) and rebuild the APK to enable sign-in and live sync.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onBackground,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val deepLinkId = extractMatchDeepLink(intent)
        if (!deepLinkId.isNullOrBlank()) {
            pendingDeepLinkMatchId = deepLinkId
        }
    }
}

@Composable
fun AppNavigation(
    auth: FirebaseAuth = Firebase.auth,
    pendingDeepLinkMatchId: String? = null,
    onConsumeDeepLink: () -> Unit = {}
) {
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    val user = currentUser

    if (user == null) {
        AuthScreen(onAuthSuccess = {})
    } else {
        key(user.uid) {
            MainKindredApp(
                user = user,
                pendingDeepLinkMatchId = pendingDeepLinkMatchId,
                onConsumeDeepLink = onConsumeDeepLink
            )
        }
    }
}

@Composable
private fun MainKindredApp(
    user: FirebaseUser,
    pendingDeepLinkMatchId: String? = null,
    onConsumeDeepLink: () -> Unit = {}
) {
    val viewModel: KindredViewModel = viewModel(
        key = user.uid,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                val geoService = GeoHashMatchingService(db = db)
                KindredViewModel(
                    repository = KindredRepository(db = db, geoService = geoService),
                    geoService = geoService,
                    currentUserId = user.uid,
                    userEmail = user.email,
                    defaultDisplayName = user.displayName,
                    isEmailVerified = user.isEmailVerified
                )
            }
        }
    )

    val dashboardState by viewModel.dashboardState.collectAsStateWithLifecycle()
    val selectedMatchId by viewModel.selectedMatchId.collectAsStateWithLifecycle()
    val activeMessagesState by viewModel.activeChatMessages.collectAsStateWithLifecycle()
    val activePartnerPresence by viewModel.activePartnerPresence.collectAsStateWithLifecycle()
    val activePartnerTyping by viewModel.activePartnerTyping.collectAsStateWithLifecycle()
    val activeMatchCallSession by viewModel.activeMatchCallSession.collectAsStateWithLifecycle()
    val globalCallSession by viewModel.globalCallSession.collectAsStateWithLifecycle()
    val globalIncomingCallAlert by viewModel.globalIncomingCallAlert.collectAsStateWithLifecycle()
    val activeCallAudioChunks by viewModel.activeCallAudioChunks.collectAsStateWithLifecycle()
    val activeCallVideoFrames by viewModel.activeCallVideoFrames.collectAsStateWithLifecycle()
    val activeMembersState by viewModel.activeMembersState.collectAsStateWithLifecycle()
    val allPresenceState by viewModel.allPresenceState.collectAsStateWithLifecycle()
    val friendRequestsState by viewModel.friendRequestsState.collectAsStateWithLifecycle()
    val friendshipsState by viewModel.friendshipsState.collectAsStateWithLifecycle()
    val suggestedFriendsState by viewModel.suggestedFriendsState.collectAsStateWithLifecycle()
    val profileViewsState by viewModel.profileViewsState.collectAsStateWithLifecycle()
    val profileLikesState by viewModel.profileLikesState.collectAsStateWithLifecycle()
    val whoLikedMeState by viewModel.whoLikedMeState.collectAsStateWithLifecycle()
    val favoritesState by viewModel.favoritesState.collectAsStateWithLifecycle()
    val activeStoriesState by viewModel.activeStoriesState.collectAsStateWithLifecycle()
    val videoFeedState by viewModel.videoFeedState.collectAsStateWithLifecycle()
    val friendsFeedPostsState by viewModel.friendsFeedPostsState.collectAsStateWithLifecycle()
    val isFeedRefreshing by viewModel.isFeedRefreshing.collectAsStateWithLifecycle()
    val myShowcaseVideos by remember(user.uid) {
        viewModel.observeUserVideos(user.uid)
    }.collectAsStateWithLifecycle(initialValue = emptyList())
    val isAdminUser by viewModel.isAdminUser.collectAsStateWithLifecycle()
    val adminReportsState by viewModel.adminReportsState.collectAsStateWithLifecycle()
    val adminAllProfilesState by viewModel.adminAllProfilesState.collectAsStateWithLifecycle()
    val icebreakerAiState by viewModel.icebreakerAiState.collectAsStateWithLifecycle()
    val statusMessage by viewModel.actionStatusMessage.collectAsStateWithLifecycle()
    val newMatchPopup by viewModel.newMatchPopup.collectAsStateWithLifecycle()
    val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()
    val activeChallenge by viewModel.activeChallenge.collectAsStateWithLifecycle()
    val latestLocation by viewModel.latestLocation.collectAsStateWithLifecycle()
    val canUndoLastSwipe by viewModel.canUndoLastSwipe.collectAsStateWithLifecycle()
    var showSearchSheet by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val lifecycleOwner = LocalLifecycleOwner.current
    val presenceScope = rememberCoroutineScope()
    var hasLocationPermission by remember {
        mutableStateOf(LocationAndGeoHelper.hasLocationPermission(context))
    }
    var isLocationEnabled by remember {
        mutableStateOf(LocationAndGeoHelper.isLocationServicesEnabled(context))
    }

    val incomingRingController = remember(context) { CallRingtoneController(context.applicationContext) }

    val locationGateLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        hasLocationPermission = LocationAndGeoHelper.hasLocationPermission(context)
        isLocationEnabled = LocationAndGeoHelper.isLocationServicesEnabled(context)
    }

    DisposableEffect(user.uid) {
        onDispose {
            incomingRingController.release()
            KindredNotificationHelper.cancelIncomingCallNotification(context)
        }
    }

    var availableAppUpdate by remember { mutableStateOf<AppReleaseInfo?>(null) }

    suspend fun runAutoUpdateCheck(force: Boolean) {
        if (!AppUpdateChecker.isNetworkConnected(context)) return
        if (!force && !AppUpdateChecker.shouldAutoCheckNow(context)) return
        AppUpdateChecker.recordAutoCheckTimestamp(context)
        when (val result = AppUpdateChecker.checkGitHubLatestRelease(context)) {
            is UpdateCheckResult.Success -> {
                val release = result.release
                if (!AppUpdateChecker.isTagDismissed(context, release.tagName)) {
                    availableAppUpdate = release
                    KindredNotificationHelper.showAppUpdateNotification(
                        context = context,
                        tagName = release.tagName,
                        title = release.title,
                        installUrl = release.apkDownloadUrl ?: release.releasePageUrl
                    )
                }
            }
            else -> Unit
        }
    }

    DisposableEffect(lifecycleOwner, user.uid) {
        var heartbeatJob: Job? = null

        fun startForegroundHeartbeat() {
            heartbeatJob?.cancel()
            heartbeatJob = presenceScope.launch {
                while (isActive) {
                    viewModel.updatePresenceNow(true)
                    delay(PresenceUtils.HEARTBEAT_INTERVAL_MS)
                }
            }
        }

        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            startForegroundHeartbeat()
            KindredBackgroundService.startIfEnabled(context)
        }

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    hasLocationPermission = LocationAndGeoHelper.hasLocationPermission(context)
                    isLocationEnabled = LocationAndGeoHelper.isLocationServicesEnabled(context)
                    startForegroundHeartbeat()
                    KindredBackgroundService.startIfEnabled(context)
                }
                Lifecycle.Event.ON_RESUME -> {
                    hasLocationPermission = LocationAndGeoHelper.hasLocationPermission(context)
                    isLocationEnabled = LocationAndGeoHelper.isLocationServicesEnabled(context)
                    presenceScope.launch { runAutoUpdateCheck(force = false) }
                }
                Lifecycle.Event.ON_STOP -> {
                    heartbeatJob?.cancel()
                    heartbeatJob = null
                    viewModel.setPresenceOnline(false)
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            heartbeatJob?.cancel()
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.setPresenceOnline(false)
        }
    }

    LaunchedEffect(lifecycleOwner, user.uid, hasLocationPermission, isLocationEnabled) {
        if (!hasLocationPermission || !isLocationEnabled) return@LaunchedEffect
        val immediateFix = LocationAndGeoHelper.fetchCurrentLocation(context)
        if (immediateFix != null) {
            viewModel.onLiveLocationTick(immediateFix)
        }
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            LocationAndGeoHelper.observeLocationUpdates(context).collect { resolved ->
                viewModel.onLiveLocationTick(resolved)
            }
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: ROUTE_DISCOVER

    DisposableEffect(lifecycleOwner, currentRoute, selectedMatchId) {
        fun syncForegroundMatch() {
            val isResumedOrStarted = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
            KindredNotificationHelper.activeMatchIdInForeground =
                if (isResumedOrStarted && currentRoute == ROUTE_MATCHES && !selectedMatchId.isNullOrBlank()) {
                    selectedMatchId
                } else {
                    null
                }
        }
        syncForegroundMatch()
        val observer = LifecycleEventObserver { _, _ -> syncForegroundMatch() }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            KindredNotificationHelper.activeMatchIdInForeground = null
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.syncFcmToken()
        }
    }

    LaunchedEffect(user.uid) {
        KindredNotificationHelper.ensureChannels(context)
        KindredBackgroundService.startIfEnabled(context)
        viewModel.syncFcmToken()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !KindredNotificationHelper.hasNotificationPermission(context)
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        runAutoUpdateCheck(force = true)
    }

    LaunchedEffect(user.uid) {
        viewModel.pushAlerts.collect { alert ->
            when (alert) {
                is KindredPushAlert.NewMatch -> {
                    KindredNotificationHelper.showMatchNotification(
                        context = context,
                        matchId = alert.matchId,
                        partnerName = alert.partnerName
                    )
                }

                is KindredPushAlert.NewMessage -> {
                    KindredNotificationHelper.showMessageNotification(
                        context = context,
                        matchId = alert.matchId,
                        senderName = alert.senderName,
                        messageText = alert.messageText
                    )
                }

                is KindredPushAlert.IncomingCall -> {
                    KindredNotificationHelper.showIncomingCallNotification(
                        context = context,
                        matchId = alert.matchId,
                        callId = alert.callId,
                        callerName = alert.callerName,
                        callType = alert.callType
                    )
                }

                is KindredPushAlert.FriendRequestAlert -> {
                    KindredNotificationHelper.showSocialNotification(
                        context = context,
                        title = "New Friend Request",
                        body = "${alert.fromName} sent you a friend request on Kindred.",
                        notificationType = "FRIEND_REQUEST",
                        tagKey = "freq_${alert.fromUid}"
                    )
                }

                is KindredPushAlert.ProfileLikeAlert -> {
                    KindredNotificationHelper.showSocialNotification(
                        context = context,
                        title = "Someone Likes You!",
                        body = "${alert.fromName} liked your Kindred profile.",
                        notificationType = "PROFILE_LIKE",
                        tagKey = "plike_${alert.fromUid}"
                    )
                }
            }
        }
    }

    LaunchedEffect(globalIncomingCallAlert?.session?.id, globalIncomingCallAlert?.session?.status) {
        val alert = globalIncomingCallAlert
        if (alert != null) {
            incomingRingController.startIncomingRing()
        } else {
            incomingRingController.stopAll()
            KindredNotificationHelper.cancelIncomingCallNotification(context)
        }
    }

    LaunchedEffect(statusMessage) {
        val msg = statusMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearStatusMessage()
        }
    }

    fun navigateToTab(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    LaunchedEffect(pendingDeepLinkMatchId) {
        val targetMatchId = pendingDeepLinkMatchId
        if (!targetMatchId.isNullOrBlank()) {
            viewModel.selectMatch(targetMatchId)
            navigateToTab(ROUTE_MATCHES)
            onConsumeDeepLink()
        }
    }

    if (!hasLocationPermission || !isLocationEnabled) {
        LocationVerificationGate(
            hasPermission = hasLocationPermission,
            isLocationEnabled = isLocationEnabled,
            onRequestPermission = {
                locationGateLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    )
                )
            },
            onOpenAppSettings = {
                runCatching { context.startActivity(LocationAndGeoHelper.buildAppSettingsIntent(context)) }
            },
            onOpenLocationSettings = {
                runCatching { context.startActivity(LocationAndGeoHelper.buildLocationSettingsIntent()) }
            }
        )
        return
    }

    val successData = (dashboardState as? UiState.Success)?.data
    val requiresProfileSetup = successData != null && (
        successData.myProfile == null || !successData.myProfile.profileCompleted
        )

    if (requiresProfileSetup) {
        val currentSnap = successData!!
        ProfileSetupScreen(
            existingProfile = currentSnap.myProfile,
            defaultDisplayName = viewModel.defaultDisplayName,
            latestLocation = latestLocation,
            isBusy = isBusy,
            onSaveSetup = { displayName, age, dob, gender, bio, cityLabel, country, lat, lng, interests, photos ->
                viewModel.saveProfile(
                    displayName = displayName,
                    age = age,
                    gender = gender,
                    bio = bio,
                    cityLabel = cityLabel,
                    country = country,
                    latitude = lat,
                    longitude = lng,
                    interests = interests,
                    photoDataUrl = photos.firstOrNull(),
                    profilePhotos = photos,
                    discoverable = true,
                    interestedIn = currentSnap.privateUser?.interestedIn ?: "Everyone",
                    minAgePref = (currentSnap.privateUser?.minAgePref ?: 18L).toInt(),
                    maxAgePref = (currentSnap.privateUser?.maxAgePref ?: 45L).toInt(),
                    maxDistanceKm = (currentSnap.privateUser?.maxDistanceKm ?: 100L).toInt(),
                    verifiedOnlyFilter = currentSnap.privateUser?.verifiedOnlyFilter ?: false,
                    dateOfBirth = dob,
                    profileCompleted = true
                )
            },
            onSignOut = {
                GoogleAuthHelper.signOut(
                    credentialManager = credentialManager,
                    scope = presenceScope,
                    onBeforeSignOut = {
                        KindredNotificationHelper.activeMatchIdInForeground = null
                        KindredBackgroundService.stop(context)
                        viewModel.unregisterFcmTokenOnSignOut()
                        viewModel.updatePresenceNow(false)
                    }
                )
            }
        )
        return
    }

    fun friendStatusForUid(targetUid: String): FriendStatus {
        val myUid = viewModel.currentUserId
        if (targetUid.isBlank() || targetUid == myUid) return FriendStatus.NONE
        val isFriend = friendshipsState.any { myUid in it.participantUids && targetUid in it.participantUids }
        if (isFriend) return FriendStatus.FRIENDS
        val pendingOutgoing = friendRequestsState.any {
            it.fromUid == myUid && it.toUid == targetUid && it.status == "PENDING"
        }
        if (pendingOutgoing) return FriendStatus.OUTGOING_PENDING
        val pendingIncoming = friendRequestsState.any {
            it.fromUid == targetUid && it.toUid == myUid && it.status == "PENDING"
        }
        if (pendingIncoming) return FriendStatus.INCOMING_PENDING
        return FriendStatus.NONE
    }

    fun handleFriendAction(targetUid: String, currentStatus: FriendStatus) {
        when (currentStatus) {
            FriendStatus.FRIENDS -> viewModel.removeFriend(targetUid)
            FriendStatus.INCOMING_PENDING -> viewModel.acceptFriendRequest(targetUid)
            FriendStatus.OUTGOING_PENDING -> viewModel.cancelFriendRequest(targetUid)
            FriendStatus.NONE -> viewModel.sendFriendRequest(targetUid)
        }
    }

    fun openUserProfileRoute(uid: String) {
        if (uid.isBlank()) return
        navController.navigate("user_profile/${Uri.encode(uid)}") {
            launchSingleTop = true
        }
    }

    val matchCount = successData?.matches?.size ?: 0
    val unreadInboxCount = successData?.matches
        ?.count { it.match.isUnreadFor(viewModel.currentUserId) } ?: 0
    val activeOnlineCount = activeMembersState.count { it.isOnline }
    val incomingFriendRequestCount = friendRequestsState.count {
        it.toUid == viewModel.currentUserId && it.status == "PENDING"
    }
    val friendsCount = friendshipsState.count { viewModel.currentUserId in it.participantUids }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 840.dp
        val isInFullScreenChat = !useRail && currentRoute == ROUTE_MATCHES && !selectedMatchId.isNullOrBlank()
        val hideBottomBar = !useRail && (
            isInFullScreenChat ||
                currentRoute == ROUTE_EDIT_PROFILE ||
                currentRoute == ROUTE_SETTINGS ||
                currentRoute == ROUTE_ACTIVE_USERS ||
                currentRoute == ROUTE_VIDEO_FEED ||
                currentRoute == ROUTE_WHO_LIKES_ME ||
                currentRoute == ROUTE_FRIENDS_HUB ||
                currentRoute == ROUTE_ADMIN_DASHBOARD ||
                currentRoute.startsWith("user_profile")
            )

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            bottomBar = {
                if (!hideBottomBar && !useRail) {
                    NavigationBar(
                        windowInsets = WindowInsets.navigationBars,
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    ) {
                        NAV_TABS.forEach { tab ->
                            val selected = currentRoute == tab.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateToTab(tab.route) },
                                modifier = Modifier.testTag(tab.testTag),
                                icon = {
                                    TabIconWithOptionalBadge(
                                        tab = tab,
                                        selected = selected,
                                        unreadCount = unreadInboxCount,
                                        matchCount = matchCount
                                    )
                                },
                                label = { Text(stringResource(tab.labelRes)) }
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useRail) {
                    NavigationRail(
                        modifier = Modifier.fillMaxHeight(),
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    ) {
                        Spacer(modifier = Modifier.height(16.dp))
                        NAV_TABS.forEach { tab ->
                            val selected = currentRoute == tab.route
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateToTab(tab.route) },
                                modifier = Modifier.testTag(tab.testTag),
                                icon = {
                                    TabIconWithOptionalBadge(
                                        tab = tab,
                                        selected = selected,
                                        unreadCount = unreadInboxCount,
                                        matchCount = matchCount
                                    )
                                },
                                label = { Text(stringResource(tab.labelRes)) }
                            )
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    when (val state = dashboardState) {
                        is UiState.Loading -> {
                            Column(
                                modifier = Modifier.align(Alignment.Center),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                CircularProgressIndicator(color = CoralRose)
                                Text(
                                    text = "Syncing your Kindred space...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        is UiState.Error -> {
                            Column(
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "Unable to load Firestore data",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.error
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = state.message,
                                    style = MaterialTheme.typography.bodyMedium,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }

                        is UiState.Success -> {
                            val data = state.data
                            val profilesByUid = remember(
                                data.myProfile,
                                data.searchableProfiles,
                                data.matches,
                                activeMembersState,
                                adminAllProfilesState
                            ) {
                                buildMap<String, PublicProfile> {
                                    data.myProfile?.let { put(it.userId, it) }
                                    data.searchableProfiles.forEach { put(it.profile.userId, it.profile) }
                                    data.matches.forEach { thread ->
                                        thread.partnerProfile?.let { put(it.userId, it) }
                                    }
                                    activeMembersState.forEach { put(it.profile.userId, it.profile) }
                                    adminAllProfilesState.forEach { put(it.userId, it) }
                                }
                            }
                            val distanceByUid = remember(data.searchableProfiles, data.matches) {
                                buildMap<String, Double> {
                                    data.searchableProfiles.forEach { put(it.profile.userId, it.distanceKm) }
                                    data.matches.forEach { t ->
                                        t.distanceKm?.let { put(t.partnerId, it) }
                                    }
                                }
                            }
                            val presenceByUid = remember(allPresenceState) {
                                allPresenceState.associateBy { it.userId }
                            }

                            NavHost(
                                navController = navController,
                                startDestination = ROUTE_DISCOVER,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                composable(ROUTE_DISCOVER) {
                                    DiscoverScreen(
                                        myProfile = data.myProfile,
                                        privateUser = data.privateUser,
                                        geoHashMeta = data.geoHashMeta,
                                        candidates = data.discoveryCandidates,
                                        onSwipe = { candidate, direction ->
                                            viewModel.swipeProfile(candidate, direction)
                                        },
                                        onBlockUser = { candidate ->
                                            viewModel.blockUser(
                                                targetUserId = candidate.profile.userId,
                                                displayName = candidate.profile.displayName
                                            )
                                        },
                                        onReportUser = { candidate, reason, details, alsoBlock ->
                                            viewModel.reportUser(
                                                reportedUid = candidate.profile.userId,
                                                displayName = candidate.profile.displayName,
                                                reason = reason,
                                                details = details,
                                                sourceSurface = "DISCOVER",
                                                alsoBlock = alsoBlock
                                            )
                                        },
                                        onRefreshLocation = { resolved ->
                                            viewModel.applyResolvedLocation(resolved)
                                        },
                                        onLocationError = { msg ->
                                            viewModel.postStatusMessage(msg)
                                        },
                                        onUpdateFilters = { interestedIn, minAge, maxAge, maxDist, verifiedOnly ->
                                            viewModel.updateFilters(
                                                interestedIn = interestedIn,
                                                minAgePref = minAge,
                                                maxAgePref = maxAge,
                                                maxDistanceKm = maxDist,
                                                verifiedOnlyFilter = verifiedOnly
                                            )
                                        },
                                        onNavigateToProfile = { navigateToTab(ROUTE_PROFILE) },
                                        onNavigateToVerify = { navigateToTab(ROUTE_VERIFY) },
                                        onOpenSearch = { showSearchSheet = true },
                                        onOpenSettings = {
                                            navController.navigate(ROUTE_SETTINGS) {
                                                launchSingleTop = true
                                            }
                                        },
                                        latestLocation = latestLocation,
                                        onLocationPermissionGranted = {
                                            hasLocationPermission = true
                                        },
                                        canUndoLastSwipe = canUndoLastSwipe,
                                        onUndoSwipe = { viewModel.undoLastSwipe() },
                                        activeOnlineCount = activeOnlineCount,
                                        onOpenActiveUsers = {
                                            navController.navigate(ROUTE_ACTIVE_USERS) {
                                                launchSingleTop = true
                                            }
                                        },
                                        stories = activeStoriesState,
                                        onPostPhotoStory = { photoUrl, caption ->
                                            viewModel.postPhotoStory(photoUrl, caption)
                                        },
                                        onPostVideoStory = { prepared, caption ->
                                            viewModel.postVideoStory(prepared, caption)
                                        },
                                        onMarkStoryViewed = { storyId ->
                                            viewModel.markStoryViewed(storyId)
                                        },
                                        onDeleteStory = { storyId ->
                                            viewModel.deleteStory(storyId)
                                        },
                                        resolveStoryVideoFile = { story ->
                                            viewModel.resolveStoryVideoFile(context, story)
                                        },
                                        onOpenVideoFeed = {
                                            navController.navigate(ROUTE_VIDEO_FEED) {
                                                launchSingleTop = true
                                            }
                                        },
                                        suggestedProfiles = data.searchableProfiles,
                                        feedPosts = friendsFeedPostsState,
                                        currentUserId = viewModel.currentUserId,
                                        friendStatusForUid = { uid -> friendStatusForUid(uid) },
                                        onFriendAction = { profile ->
                                            handleFriendAction(profile.userId, friendStatusForUid(profile.userId))
                                        },
                                        onOpenUserProfile = { profile ->
                                            openUserProfileRoute(profile.userId)
                                        },
                                        onOpenUserProfileById = { uid ->
                                            openUserProfileRoute(uid)
                                        },
                                        onPublishFeedPost = { caption, photoDataUrl, preparedVideo, onDone ->
                                            viewModel.publishFeedPost(caption, photoDataUrl, preparedVideo, onDone)
                                        },
                                        onToggleFeedPostLike = { postId, currentlyLiked ->
                                            viewModel.toggleFeedPostLike(postId, currentlyLiked)
                                        },
                                        observeFeedComments = { postId ->
                                            viewModel.observeFeedComments(postId)
                                        },
                                        onAddFeedPostComment = { postId, text, onDone ->
                                            viewModel.addFeedPostComment(postId, text, onDone)
                                        },
                                        onDeleteFeedPostComment = { postId, commentId ->
                                            viewModel.deleteFeedPostComment(postId, commentId)
                                        },
                                        onDeleteFeedPost = { postId ->
                                            viewModel.deleteFeedPost(postId)
                                        },
                                        resolveFeedPostVideoFile = { post ->
                                            viewModel.resolveFeedPostVideoFile(context, post)
                                        }
                                    )
                                }

                                composable(ROUTE_MATCHES) {
                                    MatchesAndChatScreen(
                                        currentUserId = viewModel.currentUserId,
                                        myProfile = data.myProfile,
                                        matches = data.matches,
                                        selectedMatchId = selectedMatchId,
                                        messagesState = activeMessagesState,
                                        activePartnerPresence = activePartnerPresence,
                                        activePartnerTyping = activePartnerTyping,
                                        activeCallSession = activeMatchCallSession,
                                        activeCallAudioChunks = activeCallAudioChunks,
                                        activeCallVideoFrames = activeCallVideoFrames,
                                        icebreakerAiState = icebreakerAiState,
                                        onSelectMatch = { matchId ->
                                            selectedMatchId?.takeIf { it != matchId }?.let { prev ->
                                                viewModel.setTypingStatus(prev, false)
                                            }
                                            viewModel.resetIcebreakerAiState()
                                            viewModel.selectMatch(matchId)
                                        },
                                        onOpenSearch = { showSearchSheet = true },
                                        onSendMessage = { matchId, participants, text, type ->
                                            viewModel.sendMessage(matchId, participants, text, type)
                                        },
                                        onSendMediaMessage = { matchId, participants, type, text, mediaDataUrl, mediaGifId, durationSec, disappearingSec, viewOnce ->
                                            viewModel.sendMessage(
                                                matchId = matchId,
                                                participantUids = participants,
                                                text = text,
                                                messageType = type,
                                                mediaDataUrl = mediaDataUrl,
                                                mediaGifId = mediaGifId,
                                                mediaDurationSec = durationSec,
                                                disappearingSeconds = disappearingSec,
                                                viewOnce = viewOnce
                                            )
                                        },
                                        onSendRichMessageWithReply = { matchId, participants, type, text, mediaDataUrl, mediaGifId, durationSec, disappearingSec, viewOnce, preparedVideo, replyTo ->
                                            viewModel.sendMessage(
                                                matchId = matchId,
                                                participantUids = participants,
                                                text = text,
                                                messageType = type,
                                                mediaDataUrl = mediaDataUrl,
                                                mediaGifId = mediaGifId,
                                                mediaDurationSec = durationSec,
                                                disappearingSeconds = disappearingSec,
                                                viewOnce = viewOnce,
                                                preparedVideo = preparedVideo,
                                                replyToMessageId = replyTo?.id,
                                                replyToSenderId = replyTo?.senderId,
                                                replyToText = replyTo?.text,
                                                replyToType = replyTo?.messageType
                                            )
                                        },
                                        onMarkMessagesRead = { matchId, messageIds ->
                                            viewModel.markMessagesAsRead(matchId, messageIds)
                                        },
                                        onMarkViewOnceOpened = { matchId, messageId ->
                                            viewModel.markViewOnceOpened(matchId, messageId)
                                        },
                                        onDeleteExpiredMessage = { matchId, messageId ->
                                            viewModel.deleteExpiredMessage(matchId, messageId)
                                        },
                                        onDeleteMessageForMe = { matchId, messageId ->
                                            viewModel.deleteMessageForMe(matchId, messageId)
                                        },
                                        onUnsendMessage = { matchId, messageId, isLastMessage ->
                                            viewModel.unsendMessage(matchId, messageId, isLastMessage)
                                        },
                                        onResolveVideoFile = { msg ->
                                            viewModel.resolveChatVideoFile(context, msg)
                                        },
                                        onTypingChanged = { matchId, isTyping ->
                                            viewModel.setTypingStatus(matchId, isTyping)
                                        },
                                        onUpdateDisappearingTimer = { matchId, ttl ->
                                            viewModel.updateDisappearingTimer(matchId, ttl)
                                        },
                                        onGenerateAiIcebreakers = { myProf, partnerProf, tone ->
                                            viewModel.generateAiIcebreakers(myProf, partnerProf, tone)
                                        },
                                        onStartCall = { matchId, calleeUid, participants, callType ->
                                            viewModel.startCall(matchId, calleeUid, participants, callType)
                                        },
                                        onAnswerCall = { session ->
                                            viewModel.answerCall(session)
                                        },
                                        onDeclineCall = { session ->
                                            viewModel.declineCall(session)
                                        },
                                        onToggleCallMute = { session ->
                                            viewModel.toggleCallMute(session)
                                        },
                                        onToggleCallCamera = { session ->
                                            viewModel.toggleCallCamera(session)
                                        },
                                        onSendCallAudioChunk = { session, seq, sr, b64 ->
                                            viewModel.sendCallAudioChunk(session, seq, sr, b64)
                                        },
                                        onSendCallVideoFrame = { session, seq, frameBase64 ->
                                            viewModel.sendCallVideoFrame(session, seq, frameBase64)
                                        },
                                        onEndCall = { session, duration ->
                                            viewModel.endCall(session, duration)
                                        },
                                        onUnmatch = { matchId ->
                                            viewModel.unmatch(matchId)
                                        },
                                        onBlockUser = { partnerId, partnerName ->
                                            viewModel.blockUser(
                                                targetUserId = partnerId,
                                                displayName = partnerName
                                            )
                                        },
                                        onReportUser = { partnerId, partnerName, reason, details, alsoBlock ->
                                            viewModel.reportUser(
                                                reportedUid = partnerId,
                                                displayName = partnerName,
                                                reason = reason,
                                                details = details,
                                                sourceSurface = "CHAT",
                                                matchId = selectedMatchId,
                                                alsoBlock = alsoBlock
                                            )
                                        },
                                        onRetryChat = {
                                            viewModel.retryChatObservation()
                                        },
                                        onStatusMessage = { msg ->
                                            viewModel.postStatusMessage(msg)
                                        },
                                        onOpenPartnerFullProfile = { partnerProfile ->
                                            openUserProfileRoute(partnerProfile.userId)
                                        }
                                    )
                                }

                                composable(ROUTE_VERIFY) {
                                    VerificationScreen(
                                        myProfile = data.myProfile,
                                        isEmailVerified = viewModel.isEmailVerified,
                                        verifications = data.verifications,
                                        activeChallenge = activeChallenge,
                                        isBusy = isBusy,
                                        onCycleChallenge = { viewModel.cycleVerificationChallenge() },
                                        onSubmitVerification = { challenge, analysis, locVerified ->
                                            viewModel.submitVerification(challenge, analysis, locVerified)
                                        },
                                        onNavigateToProfile = { navigateToTab(ROUTE_PROFILE) }
                                    )
                                }

                                composable(ROUTE_PROFILE) {
                                    ProfileScreen(
                                        currentUserId = viewModel.currentUserId,
                                        userEmail = viewModel.userEmail,
                                        defaultDisplayName = viewModel.defaultDisplayName,
                                        myProfile = data.myProfile,
                                        privateUser = data.privateUser,
                                        isBusy = isBusy,
                                        latestLocation = latestLocation,
                                        onSaveProfile = { name, age, gender, bio, city, country, lat, lng, interests, photo, photos, discoverable, interestedIn, minAge, maxAge, maxDist, verifiedOnly ->
                                            viewModel.saveProfile(
                                                displayName = name,
                                                age = age,
                                                gender = gender,
                                                bio = bio,
                                                cityLabel = city,
                                                country = country,
                                                latitude = lat,
                                                longitude = lng,
                                                interests = interests,
                                                photoDataUrl = photo,
                                                profilePhotos = photos,
                                                discoverable = discoverable,
                                                interestedIn = interestedIn,
                                                minAgePref = minAge,
                                                maxAgePref = maxAge,
                                                maxDistanceKm = maxDist,
                                                verifiedOnlyFilter = verifiedOnly,
                                                dateOfBirth = data.myProfile?.dateOfBirth.orEmpty(),
                                                profileCompleted = true
                                            )
                                        },
                                        onEditProfile = {
                                            navController.navigate(ROUTE_EDIT_PROFILE) {
                                                launchSingleTop = true
                                            }
                                        },
                                        onOpenSettings = {
                                            navController.navigate(ROUTE_SETTINGS) {
                                                launchSingleTop = true
                                            }
                                        },
                                        blockedUsers = data.blockedUsers,
                                        onUnblockUser = { targetUid ->
                                            viewModel.unblockUser(targetUid)
                                        },
                                        onNotificationsGranted = {
                                            viewModel.syncFcmToken()
                                        },
                                        onBeforeSignOut = {
                                            KindredNotificationHelper.activeMatchIdInForeground = null
                                            KindredBackgroundService.stop(context)
                                            selectedMatchId?.let { viewModel.setTypingStatus(it, false) }
                                            viewModel.unregisterFcmTokenOnSignOut()
                                            viewModel.updatePresenceNow(false)
                                        },
                                        myVideos = myShowcaseVideos,
                                        onUploadVideo = { caption, prepared ->
                                            viewModel.uploadProfileVideo(caption, prepared)
                                        },
                                        onDeleteVideo = { videoId ->
                                            viewModel.deleteProfileVideo(viewModel.currentUserId, videoId)
                                        },
                                        incomingFriendRequestCount = incomingFriendRequestCount,
                                        friendsCount = friendsCount,
                                        whoLikedMeCount = whoLikedMeState.size,
                                        profileViewCount = profileViewsState.size,
                                        activeOnlineCount = activeOnlineCount,
                                        isAdmin = isAdminUser,
                                        onOpenFriendsHub = {
                                            navController.navigate(ROUTE_FRIENDS_HUB) {
                                                launchSingleTop = true
                                            }
                                        },
                                        onOpenWhoLikesMe = {
                                            navController.navigate(ROUTE_WHO_LIKES_ME) {
                                                launchSingleTop = true
                                            }
                                        },
                                        onOpenActiveUsers = {
                                            navController.navigate(ROUTE_ACTIVE_USERS) {
                                                launchSingleTop = true
                                            }
                                        },
                                        onOpenVideoFeed = {
                                            navController.navigate(ROUTE_VIDEO_FEED) {
                                                launchSingleTop = true
                                            }
                                        },
                                        onOpenAdminDashboard = {
                                            navController.navigate(ROUTE_ADMIN_DASHBOARD) {
                                                launchSingleTop = true
                                            }
                                        },
                                        onUpdatePrivacy = { hideOnline, photosVis, videosVis, infoVis ->
                                            viewModel.updatePrivacyAndHideOnline(
                                                hideOnlineStatus = hideOnline,
                                                photosVisibility = photosVis,
                                                videosVisibility = videosVis,
                                                infoVisibility = infoVis
                                            )
                                        },
                                        onStatusMessage = { msg ->
                                            viewModel.postStatusMessage(msg)
                                        }
                                    )
                                }

                                composable(ROUTE_EDIT_PROFILE) {
                                    EditProfileScreen(
                                        myProfile = data.myProfile,
                                        defaultDisplayName = viewModel.defaultDisplayName,
                                        isBusy = isBusy,
                                        latestLocation = latestLocation,
                                        videos = myShowcaseVideos,
                                        onUploadVideo = { caption, prepared ->
                                            viewModel.uploadProfileVideo(caption, prepared)
                                        },
                                        onDeleteVideo = { videoId ->
                                            viewModel.deleteProfileVideo(viewModel.currentUserId, videoId)
                                        },
                                        onBack = { navController.popBackStack() },
                                        onSaveChanges = { bio, interests, photos, city, country ->
                                            viewModel.updateProfileContent(
                                                bio = bio,
                                                interests = interests,
                                                profilePhotos = photos,
                                                cityLabel = city,
                                                country = country,
                                                onComplete = { navController.popBackStack() }
                                            )
                                        },
                                        onSaveDetailedChanges = { name, gender, dob, age, bio, interests, photos, city, country ->
                                            viewModel.updateProfileContent(
                                                bio = bio,
                                                interests = interests,
                                                profilePhotos = photos,
                                                cityLabel = city,
                                                country = country,
                                                displayName = name,
                                                gender = gender,
                                                dateOfBirth = dob,
                                                computedAge = age,
                                                onComplete = { navController.popBackStack() }
                                            )
                                        },
                                        onStatusMessage = { msg ->
                                            viewModel.postStatusMessage(msg)
                                        }
                                    )
                                }

                                composable(ROUTE_SETTINGS) {
                                    SettingsScreen(
                                        myProfile = data.myProfile,
                                        privateUser = data.privateUser,
                                        isBusy = isBusy,
                                        onBack = { navController.popBackStack() },
                                        onSavePreferences = { interestedIn, minAge, maxAge, maxDist, verifiedOnly, myGender, discoverable ->
                                            viewModel.saveSettingsPreferences(
                                                interestedIn = interestedIn,
                                                minAgePref = minAge,
                                                maxAgePref = maxAge,
                                                maxDistanceKm = maxDist,
                                                verifiedOnlyFilter = verifiedOnly,
                                                myGender = myGender,
                                                discoverable = discoverable,
                                                onComplete = { navController.popBackStack() }
                                            )
                                        },
                                        onUpdatePrivacy = { hideOnline, photosVis, videosVis, infoVis ->
                                            viewModel.updatePrivacyAndHideOnline(
                                                hideOnlineStatus = hideOnline,
                                                photosVisibility = photosVis,
                                                videosVisibility = videosVis,
                                                infoVisibility = infoVis
                                            )
                                        },
                                        onDeleteAccount = {
                                            viewModel.deleteAccountAndData(
                                                onDeleted = {
                                                    KindredNotificationHelper.activeMatchIdInForeground = null
                                                    KindredBackgroundService.stop(context)
                                                    GoogleAuthHelper.signOut(
                                                        credentialManager = credentialManager,
                                                        scope = presenceScope
                                                    )
                                                },
                                                onRequiresRecentLogin = {}
                                            )
                                        }
                                    )
                                }

                                composable(
                                    route = ROUTE_USER_PROFILE,
                                    arguments = listOf(navArgument("uid") { type = NavType.StringType })
                                ) { backStackEntry ->
                                    val encodedUid = backStackEntry.arguments?.getString("uid").orEmpty()
                                    val targetUid = remember(encodedUid) { Uri.decode(encodedUid) }
                                    val targetProfile = profilesByUid[targetUid] ?: PublicProfile(
                                        userId = targetUid,
                                        displayName = "Kindred Member",
                                        profileCompleted = true
                                    )
                                    val targetVideos by remember(targetUid) {
                                        viewModel.observeUserVideos(targetUid)
                                    }.collectAsStateWithLifecycle(initialValue = emptyList())
                                    val targetFriendIds = remember(friendshipsState, targetUid) {
                                        friendshipsState
                                            .filter { targetUid in it.participantUids }
                                            .mapNotNull { fs -> fs.participantUids.firstOrNull { it != targetUid } }
                                    }
                                    val targetFriends = remember(targetFriendIds, profilesByUid) {
                                        targetFriendIds.mapNotNull { profilesByUid[it] }
                                    }
                                    val targetFriendStatus = friendStatusForUid(targetUid)
                                    val isLiked = remember(profileLikesState, targetUid) {
                                        profileLikesState.any {
                                            it.fromUid == viewModel.currentUserId && it.toUid == targetUid
                                        }
                                    }
                                    val isFavorited = remember(favoritesState, targetUid) {
                                        favoritesState.any { it.targetUid == targetUid }
                                    }
                                    val isBlocked = remember(data.blockedUsers, targetUid) {
                                        data.blockedUsers.any { it.blockedUid == targetUid }
                                    }
                                    val isMatched = remember(data.matches, targetUid) {
                                        data.matches.any { it.partnerId == targetUid }
                                    }

                                    UserProfileDetailScreen(
                                        profile = targetProfile,
                                        currentUserId = viewModel.currentUserId,
                                        videos = targetVideos,
                                        friends = targetFriends,
                                        friendStatus = targetFriendStatus,
                                        presence = presenceByUid[targetUid],
                                        distanceKm = distanceByUid[targetUid],
                                        isLiked = isLiked,
                                        isFavorited = isFavorited,
                                        isBlocked = isBlocked,
                                        isMatched = isMatched,
                                        resolveVideoFile = { videoItem ->
                                            viewModel.resolveProfileVideoFile(context, targetUid, videoItem)
                                        },
                                        onBack = { navController.popBackStack() },
                                        onRecordProfileView = {
                                            viewModel.recordProfileView(targetUid)
                                        },
                                        onOpenFriendProfile = { friend ->
                                            openUserProfileRoute(friend.userId)
                                        },
                                        onMessageClick = { profileToMsg ->
                                            viewModel.startDirectConversation(profileToMsg) {
                                                navigateToTab(ROUTE_MATCHES)
                                            }
                                        },
                                        onFriendAction = {
                                            handleFriendAction(targetUid, targetFriendStatus)
                                        },
                                        onToggleLike = {
                                            viewModel.toggleProfileLike(targetUid)
                                        },
                                        onToggleFavorite = {
                                            viewModel.toggleFavorite(targetProfile)
                                        },
                                        onReportUser = { reason, details, alsoBlock ->
                                            viewModel.reportUser(
                                                reportedUid = targetUid,
                                                displayName = targetProfile.displayName,
                                                reason = reason,
                                                details = details,
                                                sourceSurface = "USER_PROFILE",
                                                alsoBlock = alsoBlock,
                                                onReported = {
                                                    if (alsoBlock) navController.popBackStack()
                                                }
                                            )
                                        },
                                        onBlockUser = {
                                            viewModel.blockUser(
                                                targetUserId = targetUid,
                                                displayName = targetProfile.displayName
                                            )
                                        },
                                        onUnblockUser = {
                                            viewModel.unblockUser(targetUid)
                                        }
                                    )
                                }

                                composable(ROUTE_ACTIVE_USERS) {
                                    ActiveUsersScreen(
                                        activeMembers = activeMembersState,
                                        onBack = { navController.popBackStack() },
                                        onOpenProfile = { memberProfile ->
                                            openUserProfileRoute(memberProfile.userId)
                                        },
                                        onMessageProfile = { memberProfile ->
                                            viewModel.startDirectConversation(memberProfile) {
                                                navigateToTab(ROUTE_MATCHES)
                                            }
                                        }
                                    )
                                }

                                composable(ROUTE_VIDEO_FEED) {
                                    VideoFeedScreen(
                                        videoPosts = videoFeedState,
                                        currentUserId = viewModel.currentUserId,
                                        resolveVideoFile = { post ->
                                            viewModel.resolveVideoPostFile(context, post)
                                        },
                                        onPublishVideoPost = { caption, prepared ->
                                            viewModel.publishVideoPost(caption, prepared)
                                        },
                                        onToggleLike = { postId, currentlyLiked ->
                                            viewModel.toggleVideoPostLike(postId, currentlyLiked)
                                        },
                                        onDeletePost = { postId ->
                                            viewModel.deleteVideoPost(postId)
                                        },
                                        onOpenAuthorProfile = { authorUid ->
                                            openUserProfileRoute(authorUid)
                                        },
                                        onBack = { navController.popBackStack() },
                                        onStatusMessage = { msg ->
                                            viewModel.postStatusMessage(msg)
                                        }
                                    )
                                }

                                composable(ROUTE_WHO_LIKES_ME) {
                                    WhoLikesMeScreen(
                                        whoLikedMe = whoLikedMeState,
                                        profileViews = profileViewsState,
                                        favorites = favoritesState,
                                        allProfilesByUid = profilesByUid,
                                        onBack = { navController.popBackStack() },
                                        onOpenProfile = { target ->
                                            openUserProfileRoute(target.userId)
                                        },
                                        onLikeBack = { candidate ->
                                            viewModel.likeBackUser(candidate) {
                                                navigateToTab(ROUTE_MATCHES)
                                            }
                                        },
                                        onRemoveFavorite = { target ->
                                            viewModel.toggleFavorite(target)
                                        }
                                    )
                                }

                                composable(ROUTE_FRIENDS_HUB) {
                                    FriendsHubScreen(
                                        currentUserId = viewModel.currentUserId,
                                        friendships = friendshipsState,
                                        friendRequests = friendRequestsState,
                                        suggestedFriends = suggestedFriendsState,
                                        profilesByUid = profilesByUid,
                                        onBack = { navController.popBackStack() },
                                        onOpenProfile = { target ->
                                            openUserProfileRoute(target.userId)
                                        },
                                        onMessageProfile = { target ->
                                            viewModel.startDirectConversation(target) {
                                                navigateToTab(ROUTE_MATCHES)
                                            }
                                        },
                                        onAcceptRequest = { fromUid ->
                                            viewModel.acceptFriendRequest(fromUid)
                                        },
                                        onRejectRequest = { fromUid ->
                                            viewModel.rejectFriendRequest(fromUid)
                                        },
                                        onCancelRequest = { toUid ->
                                            viewModel.cancelFriendRequest(toUid)
                                        },
                                        onSendFriendRequest = { targetUid ->
                                            viewModel.sendFriendRequest(targetUid)
                                        },
                                        onRemoveFriend = { targetUid ->
                                            viewModel.removeFriend(targetUid)
                                        }
                                    )
                                }

                                composable(ROUTE_ADMIN_DASHBOARD) {
                                    if (isAdminUser) {
                                        AdminDashboardScreen(
                                            reports = adminReportsState,
                                            allProfiles = adminAllProfilesState,
                                            onBack = { navController.popBackStack() },
                                            onUpdateReportStatus = { reportId, status ->
                                                viewModel.updateReportStatus(reportId, status)
                                            },
                                            onSetUserSuspended = { targetUid, suspended ->
                                                viewModel.setUserSuspended(targetUid, suspended)
                                            },
                                            onOpenProfile = { target ->
                                                openUserProfileRoute(target.userId)
                                            }
                                        )
                                    } else {
                                        LaunchedEffect(Unit) {
                                            navController.popBackStack()
                                        }
                                    }
                                }
                            }
                            if (showSearchSheet) {
                                ProfileSearchSheet(
                                    searchableProfiles = data.searchableProfiles,
                                    onMessageProfile = { targetProfile ->
                                        showSearchSheet = false
                                        viewModel.startDirectConversation(targetProfile) {
                                            navigateToTab(ROUTE_MATCHES)
                                        }
                                    },
                                    onReportProfile = { targetProfile, reason, details, alsoBlock ->
                                        if (alsoBlock) showSearchSheet = false
                                        viewModel.reportUser(
                                            reportedUid = targetProfile.userId,
                                            displayName = targetProfile.displayName,
                                            reason = reason,
                                            details = details,
                                            sourceSurface = "SEARCH",
                                            alsoBlock = alsoBlock
                                        )
                                    },
                                    onBlockProfile = { targetProfile ->
                                        viewModel.blockUser(
                                            targetUserId = targetProfile.userId,
                                            displayName = targetProfile.displayName
                                        )
                                    },
                                    friendStatusForUid = { uid -> friendStatusForUid(uid) },
                                    onFriendAction = { targetProfile, status ->
                                        handleFriendAction(targetProfile.userId, status)
                                    },
                                    onOpenFullProfile = { targetProfile ->
                                        showSearchSheet = false
                                        openUserProfileRoute(targetProfile.userId)
                                    },
                                    onDismiss = { showSearchSheet = false }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Instant WhatsApp-style incoming call UI when ringing anywhere in the app outside active ChatScreen
        var handledGlobalCallIds by remember { mutableStateOf(emptySet<String>()) }
        val incomingAlert = globalIncomingCallAlert
        if (incomingAlert != null &&
            incomingAlert.session.id !in handledGlobalCallIds &&
            !(currentRoute == ROUTE_MATCHES && selectedMatchId == incomingAlert.session.matchId)
        ) {
            FullscreenIncomingCallDialog(
                session = incomingAlert.session,
                partner = incomingAlert.thread.partnerProfile,
                onAccept = {
                    handledGlobalCallIds = handledGlobalCallIds + incomingAlert.session.id
                    KindredNotificationHelper.cancelIncomingCallNotification(context, incomingAlert.session.id)
                    viewModel.answerCall(incomingAlert.session)
                    viewModel.selectMatch(incomingAlert.session.matchId)
                    navigateToTab(ROUTE_MATCHES)
                },
                onDecline = {
                    handledGlobalCallIds = handledGlobalCallIds + incomingAlert.session.id
                    KindredNotificationHelper.cancelIncomingCallNotification(context, incomingAlert.session.id)
                    viewModel.declineCall(incomingAlert.session)
                }
            )
        }
    }

    // Global active call overlay if user switches tabs during a call
    val ongoingGlobalCall = globalCallSession?.takeIf {
        it.status == "ACTIVE" || (it.status == "RINGING" && it.callerUid == viewModel.currentUserId)
    }
    if (ongoingGlobalCall != null &&
        !(currentRoute == ROUTE_MATCHES && selectedMatchId == ongoingGlobalCall.matchId)
    ) {
        val partnerProfile = (dashboardState as? UiState.Success)?.data?.matches
            ?.firstOrNull { it.match.id == ongoingGlobalCall.matchId }
            ?.partnerProfile
        InAppCallOverlay(
            session = ongoingGlobalCall,
            currentUserId = viewModel.currentUserId,
            partner = partnerProfile,
            incomingAudioChunks = activeCallAudioChunks,
            incomingVideoFrames = activeCallVideoFrames,
            onSendAudioChunk = { seq, sr, b64 ->
                viewModel.sendCallAudioChunk(ongoingGlobalCall, seq, sr, b64)
            },
            onSendVideoFrame = { seq, frameB64 ->
                viewModel.sendCallVideoFrame(ongoingGlobalCall, seq, frameB64)
            },
            onToggleMute = { viewModel.toggleCallMute(ongoingGlobalCall) },
            onToggleCamera = { viewModel.toggleCallCamera(ongoingGlobalCall) },
            onEndCall = { duration -> viewModel.endCall(ongoingGlobalCall, duration) }
        )
    }

    availableAppUpdate?.let { release ->
        val updateUrl = release.apkDownloadUrl ?: release.releasePageUrl
        androidx.compose.material3.AlertDialog(
            onDismissRequest = {
                AppUpdateChecker.dismissTag(context, release.tagName)
                availableAppUpdate = null
            },
            title = {
                Text(
                    text = "Update Available (${release.tagName})",
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "${release.title} is available from ${release.sourceLabel}. You are running ${AppUpdateChecker.currentVersionLabel()}."
                    )
                    Text(
                        text = release.releaseNotes,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                androidx.compose.material3.Button(
                    onClick = {
                        KindredNotificationHelper.cancelAppUpdateNotification(context)
                        AppUpdateChecker.openExternalUpdateUrl(context, updateUrl)
                        availableAppUpdate = null
                    },
                    modifier = Modifier.testTag("download_update_button")
                ) {
                    Text("Install Update")
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        AppUpdateChecker.dismissTag(context, release.tagName)
                        availableAppUpdate = null
                    },
                    modifier = Modifier.testTag("dismiss_update_button")
                ) {
                    Text("Later")
                }
            }
        )
    }

    newMatchPopup?.let { matchItem ->
        NewMatchCelebrationDialog(
            matchItem = matchItem,
            onSendMessage = {
                viewModel.dismissNewMatchPopup()
                viewModel.selectMatch(matchItem.match.id)
                navigateToTab(ROUTE_MATCHES)
            },
            onKeepBrowsing = {
                viewModel.dismissNewMatchPopup()
            }
        )
    }
}

@Composable
private fun TabIconWithOptionalBadge(
    tab: NavTabItem,
    selected: Boolean,
    unreadCount: Int,
    matchCount: Int
) {
    val icon = if (selected) tab.selectedIcon else tab.unselectedIcon
    when {
        tab.route == ROUTE_MATCHES && unreadCount > 0 -> {
            BadgedBox(
                badge = {
                    Badge(containerColor = CoralRose) {
                        Text(if (unreadCount > 9) "9+" else "$unreadCount")
                    }
                }
            ) {
                Icon(imageVector = icon, contentDescription = stringResource(tab.labelRes))
            }
        }
        tab.route == ROUTE_MATCHES && matchCount > 0 -> {
            BadgedBox(
                badge = {
                    Badge(containerColor = MaterialTheme.colorScheme.secondaryContainer) {
                        Text(if (matchCount > 9) "9+" else "$matchCount")
                    }
                }
            ) {
                Icon(imageVector = icon, contentDescription = stringResource(tab.labelRes))
            }
        }
        else -> {
            Icon(imageVector = icon, contentDescription = stringResource(tab.labelRes))
        }
    }
}
