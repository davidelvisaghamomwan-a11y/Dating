package com.example.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.FriendRequestRecord
import com.example.model.ProfileLikeRecord
import com.example.model.PublicProfile
import com.example.ui.theme.CoralRose

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimelineNotificationsSheet(
    friendRequests: List<FriendRequestRecord>,
    profileLikes: List<ProfileLikeRecord>,
    profilesByUid: Map<String, PublicProfile>,
    onAcceptRequest: (String) -> Unit,
    onRejectRequest: (String) -> Unit,
    onOpenProfile: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = null,
        modifier = Modifier.testTag("timeline_notifications_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Activity & Notifications",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Close")
                }
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                modifier = Modifier.padding(vertical = 8.dp)
            )

            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(380.dp)
            ) {
                if (friendRequests.isEmpty() && profileLikes.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "You're all caught up! No new notifications.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                if (friendRequests.isNotEmpty()) {
                    item {
                        Text(
                            text = "Friend Requests (${friendRequests.size})",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = CoralRose
                        )
                    }
                    items(friendRequests, key = { it.id }) { req ->
                        val sender = profilesByUid[req.fromUid]
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpenProfile(req.fromUid) },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                ProfileAvatarImage(
                                    photoDataUrl = sender?.photoDataUrl,
                                    displayName = sender?.displayName ?: "User",
                                    seed = req.fromUid,
                                    modifier = Modifier.size(40.dp),
                                    shape = CircleShape,
                                    initialsFontSizeSp = 15
                                )
                                Column {
                                    Text(
                                        text = sender?.displayName ?: "Someone",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "sent you a friend request",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Button(
                                    onClick = { onAcceptRequest(req.fromUid) },
                                    colors = ButtonDefaults.buttonColors(containerColor = CoralRose)
                                ) {
                                    Text("Confirm", style = MaterialTheme.typography.labelSmall)
                                }
                                OutlinedButton(onClick = { onRejectRequest(req.fromUid) }) {
                                    Text("Delete", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }

                if (profileLikes.isNotEmpty()) {
                    item {
                        Text(
                            text = "Recent Likes (${profileLikes.size})",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = CoralRose
                        )
                    }
                    items(profileLikes, key = { it.id }) { like ->
                        val liker = profilesByUid[like.fromUid]
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpenProfile(like.fromUid) },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            ProfileAvatarImage(
                                photoDataUrl = liker?.photoDataUrl,
                                displayName = liker?.displayName ?: "Member",
                                seed = like.fromUid,
                                modifier = Modifier.size(40.dp),
                                shape = CircleShape,
                                initialsFontSizeSp = 15
                            )
                            Column {
                                Text(
                                    text = "${liker?.displayName ?: "Someone"} liked your profile",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Tap to view their profile",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
