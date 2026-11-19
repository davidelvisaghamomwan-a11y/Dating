package com.example.ui.screens

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.KindredConstants
import com.example.model.PrivateUser
import com.example.model.PublicProfile
import com.example.ui.theme.ChampagneGold
import com.example.ui.theme.CoralRose
import com.example.ui.theme.EmeraldMint
import com.example.util.GeoHashUtils
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    myProfile: PublicProfile?,
    privateUser: PrivateUser?,
    includeDemoProfiles: Boolean,
    isBusy: Boolean,
    onBack: () -> Unit,
    onToggleDemoProfiles: (Boolean) -> Unit,
    onSavePreferences: (
        interestedIn: String,
        minAgePref: Int,
        maxAgePref: Int,
        maxDistanceKm: Int,
        verifiedOnlyFilter: Boolean,
        myGender: String?,
        discoverable: Boolean?
    ) -> Unit
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

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Include sample profiles (23)", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    text = "Show curated sample profiles alongside live Firestore members",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = includeDemoProfiles,
                                onCheckedChange = onToggleDemoProfiles,
                                modifier = Modifier.testTag("settings_demo_profiles_switch")
                            )
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

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}
