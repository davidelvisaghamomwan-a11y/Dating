package com.example.ui.screens

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Star
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.KindredConstants
import com.example.model.PublicProfile
import com.example.ui.components.CountryAndCitySelector
import com.example.ui.components.ProfileAvatarImage
import com.example.ui.theme.ChampagneGold
import com.example.ui.theme.CoralRose
import com.example.util.VerificationAnalyzer
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EditProfileScreen(
    myProfile: PublicProfile?,
    defaultDisplayName: String?,
    isBusy: Boolean,
    onBack: () -> Unit,
    onSaveChanges: (
        bio: String,
        interests: List<String>,
        profilePhotos: List<String>,
        cityLabel: String,
        country: String
    ) -> Unit,
    onStatusMessage: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val initialPhotos = remember(myProfile) { myProfile?.allProfilePhotos().orEmpty() }
    val initialBio = remember(myProfile) { myProfile?.bio.orEmpty() }
    val initialInterests = remember(myProfile) { myProfile?.interests ?: listOf("Coffee", "Hiking") }
    val initialCity = remember(myProfile) { myProfile?.cityLabel.orEmpty().ifBlank { "San Francisco" } }
    val initialCountry = remember(myProfile) { myProfile?.country.orEmpty().ifBlank { "United States" } }

    var bio by remember(myProfile) { mutableStateOf(initialBio) }
    var selectedInterests by remember(myProfile) { mutableStateOf(initialInterests) }
    var profilePhotos by remember(myProfile) { mutableStateOf(initialPhotos) }
    var cityLabel by remember(myProfile) { mutableStateOf(initialCity) }
    var country by remember(myProfile) { mutableStateOf(initialCountry) }

    val displayName = (myProfile?.displayName ?: defaultDisplayName).orEmpty().ifBlank { "Member" }
    val avatarSeed = myProfile?.avatarSeed ?: myProfile?.userId ?: "kindred"

    val hasUnsavedChanges = bio != initialBio ||
        selectedInterests != initialInterests ||
        profilePhotos != initialPhotos ||
        cityLabel != initialCity ||
        country != initialCountry

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
                    onStatusMessage("Added ${newlyCompressed.size} photo(s) — tap Save Changes to publish")
                } else {
                    onStatusMessage("Could not process selected photo(s)")
                }
            }
        }
    }

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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 620.dp)
                    .align(Alignment.TopCenter),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Section 1: Profile Photos (up to 4)
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
                                    text = "Profile Photos (${profilePhotos.size}/4)",
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
                                enabled = profilePhotos.size < 4,
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

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            for (slot in 0 until 4) {
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
                                            displayName = displayName,
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
                        Text("Country & City", style = MaterialTheme.typography.titleMedium)
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

                // Section 2: Bio
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

                // Section 3: Interests (up to 8)
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
                        onSaveChanges(bio, selectedInterests, profilePhotos, cityLabel, country)
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
}
