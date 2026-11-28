package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.PhoneInTalk
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import com.example.R
import com.example.auth.GoogleAuthHelper
import com.example.model.BlockedUserItem
import com.example.model.PrivateUser
import com.example.model.PublicProfile
import com.example.model.VisibilityLevel
import com.example.notifications.KindredBackgroundService
import com.example.notifications.KindredNotificationHelper
import com.example.ui.theme.ChampagneGold
import com.example.ui.theme.EmeraldMint
import com.example.update.AppUpdateChecker
import com.example.update.UpdateCheckResult
import kotlinx.coroutines.launch

private val InstagramBlueLink = Color(0xFF0095F6)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    myProfile: PublicProfile?,
    privateUser: PrivateUser?,
    isBusy: Boolean,
    blockedUsers: List<BlockedUserItem> = emptyList(),
    isAdmin: Boolean = false,
    onBack: () -> Unit,
    onOpenEditProfile: () -> Unit = {},
    onOpenFollowersFollowing: () -> Unit = {},
    onOpenActivity: () -> Unit = {},
    onOpenAdminDashboard: () -> Unit = {},
    onUnblockUser: (String) -> Unit = {},
    onNotificationsGranted: () -> Unit = {},
    onBeforeSignOut: suspend () -> Unit = {},
    onToggleDiscoverable: (Boolean) -> Unit = {},
    onUpdatePrivacy: (
        (
            hideOnlineStatus: Boolean,
            photosVis: String,
            videosVis: String,
            infoVis: String
        ) -> Unit
    )? = null,
    onDeleteAccount: (() -> Unit)? = null,
    onStatusMessage: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { runCatching { CredentialManager.create(context) }.getOrNull() }

    var searchQuery by remember { mutableStateOf("") }

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

    var notificationsEnabled by remember {
        mutableStateOf(KindredNotificationHelper.hasNotificationPermission(context))
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        notificationsEnabled = granted
        if (granted) {
            onNotificationsGranted()
            onStatusMessage("Push notifications enabled")
        } else {
            onStatusMessage("Notification permission denied — enable in system settings")
        }
    }
    var bgServiceEnabled by remember { mutableStateOf(KindredBackgroundService.isEnabled(context)) }

    var showPrivacySheet by remember { mutableStateOf(false) }
    var showBlockedSheet by remember { mutableStateOf(false) }
    var showUpdatesSheet by remember { mutableStateOf(false) }
    var showDeleteAccountDialog by remember { mutableStateOf(false) }

    val query = searchQuery.trim().lowercase()
    fun matches(vararg keywords: String): Boolean {
        if (query.isEmpty()) return true
        return keywords.any { it.lowercase().contains(query) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.settings_title),
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
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
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .navigationBarsPadding()
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 620.dp)
                    .align(Alignment.TopCenter)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 28.dp)
            ) {
                // Instagram Settings Search Bar
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                        Box(modifier = Modifier.weight(1f)) {
                            if (searchQuery.isEmpty()) {
                                Text(
                                    text = "Search",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            BasicTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyMedium.copy(
                                    color = MaterialTheme.colorScheme.onSurface
                                ),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("settings_search_input")
                            )
                        }
                        if (searchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = { searchQuery = "" },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = "Clear search",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                // Section 1: Your account
                val showAccountSection = matches("account", "edit profile", "bio", "photos")
                if (showAccountSection) {
                    SettingsSectionHeader("Your account")
                    InstagramSettingsRow(
                        icon = Icons.Outlined.AccountCircle,
                        title = "Accounts Center & Edit profile",
                        subtitle = myProfile?.displayName?.takeIf { it.isNotBlank() } ?: "Name, bio, photos, location",
                        onClick = onOpenEditProfile,
                        testTag = "settings_row_edit_profile"
                    )
                    SettingsSectionDivider()
                }

                // Section 2: How you use Kindred
                val showUsageSection = matches("notifications", "alerts", "push", "followers", "following", "connections", "likes", "comments", "activity")
                if (showUsageSection) {
                    SettingsSectionHeader("How you use Kindred")
                    if (matches("notifications", "alerts", "push")) {
                        InstagramSettingsRow(
                            icon = Icons.Outlined.Notifications,
                            title = "Notifications",
                            trailingValue = if (notificationsEnabled) "On" else "Paused",
                            trailingValueColor = if (notificationsEnabled) EmeraldMint else MaterialTheme.colorScheme.onSurfaceVariant,
                            onClick = {
                                if (!notificationsEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    runCatching {
                                        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                        context.startActivity(intent)
                                    }
                                }
                            },
                            testTag = "settings_row_notifications"
                        )
                    }
                    if (matches("followers", "following", "connections")) {
                        InstagramSettingsRow(
                            icon = Icons.Outlined.Group,
                            title = "Followers & following",
                            onClick = onOpenFollowersFollowing,
                            testTag = "settings_row_friends"
                        )
                    }
                    if (matches("likes", "comments", "activity")) {
                        InstagramSettingsRow(
                            icon = Icons.Outlined.FavoriteBorder,
                            title = "Activity & notifications",
                            onClick = onOpenActivity,
                            testTag = "settings_row_activity"
                        )
                    }
                    SettingsSectionDivider()
                }

                // Section 3: Who can see your content
                val showPrivacySection = matches("privacy", "private", "online", "visibility", "blocked", "block", "discoverable")
                if (showPrivacySection) {
                    SettingsSectionHeader("Who can see your content")
                    if (matches("privacy", "private", "online", "visibility", "discoverable")) {
                        InstagramSettingsRow(
                            icon = Icons.Outlined.Lock,
                            title = "Account privacy & visibility",
                            trailingValue = if (discoverable) "Discoverable" else "Paused",
                            onClick = { showPrivacySheet = true },
                            testTag = "settings_row_privacy"
                        )
                    }
                    if (matches("blocked", "block")) {
                        InstagramSettingsRow(
                            icon = Icons.Outlined.Block,
                            title = "Blocked",
                            trailingValue = blockedUsers.size.toString(),
                            onClick = { showBlockedSheet = true },
                            testTag = "settings_row_blocked"
                        )
                    }
                    SettingsSectionDivider()
                }


                // Section 5: More info and support
                val showSupportSection = matches("background", "calls", "sync", "battery", "update", "updates", "github", "signing", "admin", "moderation")
                if (showSupportSection) {
                    SettingsSectionHeader("More info and support")
                    if (matches("background", "calls", "sync")) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 54.dp)
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.PhoneInTalk,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(24.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Background live calls & sync",
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Text(
                                    text = if (bgServiceEnabled) "Listening for incoming calls while minimized" else "Paused when app is closed",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = bgServiceEnabled,
                                onCheckedChange = { checked ->
                                    bgServiceEnabled = checked
                                    KindredBackgroundService.setEnabled(context, checked)
                                },
                                modifier = Modifier.testTag("settings_background_service_switch")
                            )
                        }
                    }
                    if (matches("battery", "background", "calls")) {
                        InstagramSettingsRow(
                            icon = Icons.Filled.BatterySaver,
                            title = "Allow unrestricted battery usage",
                            onClick = {
                                runCatching {
                                    context.startActivity(
                                        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                    )
                                }
                            },
                            testTag = "settings_battery_optimization_button"
                        )
                    }
                    if (matches("update", "updates", "github", "signing", "version")) {
                        InstagramSettingsRow(
                            icon = Icons.Outlined.SystemUpdate,
                            title = "App updates & signing",
                            trailingValue = AppUpdateChecker.currentVersionLabel(),
                            onClick = { showUpdatesSheet = true },
                            testTag = "settings_row_updates"
                        )
                    }
                    if (isAdmin && matches("admin", "moderation", "reports")) {
                        InstagramSettingsRow(
                            icon = Icons.Outlined.AdminPanelSettings,
                            title = "Admin moderation dashboard",
                            onClick = onOpenAdminDashboard,
                            testTag = "settings_row_admin"
                        )
                    }
                    SettingsSectionDivider()
                }

                // Section 6: Login
                if (matches("login", "log out", "sign out", "delete", "account")) {
                    SettingsSectionHeader("Login")
                    if (matches("login", "log out", "sign out")) {
                        Text(
                            text = "Log out ${myProfile?.displayName?.takeIf { it.isNotBlank() } ?: ""}".trim(),
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontWeight = FontWeight.Medium,
                                color = InstagramBlueLink
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    GoogleAuthHelper.signOut(
                                        credentialManager = credentialManager,
                                        scope = scope,
                                        onBeforeSignOut = onBeforeSignOut
                                    )
                                }
                                .padding(horizontal = 16.dp, vertical = 14.dp)
                                .testTag("settings_sign_out_button")
                        )
                    }
                    if (onDeleteAccount != null && matches("delete", "account", "login")) {
                        Text(
                            text = "Delete account",
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.error
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !isBusy) { showDeleteAccountDialog = true }
                                .padding(horizontal = 16.dp, vertical = 14.dp)
                                .testTag("settings_delete_account_button")
                        )
                    }
                }
            }
        }
    }

    // Modal Bottom Sheet 1: Account Privacy & Visibility
    if (showPrivacySheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showPrivacySheet = false },
            sheetState = sheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    .padding(bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Account privacy & visibility",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Discoverable profile", style = MaterialTheme.typography.titleSmall)
                        Text(
                            text = "Allow new people to discover your profile and posts",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = discoverable,
                        onCheckedChange = { discoverable = it }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Hide online status", style = MaterialTheme.typography.titleSmall)
                        Text(
                            text = "Hide your active dot & last seen from profiles",
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

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))

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

                Button(
                    onClick = {
                        onUpdatePrivacy?.invoke(
                            hideOnlineStatus,
                            photosVisibility,
                            videosVisibility,
                            infoVisibility
                        )
                        onToggleDiscoverable(discoverable)
                        showPrivacySheet = false
                    },
                    enabled = !isBusy,
                    colors = ButtonDefaults.buttonColors(containerColor = InstagramBlueLink, contentColor = Color.White),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("settings_save_privacy_button")
                ) {
                    Text("Save privacy settings", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }

    // Modal Bottom Sheet 3: Blocked Accounts
    if (showBlockedSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showBlockedSheet = false },
            sheetState = sheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Blocked accounts (${blockedUsers.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                if (blockedUsers.isEmpty()) {
                    Text(
                        text = "You haven't blocked anyone. Accounts you block won't appear in your feed, discovery, or messages.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    blockedUsers.forEach { blocked ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = blocked.displayName.ifBlank { "Kindred Member" },
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Blocked across feed, search & chat",
                                    style = MaterialTheme.typography.bodySmall,
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

    // Modal Bottom Sheet 4: App Updates & Signing
    if (showUpdatesSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showUpdatesSheet = false },
            sheetState = sheetState
        ) {
            AppUpdatesSheetContent()
        }
    }

    if (showDeleteAccountDialog && onDeleteAccount != null) {
        AlertDialog(
            onDismissRequest = { showDeleteAccountDialog = false },
            title = { Text("Delete Kindred Account?") },
            text = {
                Text("This permanently removes your profile, posts, connections, and account. This action cannot be undone.")
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

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge.copy(
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        ),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 6.dp)
    )
}

@Composable
private fun SettingsSectionDivider() {
    HorizontalDivider(
        thickness = 4.dp,
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.55f),
        modifier = Modifier.padding(top = 6.dp)
    )
}

@Composable
private fun InstagramSettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    trailingValue: String? = null,
    trailingValueColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    onClick: () -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 54.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(24.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.5.sp),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (!trailingValue.isNullOrBlank()) {
            Text(
                text = trailingValue,
                style = MaterialTheme.typography.bodyMedium,
                color = trailingValueColor,
                maxLines = 1
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
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
        VisibilityLevel.FOLLOWERS_ONLY to "Followers Only"
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
private fun AppUpdatesSheetContent() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var repoInput by remember { mutableStateOf(AppUpdateChecker.getConfiguredGitHubRepo(context)) }
    var aiStudioInput by remember { mutableStateOf(AppUpdateChecker.getConfiguredAiStudioUrl(context)) }
    var isChecking by remember { mutableStateOf(false) }
    var checkResult by remember { mutableStateOf<UpdateCheckResult?>(null) }
    val signingSha256 = remember(context) { AppUpdateChecker.getAppSigningSha256(context) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "App updates & signing",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Installed: ${AppUpdateChecker.currentVersionLabel()}",
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
                colors = ButtonDefaults.buttonColors(containerColor = InstagramBlueLink, contentColor = Color.White),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("check_updates_button")
            ) {
                if (isChecking) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Filled.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
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
                    Icon(Icons.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Open")
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
                            onClick = { AppUpdateChecker.openExternalUpdateUrl(context, downloadUrl) },
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
                Text("Up to date • ${res.checkedSource}", style = MaterialTheme.typography.bodySmall, color = EmeraldMint)
            }
            is UpdateCheckResult.NotConfigured -> {
                Text(res.message, style = MaterialTheme.typography.bodySmall, color = ChampagneGold)
            }
            is UpdateCheckResult.Error -> {
                Text(res.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            null -> Unit
        }

        Surface(
            shape = RoundedCornerShape(12.dp),
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
                    Icon(Icons.Filled.Security, contentDescription = null, tint = EmeraldMint, modifier = Modifier.size(16.dp))
                    Text("App Signing & Play Protect", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                }
                Text(
                    text = "SHA-256: $signingSha256",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("settings_signing_sha256_text")
                )
            }
        }
    }
}
