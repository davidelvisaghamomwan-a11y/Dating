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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddBox
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.AddBox
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import coil.Coil
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.example.auth.GoogleAuthHelper
import com.example.communication.CallRingtoneController
import com.example.data.KindredRepository
import com.example.model.PublicProfile
import com.example.model.UiState
import com.example.notifications.KindredBackgroundService
import com.example.notifications.KindredNotificationHelper
import com.example.security.configureAppCheck
import com.example.service.GeoHashMatchingService
import com.example.ui.components.CreatePostComposerSheet
import com.example.ui.components.CreateStoryStudioDialog
import com.example.ui.components.FullscreenIncomingCallDialog
import com.example.ui.components.InAppCallOverlay
import com.example.ui.components.ProfileAvatarImage
import com.example.ui.components.ProfileSearchSheet
import com.example.ui.screens.ActiveUsersScreen
import com.example.ui.screens.ActivityNotificationsScreen
import com.example.ui.screens.AdminDashboardScreen
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.EditProfileScreen
import com.example.ui.screens.ExploreSearchScreen
import com.example.ui.screens.HomeFeedScreen
import com.example.ui.screens.FriendsHubScreen
import com.example.ui.screens.MatchesAndChatScreen
import com.example.ui.screens.ProfileScreen
import com.example.ui.screens.ProfileSetupScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.UserProfileDetailScreen
import com.example.ui.screens.VerificationScreen
import com.example.ui.screens.VideoFeedScreen
import com.example.ui.theme.CoralRose
import com.example.ui.theme.KindredTheme
import com.example.update.AppReleaseInfo
import com.example.update.AppUpdateChecker
import com.example.update.UpdateCheckResult
import com.example.util.LocationAndGeoHelper
import com.example.util.PresenceUtils
import com.example.util.VerificationAnalyzer
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
import java.io.File

