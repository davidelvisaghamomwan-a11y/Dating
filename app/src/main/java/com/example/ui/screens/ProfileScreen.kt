package com.example.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.VideoCall
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import com.example.R
import com.example.auth.GoogleAuthHelper
import com.example.model.BlockedUserItem
import com.example.model.KindredConstants
import com.example.model.PrivateUser
import com.example.model.ProfileVideoItem
import com.example.model.PublicProfile
import com.example.model.VisibilityLevel
import com.example.notifications.KindredNotificationHelper
import com.example.ui.components.CountryAndCitySelector
import com.example.ui.components.ProfileAvatarImage
import com.example.ui.components.VerifiedShieldBadge
import com.example.ui.theme.ChampagneGold
import com.example.ui.theme.CoralRose
import com.example.ui.theme.EmeraldMint
import com.example.util.CountryFlagHelper
import com.example.util.GeoHashUtils
import com.example.util.LocationAndGeoHelper
import com.example.util.PreparedVideoUpload
import com.example.util.ProfileCompletionUtils
import com.example.util.ResolvedDeviceLocation
import com.example.util.VerificationAnalyzer
import com.example.util.VideoClipHelper
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileScreen(
    currentUserId: String,
    userEmail: String?,
    defaultDisplayName: String?,
    myProfile: PublicProfile?,
    privateUser: PrivateUser?,
    isBusy: Boolean,
    latestLocation: ResolvedDeviceLocation? = null,
    onSaveProfile: (
        displayName: String,
        age: Int,
        gender: String,
        bio: String,
        cityLabel: String,
        country: String,
        latitude: Double,
        longitude: Double,
        interests: List<String>,
        photoDataUrl: String?,
        profilePhotos: List<String>,
        discoverable: Boolean,
        interestedIn: String,
        minAgePref: Int,
        maxAgePref: Int,
        maxDistanceKm: Int,
        verifiedOnlyFilter: Boolean
    ) -> Unit,
    onEditProfile: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    blockedUsers: List<BlockedUserItem> = emptyList(),
    onUnblockUser: (String) -> Unit = {},
    onNotificationsGranted: () -> Unit = {},
    onBeforeSignOut: suspend () -> Unit = {},
    myVideos: List<ProfileVideoItem> = emptyList(),
    onUploadVideo: (caption: String, prepared: PreparedVideoUpload) -> Unit = { _, _ -> },
    onDeleteVideo: (String) -> Unit = {},
    incomingFriendRequestCount: Int = 0,
    friendsCount: Int = 0,
    whoLikedMeCount: Int = 0,
    profileViewCount: Int = 0,
    activeOnlineCount: Int = 0,
    isAdmin: Boolean = false,
    onOpenFriendsHub: () -> Unit = {},
    onOpenWhoLikesMe: () -> Unit = {},
    onOpenActiveUsers: () -> Unit = {},
    onOpenVideoFeed: () -> Unit = {},
    onOpenAdminDashboard: () -> Unit = {},
    onUpdatePrivacy: (hideOnlineStatus: Boolean, photosVis: String, videosVis: String, infoVis: String) -> Unit = { _, _, _, _ -> },
    onStatusMessage: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    var notificationsEnabled by remember {
        mutableStateOf(KindredNotificationHelper.hasNotificationPermission(context))
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        notificationsEnabled = granted
        if (granted) {
            onNotificationsGranted()
            onStatusMessage("Push notifications enabled for new matches and messages")
        } else {
            onStatusMessage("Notification permission denied — enable alerts anytime in system settings")
        }
    }

    val pendingRegistration = remember(myProfile) {
        if (myProfile == null) {
            com.example.auth.PendingRegistrationPrefs.get(context)
        } else {
            com.example.auth.PendingRegistrationPrefs.clear(context)
            null
        }
    }

    var displayName by remember(myProfile, defaultDisplayName, pendingRegistration) {
        mutableStateOf(
            myProfile?.displayName
                ?: pendingRegistration?.displayName?.takeIf { it.isNotBlank() }
                ?: defaultDisplayName.orEmpty().take(40)
        )
    }
    var ageText by remember(myProfile, pendingRegistration) {
        mutableStateOf(
            myProfile?.age?.toString()
                ?: pendingRegistration?.age?.toString()
                ?: ""
        )
    }
    var gender by remember(myProfile, pendingRegistration) {
        mutableStateOf(myProfile?.gender ?: pendingRegistration?.gender ?: "Woman")
    }
    var bio by remember(myProfile) {
        mutableStateOf(myProfile?.bio ?: "")
    }
    var country by remember(myProfile) {
        mutableStateOf(myProfile?.country?.ifBlank { "United States" } ?: "United States")
    }
    var cityLabel by remember(myProfile) {
        mutableStateOf(myProfile?.cityLabel ?: "San Francisco")
    }
    var latitude by remember(privateUser, myProfile) {
        mutableStateOf(privateUser?.exactLatitude ?: myProfile?.coarseLatitude ?: 37.7749)
    }
    var longitude by remember(privateUser, myProfile) {
        mutableStateOf(privateUser?.exactLongitude ?: myProfile?.coarseLongitude ?: -122.4194)
    }

    LaunchedEffect(latestLocation) {
        val loc = latestLocation ?: return@LaunchedEffect
        if (loc.isLiveGps) {
            latitude = loc.exactLatitude
            longitude = loc.exactLongitude
            if (loc.cityLabel.isNotBlank()) {
                cityLabel = loc.cityLabel
            }
            if (loc.countryName.isNotBlank()) {
                country = loc.countryName
            }
        }
    }
    var selectedInterests by remember(myProfile) {
        mutableStateOf(myProfile?.interests ?: listOf("Coffee", "Hiking"))
    }
    var profilePhotos by remember(myProfile) {
        mutableStateOf(myProfile?.allProfilePhotos().orEmpty())
    }
    var discoverable by remember(myProfile) {
        mutableStateOf(myProfile?.discoverable ?: true)
    }
    var interestedIn by remember(privateUser) {
        mutableStateOf(privateUser?.interestedIn ?: "Everyone")
    }
    var agePrefRange by remember(privateUser) {
        val min = (privateUser?.minAgePref ?: 20L).toFloat().coerceIn(18f, 99f)
        val max = (privateUser?.maxAgePref ?: 45L).toFloat().coerceIn(min, 99f)
        mutableStateOf(min..max)
    }
    var maxDistanceKm by remember(privateUser) {
        mutableFloatStateOf((privateUser?.maxDistanceKm ?: 100L).toFloat().coerceIn(5f, 500f))
    }
    var verifiedOnlyFilter by remember(privateUser) {
        mutableStateOf(privateUser?.verifiedOnlyFilter ?: false)
    }

    var hideOnlineStatus by remember(myProfile, privateUser) {
        mutableStateOf(myProfile?.hideOnlineStatus ?: privateUser?.hideOnlineStatus ?: false)
    }
    var photosVisibility by remember(myProfile) {
        mutableStateOf(myProfile?.photosVisibility ?: VisibilityLevel.EVERYONE)
    }
    var videosVisibility by remember(myProfile) {
        mutableStateOf(myProfile?.videosVisibility ?: VisibilityLevel.EVERYONE)
    }
    var infoVisibility by remember(myProfile) {
        mutableStateOf(myProfile?.infoVisibility ?: VisibilityLevel.EVERYONE)
    }

    var isFetchingGps by remember { mutableStateOf(false) }
    var isPreparingVideo by remember { mutableStateOf(false) }
    var pendingVideoUpload by remember { mutableStateOf<PreparedVideoUpload?>(null) }
    var videoCaptionDraft by remember { mutableStateOf("") }

    // Zero-permission Android Photo Picker supporting up to MAX_PROFILE_PHOTOS (Play Policy compliant)
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = KindredConstants.MAX_PROFILE_PHOTOS)
    ) { uris ->
        if (uris.isNotEmpty()) {
            scope.launch {
                val newlyCompressed = uris.mapNotNull { uri ->
                    VerificationAnalyzer.compressUriToDataUrl(context, uri)
                }
                if (newlyCompressed.isNotEmpty()) {
                    profilePhotos = (profilePhotos + newlyCompressed)
                        .distinct()
                        .take(KindredConstants.MAX_PROFILE_PHOTOS)
                    onStatusMessage("Added ${newlyCompressed.size} photo(s) — tap Save Profile to publish")
                } else {
                    onStatusMessage("Could not process selected photo(s)")
                }
            }
        }
    }

    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                isPreparingVideo = true
                val res = VideoClipHelper.prepareVideoForUpload(context, uri)
                isPreparingVideo = false
                res.onSuccess { clip ->
                    pendingVideoUpload = clip
                }.onFailure { err ->
                    onStatusMessage(err.localizedMessage ?: "Unable to prepare video")
                }
            }
        }
    }

    fun fetchGps() {
        isFetchingGps = true
        scope.launch {
            val resolved = LocationAndGeoHelper.fetchCurrentLocation(context)
            isFetchingGps = false
            if (resolved != null) {
                latitude = resolved.exactLatitude
                longitude = resolved.exactLongitude
                cityLabel = resolved.cityLabel
                if (resolved.countryName.isNotBlank()) {
                    country = resolved.countryName
                }
                val locText = if (resolved.countryName.isNotBlank()) {
                    "${resolved.cityLabel}, ${resolved.countryName}"
                } else {
                    resolved.cityLabel
                }
                onStatusMessage("Detected $locText")
            } else {
                onStatusMessage("Could not acquire GPS fix; select your country and city manually.")
            }
        }
    }

    val locationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val granted = grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true ||
            grants[Manifest.permission.ACCESS_FINE_LOCATION] == true
        if (granted) {
            fetchGps()
        } else {
            onStatusMessage("Location permission denied; enter your city manually.")
        }
    }

    val completionSummary = remember(myProfile, myVideos.size) {
        ProfileCompletionUtils.calculate(myProfile, myVideos.size)
    }
    val countryFlag = CountryFlagHelper.flagEmojiForCountry(country)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 620.dp)
                .align(Alignment.TopCenter),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.profile_title),
                    style = MaterialTheme.typography.displaySmall
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    VerifiedShieldBadge(
                        isVerified = myProfile?.isProfileVerified == true,
                        score = myProfile?.verificationScore?.toInt()
                    )
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.size(48.dp)
                    ) {
                        IconButton(
                            onClick = onOpenSettings,
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("profile_settings_button")
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

            // Profile Completion Card
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                modifier = Modifier.testTag("profile_completion_card")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Profile Completion",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${completionSummary.percentage}%",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (completionSummary.percentage >= 80) EmeraldMint else CoralRose,
                            modifier = Modifier.testTag("profile_completion_percent")
                        )
                    }
                    LinearProgressIndicator(
                        progress = { completionSummary.percentage / 100f },
                        color = if (completionSummary.percentage >= 80) EmeraldMint else CoralRose,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(CircleShape)
                    )
                    if (completionSummary.missingSteps.isNotEmpty()) {
                        Text(
                            text = "Next: ${completionSummary.missingSteps.take(2).joinToString(" • ")}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            text = "All profile sections completed!",
                            style = MaterialTheme.typography.bodySmall,
                            color = EmeraldMint
                        )
                    }
                }
            }

            // Community & Connections Quick Hub
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onOpenFriendsHub,
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("profile_hub_friends_button")
                ) {
                    Icon(Icons.Filled.Group, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    val reqText = if (incomingFriendRequestCount > 0) " ($incomingFriendRequestCount new)" else ""
                    Text("Friends ($friendsCount)$reqText")
                }

                OutlinedButton(
                    onClick = onOpenWhoLikesMe,
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("profile_hub_likes_button")
                ) {
                    Icon(Icons.Filled.Favorite, contentDescription = null, tint = CoralRose, modifier = Modifier.size(17.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Likes ($whoLikedMeCount) • Views ($profileViewCount)")
                }

                OutlinedButton(
                    onClick = onOpenActiveUsers,
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("profile_hub_active_button")
                ) {
                    Icon(Icons.Filled.RadioButtonChecked, contentDescription = null, tint = EmeraldMint, modifier = Modifier.size(17.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Active ($activeOnlineCount)")
                }

                OutlinedButton(
                    onClick = onOpenVideoFeed,
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("profile_hub_videos_button")
                ) {
                    Icon(Icons.Filled.VideoLibrary, contentDescription = null, tint = ChampagneGold, modifier = Modifier.size(17.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Video Feed")
                }

                if (isAdmin) {
                    Button(
                        onClick = onOpenAdminDashboard,
                        colors = ButtonDefaults.buttonColors(containerColor = ChampagneGold, contentColor = Color.Black),
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("profile_hub_admin_button")
                    ) {
                        Icon(Icons.Filled.AdminPanelSettings, contentDescription = null, modifier = Modifier.size(17.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Admin Dashboard")
                    }
                }
            }

            // Photo & Account Header Card (up to MAX_PROFILE_PHOTOS)
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        ProfileAvatarImage(
                            photoDataUrl = profilePhotos.firstOrNull(),
                            displayName = displayName.ifBlank { "K" },
                            seed = myProfile?.avatarSeed ?: currentUserId,
                            modifier = Modifier.size(84.dp),
                            shape = CircleShape,
                            initialsFontSizeSp = 30
                        )

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = buildString {
                                    append(displayName.ifBlank { "New Kindred Member" })
                                    ageText.toIntOrNull()?.let { append(", $it") }
                                    if (countryFlag.isNotBlank()) append(" $countryFlag")
                                },
                                style = MaterialTheme.typography.titleLarge,
                                modifier = Modifier.testTag("profile_header_name_flag")
                            )
                            if (!userEmail.isNullOrBlank()) {
                                Text(
                                    text = userEmail,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            OutlinedButton(
                                onClick = {
                                    photoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                enabled = profilePhotos.size < KindredConstants.MAX_PROFILE_PHOTOS,
                                modifier = Modifier.testTag("pick_photo_button")
                            ) {
                                Icon(Icons.Filled.AddAPhoto, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    "Add Photos (${profilePhotos.size}/${KindredConstants.MAX_PROFILE_PHOTOS})"
                                )
                            }
                        }
                    }

                    Text(
                        text = "Profile Photos (${profilePhotos.size}/${KindredConstants.MAX_PROFILE_PHOTOS}) — used for anti-catfish selfie matching",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    val slotCount = maxOf(
                        4,
                        (profilePhotos.size + 1).coerceAtMost(KindredConstants.MAX_PROFILE_PHOTOS)
                    )
                    val rows = ((slotCount + 3) / 4).coerceAtLeast(1)
                    for (row in 0 until rows) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            for (col in 0 until 4) {
                                val slot = row * 4 + col
                                if (slot >= KindredConstants.MAX_PROFILE_PHOTOS) {
                                    Spacer(modifier = Modifier.weight(1f))
                                } else {
                                    val slotPhoto = profilePhotos.getOrNull(slot)
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .aspectRatio(0.82f)
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                            .border(
                                                width = if (slot == 0 && slotPhoto != null) 2.dp else 1.dp,
                                                color = if (slot == 0 && slotPhoto != null) {
                                                    CoralRose
                                                } else {
                                                    MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                                                },
                                                shape = RoundedCornerShape(16.dp)
                                            )
                                    ) {
                                        if (slotPhoto != null) {
                                            ProfileAvatarImage(
                                                photoDataUrl = slotPhoto,
                                                displayName = displayName.ifBlank { "K" },
                                                seed = myProfile?.avatarSeed ?: currentUserId,
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .clickable {
                                                        if (slot > 0) {
                                                            val reordered = profilePhotos.toMutableList()
                                                            val chosen = reordered.removeAt(slot)
                                                            reordered.add(0, chosen)
                                                            profilePhotos = reordered
                                                            onStatusMessage("Primary photo updated — tap Save Profile")
                                                        }
                                                    },
                                                shape = RoundedCornerShape(16.dp),
                                                initialsFontSizeSp = 22
                                            )

                                            if (slot == 0) {
                                                Surface(
                                                    color = CoralRose,
                                                    shape = RoundedCornerShape(bottomEnd = 10.dp),
                                                    modifier = Modifier.align(Alignment.TopStart)
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Filled.Star,
                                                            contentDescription = null,
                                                            tint = Color.White,
                                                            modifier = Modifier.size(10.dp)
                                                        )
                                                        Text(
                                                            text = "Main",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = Color.White
                                                        )
                                                    }
                                                }
                                            }

                                            IconButton(
                                                onClick = {
                                                    profilePhotos = profilePhotos.filterIndexed { index, _ -> index != slot }
                                                },
                                                modifier = Modifier
                                                    .align(Alignment.TopEnd)
                                                    .minimumInteractiveComponentSize()
                                                    .testTag("remove_photo_$slot")
                                            ) {
                                                Surface(
                                                    shape = CircleShape,
                                                    color = Color.Black.copy(alpha = 0.65f),
                                                    modifier = Modifier.size(24.dp)
                                                ) {
                                                    Box(contentAlignment = Alignment.Center) {
                                                        Icon(
                                                            imageVector = Icons.Filled.Close,
                                                            contentDescription = "Remove photo ${slot + 1}",
                                                            tint = Color.White,
                                                            modifier = Modifier.size(14.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        } else {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .clickable {
                                                        photoPickerLauncher.launch(
                                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                                        )
                                                    },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.AddAPhoto,
                                                    contentDescription = "Add profile photo slot ${slot + 1}",
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                                    modifier = Modifier.size(22.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    val photosChangedWhileVerified = myProfile?.isProfileVerified == true &&
                        profilePhotos != myProfile.allProfilePhotos()
                    if (photosChangedWhileVerified) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = ChampagneGold.copy(alpha = 0.16f),
                            border = BorderStroke(1.dp, ChampagneGold.copy(alpha = 0.45f))
                        ) {
                            Text(
                                text = "Changing your profile photos will require a quick live selfie re-verification to keep your verified badge.",
                                style = MaterialTheme.typography.bodySmall,
                                color = ChampagneGold,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }

                    FilledTonalButton(
                        onClick = onEditProfile,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("open_edit_profile_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Edit,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.btn_edit_profile),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // Showcase Videos Card
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                modifier = Modifier.testTag("profile_videos_card")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Showcase Videos (${myVideos.size})", style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = "Highlight clips displayed on your public profile",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        OutlinedButton(
                            onClick = {
                                videoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                                )
                            },
                            enabled = !isPreparingVideo && !isBusy,
                            modifier = Modifier.testTag("profile_add_video_button")
                        ) {
                            if (isPreparingVideo) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Filled.VideoCall, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Upload")
                            }
                        }
                    }

                    if (myVideos.isEmpty()) {
                        Text(
                            text = "Upload video clips of any length to stand out in Discover and User Profiles.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        LazyRow(
                            contentPadding = PaddingValues(vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            itemsIndexed(myVideos, key = { _, v -> v.id }) { _, video ->
                                val thumb = remember(video.thumbnailDataUrl) {
                                    video.thumbnailDataUrl?.let { VerificationAnalyzer.decodeBase64DataUrl(it) }
                                }
                                Box(
                                    modifier = Modifier
                                        .size(width = 116.dp, height = 152.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(Color(0xFF1A1423))
                                ) {
                                    if (thumb != null) {
                                        Image(
                                            bitmap = thumb,
                                            contentDescription = video.caption.ifBlank { "Showcase video" },
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                    Icon(
                                        imageVector = Icons.Filled.PlayArrow,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier
                                            .size(32.dp)
                                            .align(Alignment.Center)
                                    )
                                    IconButton(
                                        onClick = { onDeleteVideo(video.id) },
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .minimumInteractiveComponentSize()
                                    ) {
                                        Surface(
                                            shape = CircleShape,
                                            color = Color.Black.copy(alpha = 0.65f),
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    imageVector = Icons.Filled.DeleteOutline,
                                                    contentDescription = "Delete video",
                                                    tint = Color.White,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            }
                                        }
                                    }
                                    if (video.mediaDurationSec > 0L) {
                                        Surface(
                                            shape = CircleShape,
                                            color = Color.Black.copy(alpha = 0.65f),
                                            modifier = Modifier
                                                .align(Alignment.BottomEnd)
                                                .padding(6.dp)
                                        ) {
                                            Text(
                                                text = VideoClipHelper.formatDurationLabel(video.mediaDurationSec),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color.White,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Basic Details Card
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text("About You", style = MaterialTheme.typography.titleMedium)

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = displayName,
                            onValueChange = { if (it.length <= 40) displayName = it },
                            label = { Text("Display Name") },
                            singleLine = true,
                            modifier = Modifier
                                .weight(2f)
                                .testTag("profile_name_input")
                        )
                        OutlinedTextField(
                            value = ageText,
                            onValueChange = { input ->
                                if (input.length <= 2 && input.all { it.isDigit() }) {
                                    ageText = input
                                }
                            },
                            label = { Text("Age") },
                            placeholder = { Text("18+") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("profile_age_input")
                        )
                    }

                    Text("Gender", style = MaterialTheme.typography.titleSmall)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        KindredConstants.GENDER_OPTIONS.forEach { option ->
                            FilterChip(
                                selected = gender == option,
                                onClick = { gender = option },
                                label = { Text(option) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = CoralRose.copy(alpha = 0.2f),
                                    selectedLabelColor = CoralRose
                                )
                            )
                        }
                    }

                    OutlinedTextField(
                        value = bio,
                        onValueChange = { if (it.length <= 400) bio = it },
                        label = { Text("Bio (${bio.length}/400)") },
                        minLines = 3,
                        maxLines = 5,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("profile_bio_input")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Location (Country & City)", style = MaterialTheme.typography.titleSmall)
                        if (latestLocation?.isLiveGps == true) {
                            Surface(
                                shape = CircleShape,
                                color = EmeraldMint.copy(alpha = 0.16f),
                                border = BorderStroke(1.dp, EmeraldMint.copy(alpha = 0.45f)),
                                modifier = Modifier.testTag("profile_gps_verified_badge")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.VerifiedUser,
                                        contentDescription = null,
                                        tint = EmeraldMint,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Text(
                                        text = "GPS Verified",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = EmeraldMint,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                    CountryAndCitySelector(
                        country = country,
                        cityLabel = cityLabel,
                        onCountryChange = { country = it },
                        onCityChange = { cityLabel = it },
                        showGpsButton = true,
                        isFetchingGps = isFetchingGps,
                        onRequestGps = {
                            if (LocationAndGeoHelper.hasLocationPermission(context)) {
                                fetchGps()
                            } else {
                                locationLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.ACCESS_COARSE_LOCATION,
                                        Manifest.permission.ACCESS_FINE_LOCATION
                                    )
                                )
                            }
                        },
                        testTagPrefix = "profile"
                    )

                    val coarseLatPreview = LocationAndGeoHelper.roundToCoarse(latitude)
                    val coarseLngPreview = LocationAndGeoHelper.roundToCoarse(longitude)
                    val previewGeoHash = GeoHashUtils.encode(coarseLatPreview, coarseLngPreview, 7)
                    Text(
                        text = "Coarse coordinates: $coarseLatPreview, $coarseLngPreview • GeoHash: $previewGeoHash",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Text(
                        text = "Interests (${selectedInterests.size}/8)",
                        style = MaterialTheme.typography.titleSmall
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        KindredConstants.INTEREST_OPTIONS.forEach { tag ->
                            val selected = tag in selectedInterests
                            FilterChip(
                                selected = selected,
                                onClick = {
                                    selectedInterests = if (selected) {
                                        selectedInterests - tag
                                    } else if (selectedInterests.size < 8) {
                                        selectedInterests + tag
                                    } else {
                                        selectedInterests
                                    }
                                },
                                label = { Text(tag) }
                            )
                        }
                    }
                }
            }

            // Matching & Visibility Settings Card
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Discovery Settings", style = MaterialTheme.typography.titleMedium)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Show me in Discovery", style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = "Pause anytime without losing existing matches",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(checked = discoverable, onCheckedChange = { discoverable = it })
                    }

                    Text("Interested in", style = MaterialTheme.typography.titleSmall)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        KindredConstants.INTERESTED_IN_OPTIONS.forEach { option ->
                            FilterChip(
                                selected = interestedIn == option,
                                onClick = { interestedIn = option },
                                label = { Text(option) }
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Max distance", style = MaterialTheme.typography.titleSmall)
                        Text("${maxDistanceKm.roundToInt()} km", color = CoralRose, style = MaterialTheme.typography.titleSmall)
                    }
                    Slider(
                        value = maxDistanceKm,
                        onValueChange = { maxDistanceKm = it },
                        valueRange = 5f..500f
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Preferred age", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "${agePrefRange.start.roundToInt()} – ${agePrefRange.endInclusive.roundToInt()}",
                            color = CoralRose,
                            style = MaterialTheme.typography.titleSmall
                        )
                    }
                    RangeSlider(
                        value = agePrefRange,
                        onValueChange = { agePrefRange = it },
                        valueRange = 18f..99f
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Verified profiles only", style = MaterialTheme.typography.titleSmall)
                        Switch(checked = verifiedOnlyFilter, onCheckedChange = { verifiedOnlyFilter = it })
                    }
                }
            }

            // Privacy & Online Visibility Card
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                modifier = Modifier.testTag("profile_privacy_card")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Filled.Lock, contentDescription = null, tint = ChampagneGold, modifier = Modifier.size(18.dp))
                        Text("Privacy & Visibility", style = MaterialTheme.typography.titleMedium)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Hide Online Status", style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = "Stay invisible in Active Users and hide your last seen time",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = hideOnlineStatus,
                            onCheckedChange = { hideOnlineStatus = it },
                            modifier = Modifier.testTag("profile_hide_online_switch")
                        )
                    }

                    VisibilitySelectorRow(
                        label = "Who sees my photos",
                        selectedLevel = photosVisibility,
                        onSelect = { photosVisibility = it }
                    )
                    VisibilitySelectorRow(
                        label = "Who sees my videos",
                        selectedLevel = videosVisibility,
                        onSelect = { videosVisibility = it }
                    )
                    VisibilitySelectorRow(
                        label = "Who sees my bio & interests",
                        selectedLevel = infoVisibility,
                        onSelect = { infoVisibility = it }
                    )

                    FilledTonalButton(
                        onClick = {
                            onUpdatePrivacy(hideOnlineStatus, photosVisibility, videosVisibility, infoVisibility)
                        },
                        enabled = !isBusy,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("profile_save_privacy_button")
                    ) {
                        Text("Apply Privacy Settings")
                    }
                }
            }

            // Push Alerts & Blocked Users Safety Card
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                modifier = Modifier.testTag("safety_notifications_card")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Push Alerts & Safety", style = MaterialTheme.typography.titleMedium)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = if (notificationsEnabled) {
                                Icons.Filled.NotificationsActive
                            } else {
                                Icons.Filled.NotificationsOff
                            },
                            contentDescription = null,
                            tint = if (notificationsEnabled) EmeraldMint else ChampagneGold,
                            modifier = Modifier.size(22.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (notificationsEnabled) {
                                    "FCM Push Alerts Active"
                                } else {
                                    "Push Alerts Paused"
                                },
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = if (notificationsEnabled) {
                                    "Alerts enabled for new mutual matches and real-time chat messages."
                                } else {
                                    "Enable notifications to receive instant alerts for new matches and messages."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (!notificationsEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        FilledTonalButton(
                            onClick = {
                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("enable_notifications_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.NotificationsActive,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.btn_enable_notifications))
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Block,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Blocked Profiles (${blockedUsers.size})",
                            style = MaterialTheme.typography.titleSmall
                        )
                    }

                    if (blockedUsers.isEmpty()) {
                        Text(
                            text = "No blocked users. Anyone you block in Discover or Chat is removed from GeoHash matching and messaging.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            blockedUsers.forEach { blocked ->
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = blocked.displayName,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Text(
                                                text = "Blocked across matching & chat",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        OutlinedButton(
                                            onClick = { onUnblockUser(blocked.blockedUid) },
                                            modifier = Modifier
                                                .minimumInteractiveComponentSize()
                                                .testTag("unblock_button_${blocked.blockedUid}")
                                        ) {
                                            Text(stringResource(R.string.btn_unblock))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Button(
                onClick = {
                    val parsedAge = ageText.toIntOrNull()
                    if (parsedAge == null || parsedAge !in 18..99) {
                        onStatusMessage("Please enter a valid age (18–99).")
                        return@Button
                    }
                    onSaveProfile(
                        displayName,
                        parsedAge,
                        gender,
                        bio,
                        cityLabel,
                        country,
                        latitude,
                        longitude,
                        selectedInterests,
                        profilePhotos.firstOrNull(),
                        profilePhotos,
                        discoverable,
                        interestedIn,
                        agePrefRange.start.roundToInt(),
                        agePrefRange.endInclusive.roundToInt(),
                        maxDistanceKm.roundToInt(),
                        verifiedOnlyFilter
                    )
                },
                enabled = !isBusy && displayName.trim().length >= 2,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("save_profile_button"),
                colors = ButtonDefaults.buttonColors(containerColor = CoralRose, contentColor = Color.White)
            ) {
                if (isBusy) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                } else {
                    Text(stringResource(R.string.btn_save_profile), fontWeight = FontWeight.Bold)
                }
            }

            OutlinedButton(
                onClick = {
                    GoogleAuthHelper.signOut(
                        credentialManager = credentialManager,
                        scope = scope,
                        onBeforeSignOut = onBeforeSignOut
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("sign_out_button")
            ) {
                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.btn_sign_out))
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    pendingVideoUpload?.let { upload ->
        AlertDialog(
            onDismissRequest = {
                pendingVideoUpload = null
                videoCaptionDraft = ""
            },
            title = { Text("Upload Showcase Video") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Duration: ${VideoClipHelper.formatDurationLabel(upload.durationSec)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = videoCaptionDraft,
                        onValueChange = { if (it.length <= 160) videoCaptionDraft = it },
                        label = { Text("Caption (optional)") },
                        maxLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onUploadVideo(videoCaptionDraft.trim(), upload)
                        pendingVideoUpload = null
                        videoCaptionDraft = ""
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CoralRose)
                ) {
                    Text("Upload")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        pendingVideoUpload = null
                        videoCaptionDraft = ""
                    }
                ) { Text("Cancel") }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VisibilitySelectorRow(
    label: String,
    selectedLevel: String,
    onSelect: (String) -> Unit
) {
    val options = listOf(
        VisibilityLevel.EVERYONE to "Everyone",
        VisibilityLevel.MATCHES_AND_FRIENDS to "Matches & Friends",
        VisibilityLevel.FRIENDS_ONLY to "Friends Only"
    )
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(text = label, style = MaterialTheme.typography.labelLarge)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            options.forEach { (level, title) ->
                FilterChip(
                    selected = selectedLevel == level,
                    onClick = { onSelect(level) },
                    label = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                )
            }
        }
    }
}
