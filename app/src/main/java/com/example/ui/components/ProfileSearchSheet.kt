package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.DiscoveryCandidate
import com.example.model.PublicProfile
import com.example.ui.theme.CoralRose
import com.example.ui.theme.EmeraldMint
import com.example.util.LocationAndGeoHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileSearchSheet(
    searchableProfiles: List<DiscoveryCandidate>,
    onMessageProfile: (PublicProfile) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        ProfileSearchContent(
            searchableProfiles = searchableProfiles,
            onMessageProfile = onMessageProfile,
            onClose = onDismiss
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileSearchContent(
    searchableProfiles: List<DiscoveryCandidate>,
    onMessageProfile: (PublicProfile) -> Unit,
    onClose: () -> Unit = {}
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedCountryFilter by remember { mutableStateOf<String?>(null) }

    val availableCountries = remember(searchableProfiles) {
        searchableProfiles
            .map { it.profile.country.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
    }

    val filteredResults = remember(searchableProfiles, searchQuery, selectedCountryFilter) {
        val q = searchQuery.trim()
        searchableProfiles
            .filter { candidate ->
                val profile = candidate.profile
                val matchesCountry = selectedCountryFilter == null ||
                    profile.country.equals(selectedCountryFilter, ignoreCase = true)
                val matchesQuery = if (q.isBlank()) {
                    true
                } else {
                    profile.displayName.contains(q, ignoreCase = true) ||
                        profile.cityLabel.contains(q, ignoreCase = true) ||
                        profile.country.contains(q, ignoreCase = true)
                }
                matchesCountry && matchesQuery
            }
            .sortedWith(
                compareByDescending<DiscoveryCandidate> {
                    val qLower = q.lowercase()
                    qLower.isNotBlank() && it.profile.displayName.lowercase().startsWith(qLower)
                }
                    .thenByDescending {
                        val qLower = q.lowercase()
                        qLower.isNotBlank() && it.profile.displayName.lowercase().contains(qLower)
                    }
                    .thenBy { it.distanceKm }
            )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Surface(
                    shape = CircleShape,
                    color = CoralRose.copy(alpha = 0.16f),
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = null,
                            tint = CoralRose,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Column {
                    Text(
                        text = stringResource(R.string.search_profiles_title),
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Text(
                        text = "Find anyone by name and start messaging directly",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            IconButton(
                onClick = onClose,
                modifier = Modifier.testTag("close_search_sheet_button")
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Close search"
                )
            }
        }

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { if (it.length <= 60) searchQuery = it },
            placeholder = { Text(stringResource(R.string.search_profiles_placeholder)) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = null,
                    tint = CoralRose
                )
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(
                        onClick = { searchQuery = "" },
                        modifier = Modifier.testTag("clear_profile_search_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Clear search"
                        )
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("profile_search_input")
        )

        if (availableCountries.size > 1) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedCountryFilter == null,
                    onClick = { selectedCountryFilter = null },
                    label = { Text("All Countries") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = CoralRose.copy(alpha = 0.2f),
                        selectedLabelColor = CoralRose
                    ),
                    modifier = Modifier.testTag("search_country_chip_all")
                )
                availableCountries.forEach { country ->
                    FilterChip(
                        selected = selectedCountryFilter == country,
                        onClick = {
                            selectedCountryFilter = if (selectedCountryFilter == country) null else country
                        },
                        label = { Text(country) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = CoralRose.copy(alpha = 0.2f),
                            selectedLabelColor = CoralRose
                        ),
                        modifier = Modifier.testTag("search_country_chip_$country")
                    )
                }
            }
        }

        Text(
            text = "${filteredResults.size} ${if (filteredResults.size == 1) "profile" else "profiles"} found",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (filteredResults.isEmpty()) {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "No matching profiles",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Try searching another name, city, or country.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 12.dp)
            ) {
                items(filteredResults, key = { it.profile.userId }) { candidate ->
                    val profile = candidate.profile
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("search_result_card_${profile.userId}")
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                ProfileAvatarImage(
                                    photoDataUrl = profile.photoDataUrl,
                                    displayName = profile.displayName,
                                    seed = profile.avatarSeed,
                                    modifier = Modifier.size(54.dp),
                                    initialsFontSizeSp = 20
                                )

                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = "${profile.displayName}, ${profile.age}",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (profile.isProfileVerified) {
                                            Icon(
                                                imageVector = Icons.Filled.VerifiedUser,
                                                contentDescription = stringResource(R.string.cd_verified_badge),
                                                tint = EmeraldMint,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.LocationOn,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Text(
                                            text = buildString {
                                                append(profile.formattedLocation())
                                                if (candidate.distanceKm > 0.0) {
                                                    append(" • ")
                                                    append(LocationAndGeoHelper.formatDistanceLabel(candidate.distanceKm))
                                                }
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    if (profile.bio.isNotBlank()) {
                                        Spacer(modifier = Modifier.height(3.dp))
                                        Text(
                                            text = profile.bio,
                                            style = MaterialTheme.typography.bodySmall,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                if (profile.interests.isNotEmpty()) {
                                    FlowRow(
                                        modifier = Modifier.weight(1f),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        profile.interests.take(3).forEach { tag ->
                                            Surface(
                                                shape = RoundedCornerShape(50),
                                                color = MaterialTheme.colorScheme.surfaceContainerHigh
                                            ) {
                                                Text(
                                                    text = tag,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                )
                                            }
                                        }
                                    }
                                } else {
                                    Spacer(modifier = Modifier.weight(1f))
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                Button(
                                    onClick = { onMessageProfile(profile) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = CoralRose,
                                        contentColor = Color.White
                                    ),
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                                    modifier = Modifier
                                        .height(48.dp)
                                        .testTag("search_message_button_${profile.userId}")
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Send,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = stringResource(R.string.btn_message_directly),
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