const val ROUTE_HOME = "home"
const val ROUTE_SEARCH = "search"
const val ROUTE_ACTIVITY = "activity"
const val ROUTE_MATCHES = "matches"
const val ROUTE_VERIFY = "verify"
const val ROUTE_PROFILE = "profile"
const val ROUTE_EDIT_PROFILE = "edit_profile"
const val ROUTE_SETTINGS = "settings"
const val ROUTE_USER_PROFILE = "user_profile/{uid}"
const val ROUTE_ACTIVE_USERS = "active_users"
const val ROUTE_VIDEO_FEED = "video_feed"
const val ROUTE_FRIENDS_HUB_BASE = "friends_hub"
const val ROUTE_FRIENDS_HUB = "friends_hub?tab={tab}"
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
        route = ROUTE_HOME,
        labelRes = R.string.nav_home,
        selectedIcon = Icons.Filled.Home,
        unselectedIcon = Icons.Outlined.Home,
        testTag = "nav_home_tab"
    ),
    NavTabItem(
        route = ROUTE_SEARCH,
        labelRes = R.string.nav_search,
        selectedIcon = Icons.Filled.Search,
        unselectedIcon = Icons.Outlined.Search,
        testTag = "nav_search_tab"
    ),
    NavTabItem(
        route = "create",
        labelRes = R.string.nav_create,
        selectedIcon = Icons.Filled.AddBox,
        unselectedIcon = Icons.Outlined.AddBox,
        testTag = "nav_create_tab"
    ),
    NavTabItem(
        route = ROUTE_VIDEO_FEED,
        labelRes = R.string.nav_reels,
        selectedIcon = Icons.Filled.Movie,
        unselectedIcon = Icons.Outlined.Movie,
        testTag = "nav_reels_tab"
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
        VerificationAnalyzer.initImageCache(applicationContext)
        Coil.setImageLoader {
            ImageLoader.Builder(applicationContext)
                .memoryCache {
                    MemoryCache.Builder(applicationContext)
                        .maxSizePercent(0.25)
                        .build()
                }
                .diskCache {
                    DiskCache.Builder()
                        .directory(File(applicationContext.cacheDir, "coil_image_cache"))
                        .maxSizeBytes(100L * 1024L * 1024L)
                        .build()
                }
                .respectCacheHeaders(false)
                .crossfade(true)
                .build()
        }
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

@OptIn(ExperimentalMaterial3Api::class)
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
    val allFollowsState by viewModel.allFollowsState.collectAsStateWithLifecycle()
    val followingIdsState by viewModel.followingIdsState.collectAsStateWithLifecycle()
    val followerIdsState by viewModel.followerIdsState.collectAsStateWithLifecycle()
    val suggestedProfilesState by viewModel.suggestedProfilesState.collectAsStateWithLifecycle()
    val socialActivityState by viewModel.socialActivityState.collectAsStateWithLifecycle()
    val profileViewsState by viewModel.profileViewsState.collectAsStateWithLifecycle()
    val profileLikesState by viewModel.profileLikesState.collectAsStateWithLifecycle()
    val favoritesState by viewModel.favoritesState.collectAsStateWithLifecycle()
    val activeStoriesState by viewModel.activeStoriesState.collectAsStateWithLifecycle()
    val videoFeedState by viewModel.videoFeedState.collectAsStateWithLifecycle()
    val feedPostsState by viewModel.feedPostsState.collectAsStateWithLifecycle()
    val isFeedRefreshing by viewModel.isFeedRefreshing.collectAsStateWithLifecycle()
    val myShowcaseVideos by remember(user.uid) {
        viewModel.observeUserVideos(user.uid)
    }.collectAsStateWithLifecycle(initialValue = emptyList())
    val myHighlights by remember(user.uid) {
        viewModel.observeUserHighlights(user.uid)
    }.collectAsStateWithLifecycle(initialValue = emptyList())
    val myStoryArchive by remember(user.uid) {
        viewModel.observeUserStoriesArchive(user.uid)
    }.collectAsStateWithLifecycle(initialValue = emptyList())
    var showCreateChooserSheet by remember { mutableStateOf(false) }
    var showGlobalCreatePostSheet by remember { mutableStateOf(false) }
    var showGlobalCreateStoryStudio by remember { mutableStateOf(false) }
    val isAdminUser by viewModel.isAdminUser.collectAsStateWithLifecycle()
    val adminReportsState by viewModel.adminReportsState.collectAsStateWithLifecycle()
    val adminAllProfilesState by viewModel.adminAllProfilesState.collectAsStateWithLifecycle()
    val icebreakerAiState by viewModel.icebreakerAiState.collectAsStateWithLifecycle()
    val statusMessage by viewModel.actionStatusMessage.collectAsStateWithLifecycle()
    val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()
    val activeChallenge by viewModel.activeChallenge.collectAsStateWithLifecycle()
    val latestLocation by viewModel.latestLocation.collectAsStateWithLifecycle()
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
    val currentRoute = navBackStackEntry?.destination?.route ?: ROUTE_HOME

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

                is KindredPushAlert.NewFollowerAlert -> {
                    KindredNotificationHelper.showSocialNotification(
                        context = context,
                        title = "New Follower",
                        body = "${alert.fromName} started following you on Kindred.",
                        notificationType = KindredNotificationHelper.TYPE_NEW_FOLLOWER,
                        tagKey = "follow_${alert.fromUid}"
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
        if (route == "create") {
            showCreateChooserSheet = true
            return
        }
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

    fun openUserProfileRoute(uid: String) {
        if (uid.isBlank()) return
        navController.navigate("user_profile/${Uri.encode(uid)}") {
            launchSingleTop = true
        }
    }

    val unreadInboxCount = successData?.matches
        ?.count { it.match.isUnreadFor(viewModel.currentUserId) } ?: 0
    val activeOnlineCount = activeMembersState.count { it.isOnline }
    val activityBadgeCount = socialActivityState.size

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 840.dp
        val isInFullScreenChat = !useRail && currentRoute == ROUTE_MATCHES && !selectedMatchId.isNullOrBlank()
        val hideBottomBar = !useRail && (
            isInFullScreenChat ||
                currentRoute == ROUTE_EDIT_PROFILE ||
                currentRoute == ROUTE_SETTINGS ||
                currentRoute.startsWith(ROUTE_FRIENDS_HUB_BASE) ||
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
                        containerColor = MaterialTheme.colorScheme.background,
                        tonalElevation = 0.dp
                    ) {
                        NAV_TABS.forEach { tab ->
                            val selected = currentRoute == tab.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateToTab(tab.route) },
                                alwaysShowLabel = true,
                                label = { Text(stringResource(tab.labelRes)) },
                                modifier = Modifier.testTag(tab.testTag),
                                icon = {
                                    TabIconWithOptionalBadge(
                                        tab = tab,
                                        selected = selected,
                                        activityBadgeCount = activityBadgeCount,
                                        myProfile = successData?.myProfile
                                    )
                                }
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
                                        activityBadgeCount = activityBadgeCount,
                                        myProfile = successData?.myProfile
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

                            var initialReelIdForVideoFeed by remember { mutableStateOf<String?>(null) }

                            NavHost(
                                navController = navController,
                                startDestination = ROUTE_HOME,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                composable(ROUTE_HOME) {
                                    HomeFeedScreen(
                                        myProfile = data.myProfile,
                                        candidates = data.discoveryCandidates,
                                        onReportUser = { candidate, reason, details, alsoBlock ->
                                            viewModel.reportUser(
                                                reportedUid = candidate.profile.userId,
                                                displayName = candidate.profile.displayName,
                                                reason = reason,
                                                details = details,
                                                sourceSurface = "HOME_FEED",
                                                alsoBlock = alsoBlock
                                            )
                                        },
                                        onRefreshLocation = { resolved ->
                                            viewModel.applyResolvedLocation(resolved)
                                        },
                                        onLocationError = { msg ->
                                            viewModel.postStatusMessage(msg)
                                        },
                                        onOpenSearch = { navigateToTab(ROUTE_SEARCH) },
                                        onLocationPermissionGranted = {
                                            hasLocationPermission = true
                                        },
                                        activeOnlineCount = activeOnlineCount,
                                        onOpenActiveUsers = {
                                            navController.navigate(ROUTE_ACTIVE_USERS) {
                                                launchSingleTop = true
                                            }
                                        },
                                        stories = activeStoriesState,
                                        onPostPhotoStory = { photoUrl, caption, onDone ->
                                            viewModel.postPhotoStory(photoUrl, caption, onDone)
                                        },
                                        onPostVideoStory = { prepared, caption, onDone ->
                                            viewModel.postVideoStory(prepared, caption, onDone)
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
                                            initialReelIdForVideoFeed = null
                                            navController.navigate(ROUTE_VIDEO_FEED) {
                                                launchSingleTop = true
                                            }
                                        },
                                        suggestedProfiles = data.searchableProfiles,
                                        feedPosts = feedPostsState,
                                        videoPosts = videoFeedState,
                                        currentUserId = viewModel.currentUserId,
                                        isFollowingUid = { uid -> uid in followingIdsState },
                                        onToggleFollow = { profile ->
                                            viewModel.toggleFollow(profile.userId)
                                        },
                                        onOpenUserProfile = { profile ->
                                            openUserProfileRoute(profile.userId)
                                        },
                                        onOpenUserProfileById = { uid ->
                                            openUserProfileRoute(uid)
                                        },
                                        onPublishFeedPost = { caption, photoDataUrl, photoUrls, preparedVideo, onDone ->
                                            viewModel.publishFeedPost(caption, photoDataUrl, photoUrls, preparedVideo, onDone)
                                        },
                                        onToggleFeedPostLike = { postId, currentlyLiked ->
                                            viewModel.toggleFeedPostLike(postId, currentlyLiked)
                                        },
                                        onToggleFeedPostSave = { postId, currentlySaved ->
                                            viewModel.toggleFeedPostSave(postId, currentlySaved)
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
                                        },
                                        onToggleVideoPostLike = { postId, currentlyLiked ->
                                            viewModel.toggleVideoPostLike(postId, currentlyLiked)
                                        },
                                        onDeleteVideoPost = { postId ->
                                            viewModel.deleteVideoPost(postId)
                                        },
                                        resolveVideoPostFile = { videoPost ->
                                            viewModel.resolveVideoPostFile(context, videoPost)
                                        },
                                        observeVideoComments = { postId ->
                                            viewModel.observeVideoPostComments(postId)
                                        },
                                        onAddVideoComment = { postId, text, onDone ->
                                            viewModel.addVideoPostComment(postId, text, onDone)
                                        },
                                        onDeleteVideoComment = { postId, commentId ->
                                            viewModel.deleteVideoPostComment(postId, commentId)
                                        },
                                        onOpenReelInVideoFeed = { reelId ->
                                            initialReelIdForVideoFeed = reelId
                                            navController.navigate(ROUTE_VIDEO_FEED) {
                                                launchSingleTop = true
                                            }
                                        },
                                        isFeedRefreshing = isFeedRefreshing,
                                        onRefreshFeed = {
                                            viewModel.refreshFeed()
                                        },
                                        unreadMessageCount = unreadInboxCount,
                                        incomingNotificationCount = activityBadgeCount,
                                        profilesByUid = profilesByUid,
                                        onReplyToStory = { authorUid, replyText ->
                                            viewModel.replyToStory(authorUid, replyText)
                                        },
                                        onOpenActivity = {
                                            navigateToTab(ROUTE_ACTIVITY)
                                        },
                                        onNavigateToInbox = {
                                            navigateToTab(ROUTE_MATCHES)
                                        }
                                    )
                                }

                                composable(ROUTE_SEARCH) {
                                    ExploreSearchScreen(
                                        currentUserId = viewModel.currentUserId,
                                        myProfile = data.myProfile,
                                        searchableProfiles = data.searchableProfiles,
                                        feedPosts = feedPostsState,
                                        videoPosts = videoFeedState,
                                        profilesByUid = profilesByUid,
                                        activeMembers = activeMembersState,
                                        onOpenActiveUsers = {
                                            navController.navigate(ROUTE_ACTIVE_USERS) {
                                                launchSingleTop = true
                                            }
                                        },
                                        isFollowingUid = { uid -> uid in followingIdsState },
                                        onToggleFollow = { target ->
                                            viewModel.toggleFollow(target.userId)
                                        },
                                        onOpenUserProfile = { targetUid ->
                                            openUserProfileRoute(targetUid)
                                        },
                                        onToggleFeedPostLike = { postId, currentlyLiked ->
                                            viewModel.toggleFeedPostLike(postId, currentlyLiked)
                                        },
                                        onToggleFeedPostSave = { postId, currentlySaved ->
                                            viewModel.toggleFeedPostSave(postId, currentlySaved)
                                        },
                                        onToggleVideoPostLike = { postId, currentlyLiked ->
                                            viewModel.toggleVideoPostLike(postId, currentlyLiked)
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
                                        observeVideoComments = { postId ->
                                            viewModel.observeVideoPostComments(postId)
                                        },
                                        onAddVideoComment = { postId, text, onDone ->
                                            viewModel.addVideoPostComment(postId, text, onDone)
                                        },
                                        onDeleteVideoComment = { postId, commentId ->
                                            viewModel.deleteVideoPostComment(postId, commentId)
                                        },
                                        onDeleteFeedPost = { postId ->
                                            viewModel.deleteFeedPost(postId)
                                        },
                                        resolveFeedPostVideoFile = { post ->
                                            viewModel.resolveFeedPostVideoFile(context, post)
                                        },
                                        resolveVideoPostFile = { videoPost ->
                                            viewModel.resolveVideoPostFile(context, videoPost)
                                        },
                                        onRefreshExplore = {
                                            viewModel.refreshFeed()
                                        }
                                    )
                                }

                                composable(ROUTE_ACTIVITY) {
                                    ActivityNotificationsScreen(
                                        activities = socialActivityState,
                                        followingIds = followingIdsState,
                                        followerIds = followerIdsState,
                                        profileByUid = profilesByUid,
                                        onToggleFollow = { targetUid ->
                                            viewModel.toggleFollow(targetUid)
                                        },
                                        onOpenProfile = { targetUid ->
                                            openUserProfileRoute(targetUid)
                                        },
                                        onOpenReels = { reelId ->
                                            initialReelIdForVideoFeed = reelId
                                            navController.navigate(ROUTE_VIDEO_FEED) {
                                                launchSingleTop = true
                                            }
                                        },
                                        onOpenExplore = {
                                            navigateToTab(ROUTE_SEARCH)
                                        },
                                        onBack = {
                                            if (!navController.popBackStack()) {
                                                navigateToTab(ROUTE_HOME)
                                            }
                                        }
                                    )
                                }

                                composable(ROUTE_MATCHES) {
                                    val followingProfilesList = remember(followingIdsState, profilesByUid) {
                                        followingIdsState.mapNotNull { profilesByUid[it] }
                                    }
                                    val onlineUserIds = remember(activeMembersState) {
                                        activeMembersState.filter { it.isOnline }.map { it.profile.userId }.toSet()
                                    }
                                    MatchesAndChatScreen(
                                        currentUserId = viewModel.currentUserId,
                                        myProfile = data.myProfile,
                                        matches = data.matches,
                                        selectedMatchId = selectedMatchId,
                                        messagesState = activeMessagesState,
                                        followingProfiles = followingProfilesList,
                                        onlineUserIds = onlineUserIds,
                                        onMessageProfile = { profile ->
                                            viewModel.startDirectConversation(profile)
                                        },
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
                                    val myFeedPosts = remember(feedPostsState, viewModel.currentUserId) {
                                        feedPostsState.filter { it.userId == viewModel.currentUserId }
                                    }
                                    val savedFeedPosts = remember(feedPostsState, viewModel.currentUserId) {
                                        feedPostsState.filter { viewModel.currentUserId in it.savedByUids }
                                    }
                                    ProfileScreen(
                                        currentUserId = viewModel.currentUserId,
                                        userEmail = viewModel.userEmail,
                                        defaultDisplayName = viewModel.defaultDisplayName,
                                        myProfile = data.myProfile,
                                        myPosts = myFeedPosts,
                                        savedPosts = savedFeedPosts,
                                        profilesByUid = profilesByUid,
                                        myVideos = myShowcaseVideos,
                                        highlights = myHighlights,
                                        archivedStories = myStoryArchive,
                                        followersCount = followerIdsState.size,
                                        followingCount = followingIdsState.size,
                                        profileViewCount = profileViewsState.size,
                                        activityBadgeCount = activityBadgeCount,
                                        isAdmin = isAdminUser,
                                        isFollowingUid = { uid -> uid in followingIdsState },
                                        onToggleFollow = { target ->
                                            viewModel.toggleFollow(target.userId)
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
                                        onOpenFollowersFollowing = { tabIndex ->
                                            navController.navigate("$ROUTE_FRIENDS_HUB_BASE?tab=$tabIndex") {
                                                launchSingleTop = true
                                            }
                                        },
                                        onOpenActivity = {
                                            navigateToTab(ROUTE_ACTIVITY)
                                        },
                                        onOpenAdminDashboard = {
                                            navController.navigate(ROUTE_ADMIN_DASHBOARD) {
                                                launchSingleTop = true
                                            }
                                        },
                                        onOpenVerification = {
                                            navigateToTab(ROUTE_VERIFY)
                                        },
                                        onOpenVideoFeed = {
                                            navController.navigate(ROUTE_VIDEO_FEED) {
                                                launchSingleTop = true
                                            }
                                        },
                                        onOpenCreatePost = {
                                            showGlobalCreatePostSheet = true
                                        },
                                        onOpenCreateStory = {
                                            showGlobalCreateStoryStudio = true
                                        },
                                        onOpenUserProfile = { targetUid ->
                                            openUserProfileRoute(targetUid)
                                        },
                                        onToggleFeedPostLike = { postId, currentlyLiked ->
                                            viewModel.toggleFeedPostLike(postId, currentlyLiked)
                                        },
                                        onToggleFeedPostSave = { postId, currentlySaved ->
                                            viewModel.toggleFeedPostSave(postId, currentlySaved)
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
                                        },
                                        resolveProfileVideoFile = { videoItem ->
                                            viewModel.resolveProfileVideoFile(context, viewModel.currentUserId, videoItem)
                                        },
                                        onDeleteVideo = { videoId ->
                                            viewModel.deleteProfileVideo(viewModel.currentUserId, videoId)
                                        },
                                        onCreateHighlight = { title, coverUrl, items ->
                                            viewModel.createStoryHighlight(title, coverUrl, items)
                                        },
                                        onDeleteHighlight = { highlightId ->
                                            viewModel.deleteStoryHighlight(highlightId)
                                        },
                                        onBeforeSignOut = {
                                            KindredNotificationHelper.activeMatchIdInForeground = null
                                            KindredBackgroundService.stop(context)
                                            selectedMatchId?.let { viewModel.setTypingStatus(it, false) }
                                            viewModel.unregisterFcmTokenOnSignOut()
                                            viewModel.updatePresenceNow(false)
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
                                        blockedUsers = data.blockedUsers,
                                        isAdmin = isAdminUser,
                                        onBack = { navController.popBackStack() },
                                        onOpenEditProfile = {
                                            navController.navigate(ROUTE_EDIT_PROFILE) {
                                                launchSingleTop = true
                                            }
                                        },
                                        onOpenVerification = {
                                            navController.navigate(ROUTE_VERIFY) {
                                                launchSingleTop = true
                                            }
                                        },
                                        onOpenFollowersFollowing = {
                                            navController.navigate("$ROUTE_FRIENDS_HUB_BASE?tab=0") {
                                                launchSingleTop = true
                                            }
                                        },
                                        onOpenActivity = {
                                            navigateToTab(ROUTE_ACTIVITY)
                                        },
                                        onOpenAdminDashboard = {
                                            navController.navigate(ROUTE_ADMIN_DASHBOARD) {
                                                launchSingleTop = true
                                            }
                                        },
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
                                        onToggleDiscoverable = { discoverable ->
                                            viewModel.updateDiscoverable(discoverable)
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
                                        },
                                        onStatusMessage = { msg ->
                                            viewModel.postStatusMessage(msg)
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
                                    val targetHighlights by remember(targetUid) {
                                        viewModel.observeUserHighlights(targetUid)
                                    }.collectAsStateWithLifecycle(initialValue = emptyList())
                                    val targetFollowerIds = remember(allFollowsState, targetUid) {
                                        allFollowsState
                                            .filter { it.followingUid == targetUid }
                                            .map { it.followerUid }
                                            .toSet()
                                    }
                                    val targetFollowingIds = remember(allFollowsState, targetUid) {
                                        allFollowsState
                                            .filter { it.followerUid == targetUid }
                                            .map { it.followingUid }
                                            .toSet()
                                    }
                                    val mutualFollowerProfiles = remember(targetFollowerIds, followingIdsState, profilesByUid) {
                                        targetFollowerIds
                                            .filter { it in followingIdsState }
                                            .mapNotNull { profilesByUid[it] }
                                    }
                                    val isFollowingTarget = targetUid in followingIdsState
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
                                    val targetUserPosts = remember(feedPostsState, targetUid) {
                                        feedPostsState.filter { it.userId == targetUid }
                                    }
                                    val targetUserReels = remember(videoFeedState, targetUid) {
                                        videoFeedState.filter { it.userId == targetUid }
                                    }

                                    UserProfileDetailScreen(
                                        profile = targetProfile,
                                        currentUserId = viewModel.currentUserId,
                                        videos = targetVideos,
                                        followerIds = targetFollowerIds,
                                        followingIds = targetFollowingIds,
                                        mutualFollowerProfiles = mutualFollowerProfiles,
                                        isFollowing = isFollowingTarget,
                                        presence = presenceByUid[targetUid],
                                        distanceKm = distanceByUid[targetUid],
                                        isLiked = isLiked,
                                        isFavorited = isFavorited,
                                        isBlocked = isBlocked,
                                        resolveVideoFile = { videoItem ->
                                            viewModel.resolveProfileVideoFile(context, targetUid, videoItem)
                                        },
                                        onBack = { navController.popBackStack() },
                                        onRecordProfileView = {
                                            viewModel.recordProfileView(targetUid)
                                        },
                                        onOpenMutualProfile = { mutual ->
                                            openUserProfileRoute(mutual.userId)
                                        },
                                        onOpenFollowersFollowing = { tabIndex ->
                                            navController.navigate("$ROUTE_FRIENDS_HUB_BASE?tab=$tabIndex") {
                                                launchSingleTop = true
                                            }
                                        },
                                        onMessageClick = { profileToMsg ->
                                            viewModel.startDirectConversation(profileToMsg) {
                                                navigateToTab(ROUTE_MATCHES)
                                            }
                                        },
                                        onToggleFollow = {
                                            viewModel.toggleFollow(targetUid)
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
                                        },
                                        highlights = targetHighlights,
                                        userPosts = targetUserPosts,
                                        userReels = targetUserReels,
                                        onStatusMessage = { msg -> viewModel.postStatusMessage(msg) }
                                    )
                                }

                                composable(ROUTE_ACTIVE_USERS) {
                                    ActiveUsersScreen(
                                        activeMembers = activeMembersState,
                                        onBack = {
                                            if (!navController.popBackStack()) {
                                                navigateToTab(ROUTE_HOME)
                                            }
                                        },
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
                                        profilesByUid = profilesByUid,
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
                                        onBack = {
                                            if (!navController.popBackStack()) {
                                                navigateToTab(ROUTE_HOME)
                                            }
                                        },
                                        onStatusMessage = { msg ->
                                            viewModel.postStatusMessage(msg)
                                        },
                                        isFollowingUid = { uid -> uid in followingIdsState },
                                        onToggleFollow = { uid ->
                                            viewModel.toggleFollow(uid)
                                        },
                                        myProfile = data.myProfile,
                                        observeComments = { postId ->
                                            viewModel.observeVideoPostComments(postId)
                                        },
                                        onAddComment = { postId, text ->
                                            viewModel.addVideoPostComment(postId, text)
                                        },
                                        onDeleteComment = { postId, commentId ->
                                            viewModel.deleteVideoPostComment(postId, commentId)
                                        },
                                        initialPostId = initialReelIdForVideoFeed
                                    )
                                }

                                composable(
                                    route = ROUTE_FRIENDS_HUB,
                                    arguments = listOf(
                                        navArgument("tab") {
                                            type = NavType.IntType
                                            defaultValue = 0
                                        }
                                    )
                                ) { backStackEntry ->
                                    val initialTab = backStackEntry.arguments?.getInt("tab") ?: 0
                                    val suggestedList = remember(suggestedProfilesState) {
                                        suggestedProfilesState.map { it.profile }
                                    }
                                    FriendsHubScreen(
                                        currentUserId = viewModel.currentUserId,
                                        followerIds = followerIdsState,
                                        followingIds = followingIdsState,
                                        suggestedProfiles = suggestedList,
                                        profilesByUid = profilesByUid,
                                        initialTab = initialTab,
                                        onBack = { navController.popBackStack() },
                                        onOpenProfile = { target ->
                                            openUserProfileRoute(target.userId)
                                        },
                                        onMessageProfile = { target ->
                                            viewModel.startDirectConversation(target) {
                                                navigateToTab(ROUTE_MATCHES)
                                            }
                                        },
                                        onToggleFollow = { targetUid ->
                                            viewModel.toggleFollow(targetUid)
                                        },
                                        onRemoveFollower = { followerUid ->
                                            viewModel.removeFollower(followerUid)
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
                                    isFollowingUid = { uid -> uid in followingIdsState },
                                    onToggleFollow = { targetProfile ->
                                        viewModel.toggleFollow(targetProfile.userId)
                                    },
                                    onOpenFullProfile = { targetProfile ->
                                        showSearchSheet = false
                                        openUserProfileRoute(targetProfile.userId)
                                    },
                                    onDismiss = { showSearchSheet = false }
                                )
                            }
                            if (showCreateChooserSheet) {
                                ModalBottomSheet(
                                    onDismissRequest = { showCreateChooserSheet = false },
                                    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .navigationBarsPadding()
                                            .padding(horizontal = 20.dp, vertical = 8.dp)
                                            .padding(bottom = 20.dp)
                                            .testTag("create_chooser_sheet"),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = "Create",
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                            modifier = Modifier.padding(bottom = 6.dp)
                                        )
                                        ListItem(
                                            headlineContent = { Text("Post photo, carousel or video") },
                                            supportingContent = { Text("Share to your followers and Home feed") },
                                            leadingContent = {
                                                Icon(
                                                    imageVector = Icons.Filled.PhotoLibrary,
                                                    contentDescription = "Create post",
                                                    tint = CoralRose
                                                )
                                            },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    showCreateChooserSheet = false
                                                    showGlobalCreatePostSheet = true
                                                }
                                                .testTag("create_option_post")
                                        )
                                        ListItem(
                                            headlineContent = { Text("Reel") },
                                            supportingContent = { Text("Upload a short vertical video reel") },
                                            leadingContent = {
                                                Icon(
                                                    imageVector = Icons.Filled.Movie,
                                                    contentDescription = "Create reel",
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                            },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    showCreateChooserSheet = false
                                                    initialReelIdForVideoFeed = null
                                                    navController.navigate(ROUTE_VIDEO_FEED) {
                                                        launchSingleTop = true
                                                    }
                                                }
                                                .testTag("create_option_reel")
                                        )
                                        ListItem(
                                            headlineContent = { Text("Story") },
                                            supportingContent = { Text("Share a 24-hour photo or video story") },
                                            leadingContent = {
                                                Icon(
                                                    imageVector = Icons.Filled.AutoAwesome,
                                                    contentDescription = "Create story",
                                                    tint = CoralRose
                                                )
                                            },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    showCreateChooserSheet = false
                                                    showGlobalCreateStoryStudio = true
                                                }
                                                .testTag("create_option_story")
                                        )
                                    }
                                }
                            }
                            if (showGlobalCreatePostSheet) {
                                CreatePostComposerSheet(
                                    myProfile = data.myProfile,
                                    onCreateStoryClick = {
                                        showGlobalCreateStoryStudio = true
                                    },
                                    onDismiss = { showGlobalCreatePostSheet = false },
                                    onPublish = { caption, photoDataUrl, photoUrls, video ->
                                        viewModel.publishFeedPost(caption, photoDataUrl, photoUrls, video) {}
                                    }
                                )
                            }
                            if (showGlobalCreateStoryStudio) {
                                CreateStoryStudioDialog(
                                    myProfile = data.myProfile,
                                    currentUserId = viewModel.currentUserId,
                                    onPostPhotoStory = { photoUrl, caption, onDone ->
                                        viewModel.postPhotoStory(photoUrl, caption, onDone)
                                    },
                                    onPostVideoStory = { prepared, caption, onDone ->
                                        viewModel.postVideoStory(prepared, caption, onDone)
                                    },
                                    onStatusMessage = { msg ->
                                        viewModel.postStatusMessage(msg)
                                    },
                                    onDismiss = { showGlobalCreateStoryStudio = false }
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

}

@Composable
private fun TabIconWithOptionalBadge(
    tab: NavTabItem,
    selected: Boolean,
    activityBadgeCount: Int,
    myProfile: PublicProfile? = null
) {
    val icon = if (selected) tab.selectedIcon else tab.unselectedIcon
    when {
        tab.route == ROUTE_PROFILE && myProfile != null -> {
            ProfileAvatarImage(
                photoDataUrl = myProfile.photoDataUrl,
                displayName = myProfile.displayName,
                seed = myProfile.avatarSeed,
                modifier = Modifier
                    .size(28.dp)
                    .then(
                        if (selected) {
                            Modifier.border(2.dp, MaterialTheme.colorScheme.onBackground, CircleShape)
                        } else {
                            Modifier
                        }
                    ),
                shape = CircleShape,
                initialsFontSizeSp = 11
            )
        }
        tab.route == ROUTE_ACTIVITY && activityBadgeCount > 0 -> {
            BadgedBox(
                badge = {
                    Badge(containerColor = CoralRose) {
                        Text(if (activityBadgeCount > 9) "9+" else "$activityBadgeCount")
                    }
                }
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = stringResource(tab.labelRes),
                    modifier = Modifier.size(26.dp)
                )
            }
        }
        else -> {
            Icon(
                imageVector = icon,
                contentDescription = stringResource(tab.labelRes),
                modifier = Modifier.size(26.dp)
            )
        }
    }
}
