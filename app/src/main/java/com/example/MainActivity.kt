package com.example

import android.Manifest
import android.content.Intent
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
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.data.KindredRepository
import com.example.model.UiState
import com.example.notifications.KindredNotificationHelper
import com.example.security.configureAppCheck
import com.example.service.GeoHashMatchingService
import com.example.ui.components.NewMatchCelebrationDialog
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.DiscoverScreen
import com.example.ui.screens.EditProfileScreen
import com.example.ui.screens.MatchesAndChatScreen
import com.example.ui.screens.ProfileScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.VerificationScreen
import com.example.ui.theme.CoralRose
import com.example.ui.theme.KindredTheme
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
    val statusMessage by viewModel.actionStatusMessage.collectAsStateWithLifecycle()
    val newMatchPopup by viewModel.newMatchPopup.collectAsStateWithLifecycle()
    val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()
    val activeChallenge by viewModel.activeChallenge.collectAsStateWithLifecycle()
    val aiPhotoGenState by viewModel.aiPhotoGenState.collectAsStateWithLifecycle()
    val latestLocation by viewModel.latestLocation.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val presenceScope = rememberCoroutineScope()
    var hasLocationPermission by remember {
        mutableStateOf(LocationAndGeoHelper.hasLocationPermission(context))
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
        }

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    hasLocationPermission = LocationAndGeoHelper.hasLocationPermission(context)
                    startForegroundHeartbeat()
                }
                Lifecycle.Event.ON_RESUME -> {
                    hasLocationPermission = LocationAndGeoHelper.hasLocationPermission(context)
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

    LaunchedEffect(lifecycleOwner, user.uid, hasLocationPermission) {
        if (!hasLocationPermission) return@LaunchedEffect
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

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.syncFcmToken()
        }
    }

    LaunchedEffect(user.uid) {
        KindredNotificationHelper.ensureChannels(context)
        viewModel.syncFcmToken()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !KindredNotificationHelper.hasNotificationPermission(context)
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
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
            }
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

    val matchCount = (dashboardState as? UiState.Success)?.data?.matches?.size ?: 0

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 840.dp
        val isInFullScreenChat = !useRail && currentRoute == ROUTE_MATCHES && !selectedMatchId.isNullOrBlank()
        val hideBottomBar = !useRail && (
            isInFullScreenChat ||
                currentRoute == ROUTE_EDIT_PROFILE ||
                currentRoute == ROUTE_SETTINGS
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
                                        includeDemoProfiles = data.includeDemoProfiles,
                                        onToggleDemoProfiles = { enabled ->
                                            viewModel.toggleDemoProfiles(enabled)
                                        },
                                        onResetDemoSwipes = {
                                            viewModel.resetDemoSwipes()
                                        },
                                        onSwipe = { candidate, direction ->
                                            viewModel.swipeProfile(candidate, direction)
                                        },
                                        onBlockUser = { candidate ->
                                            viewModel.blockUser(
                                                targetUserId = candidate.profile.userId,
                                                displayName = candidate.profile.displayName
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
                                        onOpenSettings = {
                                            navController.navigate(ROUTE_SETTINGS) {
                                                launchSingleTop = true
                                            }
                                        },
                                        latestLocation = latestLocation,
                                        onLocationPermissionGranted = {
                                            hasLocationPermission = true
                                        }
                                    )
                                }

                                composable(ROUTE_MATCHES) {
                                    MatchesAndChatScreen(
                                        currentUserId = viewModel.currentUserId,
                                        matches = data.matches,
                                        selectedMatchId = selectedMatchId,
                                        messagesState = activeMessagesState,
                                        activePartnerPresence = activePartnerPresence,
                                        onSelectMatch = { matchId ->
                                            viewModel.selectMatch(matchId)
                                        },
                                        onSendMessage = { matchId, participants, text, type ->
                                            viewModel.sendMessage(matchId, participants, text, type)
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
                                        onRetryChat = {
                                            viewModel.retryChatObservation()
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
                                        onSaveProfile = { name, age, gender, bio, city, lat, lng, interests, photo, photos, discoverable, interestedIn, minAge, maxAge, maxDist, verifiedOnly ->
                                            viewModel.saveProfile(
                                                displayName = name,
                                                age = age,
                                                gender = gender,
                                                bio = bio,
                                                cityLabel = city,
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
                                                verifiedOnlyFilter = verifiedOnly
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
                                        aiPhotoGenState = aiPhotoGenState,
                                        onGenerateAiPhoto = { preset, customDetails, subjectGender, referenceBitmap ->
                                            viewModel.generateAiProfilePhoto(
                                                preset = preset,
                                                customDetails = customDetails,
                                                subjectGender = subjectGender,
                                                referenceBitmap = referenceBitmap
                                            )
                                        },
                                        onResetAiPhotoState = { viewModel.resetAiPhotoGenState() },
                                        onAddGeneratedPhoto = { dataUrl ->
                                            viewModel.addGeneratedPhotoToProfile(dataUrl)
                                        },
                                        blockedUsers = data.blockedUsers,
                                        onUnblockUser = { targetUid ->
                                            viewModel.unblockUser(targetUid)
                                        },
                                        onNotificationsGranted = {
                                            viewModel.syncFcmToken()
                                        },
                                        onBeforeSignOut = {
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
                                        onBack = { navController.popBackStack() },
                                        onSaveChanges = { bio, interests, photos ->
                                            viewModel.updateProfileContent(
                                                bio = bio,
                                                interests = interests,
                                                profilePhotos = photos,
                                                onComplete = { navController.popBackStack() }
                                            )
                                        },
                                        aiPhotoGenState = aiPhotoGenState,
                                        onGenerateAiPhoto = { preset, customDetails, subjectGender, referenceBitmap ->
                                            viewModel.generateAiProfilePhoto(
                                                preset = preset,
                                                customDetails = customDetails,
                                                subjectGender = subjectGender,
                                                referenceBitmap = referenceBitmap
                                            )
                                        },
                                        onResetAiPhotoState = { viewModel.resetAiPhotoGenState() },
                                        onStatusMessage = { msg ->
                                            viewModel.postStatusMessage(msg)
                                        }
                                    )
                                }

                                composable(ROUTE_SETTINGS) {
                                    SettingsScreen(
                                        myProfile = data.myProfile,
                                        privateUser = data.privateUser,
                                        includeDemoProfiles = data.includeDemoProfiles,
                                        isBusy = isBusy,
                                        onBack = { navController.popBackStack() },
                                        onToggleDemoProfiles = { enabled ->
                                            viewModel.toggleDemoProfiles(enabled)
                                        },
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
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
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
    matchCount: Int
) {
    val icon = if (selected) tab.selectedIcon else tab.unselectedIcon
    if (tab.route == ROUTE_MATCHES && matchCount > 0) {
        BadgedBox(
            badge = {
                Badge(containerColor = CoralRose) {
                    Text(if (matchCount > 9) "9+" else "$matchCount")
                }
            }
        ) {
            Icon(imageVector = icon, contentDescription = stringResource(tab.labelRes))
        }
    } else {
        Icon(imageVector = icon, contentDescription = stringResource(tab.labelRes))
    }
}
