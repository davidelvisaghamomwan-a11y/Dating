package com.example.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.SampleProfilesProvider
import com.example.model.DiscoveryCandidate
import com.example.model.KindredConstants
import com.example.model.PrivateUser
import com.example.model.PublicProfile
import com.example.service.MatchingLogicService
import com.example.ui.components.LocationDistancePill
import com.example.ui.components.ProfileAvatarImage
import com.example.ui.components.SwipeCardController
import com.example.ui.components.SwipeCardDeck
import com.example.ui.components.SwipeDirection
import com.example.ui.components.VerifiedShieldBadge
import com.example.ui.theme.ChampagneGold
import com.example.ui.theme.CoralRose
import com.example.ui.theme.EmeraldMint
import com.example.util.LocationAndGeoHelper
import com.example.util.ResolvedDeviceLocation
import com.example.viewmodel.GeoHashContextMeta
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

enum class DiscoveryFeedMode {
    DECK,
    FEED
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DiscoverScreen(
    myProfile: PublicProfile?,
    privateUser: PrivateUser?,
    geoHashMeta: GeoHashContextMeta?,
    candidates: List<DiscoveryCandidate>,
    includeDemoProfiles: Boolean = true,
    onToggleDemoProfiles: (Boolean) -> Unit = {},
    onResetDemoSwipes: () -> Unit = {},
    onSwipe: (DiscoveryCandidate, String) -> Unit,
    onBlockUser: (DiscoveryCandidate) -> Unit = {},
    onRefreshLocation: (ResolvedDeviceLocation) -> Unit,
    onLocationError: (String) -> Unit,
    onUpdateFilters: (String, Int, Int, Int, Boolean) -> Unit,
    onNavigateToProfile: () -> Unit,
    onNavigateToVerify: () -> Unit,
    onOpenSettings: () -> Unit = {},
    latestLocation: ResolvedDeviceLocation? = null,
    onLocationPermissionGranted: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showFilterSheet by remember { mutableStateOf(false) }
    var blockConfirmCandidate by remember { mutableStateOf<DiscoveryCandidate?>(null) }
    var isLocating by remember { mutableStateOf(false) }

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
    val minAgePref = (privateUser?.minAgePref ?: 18L).toInt()
    val maxAgePref = (privateUser?.maxAgePref ?: 99L).toInt()
    val interestedInPref = privateUser?.interestedIn ?: "Everyone"
    val verifiedOnlyPref = privateUser?.verifiedOnlyFilter ?: false
    val isLiveGps = latestLocation?.isLiveGps == true
    val cityDisplay = latestLocation?.cityLabel ?: myProfile?.cityLabel ?: "Set location"

    var feedMode by remember { mutableStateOf(DiscoveryFeedMode.DECK) }
    var selectedInterestTag by remember { mutableStateOf<String?>(null) }

    val availableInterests = remember(candidates) {
        candidates
            .flatMap { it.profile.interests }
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .map { it.key }
            .take(8)
    }

    val filteredCandidates = remember(candidates, selectedInterestTag) {
        MatchingLogicService.filterByInterestTag(candidates, selectedInterestTag)
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        val isWide = maxWidth >= 700.dp

        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = if (isWide) 640.dp else 560.dp)
                .align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Header: Brand + Location Pill + Filter Action
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
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

            Spacer(modifier = Modifier.height(10.dp))

            if (myProfile != null) {
                DiscoveryFeedControlsBar(
                    feedMode = feedMode,
                    onSelectMode = { feedMode = it },
                    interestedIn = interestedInPref,
                    minAge = minAgePref,
                    maxAge = maxAgePref,
                    verifiedOnly = verifiedOnlyPref,
                    matchCount = filteredCandidates.size,
                    availableInterests = availableInterests,
                    selectedInterest = selectedInterestTag,
                    onSelectInterest = { tag ->
                        selectedInterestTag = if (selectedInterestTag == tag) null else tag
                    }
                )
                Spacer(modifier = Modifier.height(10.dp))
            }

            if (myProfile == null) {
                OnboardingProfilePromptCard(
                    onCreateProfile = onNavigateToProfile,
                    modifier = Modifier.weight(1f)
                )
            } else if (filteredCandidates.isEmpty()) {
                if (candidates.isNotEmpty() && selectedInterestTag != null) {
                    EmptyInterestFilterCard(
                        selectedInterest = selectedInterestTag!!,
                        onClearInterest = { selectedInterestTag = null },
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    EmptyDiscoveryCard(
                        maxDistanceKm = maxDistanceKm,
                        isVerified = myProfile.isProfileVerified,
                        includeDemoProfiles = includeDemoProfiles,
                        onExpandRadius = {
                            onUpdateFilters(
                                privateUser?.interestedIn ?: "Everyone",
                                (privateUser?.minAgePref ?: 18L).toInt(),
                                (privateUser?.maxAgePref ?: 99L).toInt(),
                                500,
                                false
                            )
                        },
                        onResetDemoSwipes = onResetDemoSwipes,
                        onOpenFilters = { showFilterSheet = true },
                        onGoVerify = onNavigateToVerify,
                        modifier = Modifier.weight(1f)
                    )
                }
            } else if (feedMode == DiscoveryFeedMode.FEED) {
                MatchCardFeed(
                    candidates = filteredCandidates,
                    onLike = { candidate -> onSwipe(candidate, "LIKE") },
                    onPass = { candidate -> onSwipe(candidate, "PASS") },
                    onBlockUser = { candidate -> blockConfirmCandidate = candidate },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                )
            } else {
                val topCandidate = filteredCandidates.first()
                val nextCandidate = filteredCandidates.getOrNull(1)
                var activeController by remember { mutableStateOf<SwipeCardController?>(null) }

                SwipeCardDeck(
                    topItem = topCandidate,
                    topKey = topCandidate.profile.userId,
                    nextItem = nextCandidate,
                    onSwiped = { candidate, swipeDirection ->
                        val mapped = if (swipeDirection == SwipeDirection.RIGHT) "LIKE" else "PASS"
                        onSwipe(candidate, mapped)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    controllerOut = { activeController = it }
                ) { item, _ ->
                    ProfileDeckCard(
                        candidate = item,
                        onBlockClick = { blockConfirmCandidate = item },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Bottom Swipe Controls (>= 48dp accessible touch targets) with card fly-off sync
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
                        shadowElevation = 6.dp,
                        modifier = Modifier.size(64.dp)
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
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(30.dp)
                            )
                        }
                    }

                    Surface(
                        shape = CircleShape,
                        color = CoralRose,
                        shadowElevation = 10.dp,
                        modifier = Modifier.size(72.dp)
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

    if (showFilterSheet) {
        DiscoveryFiltersBottomSheet(
            privateUser = privateUser,
            includeDemoProfiles = includeDemoProfiles,
            onToggleDemoProfiles = onToggleDemoProfiles,
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
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProfileDeckCard(
    candidate: DiscoveryCandidate,
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

    Card(
        modifier = modifier.testTag("discovery_profile_card"),
        shape = RoundedCornerShape(28.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (photos.size > 1) {
                        Modifier.clickable {
                            activePhotoIndex = (activePhotoIndex + 1) % photos.size
                        }
                    } else {
                        Modifier
                    }
                )
        ) {
            ProfileAvatarImage(
                photoDataUrl = displayedPhoto,
                displayName = profile.displayName,
                seed = profile.avatarSeed,
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(28.dp),
                initialsFontSizeSp = 72
            )

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

                // Top Badges Row: Location Distance + Verification Shield
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LocationDistancePill(
                        cityLabel = profile.cityLabel,
                        distanceKm = candidate.distanceKm,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (SampleProfilesProvider.isDemoProfileId(profile.userId)) {
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = Color.Black.copy(alpha = 0.55f),
                                border = BorderStroke(1.dp, ChampagneGold.copy(alpha = 0.55f))
                            ) {
                                Text(
                                    text = "Sample",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ChampagneGold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                        VerifiedShieldBadge(
                            isVerified = profile.isProfileVerified,
                            score = profile.verificationScore.toInt()
                        )
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

            // Bottom Profile Details Overlay
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomStart)
                    .padding(20.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "${profile.displayName}, ${profile.age}",
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
                    val sharedLower = remember(candidate.sharedInterests) {
                        candidate.sharedInterests.map { it.lowercase() }.toSet()
                    }
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        profile.interests.take(5).forEach { tag ->
                            val isShared = tag.lowercase() in sharedLower
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = if (isShared) EmeraldMint.copy(alpha = 0.24f) else Color.White.copy(alpha = 0.16f),
                                border = BorderStroke(
                                    1.dp,
                                    if (isShared) EmeraldMint.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.25f)
                                )
                            ) {
                                Text(
                                    text = if (isShared) "★ $tag" else tag,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (isShared) EmeraldMint else Color.White,
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
private fun DiscoveryFeedControlsBar(
    feedMode: DiscoveryFeedMode,
    onSelectMode: (DiscoveryFeedMode) -> Unit,
    interestedIn: String,
    minAge: Int,
    maxAge: Int,
    verifiedOnly: Boolean,
    matchCount: Int,
    availableInterests: List<String>,
    selectedInterest: String?,
    onSelectInterest: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = buildString {
                    append("$matchCount ")
                    append(if (matchCount == 1) "match" else "matches")
                    append(" • $interestedIn • $minAge–$maxAge")
                    if (verifiedOnly) append(" • Verified")
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp)
                    .testTag("matching_summary_label")
            )

            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
            ) {
                Row(
                    modifier = Modifier.padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ModeTogglePill(
                        label = stringResource(R.string.discover_mode_deck),
                        icon = Icons.Filled.Style,
                        selected = feedMode == DiscoveryFeedMode.DECK,
                        contentDescription = stringResource(R.string.cd_switch_to_deck),
                        onClick = { onSelectMode(DiscoveryFeedMode.DECK) },
                        testTag = "view_mode_deck_button"
                    )
                    ModeTogglePill(
                        label = stringResource(R.string.discover_mode_feed),
                        icon = Icons.Filled.ViewAgenda,
                        selected = feedMode == DiscoveryFeedMode.FEED,
                        contentDescription = stringResource(R.string.cd_switch_to_feed),
                        onClick = { onSelectMode(DiscoveryFeedMode.FEED) },
                        testTag = "view_mode_feed_button"
                    )
                }
            }
        }

        if (feedMode == DiscoveryFeedMode.FEED && availableInterests.isNotEmpty()) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                item(key = "all_interests") {
                    FilterChip(
                        selected = selectedInterest == null,
                        onClick = {
                            if (selectedInterest != null) onSelectInterest(selectedInterest)
                        },
                        label = { Text("All") },
                        modifier = Modifier.testTag("feed_interest_chip_all"),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = CoralRose.copy(alpha = 0.2f),
                            selectedLabelColor = CoralRose
                        )
                    )
                }
                items(availableInterests, key = { it }) { tag ->
                    FilterChip(
                        selected = selectedInterest == tag,
                        onClick = { onSelectInterest(tag) },
                        label = { Text(tag) },
                        modifier = Modifier.testTag("feed_interest_chip_$tag"),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = CoralRose.copy(alpha = 0.2f),
                            selectedLabelColor = CoralRose
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun ModeTogglePill(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    contentDescription: String,
    onClick: () -> Unit,
    testTag: String
) {
    Surface(
        shape = RoundedCornerShape(50),
        color = if (selected) CoralRose else Color.Transparent,
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(15.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun MatchCardFeed(
    candidates: List<DiscoveryCandidate>,
    onLike: (DiscoveryCandidate) -> Unit,
    onPass: (DiscoveryCandidate) -> Unit,
    onBlockUser: (DiscoveryCandidate) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.testTag("match_card_feed"),
        contentPadding = PaddingValues(top = 4.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(
            items = candidates,
            key = { it.profile.userId }
        ) { candidate ->
            FeedMatchCard(
                candidate = candidate,
                onLike = { onLike(candidate) },
                onPass = { onPass(candidate) },
                onBlockClick = { onBlockUser(candidate) }
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FeedMatchCard(
    candidate: DiscoveryCandidate,
    onLike: () -> Unit,
    onPass: () -> Unit,
    onBlockClick: () -> Unit
) {
    val profile = candidate.profile
    val photos = remember(profile.userId, profile.photoDataUrl, profile.profilePhotos) {
        profile.allProfilePhotos()
    }
    var activePhotoIndex by remember(profile.userId, photos.size) {
        mutableStateOf(0)
    }
    val displayedPhoto = photos.getOrNull(activePhotoIndex.coerceIn(0, (photos.size - 1).coerceAtLeast(0)))
    val sharedLower = remember(candidate.sharedInterests) {
        candidate.sharedInterests.map { it.lowercase() }.toSet()
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("feed_match_card_${profile.userId}"),
        shape = RoundedCornerShape(26.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(320.dp)
                    .then(
                        if (photos.size > 1) {
                            Modifier.clickable {
                                activePhotoIndex = (activePhotoIndex + 1) % photos.size
                            }
                        } else {
                            Modifier
                        }
                    )
            ) {
                ProfileAvatarImage(
                    photoDataUrl = displayedPhoto,
                    displayName = profile.displayName,
                    seed = profile.avatarSeed,
                    modifier = Modifier.fillMaxSize(),
                    shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
                    initialsFontSizeSp = 64
                )

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0.0f to Color.Black.copy(alpha = 0.30f),
                                0.42f to Color.Transparent,
                                0.72f to Color(0xFF140D13).copy(alpha = 0.68f),
                                1.0f to Color(0xFF140D13).copy(alpha = 0.94f)
                            )
                        )
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (photos.size > 1) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            photos.indices.forEach { idx ->
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(3.5.dp)
                                        .clip(RoundedCornerShape(50))
                                        .background(
                                            if (idx == activePhotoIndex) Color.White
                                            else Color.White.copy(alpha = 0.35f)
                                        )
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        LocationDistancePill(
                            cityLabel = profile.cityLabel,
                            distanceKm = candidate.distanceKm,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = CoralRose.copy(alpha = 0.92f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.AutoAwesome,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Text(
                                        text = "${candidate.proximityMatchScore}% Match",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }
                            IconButton(
                                onClick = onBlockClick,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("feed_block_button_${profile.userId}")
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

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomStart)
                        .padding(horizontal = 18.dp, vertical = 14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "${profile.displayName}, ${profile.age}",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (profile.isProfileVerified) {
                            Icon(
                                imageVector = Icons.Filled.VerifiedUser,
                                contentDescription = stringResource(R.string.cd_verified_badge),
                                tint = EmeraldMint,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = profile.gender,
                            style = MaterialTheme.typography.labelLarge,
                            color = ChampagneGold
                        )
                        if (SampleProfilesProvider.isDemoProfileId(profile.userId)) {
                            Text(
                                text = "• Sample",
                                style = MaterialTheme.typography.labelMedium,
                                color = ChampagneGold.copy(alpha = 0.85f)
                            )
                        }
                        if (candidate.matchReasons.isNotEmpty()) {
                            Text(
                                text = "• ${candidate.matchReasons.joinToString(" • ")}",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.86f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (profile.bio.isNotBlank()) {
                    Text(
                        text = profile.bio,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (profile.interests.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        profile.interests.take(6).forEach { tag ->
                            val isShared = tag.lowercase() in sharedLower
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = if (isShared) {
                                    EmeraldMint.copy(alpha = 0.16f)
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerHigh
                                },
                                border = BorderStroke(
                                    1.dp,
                                    if (isShared) EmeraldMint.copy(alpha = 0.7f)
                                    else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                                )
                            ) {
                                Text(
                                    text = if (isShared) "★ $tag" else tag,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (isShared) EmeraldMint else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onPass,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("feed_pass_button_${profile.userId}"),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.btn_pass))
                    }

                    Button(
                        onClick = onLike,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("feed_like_button_${profile.userId}"),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = CoralRose)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Favorite,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.btn_like))
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyInterestFilterCard(
    selectedInterest: String,
    onClearInterest: () -> Unit,
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
                .padding(28.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "No matches for \"$selectedInterest\"",
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Clear this quick interest filter to see all members matching your Firestore location and preferences.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = onClearInterest,
                colors = ButtonDefaults.buttonColors(containerColor = CoralRose),
                modifier = Modifier
                    .height(48.dp)
                    .testTag("clear_interest_filter_button")
            ) {
                Text("Show All Matches")
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
    includeDemoProfiles: Boolean,
    onExpandRadius: () -> Unit,
    onResetDemoSwipes: () -> Unit,
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
                text = "You've seen everyone within $maxDistanceKm km matching your current filters. New members appear automatically in real time as they join.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(22.dp))
            Button(
                onClick = onResetDemoSwipes,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("reset_demo_swipes_button"),
                colors = ButtonDefaults.buttonColors(containerColor = CoralRose)
            ) {
                Text(if (includeDemoProfiles) "Replay 23 Sample Profiles" else "Show 23 Sample Profiles")
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
    includeDemoProfiles: Boolean,
    onToggleDemoProfiles: (Boolean) -> Unit,
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

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Include sample profiles (23)", style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = "10 Women, 7 Men, 6 Non-binary nearby demo cards",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = includeDemoProfiles,
                    onCheckedChange = onToggleDemoProfiles
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
