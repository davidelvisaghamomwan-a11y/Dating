package com.example.ui.screens

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.VideoCall
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.KindredConstants
import com.example.model.ProfileVideoItem
import com.example.model.PublicProfile
import com.example.ui.components.CountryAndCitySelector
import com.example.ui.components.ProfileAvatarImage
import com.example.ui.theme.ChampagneGold
import com.example.ui.theme.CoralRose
import com.example.ui.theme.EmeraldMint
import com.example.util.DobUtils
import com.example.util.DobValidationResult
import com.example.util.PreparedVideoUpload
import com.example.util.ResolvedDeviceLocation
import com.example.util.VerificationAnalyzer
import com.example.util.VideoClipHelper
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EditProfileScreen(
    myProfile: PublicProfile?,
    defaultDisplayName: String?,
    isBusy: Boolean,
    latestLocation: ResolvedDeviceLocation? = null,
    videos: List<ProfileVideoItem> = emptyList(),
    onUploadVideo: (caption: String, prepared: PreparedVideoUpload) -> Unit = { _, _ -> },
    onUploadVideoWithResult: ((caption: String, prepared: PreparedVideoUpload, onComplete: (Boolean, String?) -> Unit) -> Unit)? = null,
    onDeleteVideo: (videoId: String) -> Unit = {},
    onBack: () -> Unit,
    onSaveChanges: (
        bio: String,
        interests: List<String>,
        profilePhotos: List<String>,
        cityLabel: String,
        country: String
    ) -> Unit,
    onSaveDetailedChanges: (
        (
            displayName: String,
            gender: String,
            dateOfBirth: String,
            computedAge: Int?,
            bio: String,
            interests: List<String>,
            profilePhotos: List<String>,
            cityLabel: String,
            country: String
        ) -> Unit
    )? = null,
    onStatusMessage: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val initialName = remember(myProfile, defaultDisplayName) {
        (myProfile?.displayName ?: defaultDisplayName).orEmpty().ifBlank { "Member" }
    }
    val initialGender = remember(myProfile) {
        myProfile?.gender?.takeIf { it in KindredConstants.GENDER_OPTIONS } ?: "Woman"
    }
    val parsedInitialDob = remember(myProfile?.dateOfBirth) {
        DobUtils.parseDob(myProfile?.dateOfBirth)
    }

    val initialPhotos = remember(myProfile) { myProfile?.allProfilePhotos().orEmpty() }
    val initialBio = remember(myProfile) { myProfile?.bio.orEmpty() }
    val initialInterests = remember(myProfile) { myProfile?.interests ?: listOf("Coffee", "Hiking") }
    val initialCity = remember(myProfile) { myProfile?.cityLabel.orEmpty().ifBlank { "San Francisco" } }
    val initialCountry = remember(myProfile) { myProfile?.country.orEmpty().ifBlank { "United States" } }

    var editableName by remember(myProfile) { mutableStateOf(initialName) }
    var editableGender by remember(myProfile) { mutableStateOf(initialGender) }
    var selectedDay by remember(parsedInitialDob) { mutableStateOf<Int?>(parsedInitialDob?.first) }
    var selectedMonth by remember(parsedInitialDob) { mutableStateOf<Int?>(parsedInitialDob?.second) }
    var selectedYear by remember(parsedInitialDob) { mutableStateOf<Int?>(parsedInitialDob?.third) }
    var dayExpanded by remember { mutableStateOf(false) }
    var monthExpanded by remember { mutableStateOf(false) }
    var yearExpanded by remember { mutableStateOf(false) }

    var bio by remember(myProfile) { mutableStateOf(initialBio) }
    var selectedInterests by remember(myProfile) { mutableStateOf(initialInterests) }
    var profilePhotos by remember(myProfile) { mutableStateOf(initialPhotos) }
    var cityLabel by remember(myProfile) { mutableStateOf(initialCity) }
    var country by remember(myProfile) { mutableStateOf(initialCountry) }

    var pendingVideoUpload by remember { mutableStateOf<PreparedVideoUpload?>(null) }
    var videoCaptionDraft by remember { mutableStateOf("") }
    var isPreparingVideo by remember { mutableStateOf(false) }
    var isUploadingVideo by remember { mutableStateOf(false) }
    var videoUploadError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(latestLocation) {
        val loc = latestLocation ?: return@LaunchedEffect
        if (loc.isLiveGps) {
            if (loc.cityLabel.isNotBlank()) {
                cityLabel = loc.cityLabel
            }
            if (loc.countryName.isNotBlank()) {
                country = loc.countryName
            }
        }
    }

    val avatarSeed = myProfile?.avatarSeed ?: myProfile?.userId ?: "kindred"
    val dobValidation = remember(selectedDay, selectedMonth, selectedYear) {
        DobUtils.validateDob(selectedDay, selectedMonth, selectedYear)
    }
    val validDob = dobValidation as? DobValidationResult.Valid

    val hasUnsavedChanges = bio != initialBio ||
        editableName != initialName ||
        editableGender != initialGender ||
        (validDob?.dobFormatted ?: myProfile?.dateOfBirth.orEmpty()) != myProfile?.dateOfBirth.orEmpty() ||
        selectedInterests != initialInterests ||
        profilePhotos != initialPhotos ||
        cityLabel != initialCity ||
        country != initialCountry

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
                    onStatusMessage("Added ${newlyCompressed.size} photo(s) — tap Save Changes to publish")
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
                res.onSuccess { prepared ->
                    pendingVideoUpload = prepared
                }.onFailure { err ->
                    onStatusMessage(err.localizedMessage ?: "Unable to prepare video")
                }
            }
        }
    }

    val yearOptions = remember { DobUtils.yearOptions() }
    val validDayCount = remember(selectedMonth, selectedYear) {
        if (selectedMonth != null && selectedYear != null) {
            DobUtils.daysInMonth(selectedMonth!!, selectedYear!!)
        } else 31
    }
    val dayOptions = remember(validDayCount) { (1..validDayCount).toList() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = stringResource(R.string.edit_profile_title),
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            text = if (hasUnsavedChanges) "Unsaved changes" else "Synced with Firestore",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (hasUnsavedChanges) ChampagneGold else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("edit_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.edit_profile_back_cd)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
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
                // Section 1: Expanded Profile Photos (up to 12)
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
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Profile Photos (${profilePhotos.size}/${KindredConstants.MAX_PROFILE_PHOTOS})",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = "Tap any photo to set it as your Main card photo",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            OutlinedButton(
                                onClick = {
                                    photoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                enabled = profilePhotos.size < KindredConstants.MAX_PROFILE_PHOTOS,
                                modifier = Modifier.testTag("edit_add_photos_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.AddAPhoto,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Add")
                            }
                        }

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
                                                .aspectRatio(0.8f)
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
                                                    displayName = editableName,
                                                    seed = avatarSeed,
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .clickable {
                                                            if (slot > 0) {
                                                                val reordered = profilePhotos.toMutableList()
                                                                val chosen = reordered.removeAt(slot)
                                                                reordered.add(0, chosen)
                                                                profilePhotos = reordered
                                                                onStatusMessage("Primary photo updated")
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
                                                        profilePhotos = profilePhotos.filterIndexed { idx, _ -> idx != slot }
                                                    },
                                                    modifier = Modifier
                                                        .align(Alignment.TopEnd)
                                                        .minimumInteractiveComponentSize()
                                                        .testTag("edit_remove_photo_$slot")
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
                                                        contentDescription = "Add photo slot ${slot + 1}",
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
                            profilePhotos != initialPhotos
                        if (photosChangedWhileVerified) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = ChampagneGold.copy(alpha = 0.16f),
                                border = BorderStroke(1.dp, ChampagneGold.copy(alpha = 0.45f))
                            ) {
                                Text(
                                    text = "Updating your profile photos will reset your Verified badge until you complete a quick selfie check.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = ChampagneGold,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                        }
                    }
                }

                // Section 2: Showcase Videos
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    modifier = Modifier.testTag("edit_showcase_videos_card")
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
                                Text(
                                    text = "Showcase Videos (${videos.size})",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = "Share high-resolution clips of any length on your profile",
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
                                modifier = Modifier.testTag("edit_add_video_button")
                            ) {
                                if (isPreparingVideo) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(Icons.Filled.VideoCall, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Add Video")
                                }
                            }
                        }

                        if (videos.isEmpty()) {
                            Text(
                                text = "No showcase videos uploaded yet.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            LazyRow(
                                contentPadding = PaddingValues(vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                itemsIndexed(videos, key = { _, v -> v.id }) { idx, video ->
                                    val thumb = remember(video.thumbnailDataUrl) {
                                        video.thumbnailDataUrl?.let { VerificationAnalyzer.decodeBase64DataUrl(it) }
                                    }
                                    Box(
                                        modifier = Modifier
                                            .size(width = 124.dp, height = 160.dp)
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(Color(0xFF1A1423))
                                            .testTag("edit_video_item_$idx")
                                    ) {
                                        if (thumb != null) {
                                            Image(
                                                bitmap = thumb,
                                                contentDescription = video.caption.ifBlank { "Profile video" },
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        }
                                        Icon(
                                            imageVector = Icons.Filled.PlayArrow,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier
                                                .size(34.dp)
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
                                                modifier = Modifier.size(26.dp)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Icon(
                                                        imageVector = Icons.Filled.DeleteOutline,
                                                        contentDescription = "Delete video",
                                                        tint = Color.White,
                                                        modifier = Modifier.size(15.dp)
                                                    )
                                                }
                                            }
                                        }
                                        if (video.caption.isNotBlank() || video.mediaDurationSec > 0L) {
                                            Surface(
                                                color = Color.Black.copy(alpha = 0.6f),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .align(Alignment.BottomCenter)
                                            ) {
                                                Text(
                                                    text = buildString {
                                                        if (video.mediaDurationSec > 0L) {
                                                            append(VideoClipHelper.formatDurationLabel(video.mediaDurationSec))
                                                        }
                                                        if (video.caption.isNotBlank()) {
                                                            if (isNotEmpty()) append(" • ")
                                                            append(video.caption)
                                                        }
                                                    },
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = Color.White,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Section 3: Basic Info & Date of Birth (DD-MM-YYYY)
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
                        Text("Identity & Birthday", style = MaterialTheme.typography.titleMedium)

                        OutlinedTextField(
                            value = editableName,
                            onValueChange = { if (it.length <= 40) editableName = it },
                            label = { Text("Display Name") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("edit_name_input")
                        )

                        Text("Gender", style = MaterialTheme.typography.titleSmall)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            KindredConstants.GENDER_OPTIONS.forEach { option ->
                                FilterChip(
                                    selected = editableGender == option,
                                    onClick = { editableGender = option },
                                    label = { Text(option) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = CoralRose.copy(alpha = 0.2f),
                                        selectedLabelColor = CoralRose
                                    )
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Filled.Cake, contentDescription = null, tint = CoralRose, modifier = Modifier.size(18.dp))
                                Text("Date of Birth (DD-MM-YYYY)", style = MaterialTheme.typography.titleSmall)
                            }
                            Text(
                                text = validDob?.let { "Age: ${it.age}" }
                                    ?: myProfile?.age?.let { "Age: $it" }
                                    ?: "Age: --",
                                style = MaterialTheme.typography.labelLarge,
                                color = if (validDob != null) EmeraldMint else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ExposedDropdownMenuBox(
                                expanded = dayExpanded,
                                onExpandedChange = { dayExpanded = !dayExpanded },
                                modifier = Modifier.weight(0.95f)
                            ) {
                                OutlinedTextField(
                                    value = selectedDay?.toString()?.padStart(2, '0') ?: "",
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Day") },
                                    placeholder = { Text("DD") },
                                    trailingIcon = { Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null) },
                                    modifier = Modifier
                                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                        .fillMaxWidth()
                                )
                                ExposedDropdownMenu(
                                    expanded = dayExpanded,
                                    onDismissRequest = { dayExpanded = false }
                                ) {
                                    dayOptions.forEach { d ->
                                        DropdownMenuItem(
                                            text = { Text(d.toString().padStart(2, '0')) },
                                            onClick = {
                                                selectedDay = d
                                                dayExpanded = false
                                            }
                                        )
                                    }
                                }
                            }

                            ExposedDropdownMenuBox(
                                expanded = monthExpanded,
                                onExpandedChange = { monthExpanded = !monthExpanded },
                                modifier = Modifier.weight(1.1f)
                            ) {
                                val mLabel = selectedMonth?.let { m ->
                                    DobUtils.MONTH_OPTIONS.firstOrNull { it.number == m }?.label
                                        ?: m.toString().padStart(2, '0')
                                } ?: ""
                                OutlinedTextField(
                                    value = mLabel,
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Month") },
                                    placeholder = { Text("MM") },
                                    trailingIcon = { Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null) },
                                    modifier = Modifier
                                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                        .fillMaxWidth()
                                )
                                ExposedDropdownMenu(
                                    expanded = monthExpanded,
                                    onDismissRequest = { monthExpanded = false }
                                ) {
                                    DobUtils.MONTH_OPTIONS.forEach { option ->
                                        DropdownMenuItem(
                                            text = { Text(option.label) },
                                            onClick = {
                                                selectedMonth = option.number
                                                selectedDay?.let { curD ->
                                                    val maxD = DobUtils.daysInMonth(option.number, selectedYear ?: 2000)
                                                    if (curD > maxD) selectedDay = maxD
                                                }
                                                monthExpanded = false
                                            }
                                        )
                                    }
                                }
                            }

                            ExposedDropdownMenuBox(
                                expanded = yearExpanded,
                                onExpandedChange = { yearExpanded = !yearExpanded },
                                modifier = Modifier.weight(1.15f)
                            ) {
                                OutlinedTextField(
                                    value = selectedYear?.toString() ?: "",
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Year") },
                                    placeholder = { Text("YYYY") },
                                    trailingIcon = { Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null) },
                                    modifier = Modifier
                                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                        .fillMaxWidth()
                                )
                                ExposedDropdownMenu(
                                    expanded = yearExpanded,
                                    onDismissRequest = { yearExpanded = false }
                                ) {
                                    yearOptions.forEach { y ->
                                        DropdownMenuItem(
                                            text = { Text(y.toString()) },
                                            onClick = {
                                                selectedYear = y
                                                val m = selectedMonth
                                                val d = selectedDay
                                                if (m != null && d != null) {
                                                    val maxD = DobUtils.daysInMonth(m, y)
                                                    if (d > maxD) selectedDay = maxD
                                                }
                                                yearExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Location (Country & City)
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
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Country & City", style = MaterialTheme.typography.titleMedium)
                            if (latestLocation?.isLiveGps == true) {
                                Surface(
                                    shape = CircleShape,
                                    color = EmeraldMint.copy(alpha = 0.16f),
                                    border = BorderStroke(1.dp, EmeraldMint.copy(alpha = 0.45f)),
                                    modifier = Modifier.testTag("edit_gps_verified_badge")
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
                            showGpsButton = false,
                            testTagPrefix = "edit"
                        )
                    }
                }

                // Section 4: Bio
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("About Me (Bio)", style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = "${bio.length}/400",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (bio.length >= 380) CoralRose else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        OutlinedTextField(
                            value = bio,
                            onValueChange = { if (it.length <= 400) bio = it },
                            placeholder = {
                                Text("Share what makes your weekends great, favorite coffee spots, or what you're looking for...")
                            },
                            minLines = 4,
                            maxLines = 7,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("edit_bio_input")
                        )
                    }
                }

                // Section 5: Interests (up to 8)
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
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Interests", style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = "${selectedInterests.size}/8 selected",
                                style = MaterialTheme.typography.labelMedium,
                                color = CoralRose
                            )
                        }

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
                                            onStatusMessage("You can select up to 8 interests")
                                            selectedInterests
                                        }
                                    },
                                    label = { Text(tag) },
                                    leadingIcon = if (selected) {
                                        {
                                            Icon(
                                                imageVector = Icons.Filled.Check,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    } else null,
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = CoralRose.copy(alpha = 0.2f),
                                        selectedLabelColor = CoralRose,
                                        selectedLeadingIconColor = CoralRose
                                    ),
                                    modifier = Modifier.testTag("edit_interest_chip_$tag")
                                )
                            }
                        }
                    }
                }

                // Save CTA
                Button(
                    onClick = {
                        val dobStr = validDob?.dobFormatted ?: myProfile?.dateOfBirth.orEmpty()
                        val detailed = onSaveDetailedChanges
                        if (detailed != null) {
                            detailed(
                                editableName.trim().ifBlank { initialName },
                                editableGender,
                                dobStr,
                                validDob?.age,
                                bio,
                                selectedInterests,
                                profilePhotos,
                                cityLabel,
                                country
                            )
                        } else {
                            onSaveChanges(bio, selectedInterests, profilePhotos, cityLabel, country)
                        }
                    },
                    enabled = !isBusy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("edit_save_profile_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CoralRose,
                        contentColor = Color.White
                    )
                ) {
                    if (isBusy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.btn_save_changes),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    pendingVideoUpload?.let { upload ->
        AlertDialog(
            onDismissRequest = {
                if (!isUploadingVideo) {
                    pendingVideoUpload = null
                    videoCaptionDraft = ""
                    videoUploadError = null
                }
            },
            title = { Text("Add Showcase Video") },
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
                        enabled = !isUploadingVideo,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (videoUploadError != null) {
                        Text(
                            text = videoUploadError.orEmpty(),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (isUploadingVideo) return@Button
                        isUploadingVideo = true
                        videoUploadError = null
                        val cap = videoCaptionDraft.trim()
                        scope.launch {
                            try {
                                kotlinx.coroutines.withTimeout(45_000L) {
                                    val uploadRes = onUploadVideoWithResult
                                    if (uploadRes != null) {
                                        var done = false
                                        uploadRes(cap, upload) { success, errMsg ->
                                            done = true
                                            isUploadingVideo = false
                                            if (success) {
                                                pendingVideoUpload = null
                                                videoCaptionDraft = ""
                                                videoUploadError = null
                                            } else {
                                                videoUploadError = errMsg ?: "Upload failed"
                                            }
                                        }
                                        while (!done) { kotlinx.coroutines.delay(100L) }
                                    } else {
                                        onUploadVideo(cap, upload)
                                        isUploadingVideo = false
                                        pendingVideoUpload = null
                                        videoCaptionDraft = ""
                                    }
                                }
                            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                                android.util.Log.e("EditProfile", "Video upload timed out", e)
                                videoUploadError = "Upload timed out. Check network connection."
                                isUploadingVideo = false
                            } catch (e: kotlinx.coroutines.CancellationException) {
                                isUploadingVideo = false
                                throw e
                            } catch (e: Exception) {
                                android.util.Log.e("EditProfile", "Video upload error", e)
                                videoUploadError = e.localizedMessage ?: "Upload failed"
                                isUploadingVideo = false
                            } finally {
                                isUploadingVideo = false
                            }
                        }
                    },
                    enabled = !isUploadingVideo,
                    colors = ButtonDefaults.buttonColors(containerColor = CoralRose)
                ) {
                    if (isUploadingVideo) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Text("Upload")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        if (!isUploadingVideo) {
                            pendingVideoUpload = null
                            videoCaptionDraft = ""
                            videoUploadError = null
                        }
                    },
                    enabled = !isUploadingVideo
                ) { Text("Cancel") }
            }
        )
    }
}
