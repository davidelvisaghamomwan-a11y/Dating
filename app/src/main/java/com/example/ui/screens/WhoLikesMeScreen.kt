package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BookmarkRemove
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.DiscoveryCandidate
import com.example.model.FavoriteRecord
import com.example.model.ProfileViewRecord
import com.example.model.PublicProfile
import com.example.ui.components.ProfileAvatarImage
import com.example.ui.components.VerifiedShieldBadge
import com.example.ui.theme.CoralRose
import com.example.util.CountryFlagHelper
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhoLikesMeScreen(
    whoLikedMe: List<DiscoveryCandidate>,
    profileViews: List<ProfileViewRecord>,
    favorites: List<FavoriteRecord>,
    allProfilesByUid: Map<String, PublicProfile>,
    initialTab: Int = 0,
    onBack: () -> Unit,
    onOpenProfile: (PublicProfile) -> Unit,
    onLikeBack: (DiscoveryCandidate) -> Unit,
    onRemoveFavorite: (PublicProfile) -> Unit
) {
    var selectedTab by remember(initialTab) { mutableIntStateOf(initialTab.coerceIn(0, 2)) }
    val timeFormatter = remember { SimpleDateFormat("MMM d • h:mm a", Locale.getDefault()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Activity & Connections") },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("who_likes_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .navigationBarsPadding()
                .testTag("who_likes_me_screen")
        ) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Likes (${whoLikedMe.size})", maxLines = 1) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Views (${profileViews.size})", maxLines = 1) }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("Saved (${favorites.size})", maxLines = 1) }
                )
            }

            Box(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 620.dp)
                        .align(Alignment.TopCenter),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    when (selectedTab) {
                        0 -> {
                            if (whoLikedMe.isEmpty()) {
                                item {
                                    EmptyTabMessage("No new likes yet. Complete your profile & upload photos to boost visibility!")
                                }
                            } else {
                                items(whoLikedMe, key = { it.profile.userId }) { candidate ->
                                    val p = candidate.profile
                                    val flag = p.flagEmoji()
                                    Card(
                                        shape = RoundedCornerShape(18.dp),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { onOpenProfile(p) }
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(14.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            ProfileAvatarImage(
                                                photoDataUrl = p.photoDataUrl,
                                                displayName = p.displayName,
                                                seed = p.avatarSeed,
                                                modifier = Modifier.size(52.dp),
                                                shape = CircleShape,
                                                initialsFontSizeSp = 18
                                            )
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    Text(
                                                        text = buildString {
                                                            append("${p.displayName}, ${p.age}")
                                                            if (flag.isNotBlank()) append(" $flag")
                                                        },
                                                        style = MaterialTheme.typography.titleMedium,
                                                        fontWeight = FontWeight.SemiBold,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    VerifiedShieldBadge(isVerified = p.isProfileVerified, compact = true)
                                                }
                                                Text(
                                                    text = p.formattedLocation(),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            Button(
                                                onClick = { onLikeBack(candidate) },
                                                colors = ButtonDefaults.buttonColors(containerColor = CoralRose, contentColor = Color.White),
                                                modifier = Modifier.testTag("like_back_${p.userId}")
                                            ) {
                                                Icon(Icons.Filled.Favorite, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Match")
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        1 -> {
                            if (profileViews.isEmpty()) {
                                item { EmptyTabMessage("No profile views recorded yet.") }
                            } else {
                                items(profileViews, key = { it.id }) { view ->
                                    val profile = allProfilesByUid[view.viewerUid]
                                    val whenText = view.viewedAt?.toDate()?.let { timeFormatter.format(it) } ?: "Recently"
                                    Card(
                                        shape = RoundedCornerShape(18.dp),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable(enabled = profile != null) {
                                                if (profile != null) onOpenProfile(profile)
                                            }
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(14.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            ProfileAvatarImage(
                                                photoDataUrl = profile?.photoDataUrl ?: view.viewerPhoto,
                                                displayName = profile?.displayName ?: view.viewerName.ifBlank { "Member" },
                                                seed = view.viewerUid,
                                                modifier = Modifier.size(48.dp),
                                                shape = CircleShape,
                                                initialsFontSizeSp = 16
                                            )
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = profile?.displayName ?: view.viewerName.ifBlank { "Kindred Member" },
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                                Text(
                                                    text = "Viewed your profile • $whenText",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        else -> {
                            if (favorites.isEmpty()) {
                                item { EmptyTabMessage("Save profiles from Search, Discover, or User Profiles to keep them here.") }
                            } else {
                                items(favorites, key = { it.id }) { fav ->
                                    val resolvedProfile = allProfilesByUid[fav.targetUid] ?: PublicProfile(
                                        userId = fav.targetUid,
                                        displayName = fav.displayName,
                                        age = fav.age,
                                        cityLabel = fav.cityLabel,
                                        country = fav.country,
                                        photoDataUrl = fav.photoDataUrl
                                    )
                                    val flag = CountryFlagHelper.flagEmojiForCountry(resolvedProfile.country)
                                    Card(
                                        shape = RoundedCornerShape(18.dp),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { onOpenProfile(resolvedProfile) }
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(14.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            ProfileAvatarImage(
                                                photoDataUrl = resolvedProfile.photoDataUrl,
                                                displayName = resolvedProfile.displayName,
                                                seed = resolvedProfile.avatarSeed,
                                                modifier = Modifier.size(48.dp),
                                                shape = CircleShape,
                                                initialsFontSizeSp = 16
                                            )
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = buildString {
                                                        append("${resolvedProfile.displayName}, ${resolvedProfile.age}")
                                                        if (flag.isNotBlank()) append(" $flag")
                                                    },
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                                Text(
                                                    text = resolvedProfile.formattedLocation(),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            IconButton(onClick = { onRemoveFavorite(resolvedProfile) }) {
                                                Icon(Icons.Filled.BookmarkRemove, contentDescription = "Remove favorite")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyTabMessage(message: String) {
    Text(
        text = message,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 24.dp)
    )
}
