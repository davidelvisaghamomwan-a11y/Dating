package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.PublicProfile
import com.example.model.ReportRecord
import com.example.ui.theme.ChampagneGold
import com.example.ui.theme.CoralRose
import com.example.ui.theme.EmeraldMint

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AdminDashboardScreen(
    reports: List<ReportRecord>,
    allProfiles: List<PublicProfile>,
    onBack: () -> Unit,
    onUpdateReportStatus: (reportId: String, status: String) -> Unit,
    onSetUserSuspended: (targetUid: String, suspended: Boolean) -> Unit,
    onOpenProfile: (PublicProfile) -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val profilesByUid = remember(allProfiles) { allProfiles.associateBy { it.userId } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Admin Moderation") },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("admin_back_button")
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
                .testTag("admin_dashboard_screen")
        ) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Reports (${reports.size})") }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Members (${allProfiles.size})") }
                )
            }

            Box(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 640.dp)
                        .align(Alignment.TopCenter),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (selectedTab == 0) {
                        items(reports, key = { it.id }) { report ->
                            val target = profilesByUid[report.reportedUid]
                            Card(
                                shape = RoundedCornerShape(18.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "${target?.displayName ?: report.reportedUid} (${report.sourceSurface})",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = report.status,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (report.status == "OPEN") CoralRose else EmeraldMint
                                        )
                                    }
                                    Text(text = "Reason: ${report.reason}", style = MaterialTheme.typography.bodyMedium)
                                    if (!report.details.isNullOrBlank()) {
                                        Text(
                                            text = report.details,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        OutlinedButton(onClick = { onUpdateReportStatus(report.id, "REVIEWED") }) {
                                            Text("Mark Reviewed")
                                        }
                                        OutlinedButton(onClick = { onUpdateReportStatus(report.id, "DISMISSED") }) {
                                            Text("Dismiss")
                                        }
                                        if (target != null) {
                                            Button(
                                                onClick = { onSetUserSuspended(target.userId, !target.suspended) },
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = if (target.suspended) EmeraldMint else MaterialTheme.colorScheme.error
                                                )
                                            ) {
                                                Text(if (target.suspended) "Reinstate" else "Suspend User")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        items(allProfiles, key = { it.userId }) { member ->
                            Card(
                                shape = RoundedCornerShape(18.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onOpenProfile(member) }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "${member.displayName}, ${member.age} ${member.flagEmoji()}",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "${member.formattedLocation()} • ${if (member.suspended) "SUSPENDED" else "Active"}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (member.suspended) MaterialTheme.colorScheme.error else ChampagneGold
                                        )
                                    }
                                    OutlinedButton(onClick = { onSetUserSuspended(member.userId, !member.suspended) }) {
                                        Text(if (member.suspended) "Reinstate" else "Suspend")
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
