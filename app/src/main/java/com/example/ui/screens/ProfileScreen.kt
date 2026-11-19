package com.example.ui.screens

import android.Manifest
import android.graphics.Bitmap
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import com.example.R
import com.example.ai.AiPhotoGenUiState
import com.example.ai.AiPhotoStylePreset
import com.example.auth.GoogleAuthHelper
import com.example.model.BlockedUserItem
import com.example.model.KindredConstants
import com.example.model.PrivateUser
import com.example.model.PublicProfile
import com.example.notifications.KindredNotificationHelper
import com.example.ui.components.AiPhotoStudioSheet
import com.example.ui.components.ProfileAvatarImage
import com.example.ui.components.VerifiedShieldBadge
import com.example.ui.theme.ChampagneGold
import com.example.ui.theme.CoralRose
import com.example.ui.theme.EmeraldMint
import com.example.util.GeoHashUtils
import com.example.util.LocationAndGeoHelper
import com.example.util.VerificationAnalyzer
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
    onSaveProfile: (
        displayName: String,
        age: Int,
        gender: String,
        bio: String,
        cityLabel: String,
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
    aiPhotoGenState: AiPhotoGenUiState = AiPhotoGenUiState.Idle,
    onGenerateAiPhoto: (
        preset: AiPhotoStylePreset,
        customDetails: String,
        subjectGender: String?,
        referenceBitmap: Bitmap?
    ) -> Unit = { _, _, _, _ -> },
    onResetAiPhotoState: () -> Unit = {},
    onAddGeneratedPhoto: (String) -> Unit = {},
    blockedUsers: List<BlockedUserItem> = emptyList(),
    onUnblockUser: (String) -> Unit = {},
    onNotificationsGranted: () -> Unit = {},
    onBeforeSignOut: suspend () -> Unit = {},
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

    var displayName by remember(myProfile, defaultDisplayName) {
        mutableStateOf(myProfile?.displayName ?: defaultDisplayName.orEmpty().take(40))
    }
    var ageText by remember(myProfile) {
        mutableStateOf((myProfile?.age ?: 26L).toString())
    }
    var gender by remember(myProfile) {
        mutableStateOf(myProfile?.gender ?: "Woman")
    }
    var bio by remember(myProfile) {
        mutableStateOf(myProfile?.bio ?: "")
    }
    var cityLabel by remember(myProfile) {
        mutableStateOf(myProfile?.cityLabel ?: "San Francisco, CA")
    }
    var latitude by remember(privateUser, myProfile) {
        mutableStateOf(privateUser?.exactLatitude ?: myProfile?.coarseLatitude ?: 37.7749)
    }
    var longitude by remember(privateUser, myProfile) {
        mutableStateOf(privateUser?.exactLongitude ?: myProfile?.coarseLongitude ?: -122.4194)
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

    var isFetchingGps by remember { mutableStateOf(false) }
    var showAiPhotoStudio by remember { mutableStateOf(false) }

    // Zero-permission Android Photo Picker supporting up to 4 profile photos (Play Policy compliant)
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 4)
    ) { uris ->
        if (uris.isNotEmpty()) {
            scope.launch {
                val newlyCompressed = uris.mapNotNull { uri ->
                    VerificationAnalyzer.compressUriToDataUrl(context, uri)
                }
                if (newlyCompressed.isNotEmpty()) {
                    profilePhotos = (profilePhotos + newlyCompressed).distinct().take(4)
                    onStatusMessage("Added ${newlyCompressed.size} photo(s) — tap Save Profile to publish")
                } else {
                    onStatusMessage("Could not process selected photo(s)")
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
                onStatusMessage("Detected ${resolved.cityLabel}")
            } else {
                onStatusMessage("Could not acquire GPS fix; enter your city manually.")
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

    Box(
        modifier = Modifier
            .fillMaxSize()
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

            // Photo & Account Header Card
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
                                text = displayName.ifBlank { "New Kindred Member" },
                                style = MaterialTheme.typography.titleLarge
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
                                enabled = profilePhotos.size < 4,
                                modifier = Modifier.testTag("pick_photo_button")
                            ) {
                                Icon(Icons.Filled.AddAPhoto, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    if (profilePhotos.isEmpty()) "Add Profile Photos (0/4)"
                                    else "Add Photos (${profilePhotos.size}/4)"
                                )
                            }
                        }
                    }

                    // Multi-Photo Verification Strip
                    Text(
                        text = "Profile Photos (${profilePhotos.size}/4) — used for anti-catfish selfie matching",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        for (slot in 0 until 4) {
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
                        onClick = {
                            onResetAiPhotoState()
                            showAiPhotoStudio = true
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("profile_ai_photo_studio_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AutoAwesome,
                            contentDescription = null,
                            tint = CoralRose,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.btn_open_ai_studio),
                            fontWeight = FontWeight.SemiBold
                        )
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
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = cityLabel,
                            onValueChange = { if (it.length <= 80) cityLabel = it },
                            label = { Text("City / Neighborhood") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedButton(
                            onClick = {
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
                            enabled = !isFetchingGps,
                            modifier = Modifier
                                .height(54.dp)
                                .testTag("profile_gps_button")
                        ) {
                            if (isFetchingGps) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Filled.MyLocation, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("GPS")
                            }
                        }
                    }

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
                                                text = if (blocked.isDemo) {
                                                    "Excluded from discovery deck"
                                                } else {
                                                    "Blocked across matching & chat"
                                                },
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
                    val parsedAge = ageText.toIntOrNull()?.coerceIn(18, 99) ?: 25
                    onSaveProfile(
                        displayName,
                        parsedAge,
                        gender,
                        bio,
                        cityLabel,
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

    if (showAiPhotoStudio) {
        AiPhotoStudioSheet(
            state = aiPhotoGenState,
            subjectGender = gender,
            existingProfilePhotos = profilePhotos,
            onGenerate = onGenerateAiPhoto,
            onApplyPhoto = { dataUrl ->
                profilePhotos = if (profilePhotos.size < 4) {
                    (profilePhotos + dataUrl).distinct().take(4)
                } else {
                    (profilePhotos.take(3) + dataUrl).distinct().take(4)
                }
                if (myProfile != null) {
                    onAddGeneratedPhoto(dataUrl)
                } else {
                    onStatusMessage("AI portrait added — tap Save Profile to publish")
                }
                showAiPhotoStudio = false
                onResetAiPhotoState()
            },
            onResetState = onResetAiPhotoState,
            onDismiss = {
                showAiPhotoStudio = false
                onResetAiPhotoState()
            }
        )
    }
}
