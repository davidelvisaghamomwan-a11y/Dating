package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.view.ViewGroup
import android.widget.MediaController
import android.widget.VideoView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.model.DiscoveryCandidate
import com.example.model.FeedComment
import com.example.model.FeedPost
import com.example.model.FriendStatus
import com.example.model.KindredConstants
import com.example.model.PrivateUser
import com.example.model.PublicProfile
import com.example.model.StoryItem
import com.example.ui.components.LocationDistancePill
import com.example.ui.components.ProfileAvatarImage
import com.example.ui.components.ReportUserDialog
import com.example.ui.components.StoriesTrayRow
import com.example.ui.components.StoryViewerDialog
import com.example.ui.components.SwipeCardController
import com.example.ui.components.SwipeCardDeck
import com.example.ui.components.SwipeDirection
import com.example.ui.components.VerifiedShieldBadge
import com.example.ui.theme.ChampagneGold
import com.example.ui.theme.CoralRose
import com.example.ui.theme.EmeraldMint
import com.example.util.CountryFlagHelper
import com.example.util.LocationAndGeoHelper
import com.example.util.PreparedVideoUpload
import com.example.util.ResolvedDeviceLocation
import com.example.util.VerificationAnalyzer
import com.example.util.VideoClipHelper
import com.example.viewmodel.GeoHashContextMeta
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DiscoverScreen(
    myProfile: PublicProfile?,
    privateUser: PrivateUser?,
    geoHashMeta: GeoHashContextMeta?,
    candidates: List<DiscoveryCandidate>,
    onSwipe: (DiscoveryCandidate, String) -> Unit,
    onBlockUser: (DiscoveryCandidate) -> Unit = {},
    onReportUser: (candidate: DiscoveryCandidate, reason: String, details: String, alsoBlock: Boolean) -> Unit = { _, _, _, _ -> },
    onRefreshLocation: (ResolvedDeviceLocation) -> Unit,
    onLocationError: (String) -> Unit,
    onUpdateFilters: (String, Int, Int, Int, Boolean) -> Unit,
    onNavigateToProfile: () -> Unit,
    onNavigateToVerify: () -> Unit,
    onOpenSearch: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    latestLocation: ResolvedDeviceLocation? = null,
    onLocationPermissionGranted: () -> Unit = {},
    canUndoLastSwipe: Boolean = false,
    onUndoSwipe: () -> Unit = {},
    activeOnlineCount: Int = 0,
    onOpenActiveUsers: () -> Unit = {},
    stories: List<StoryItem> = emptyList(),
    onPostPhotoStory: (photoDataUrl: String, caption: String) -> Unit = { _, _ -> },
    onPostVideoStory: (prepared: PreparedVideoUpload, caption: String) -> Unit = { _, _ -> },
    onMarkStoryViewed: (String) -> Unit = {},
    onDeleteStory: (String) -> Unit = {},
    resolveStoryVideoFile: suspend (StoryItem) -> Result<File> = { Result.failure(IllegalStateException("Unavailable")) },
    onOpenVideoFeed: () -> Unit = {},
    suggestedProfiles: List<DiscoveryCandidate> = emptyList(),
    feedPosts: List<FeedPost> = emptyList(),
    currentUserId: String = myProfile?.userId ?: "",
    friendStatusForUid: (String) -> FriendStatus = { FriendStatus.NONE },
    onFriendAction: (PublicProfile) -> Unit = {},
    onOpenUserProfile: (PublicProfile) -> Unit = {},
    onOpenUserProfileById: (String) -> Unit = {},
    onPublishFeedPost: (caption: String, photoDataUrl: String?, preparedVideo: PreparedVideoUpload?, onComplete: () -> Unit) -> Unit = { _, _, _, cb -> cb() },
    onToggleFeedPostLike: (postId: String, currentlyLiked: Boolean) -> Unit = { _, _ -> },
    observeFeedComments: (String) -> Flow<List<FeedComment>> = { flowOf(emptyList()) },
    onAddFeedPostComment: (postId: String, text: String, onDone: () -> Unit) -> Unit = { _, _, cb -> cb() },
    onDeleteFeedPostComment: (postId: String, commentId: String) -> Unit = { _, _ -> },
    onDeleteFeedPost: (postId: String) -> Unit = {},
    resolveFeedPostVideoFile: suspend (FeedPost) -> Result<File> = { Result.failure(IllegalStateException("Unavailable")) }
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showFilterSheet by remember { mutableStateOf(false) }
    var blockConfirmCandidate by remember { mutableStateOf<DiscoveryCandidate?>(null) }
    var reportConfirmCandidate by remember { mutableStateOf<DiscoveryCandidate?>(null) }
    var isLocating by remember { mutableStateOf(false) }
    var selectedMode by remember { mutableStateOf("DECK") }
    var activeStoryIndex by remember { mutableStateOf<Int?>(null) }
    var showPostComposerSheet by remember { mutableStateOf(false) }
    var activeCommentsPost by remember { mutableStateOf<FeedPost?>(null) }

    fun fetchAndApplyGps() {
        isLocating = true
        scope.launch {
            val loc = LocationAndGeoHelper.fetchCurrentLocation(context)
            isLocating = false
            if (loc != null) {
                onRefreshLocation(loc)
            } else {
                onLocationError("Unable to obtain GPS fix. Ensure location is turned on in your device settings.")
            }
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val granted = grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true ||
            grants[Manifest.permission.ACCESS_FINE_LOCATION] == true
        if (granted) {
            onLocationPermissionGranted()
            fetchAndApplyGps()
        } else {
            onLocationError("Location permission denied. You can still set your city manually in Profile.")
        }
    }

    val maxDistanceKm = (privateUser?.maxDistanceKm ?: 100L).toInt()
    val isLiveGps = latestLocation?.isLiveGps == true
    val cityDisplay = latestLocation?.let { loc ->
        if (loc.countryName.isNotBlank() && !loc.cityLabel.contains(loc.countryName, ignoreCase = true)) {
            "${loc.cityLabel}, ${loc.countryName}"
        } else {
            loc.cityLabel
        }
    } ?: myProfile?.formattedLocation() ?: "Set location"

    val peopleToKnow = remember(suggestedProfiles, candidates) {
        (if (suggestedProfiles.isNotEmpty()) suggestedProfiles else candidates).take(20)
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        val isWide = maxWidth >= 700.dp

        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = if (isWide) 640.dp else 560.dp)
                .align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Header: Brand + Location Pill + Search / Filter / Settings Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f, fill = false)) {
                    Text(
                        text = stringResource(R.string.discover_title),
                        style = MaterialTheme.typography.displaySmall,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .clip(RoundedCornerShape(50))
                            .clickable {
                                if (LocationAndGeoHelper.hasLocationPermission(context)) {
                                    fetchAndApplyGps()
                                } else {
                                    locationPermissionLauncher.launch(
                                        arrayOf(
                                            Manifest.permission.ACCESS_COARSE_LOCATION,
                                            Manifest.permission.ACCESS_FINE_LOCATION
                                        )
                                    )
                                }
                            }
                            .testTag("refresh_location_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (isLocating) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Filled.MyLocation,
                                    contentDescription = stringResource(R.string.btn_update_location),
                                    tint = if (isLiveGps) EmeraldMint else CoralRose,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                            Text(
                                text = buildString {
                                    if (isLiveGps) append("Live GPS • ")
                                    append("$cityDisplay • ${maxDistanceKm} km")
                                    if (geoHashMeta != null) {
                                        val cell = geoHashMeta.centerGeoHash.take(geoHashMeta.queryPrecision)
                                        append(" • #$cell")
                                    }
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.size(48.dp)
                    ) {
                        IconButton(
                            onClick = onOpenSearch,
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("discover_search_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Search,
                                contentDescription = stringResource(R.string.discover_search_cd),
                                tint = CoralRose
                            )
                        }
                    }

                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.size(48.dp)
                    ) {
                        IconButton(
                            onClick = { showFilterSheet = true },
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("open_filters_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Tune,
                                contentDescription = "Discovery filters",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.size(48.dp)
                    ) {
                        IconButton(
                            onClick = onOpenSettings,
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("discover_settings_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Settings,
                                contentDescription = stringResource(R.string.settings_cd),
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Compact Live Online Banner + Swipe Deck / Social Feed Switcher
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = EmeraldMint.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, EmeraldMint.copy(alpha = 0.45f)),
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable(onClick = onOpenActiveUsers)
                        .testTag("discover_online_banner")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(EmeraldMint)
                        )
                        Text(
                            text = "$activeOnlineCount online now",
                            style = MaterialTheme.typography.labelMedium,
                            color = EmeraldMint,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = selectedMode == "DECK",
                        onClick = { selectedMode = "DECK" },
                        label = { Text("Deck") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = CoralRose.copy(alpha = 0.2f),
                            selectedLabelColor = CoralRose
                        ),
                        modifier = Modifier.testTag("discover_mode_deck")
                    )
                    FilterChip(
                        selected = selectedMode == "FEED",
                        onClick = { selectedMode = "FEED" },
                        label = { Text("Social Feed") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = CoralRose.copy(alpha = 0.2f),
                            selectedLabelColor = CoralRose
                        ),
                        modifier = Modifier.testTag("discover_mode_feed")
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (myProfile == null) {
                OnboardingProfilePromptCard(
                    onCreateProfile = onNavigateToProfile,
                    modifier = Modifier.weight(1f)
                )
            } else if (selectedMode == "FEED") {
                SocialTimelineFeedContent(
                    myProfile = myProfile,
                    currentUserId = currentUserId,
                    stories = stories,
                    peopleToKnow = peopleToKnow,
                    feedPosts = feedPosts,
                    friendStatusForUid = friendStatusForUid,
                    onOpenStory = { idx -> activeStoryIndex = idx },
                    onPostPhotoStory = onPostPhotoStory,
                    onPostVideoStory = onPostVideoStory,
                    onOpenVideoFeed = onOpenVideoFeed,
                    onLocationError = onLocationError,
                    onOpenProfile = onOpenUserProfile,
                    onOpenProfileById = onOpenUserProfileById,
                    onFriendAction = onFriendAction,
                    onOpenCreatePost = { showPostComposerSheet = true },
                    onTogglePostLike = onToggleFeedPostLike,
                    onOpenComments = { post -> activeCommentsPost = post },
                    onDeletePost = onDeleteFeedPost,
                    resolvePostVideoFile = resolveFeedPostVideoFile,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                )
            } else if (candidates.isEmpty()) {
                EmptyDiscoveryCard(
                    maxDistanceKm = maxDistanceKm,
                    isVerified = myProfile.isProfileVerified,
                    onSearchPeople = onOpenSearch,
                    onExpandRadius = {
                        onUpdateFilters(
                            privateUser?.interestedIn ?: "Everyone",
                            (privateUser?.minAgePref ?: 18L).toInt(),
                            (privateUser?.maxAgePref ?: 99L).toInt(),
                            500,
                            false
                        )
                    },
                    onOpenFilters = { showFilterSheet = true },
                    onGoVerify = onNavigateToVerify,
                    modifier = Modifier.weight(1f)
                )
            } else {
                val topCandidate = candidates.first()
                val nextCandidate = candidates.getOrNull(1)
                val thirdCandidate = candidates.getOrNull(2)
                var activeController by remember { mutableStateOf<SwipeCardController?>(null) }
                var dragDirection by remember { mutableStateOf<SwipeDirection?>(null) }
                var dragProgress by remember { mutableFloatStateOf(0f) }

                SwipeCardDeck(
                    topItem = topCandidate,
                    topKey = topCandidate.profile.userId,
                    nextItem = nextCandidate,
                    thirdItem = thirdCandidate,
                    onSwiped = { candidate, swipeDirection ->
                        dragDirection = null
                        dragProgress = 0f
                        val mapped = if (swipeDirection == SwipeDirection.RIGHT) "LIKE" else "PASS"
                        onSwipe(candidate, mapped)
                    },
                    onDragProgressChanged = { dir, progress ->
                        dragDirection = dir
                        dragProgress = progress
                    },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    controllerOut = { activeController = it }
                ) { item, _ ->
                    ProfileDeckCard(
                        candidate = item,
                        friendStatus = friendStatusForUid(item.profile.userId),
                        onOpenProfile = { onOpenUserProfile(item.profile) },
                        onFriendAction = { onFriendAction(item.profile) },
                        onReportClick = { reportConfirmCandidate = item },
                        onBlockClick = { blockConfirmCandidate = item },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                val passScale = if (dragDirection == SwipeDirection.LEFT) 1f + (0.14f * dragProgress) else 1f
                val likeScale = if (dragDirection == SwipeDirection.RIGHT) 1f + (0.14f * dragProgress) else 1f

                // Bottom Swipe Controls (>= 48dp accessible touch targets): Undo, Pass, Like
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        border = BorderStroke(
                            1.dp,
                            if (canUndoLastSwipe) {
                                ChampagneGold.copy(alpha = 0.65f)
                            } else {
                                MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                            }
                        ),
                        shadowElevation = if (canUndoLastSwipe) 4.dp else 0.dp,
                        modifier = Modifier.size(52.dp)
                    ) {
                        IconButton(
                            onClick = onUndoSwipe,
                            enabled = canUndoLastSwipe,
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("undo_swipe_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Undo,
                                contentDescription = stringResource(R.string.btn_undo_swipe),
                                tint = if (canUndoLastSwipe) {
                                    ChampagneGold
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                                },
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        border = BorderStroke(
                            width = if (dragDirection == SwipeDirection.LEFT) 2.dp else 1.dp,
                            color = if (dragDirection == SwipeDirection.LEFT) {
                                CoralRose
                            } else {
                                MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
                            }
                        ),
                        shadowElevation = 6.dp,
                        modifier = Modifier
                            .size(64.dp)
                            .graphicsLayer {
                                scaleX = passScale
                                scaleY = passScale
                            }
                    ) {
                        IconButton(
                            onClick = {
                                val ctrl = activeController
                                if (ctrl != null) {
                                    scope.launch { ctrl.animateSwipe(SwipeDirection.LEFT) }
                                } else {
                                    onSwipe(topCandidate, "PASS")
                                }
                            },
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("pass_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = stringResource(R.string.btn_pass),
                                tint = if (dragDirection == SwipeDirection.LEFT) CoralRose else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(30.dp)
                            )
                        }
                    }

                    Surface(
                        shape = CircleShape,
                        color = if (dragDirection == SwipeDirection.RIGHT) EmeraldMint else CoralRose,
                        shadowElevation = 10.dp,
                        modifier = Modifier
                            .size(72.dp)
                            .graphicsLayer {
                                scaleX = likeScale
                                scaleY = likeScale
                            }
                    ) {
                        IconButton(
                            onClick = {
                                val ctrl = activeController
                                if (ctrl != null) {
                                    scope.launch { ctrl.animateSwipe(SwipeDirection.RIGHT) }
                                } else {
                                    onSwipe(topCandidate, "LIKE")
                                }
                            },
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("like_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Favorite,
                                contentDescription = stringResource(R.string.btn_like),
                                tint = Color.White,
                                modifier = Modifier.size(34.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    activeStoryIndex?.let { startIdx ->
        StoryViewerDialog(
            stories = stories,
            initialIndex = startIdx,
            currentUserId = currentUserId,
            resolveVideoFile = resolveStoryVideoFile,
            onMarkViewed = onMarkStoryViewed,
            onDeleteStory = onDeleteStory,
            onOpenAuthorProfile = onOpenUserProfileById,
            onDismiss = { activeStoryIndex = null }
        )
    }

    if (showPostComposerSheet) {
        CreateFeedPostSheet(
            onDismiss = { showPostComposerSheet = false },
            onPublish = { caption, photo, video ->
                onPublishFeedPost(caption, photo, video) {
                    showPostComposerSheet = false
                }
            },
            onError = onLocationError
        )
    }

    activeCommentsPost?.let { post ->
        val comments by remember(post.id) { observeFeedComments(post.id) }
            .collectAsStateWithLifecycle(initialValue = emptyList())
        FeedPostCommentsSheet(
            post = post,
            comments = comments,
            currentUserId = currentUserId,
            onDismiss = { activeCommentsPost = null },
            onAddComment = { text, clearCb -> onAddFeedPostComment(post.id, text, clearCb) },
            onDeleteComment = { commentId -> onDeleteFeedPostComment(post.id, commentId) },
            onOpenAuthorProfile = onOpenUserProfileById
        )
    }

    if (showFilterSheet) {
        DiscoveryFiltersBottomSheet(
            privateUser = privateUser,
            onDismiss = { showFilterSheet = false },
            onApply = { interestedIn, minAge, maxAge, maxDist, verifiedOnly ->
                showFilterSheet = false
                onUpdateFilters(interestedIn, minAge, maxAge, maxDist, verifiedOnly)
            }
        )
    }

    blockConfirmCandidate?.let { candidateToBlock ->
        AlertDialog(
            onDismissRequest = { blockConfirmCandidate = null },
            title = { Text("Block ${candidateToBlock.profile.displayName}?") },
            text = {
                Text(
                    "They will be removed from your GeoHash discovery feed and prevented from matching or messaging you. You can unblock them anytime in Profile."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val target = candidateToBlock
                        blockConfirmCandidate = null
                        onBlockUser(target)
                    },
                    modifier = Modifier.testTag("confirm_block_button")
                ) {
                    Text("Block", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { blockConfirmCandidate = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    reportConfirmCandidate?.let { candidateToReport ->
        ReportUserDialog(
            targetName = candidateToReport.profile.displayName,
            onDismiss = { reportConfirmCandidate = null },
            onSubmitReport = { reason, details, alsoBlock ->
                val target = candidateToReport
                reportConfirmCandidate = null
                onReportUser(target, reason, details, alsoBlock)
            }
        )
    }
}

@Composable
private fun SocialTimelineFeedContent(
    myProfile: PublicProfile,
    currentUserId: String,
    stories: List<StoryItem>,
    peopleToKnow: List<DiscoveryCandidate>,
    feedPosts: List<FeedPost>,
    friendStatusForUid: (String) -> FriendStatus,
    onOpenStory: (Int) -> Unit,
    onPostPhotoStory: (String, String) -> Unit,
    onPostVideoStory: (PreparedVideoUpload, String) -> Unit,
    onOpenVideoFeed: () -> Unit,
    onLocationError: (String) -> Unit,
    onOpenProfile: (PublicProfile) -> Unit,
    onOpenProfileById: (String) -> Unit,
    onFriendAction: (PublicProfile) -> Unit,
    onOpenCreatePost: () -> Unit,
    onTogglePostLike: (String, Boolean) -> Unit,
    onOpenComments: (FeedPost) -> Unit,
    onDeletePost: (String) -> Unit,
    resolvePostVideoFile: suspend (FeedPost) -> Result<File>,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.testTag("social_timeline_feed"),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item(key = "stories_row") {
            StoriesTrayRow(
                myProfile = myProfile,
                stories = stories,
                currentUserId = currentUserId,
                onOpenStory = onOpenStory,
                onPostPhotoStory = onPostPhotoStory,
                onPostVideoStory = onPostVideoStory,
                onOpenVideoFeed = onOpenVideoFeed,
                onStatusMessage = onLocationError
            )
        }

        if (peopleToKnow.isNotEmpty()) {
            item(key = "people_you_may_know_section") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "People You May Know",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(vertical = 2.dp),
                        modifier = Modifier.testTag("people_you_may_know_list")
                    ) {
                        items(peopleToKnow, key = { it.profile.userId }) { candidate ->
                            val person = candidate.profile
                            val flag = person.flagEmoji()
                            val fStatus = friendStatusForUid(person.userId)
                            Card(
                                shape = RoundedCornerShape(18.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                                modifier = Modifier
                                    .width(166.dp)
                                    .clickable { onOpenProfile(person) }
                                    .testTag("people_you_may_know_card_${person.userId}")
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    ProfileAvatarImage(
                                        photoDataUrl = person.photoDataUrl,
                                        displayName = person.displayName,
                                        seed = person.avatarSeed,
                                        modifier = Modifier.size(64.dp),
                                        shape = CircleShape,
                                        initialsFontSizeSp = 20
                                    )
                                    Text(
                                        text = buildString {
                                            append("${person.displayName}, ${person.age}")
                                            if (flag.isNotBlank()) append(" $flag")
                                        },
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = person.bio.ifBlank { person.formattedLocation() },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        textAlign = TextAlign.Center
                                    )
                                    val friendBtnLabel = when (fStatus) {
                                        FriendStatus.NONE -> "Add Friend"
                                        FriendStatus.OUTGOING_PENDING -> "Request Sent"
                                        FriendStatus.INCOMING_PENDING -> "Accept"
                                        FriendStatus.FRIENDS -> "Friends ✓"
                                    }
                                    FilledTonalButton(
                                        onClick = { onFriendAction(person) },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .minimumInteractiveComponentSize()
                                            .testTag("feed_add_friend_${person.userId}")
                                    ) {
                                        Text(friendBtnLabel, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        item(key = "friends_posts_header") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Friends' Posts",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Button(
                    onClick = onOpenCreatePost,
                    colors = ButtonDefaults.buttonColors(containerColor = CoralRose, contentColor = Color.White),
                    modifier = Modifier.testTag("create_feed_post_button")
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Create Post")
                }
            }
        }

        if (feedPosts.isEmpty()) {
            item(key = "empty_feed_posts") {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("No timeline posts yet", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            text = "Share your first photo, clip, or update with friends and connections.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            items(feedPosts, key = { it.id }) { post ->
                SocialFeedPostCard(
                    post = post,
                    currentUserId = currentUserId,
                    resolveVideoFile = resolvePostVideoFile,
                    onToggleLike = { onTogglePostLike(post.id, currentUserId in post.likedByUids) },
                    onOpenComments = { onOpenComments(post) },
                    onDeletePost = { onDeletePost(post.id) },
                    onOpenAuthor = { onOpenProfileById(post.userId) }
                )
            }
        }
    }
}

@Composable
private fun SocialFeedPostCard(
    post: FeedPost,
    currentUserId: String,
    resolveVideoFile: suspend (FeedPost) -> Result<File>,
    onToggleLike: () -> Unit,
    onOpenComments: () -> Unit,
    onDeletePost: () -> Unit,
    onOpenAuthor: () -> Unit
) {
    val context = LocalContext.current
    val isLiked = currentUserId in post.likedByUids
    val flag = CountryFlagHelper.flagEmojiForCountry(post.authorCountry.orEmpty())
    val timeLabel = remember(post.createdAt) {
        post.createdAt?.toDate()?.let {
            SimpleDateFormat("MMM d • h:mm a", Locale.getDefault()).format(it)
        } ?: "Just now"
    }
    var playVideo by remember(post.id) { mutableStateOf(false) }
    var resolvedVideoFile by remember(post.id) { mutableStateOf<File?>(null) }
    var loadingVideo by remember(post.id) { mutableStateOf(false) }

    LaunchedEffect(post.id, playVideo) {
        if (post.mediaType == "VIDEO" && playVideo && resolvedVideoFile == null && !loadingVideo) {
            loadingVideo = true
            resolvedVideoFile = resolveVideoFile(post).getOrNull()
            loadingVideo = false
        }
    }

    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("feed_post_card_${post.id}")
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .clickable(onClick = onOpenAuthor)
                ) {
                    ProfileAvatarImage(
                        photoDataUrl = post.authorPhoto,
                        displayName = post.authorName.ifBlank { "Member" },
                        seed = post.userId,
                        modifier = Modifier.size(42.dp),
                        shape = CircleShape,
                        initialsFontSizeSp = 15
                    )
                    Column {
                        Text(
                            text = buildString {
                                append(post.authorName.ifBlank { "Kindred Member" })
                                if (flag.isNotBlank()) append(" $flag")
                            },
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = buildString {
                                if (!post.authorCity.isNullOrBlank()) {
                                    append(post.authorCity)
                                    append(" • ")
                                }
                                append(timeLabel)
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (post.userId == currentUserId) {
                    IconButton(onClick = onDeletePost) {
                        Icon(Icons.Filled.DeleteOutline, contentDescription = "Delete post")
                    }
                }
            }

            if (post.caption.isNotBlank()) {
                Text(text = post.caption, style = MaterialTheme.typography.bodyMedium)
            }

            if (post.mediaType == "PHOTO" && !post.mediaDataUrl.isNullOrBlank()) {
                val bmp = remember(post.mediaDataUrl) {
                    VerificationAnalyzer.decodeBase64DataUrl(post.mediaDataUrl)
                }
                if (bmp != null) {
                    Image(
                        bitmap = bmp,
                        contentDescription = "Post photo",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(240.dp)
                            .clip(RoundedCornerShape(16.dp))
                    )
                }
            } else if (post.mediaType == "VIDEO") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF141118))
                        .clickable { playVideo = true }
                ) {
                    val file = resolvedVideoFile
                    if (playVideo && file != null) {
                        AndroidView(
                            factory = { ctx ->
                                VideoView(ctx).apply {
                                    val videoView = this
                                    layoutParams = ViewGroup.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        ViewGroup.LayoutParams.MATCH_PARENT
                                    )
                                    val ctrl = MediaController(ctx).apply { setAnchorView(videoView) }
                                    setMediaController(ctrl)
                                    setVideoURI(Uri.fromFile(file))
                                    setOnPreparedListener { mp ->
                                        mp.isLooping = true
                                        start()
                                    }
                                }
                            },
                            onRelease = { runCatching { it.stopPlayback() } },
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        val thumb = remember(post.thumbnailDataUrl) {
                            post.thumbnailDataUrl?.let { VerificationAnalyzer.decodeBase64DataUrl(it) }
                        }
                        if (thumb != null) {
                            Image(
                                bitmap = thumb,
                                contentDescription = "Video preview",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        if (loadingVideo) {
                            CircularProgressIndicator(color = CoralRose, modifier = Modifier.align(Alignment.Center))
                        } else {
                            Icon(
                                imageVector = Icons.Filled.PlayArrow,
                                contentDescription = "Play video",
                                tint = Color.White,
                                modifier = Modifier
                                    .size(48.dp)
                                    .align(Alignment.Center)
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onToggleLike,
                        modifier = Modifier.testTag("feed_like_button_${post.id}")
                    ) {
                        Icon(
                            imageVector = if (isLiked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            contentDescription = "Like post",
                            tint = if (isLiked) CoralRose else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = "${post.likedByUids.size}",
                        style = MaterialTheme.typography.labelLarge
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    IconButton(
                        onClick = onOpenComments,
                        modifier = Modifier.testTag("feed_comment_button_${post.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ChatBubbleOutline,
                            contentDescription = "Comments",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = "${post.commentCount}",
                        style = MaterialTheme.typography.labelLarge
                    )
                }

                IconButton(
                    onClick = {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(
                                Intent.EXTRA_TEXT,
                                "${post.authorName} shared on Kindred: ${post.caption.ifBlank { "Check out my post on Kindred!" }}"
                            )
                        }
                        runCatching {
                            context.startActivity(Intent.createChooser(shareIntent, "Share post"))
                        }
                    },
                    modifier = Modifier.testTag("feed_share_button_${post.id}")
                ) {
                    Icon(Icons.Filled.Share, contentDescription = "Share post", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateFeedPostSheet(
    onDismiss: () -> Unit,
    onPublish: (caption: String, photoDataUrl: String?, preparedVideo: PreparedVideoUpload?) -> Unit,
    onError: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var caption by remember { mutableStateOf("") }
    var photoDataUrl by remember { mutableStateOf<String?>(null) }
    var preparedVideo by remember { mutableStateOf<PreparedVideoUpload?>(null) }
    var preparing by remember { mutableStateOf(false) }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                preparing = true
                val res = VerificationAnalyzer.compressUriToDataUrl(context, uri)
                preparing = false
                if (res != null) {
                    preparedVideo = null
                    photoDataUrl = res
                } else {
                    onError("Could not process photo")
                }
            }
        }
    }

    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                preparing = true
                VideoClipHelper.prepareVideoForUpload(context, uri)
                    .onSuccess { clip ->
                        photoDataUrl = null
                        preparedVideo = clip
                    }
                    .onFailure { onError(it.localizedMessage ?: "Could not prepare video") }
                preparing = false
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 24.dp, vertical = 12.dp)
                .padding(bottom = 24.dp)
                .testTag("create_feed_post_sheet"),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Create Timeline Post", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = caption,
                onValueChange = { if (it.length <= 500) caption = it },
                label = { Text("What's on your mind?") },
                minLines = 3,
                maxLines = 5,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("create_post_caption_input")
            )

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = {
                        photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (photoDataUrl != null) "Photo ✓" else "Photo")
                }
                OutlinedButton(
                    onClick = {
                        videoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.VideoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (preparedVideo != null) "Video ✓" else "Video")
                }
            }

            if (preparing) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            Button(
                onClick = { onPublish(caption.trim(), photoDataUrl, preparedVideo) },
                enabled = !preparing && (caption.isNotBlank() || photoDataUrl != null || preparedVideo != null),
                colors = ButtonDefaults.buttonColors(containerColor = CoralRose, contentColor = Color.White),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("submit_feed_post_button")
            ) {
                Text("Publish Post", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FeedPostCommentsSheet(
    post: FeedPost,
    comments: List<FeedComment>,
    currentUserId: String,
    onDismiss: () -> Unit,
    onAddComment: (text: String, onDone: () -> Unit) -> Unit,
    onDeleteComment: (commentId: String) -> Unit,
    onOpenAuthorProfile: (String) -> Unit
) {
    var commentDraft by remember(post.id) { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp)
                .testTag("feed_comments_sheet"),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Comments (${comments.size})",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            if (comments.isEmpty()) {
                Text(
                    text = "Be the first to leave a comment.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(comments, key = { it.id }) { comment ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            ProfileAvatarImage(
                                photoDataUrl = comment.authorPhoto,
                                displayName = comment.authorName.ifBlank { "Member" },
                                seed = comment.userId,
                                modifier = Modifier
                                    .size(34.dp)
                                    .clickable {
                                        onDismiss()
                                        onOpenAuthorProfile(comment.userId)
                                    },
                                shape = CircleShape,
                                initialsFontSizeSp = 13
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = comment.authorName.ifBlank { "Member" },
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = comment.text,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            if (comment.userId == currentUserId) {
                                IconButton(onClick = { onDeleteComment(comment.id) }) {
                                    Icon(Icons.Filled.DeleteOutline, contentDescription = "Delete comment", modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = commentDraft,
                    onValueChange = { if (it.length <= 400) commentDraft = it },
                    placeholder = { Text("Write a comment…") },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("feed_comment_input")
                )
                IconButton(
                    onClick = {
                        val txt = commentDraft.trim()
                        if (txt.isNotEmpty()) {
                            onAddComment(txt) { commentDraft = "" }
                        }
                    },
                    enabled = commentDraft.isNotBlank(),
                    modifier = Modifier.testTag("send_feed_comment_button")
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send comment", tint = CoralRose)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProfileDeckCard(
    candidate: DiscoveryCandidate,
    friendStatus: FriendStatus = FriendStatus.NONE,
    onOpenProfile: () -> Unit = {},
    onFriendAction: () -> Unit = {},
    onReportClick: () -> Unit = {},
    onBlockClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val profile = candidate.profile
    val photos = remember(profile.userId, profile.photoDataUrl, profile.profilePhotos) {
        profile.allProfilePhotos()
    }
    var activePhotoIndex by remember(profile.userId, photos.size) {
        mutableStateOf(0)
    }
    val displayedPhoto = photos.getOrNull(activePhotoIndex.coerceIn(0, (photos.size - 1).coerceAtLeast(0)))
    val flag = profile.flagEmoji()

    Card(
        modifier = modifier.testTag("discovery_profile_card"),
        shape = RoundedCornerShape(28.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            ProfileAvatarImage(
                photoDataUrl = displayedPhoto,
                displayName = profile.displayName,
                seed = profile.avatarSeed,
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(28.dp),
                initialsFontSizeSp = 72
            )

            if (photos.size > 1) {
                Row(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .weight(0.35f)
                            .fillMaxHeight()
                            .clickable {
                                activePhotoIndex = if (activePhotoIndex > 0) {
                                    activePhotoIndex - 1
                                } else {
                                    photos.lastIndex
                                }
                            }
                    )
                    Box(
                        modifier = Modifier
                            .weight(0.65f)
                            .fillMaxHeight()
                            .clickable {
                                activePhotoIndex = (activePhotoIndex + 1) % photos.size
                            }
                    )
                }
            }

            // Bottom atmospheric readability scrim
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0.0f to Color.Black.copy(alpha = 0.28f),
                            0.35f to Color.Transparent,
                            0.62f to Color(0xFF140D13).copy(alpha = 0.65f),
                            1.0f to Color(0xFF140D13).copy(alpha = 0.96f)
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (photos.size > 1) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        photos.indices.forEach { idx ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(
                                        if (idx == activePhotoIndex) Color.White
                                        else Color.White.copy(alpha = 0.35f)
                                    )
                            )
                        }
                    }
                }

                // Top Badges Row: Location Distance + Add Friend / Verification / Report / Block
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LocationDistancePill(
                        cityLabel = profile.formattedLocation(),
                        distanceKm = candidate.distanceKm,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onFriendAction,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("discover_add_friend_button")
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = Color.Black.copy(alpha = 0.55f),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.28f)),
                                modifier = Modifier.size(32.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = if (friendStatus == FriendStatus.FRIENDS) Icons.Filled.Check else Icons.Filled.PersonAdd,
                                        contentDescription = "Add friend",
                                        tint = if (friendStatus == FriendStatus.FRIENDS) EmeraldMint else Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                        VerifiedShieldBadge(
                            isVerified = profile.isProfileVerified,
                            score = profile.verificationScore.toInt()
                        )
                        IconButton(
                            onClick = onReportClick,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("discover_report_button")
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = Color.Black.copy(alpha = 0.55f),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.28f)),
                                modifier = Modifier.size(32.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Filled.Flag,
                                        contentDescription = stringResource(R.string.discover_report_cd),
                                        tint = ChampagneGold,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                        IconButton(
                            onClick = onBlockClick,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("discover_block_button")
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = Color.Black.copy(alpha = 0.55f),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.28f)),
                                modifier = Modifier.size(32.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Filled.Block,
                                        contentDescription = stringResource(R.string.discover_block_cd),
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Bottom Profile Details Overlay (clickable -> full UserProfileDetailScreen)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomStart)
                    .clickable(onClick = onOpenProfile)
                    .padding(20.dp)
                    .testTag("discover_card_open_profile_area")
            ) {
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = buildString {
                            append("${profile.displayName}, ${profile.age}")
                            if (flag.isNotBlank()) append(" $flag")
                        },
                        style = MaterialTheme.typography.displaySmall,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (profile.isProfileVerified) {
                        Icon(
                            imageVector = Icons.Filled.VerifiedUser,
                            contentDescription = stringResource(R.string.cd_verified_badge),
                            tint = EmeraldMint,
                            modifier = Modifier
                                .padding(bottom = 5.dp)
                                .size(22.dp)
                        )
                    }
                }

                Text(
                    text = buildString {
                        append(profile.gender)
                        if (candidate.proximityMatchScore > 0) {
                            append(" • ${candidate.proximityMatchScore}% Match")
                        }
                        if (profile.geohash.isNotBlank()) {
                            append(" • #${profile.geohash.take(5)}")
                        }
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = ChampagneGold
                )

                if (profile.bio.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = profile.bio,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.9f),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (profile.interests.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        profile.interests.take(5).forEach { tag ->
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = Color.White.copy(alpha = 0.16f),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f))
                            ) {
                                Text(
                                    text = tag,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OnboardingProfilePromptCard(
    onCreateProfile: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(28.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_match_celebration),
                contentDescription = stringResource(R.string.cd_match_art),
                modifier = Modifier
                    .size(160.dp)
                    .clip(RoundedCornerShape(24.dp)),
                contentScale = ContentScale.Crop
            )
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = "Set Up Your Kindred Card",
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Add your bio, interests, photo, and GPS area so nearby verified members can discover and match with you.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = onCreateProfile,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("onboarding_create_profile_button"),
                colors = ButtonDefaults.buttonColors(containerColor = CoralRose)
            ) {
                Text("Create Your Profile")
            }
        }
    }
}

@Composable
private fun EmptyDiscoveryCard(
    maxDistanceKm: Int,
    isVerified: Boolean,
    onSearchPeople: () -> Unit,
    onExpandRadius: () -> Unit,
    onOpenFilters: () -> Unit,
    onGoVerify: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(28.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_match_celebration),
                contentDescription = stringResource(R.string.cd_match_art),
                modifier = Modifier
                    .size(150.dp)
                    .clip(RoundedCornerShape(24.dp)),
                contentScale = ContentScale.Crop
            )
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = "No Nearby Profiles Right Now",
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "You've seen everyone within $maxDistanceKm km matching your current filters. Search real members by name or expand your radius to connect in real time.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(22.dp))
            Button(
                onClick = onSearchPeople,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("empty_discover_search_button"),
                colors = ButtonDefaults.buttonColors(containerColor = CoralRose)
            ) {
                Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Search People")
            }
            Spacer(modifier = Modifier.height(10.dp))
            if (maxDistanceKm < 500) {
                OutlinedButton(
                    onClick = onExpandRadius,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                ) {
                    Icon(Icons.Filled.LocationOn, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Expand Radius to 500 km")
                }
                Spacer(modifier = Modifier.height(10.dp))
            }
            OutlinedButton(
                onClick = onOpenFilters,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Text("Adjust Discovery Filters")
            }
            if (!isVerified) {
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedButton(
                    onClick = onGoVerify,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                ) {
                    Icon(Icons.Filled.VerifiedUser, contentDescription = null, tint = EmeraldMint, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Get Verified While You Wait")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun DiscoveryFiltersBottomSheet(
    privateUser: PrivateUser?,
    onDismiss: () -> Unit,
    onApply: (String, Int, Int, Int, Boolean) -> Unit
) {
    var interestedIn by remember(privateUser) {
        mutableStateOf(privateUser?.interestedIn ?: "Everyone")
    }
    var ageRange by remember(privateUser) {
        val min = (privateUser?.minAgePref ?: 20L).toFloat().coerceIn(18f, 99f)
        val max = (privateUser?.maxAgePref ?: 45L).toFloat().coerceIn(min, 99f)
        mutableStateOf(min..max)
    }
    var maxDistance by remember(privateUser) {
        mutableFloatStateOf((privateUser?.maxDistanceKm ?: 100L).toFloat().coerceIn(5f, 500f))
    }
    var verifiedOnly by remember(privateUser) {
        mutableStateOf(privateUser?.verifiedOnlyFilter ?: false)
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 12.dp)
                .padding(bottom = 24.dp)
        ) {
            Text(
                text = "Discovery Preferences",
                style = MaterialTheme.typography.headlineMedium
            )
            Spacer(modifier = Modifier.height(16.dp))

            Text("Show me", style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                KindredConstants.INTERESTED_IN_OPTIONS.forEach { option ->
                    FilterChip(
                        selected = interestedIn == option,
                        onClick = { interestedIn = option },
                        label = { Text(option) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = CoralRose.copy(alpha = 0.2f),
                            selectedLabelColor = CoralRose
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Maximum distance", style = MaterialTheme.typography.titleSmall)
                Text(
                    text = "${maxDistance.roundToInt()} km",
                    style = MaterialTheme.typography.titleSmall,
                    color = CoralRose
                )
            }
            Slider(
                value = maxDistance,
                onValueChange = { maxDistance = it },
                valueRange = 5f..500f
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Age range", style = MaterialTheme.typography.titleSmall)
                Text(
                    text = "${ageRange.start.roundToInt()} – ${ageRange.endInclusive.roundToInt()}",
                    style = MaterialTheme.typography.titleSmall,
                    color = CoralRose
                )
            }
            RangeSlider(
                value = ageRange,
                onValueChange = { ageRange = it },
                valueRange = 18f..99f
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Verified profiles only", style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = "Only show members who passed live camera verification",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = verifiedOnly,
                    onCheckedChange = { verifiedOnly = it }
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = {
                    onApply(
                        interestedIn,
                        ageRange.start.roundToInt(),
                        ageRange.endInclusive.roundToInt(),
                        maxDistance.roundToInt(),
                        verifiedOnly
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("apply_filters_button"),
                colors = ButtonDefaults.buttonColors(containerColor = CoralRose)
            ) {
                Text("Apply Filters")
            }
        }
    }
}
