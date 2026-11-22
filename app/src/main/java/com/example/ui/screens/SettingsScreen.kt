package com.example.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VerifiedUser
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.KindredConstants
import com.example.model.PrivateUser
import com.example.model.PublicProfile
import com.example.model.VisibilityLevel
import com.example.notifications.KindredBackgroundService
import com.example.ui.theme.ChampagneGold
import com.example.ui.theme.CoralRose
import com.example.ui.theme.EmeraldMint
import com.example.update.AppUpdateChecker
import com.example.update.UpdateCheckResult
import com.example.util.GeoHashUtils
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    myProfile: PublicProfile?,
    privateUser: PrivateUser?,
    isBusy: Boolean,
    onBack: () -> Unit,
    onSavePreferences: (
        interestedIn: String,
        minAgePref: Int,
        maxAgePref: Int,
        maxDistanceKm: Int,
        verifiedOnlyFilter: Boolean,
        myGender: String?,
        discoverable: Boolean?
    ) -> Unit,
    onUpdatePrivacy: (
        (
            hideOnlineStatus: Boolean,
            photosVis: String,
            videosVis: String,
            infoVis: String
        ) -> Unit
    )? = null,
    onDeleteAccount: (() -> Unit)? = null
) {
    var interestedIn by remember(privateUser) {
        mutableStateOf(privateUser?.interestedIn ?: "Everyone")
    }
    var ageRange by remember(privateUser) {
        val min = (privateUser?.minAgePref ?: 20L).toFloat().coerceIn(18f, 99f)
        val max = (privateUser?.maxAgePref ?: 45L).toFloat().coerceIn(min, 99f)
        mutableStateOf(min..max)
    }
    var maxDistanceKm by remember(privateUser) {
        mutableFloatStateOf((privateUser?.maxDistanceKm ?: 100L).toFloat().coerceIn(5f, 500f))
    }
    var verifiedOnly by remember(privateUser) {
        mutableStateOf(privateUser?.verifiedOnlyFilter ?: false)
    }

    var myGender by remember(myProfile) {
        mutableStateOf(myProfile?.gender ?: "Woman")
    }
    var discoverable by remember(myProfile) {
        mutableStateOf(myProfile?.discoverable ?: true)
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
    var showDeleteAccountDialog by remember { mutableStateOf(false) }

    val roundedDistance = maxDistanceKm.roundToInt()
    val geoPrecision = GeoHashUtils.precisionForRadiusKm(roundedDistance.toDouble())

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = stringResource(R.string.settings_title),
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            text = "Real-time Firestore match filters",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("settings_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.settings_back_cd)
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
                // Card 1: Match Filtering Preferences (Gender Interested In, Age Range, Search Radius)
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
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Tune,
                                contentDescription = null,
                                tint = CoralRose
                            )
                            Text("Who You Want to Meet", style = MaterialTheme.typography.titleMedium)
                        }

                        Text("Preferred gender", style = MaterialTheme.typography.titleSmall)
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
                                    ),
                                    modifier = Modifier.testTag("settings_interested_in_$option")
                                )
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Preferred age range", style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = "${ageRange.start.roundToInt()} – ${ageRange.endInclusive.roundToInt()} yrs",
                                style = MaterialTheme.typography.titleSmall,
                                color = CoralRose,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        RangeSlider(
                            value = ageRange,
                            onValueChange = { ageRange = it },
                            valueRange = 18f..99f,
                            colors = SliderDefaults.colors(
                                thumbColor = CoralRose,
                                activeTrackColor = CoralRose
                            ),
                            modifier = Modifier.testTag("settings_age_slider")
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.LocationOn,
                                    contentDescription = null,
                                    tint = ChampagneGold,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text("Search radius", style = MaterialTheme.typography.titleSmall)
                            }
                            Text(
                                text = "$roundedDistance km",
                                style = MaterialTheme.typography.titleSmall,
                                color = CoralRose,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Slider(
                            value = maxDistanceKm,
                            onValueChange = { maxDistanceKm = it },
                            valueRange = 5f..500f,
                            colors = SliderDefaults.colors(
                                thumbColor = CoralRose,
                                activeTrackColor = CoralRose
                            ),
                            modifier = Modifier.testTag("settings_radius_slider")
                        )
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh
                        ) {
                            Text(
                                text = "GeoHash precision $geoPrecision grid active • Filters nearby profiles within $roundedDistance km",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.VerifiedUser,
                                    contentDescription = null,
                                    tint = EmeraldMint,
                                    modifier = Modifier.size(20.dp)
                                )
                                Column {
                                    Text("Verified profiles only", style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        text = "Require live selfie liveness badge in Discover",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Switch(
                                checked = verifiedOnly,
                                onCheckedChange = { verifiedOnly = it },
                                modifier = Modifier.testTag("settings_verified_only_switch")
                            )
                        }
                    }
                }

                // Card 2: Your Gender & Discovery Visibility
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
                        Text("Your Identity & Visibility", style = MaterialTheme.typography.titleMedium)

                        if (myProfile == null) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = ChampagneGold.copy(alpha = 0.14f),
                                border = BorderStroke(1.dp, ChampagneGold.copy(alpha = 0.45f))
                            ) {
                                Text(
                                    text = "Dating preferences are saved immediately to your Firestore account. Create your profile in the Profile tab to publish your own gender and visibility card.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = ChampagneGold,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        } else {
                            Text("My gender", style = MaterialTheme.typography.titleSmall)
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                KindredConstants.GENDER_OPTIONS.forEach { option ->
                                    FilterChip(
                                        selected = myGender == option,
                                        onClick = { myGender = option },
                                        label = { Text(option) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = CoralRose.copy(alpha = 0.2f),
                                            selectedLabelColor = CoralRose
                                        ),
                                        modifier = Modifier.testTag("settings_gender_$option")
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Show me in Discovery", style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        text = "Pause your public card anytime while keeping existing matches",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = discoverable,
                                    onCheckedChange = { discoverable = it },
                                    modifier = Modifier.testTag("settings_discoverable_switch")
                                )
                            }
                        }
                    }
                }

                Button(
                    onClick = {
                        onSavePreferences(
                            interestedIn,
                            ageRange.start.roundToInt(),
                            ageRange.endInclusive.roundToInt(),
                            roundedDistance,
                            verifiedOnly,
                            if (myProfile != null) myGender else null,
                            if (myProfile != null) discoverable else null
                        )
                    },
                    enabled = !isBusy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("settings_save_preferences_button"),
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
                            text = stringResource(R.string.btn_save_preferences),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                if (onUpdatePrivacy != null && myProfile != null) {
                    Card(
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                        modifier = Modifier.testTag("settings_privacy_card")
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
                                Text("Privacy & Online Visibility", style = MaterialTheme.typography.titleMedium)
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Hide Online Status", style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        text = "Hide your online dot & last seen from Active Users and profiles",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = hideOnlineStatus,
                                    onCheckedChange = { hideOnlineStatus = it },
                                    modifier = Modifier.testTag("settings_hide_online_switch")
                                )
                            }
                            SettingsVisibilityPicker(
                                title = "Photos visibility",
                                current = photosVisibility,
                                onSelect = { photosVisibility = it }
                            )
                            SettingsVisibilityPicker(
                                title = "Videos visibility",
                                current = videosVisibility,
                                onSelect = { videosVisibility = it }
                            )
                            SettingsVisibilityPicker(
                                title = "Bio & interests visibility",
                                current = infoVisibility,
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
                                    .testTag("settings_save_privacy_button")
                            ) {
                                Text("Save Privacy Settings")
                            }
                        }
                    }
                }

                BackgroundCallsCard()

                AppUpdatesCard()

                if (onDeleteAccount != null) {
                    Card(
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f)
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f)),
                        modifier = Modifier.testTag("settings_delete_account_card")
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "Danger Zone",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Permanently remove your Kindred profile, photos, videos, matches, and account credentials.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            OutlinedButton(
                                onClick = { showDeleteAccountDialog = true },
                                enabled = !isBusy,
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.6f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("settings_delete_account_button")
                            ) {
                                Icon(Icons.Filled.DeleteForever, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Delete Account", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    if (showDeleteAccountDialog && onDeleteAccount != null) {
        AlertDialog(
            onDismissRequest = { showDeleteAccountDialog = false },
            title = { Text("Delete Kindred Account?") },
            text = {
                Text("This permanently removes your profile, discovery card, matches, and account. This action cannot be undone.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteAccountDialog = false
                        onDeleteAccount()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete Permanently")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAccountDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SettingsVisibilityPicker(
    title: String,
    current: String,
    onSelect: (String) -> Unit
) {
    val options = listOf(
        VisibilityLevel.EVERYONE to "Everyone",
        VisibilityLevel.MATCHES_AND_FRIENDS to "Matches & Friends",
        VisibilityLevel.FRIENDS_ONLY to "Friends Only"
    )
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = title, style = MaterialTheme.typography.labelLarge)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            options.forEach { (level, label) ->
                FilterChip(
                    selected = current == level,
                    onClick = { onSelect(level) },
                    label = { Text(label) }
                )
            }
        }
    }
}

@Composable
private fun BackgroundCallsCard() {
    val context = LocalContext.current
    var bgEnabled by remember { mutableStateOf(KindredBackgroundService.isEnabled(context)) }

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.testTag("settings_background_service_card")
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
                Icon(
                    imageVector = Icons.Filled.NotificationsActive,
                    contentDescription = null,
                    tint = EmeraldMint
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text("Background Live Calls & Sync", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = if (bgEnabled) "Active • Listening while minimized" else "Paused when app is closed",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (bgEnabled) EmeraldMint else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = bgEnabled,
                    onCheckedChange = { checked ->
                        bgEnabled = checked
                        KindredBackgroundService.setEnabled(context, checked)
                    },
                    modifier = Modifier.testTag("settings_background_service_switch")
                )
            }

            Text(
                text = "Keeps Kindred connected in the background so incoming voice and video calls ring and appear immediately without opening the app manually.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedButton(
                onClick = {
                    runCatching {
                        context.startActivity(
                            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("settings_battery_optimization_button")
            ) {
                Icon(
                    imageVector = Icons.Filled.BatterySaver,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.size(6.dp))
                Text("Allow Unrestricted Battery Usage")
            }
        }
    }
}

@Composable
private fun AppUpdatesCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var repoInput by remember { mutableStateOf(AppUpdateChecker.getConfiguredGitHubRepo(context)) }
    var aiStudioInput by remember { mutableStateOf(AppUpdateChecker.getConfiguredAiStudioUrl(context)) }
    var isChecking by remember { mutableStateOf(false) }
    var checkResult by remember { mutableStateOf<UpdateCheckResult?>(null) }
    val signingSha256 = remember(context) { AppUpdateChecker.getAppSigningSha256(context) }

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.testTag("settings_update_card")
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
                Icon(
                    imageVector = Icons.Filled.SystemUpdate,
                    contentDescription = null,
                    tint = EmeraldMint
                )
                Column {
                    Text("App Updates (GitHub & AI Studio)", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "Installed: ${AppUpdateChecker.currentVersionLabel()}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Text(
                text = "Checks GitHub Releases for newer APK builds and keeps your AI Studio cloud link handy.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedTextField(
                value = repoInput,
                onValueChange = { repoInput = it },
                label = { Text("GitHub Repo (owner/repo or URL)") },
                placeholder = { Text("e.g. username/kindred") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("settings_github_repo_input")
            )

            OutlinedTextField(
                value = aiStudioInput,
                onValueChange = { aiStudioInput = it },
                label = { Text("AI Studio App Link (optional)") },
                placeholder = { Text("https://ais-pre-...run.app") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("settings_aistudio_url_input")
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = {
                        val savedSlug = AppUpdateChecker.saveGitHubRepo(context, repoInput)
                        if (savedSlug.isNotBlank()) repoInput = savedSlug
                        AppUpdateChecker.saveAiStudioUrl(context, aiStudioInput)
                        isChecking = true
                        scope.launch {
                            checkResult = AppUpdateChecker.checkGitHubLatestRelease(context)
                            isChecking = false
                        }
                    },
                    enabled = !isChecking,
                    colors = ButtonDefaults.buttonColors(containerColor = CoralRose, contentColor = Color.White),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("check_updates_button")
                ) {
                    if (isChecking) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.CloudDownload,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.size(6.dp))
                        Text("Check Updates")
                    }
                }

                if (aiStudioInput.trim().startsWith("http")) {
                    OutlinedButton(
                        onClick = {
                            AppUpdateChecker.saveAiStudioUrl(context, aiStudioInput)
                            AppUpdateChecker.openExternalUpdateUrl(context, aiStudioInput)
                        },
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("open_aistudio_url_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.OpenInNew,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.size(6.dp))
                        Text("AI Studio")
                    }
                }
            }

            when (val res = checkResult) {
                is UpdateCheckResult.Success -> {
                    val downloadUrl = res.release.apkDownloadUrl ?: res.release.releasePageUrl
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = EmeraldMint.copy(alpha = 0.14f),
                        border = BorderStroke(1.dp, EmeraldMint.copy(alpha = 0.45f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Update available: ${res.release.title} (${res.release.tagName})",
                                style = MaterialTheme.typography.titleSmall,
                                color = EmeraldMint,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = res.release.releaseNotes,
                                style = MaterialTheme.typography.bodySmall
                            )
                            Button(
                                onClick = {
                                    AppUpdateChecker.openExternalUpdateUrl(context, downloadUrl)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = EmeraldMint, contentColor = Color.White),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("settings_download_update_button")
                            ) {
                                Text("Download New APK Release", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
                is UpdateCheckResult.UpToDate -> {
                    Text(
                        text = "Up to date • ${res.checkedSource}",
                        style = MaterialTheme.typography.bodySmall,
                        color = EmeraldMint
                    )
                }
                is UpdateCheckResult.NotConfigured -> {
                    Text(
                        text = res.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = ChampagneGold
                    )
                }
                is UpdateCheckResult.Error -> {
                    Text(
                        text = res.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                null -> Unit
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Security,
                            contentDescription = null,
                            tint = EmeraldMint,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "App Signing & Google Play Protect",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Text(
                        text = "SHA-256: $signingSha256",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("settings_signing_sha256_text")
                    )
                    Text(
                        text = "Install consistently signed release APKs (kindred-release.apk) from the same signing certificate to avoid unrecognized debug build prompts.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
