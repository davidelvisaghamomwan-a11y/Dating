package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import com.example.R
import com.example.auth.GoogleAuthHelper
import com.example.auth.PendingRegistrationPrefs
import com.example.model.KindredConstants
import com.example.ui.theme.ChampagneGold
import com.example.ui.theme.CoralRose
import com.example.ui.theme.EmeraldMint
import com.example.ui.theme.PlumObsidian

enum class AuthMode {
    SIGN_IN,
    SIGN_UP
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AuthScreen(
    onAuthSuccess: () -> Unit,
    enableAutoSignIn: Boolean = true
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    var authMode by remember { mutableStateOf(AuthMode.SIGN_IN) }
    var signUpDisplayName by remember { mutableStateOf("") }
    var signUpAgeText by remember { mutableStateOf("26") }
    var signUpGender by remember { mutableStateOf("Woman") }

    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    if (enableAutoSignIn) {
        LaunchedEffect(Unit) {
            GoogleAuthHelper.attemptAutoSignIn(
                context = context,
                credentialManager = credentialManager,
                onAuthSuccess = onAuthSuccess,
                onUnauthenticated = {},
                scope = scope
            )
        }
    }

    fun launchGoogleAuth() {
        errorMessage = null
        if (authMode == AuthMode.SIGN_UP) {
            val trimmedName = signUpDisplayName.trim()
            val parsedAge = signUpAgeText.toIntOrNull()
            if (trimmedName.length < 2) {
                errorMessage = "Enter a display name (at least 2 characters) to create your account."
                return
            }
            if (parsedAge == null || parsedAge !in 18..99) {
                errorMessage = "You must be 18 or older (18–99) to register on Kindred."
                return
            }
            PendingRegistrationPrefs.save(
                context = context,
                displayName = trimmedName,
                age = parsedAge,
                gender = signUpGender
            )
        }

        isLoading = true
        GoogleAuthHelper.onGoogleSignInClicked(
            context = context,
            credentialManager = credentialManager,
            onAuthSuccess = {
                isLoading = false
                onAuthSuccess()
            },
            onAuthError = { msg ->
                isLoading = false
                errorMessage = msg
            },
            onAuthCancelled = {
                isLoading = false
            },
            scope = scope
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PlumObsidian)
    ) {
        // Editorial full-bleed twilight hero header
        Image(
            painter = painterResource(id = R.drawable.img_auth_hero),
            contentDescription = stringResource(R.string.cd_auth_hero),
            modifier = Modifier
                .fillMaxWidth()
                .height(340.dp),
            contentScale = ContentScale.Crop
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(340.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            PlumObsidian.copy(alpha = 0.25f),
                            PlumObsidian.copy(alpha = 0.62f),
                            PlumObsidian
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(112.dp))

            Column(
                modifier = Modifier.widthIn(max = 520.dp),
                horizontalAlignment = Alignment.Start
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_kindred_emblem),
                        contentDescription = stringResource(R.string.cd_app_logo),
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(16.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Column {
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.displayLarge,
                            color = Color.White
                        )
                        Text(
                            text = "Real people. Real proximity.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = ChampagneGold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = stringResource(R.string.tagline),
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White.copy(alpha = 0.94f)
                )

                Spacer(modifier = Modifier.height(22.dp))

                // Modern Login / Sign-Up Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(26.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.White.copy(alpha = 0.08f)
                    ),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Segmented Mode Toggle (Sign In / Create Account)
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Color.Black.copy(alpha = 0.28f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(4.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                AuthModeTabButton(
                                    label = "Sign In",
                                    selected = authMode == AuthMode.SIGN_IN,
                                    onClick = {
                                        errorMessage = null
                                        authMode = AuthMode.SIGN_IN
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("auth_mode_sign_in")
                                )
                                AuthModeTabButton(
                                    label = "Create Account",
                                    selected = authMode == AuthMode.SIGN_UP,
                                    onClick = {
                                        errorMessage = null
                                        authMode = AuthMode.SIGN_UP
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("auth_mode_sign_up")
                                )
                            }
                        }

                        if (authMode == AuthMode.SIGN_IN) {
                            Text(
                                text = "Welcome back",
                                style = MaterialTheme.typography.titleLarge,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Sign in securely with Firebase Authentication & Google Identity to sync matches, live calls, and verified GPS proximity.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.78f)
                            )
                        } else {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.PersonAdd,
                                    contentDescription = null,
                                    tint = CoralRose,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "Create your Kindred account",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                text = "Set your profile basics now, then complete instant Firebase registration with your Google account.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.76f)
                            )
                        }

                        AnimatedVisibility(visible = authMode == AuthMode.SIGN_UP) {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                OutlinedTextField(
                                    value = signUpDisplayName,
                                    onValueChange = { signUpDisplayName = it.take(40) },
                                    label = { Text("Display Name") },
                                    placeholder = { Text("How matches see you") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(
                                        capitalization = KeyboardCapitalization.Words
                                    ),
                                    colors = authTextFieldColors(),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("auth_signup_name_input")
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    OutlinedTextField(
                                        value = signUpAgeText,
                                        onValueChange = { input ->
                                            signUpAgeText = input.filter { it.isDigit() }.take(2)
                                        },
                                        label = { Text("Age (18+)") },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        colors = authTextFieldColors(),
                                        modifier = Modifier
                                            .weight(0.42f)
                                            .testTag("auth_signup_age_input")
                                    )
                                    Column(
                                        modifier = Modifier.weight(0.58f),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = "I identify as",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = Color.White.copy(alpha = 0.8f)
                                        )
                                        FlowRow(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            KindredConstants.GENDER_OPTIONS.forEach { option ->
                                                FilterChip(
                                                    selected = signUpGender == option,
                                                    onClick = { signUpGender = option },
                                                    label = { Text(option) },
                                                    colors = FilterChipDefaults.filterChipColors(
                                                        selectedContainerColor = CoralRose,
                                                        selectedLabelColor = Color.White,
                                                        labelColor = Color.White.copy(alpha = 0.85f),
                                                        containerColor = Color.White.copy(alpha = 0.08f)
                                                    ),
                                                    modifier = Modifier.testTag("auth_signup_gender_$option")
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        if (!errorMessage.isNullOrBlank()) {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.error.copy(alpha = 0.22f),
                                border = BorderStroke(1.dp, Color(0xFFFFB4AB).copy(alpha = 0.4f))
                            ) {
                                Text(
                                    text = errorMessage!!,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color(0xFFFFDAD6),
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }

                        Button(
                            onClick = { launchGoogleAuth() },
                            enabled = !isLoading,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp)
                                .testTag(
                                    if (authMode == AuthMode.SIGN_UP) {
                                        "google_sign_up_button"
                                    } else {
                                        "google_sign_in_button"
                                    }
                                ),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = CoralRose,
                                contentColor = Color.White
                            )
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(22.dp),
                                    color = Color.White,
                                    strokeWidth = 2.5.dp
                                )
                            } else {
                                Text(
                                    text = if (authMode == AuthMode.SIGN_UP) {
                                        stringResource(R.string.create_account_with_google)
                                    } else {
                                        stringResource(R.string.sign_in_with_google)
                                    },
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (authMode == AuthMode.SIGN_IN) {
                                    "New to Kindred?"
                                } else {
                                    "Already have an account?"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.72f)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            TextButton(
                                onClick = {
                                    errorMessage = null
                                    authMode = if (authMode == AuthMode.SIGN_IN) {
                                        AuthMode.SIGN_UP
                                    } else {
                                        AuthMode.SIGN_IN
                                    }
                                },
                                modifier = Modifier.testTag("auth_switch_mode_text_button")
                            ) {
                                Text(
                                    text = if (authMode == AuthMode.SIGN_IN) "Create Account" else "Sign In",
                                    color = ChampagneGold,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(22.dp))

                AuthFeatureHighlight(
                    icon = Icons.Filled.VerifiedUser,
                    tint = EmeraldMint,
                    title = "Live Camera & GPS Verification",
                    subtitle = "On-device face detection plus verified GPS country & city badges."
                )
                Spacer(modifier = Modifier.height(14.dp))
                AuthFeatureHighlight(
                    icon = Icons.Filled.LocationOn,
                    tint = ChampagneGold,
                    title = "Real People, Real Proximity",
                    subtitle = "No sample bots — real GeoHash distance matching around you."
                )
                Spacer(modifier = Modifier.height(14.dp))
                AuthFeatureHighlight(
                    icon = Icons.Filled.ChatBubble,
                    tint = CoralRose,
                    title = "Live Calls, Photos & Direct Search",
                    subtitle = "Voice & video calls with ringtone alerts, read receipts, and member search."
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Protected by Firebase Authentication, Google Identity & zero-trust Firestore rules.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.6f)
                )
            }
        }
    }
}

@Composable
private fun AuthModeTabButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (selected) CoralRose else Color.Transparent,
        modifier = modifier.height(48.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) Color.White else Color.White.copy(alpha = 0.72f)
            )
        }
    }
}

@Composable
private fun authTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
    focusedBorderColor = CoralRose,
    unfocusedBorderColor = Color.White.copy(alpha = 0.28f),
    focusedLabelColor = ChampagneGold,
    unfocusedLabelColor = Color.White.copy(alpha = 0.72f),
    focusedPlaceholderColor = Color.White.copy(alpha = 0.45f),
    unfocusedPlaceholderColor = Color.White.copy(alpha = 0.45f),
    cursorColor = CoralRose
)

@Composable
private fun AuthFeatureHighlight(
    icon: ImageVector,
    tint: Color,
    title: String,
    subtitle: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Surface(
            modifier = Modifier.size(40.dp),
            shape = CircleShape,
            color = tint.copy(alpha = 0.16f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.72f)
            )
        }
    }
}
