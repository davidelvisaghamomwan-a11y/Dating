package com.example.ui.screens

import android.util.Patterns
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.Image
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import com.example.R
import com.example.auth.GoogleAuthHelper
import com.example.auth.PendingRegistrationPrefs
import com.example.model.KindredConstants
import com.example.ui.theme.GrandHotelFamily

enum class AuthMode {
    SIGN_IN,
    SIGN_UP
}

private val IgLoginBlue = Color(0xFF0095F6)
private val IgLoginBlueSoft = Color(0xFF4CB5F9)
private val IgInputBackground = Color(0xFFFAFAFA)
private val IgInputBorder = Color(0xFFDBDBDB)
private val IgTextPrimary = Color(0xFF262626)
private val IgTextMuted = Color(0xFF8E8E8E)

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
    var emailOrIdentifier by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    var signUpDisplayName by remember { mutableStateOf("") }
    var signUpAgeText by remember { mutableStateOf("") }
    var signUpGender by remember { mutableStateOf("Woman") }

    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var infoMessage by remember { mutableStateOf<String?>(null) }

    if (enableAutoSignIn) {
        LaunchedEffect(Unit) {
            if (!GoogleAuthHelper.isAutoSignInSuppressed()) {
                GoogleAuthHelper.attemptAutoSignIn(
                    context = context,
                    credentialManager = credentialManager,
                    onAuthSuccess = onAuthSuccess,
                    onUnauthenticated = {},
                    scope = scope
                )
            }
        }
    }

    fun launchGoogleAuth() {
        errorMessage = null
        infoMessage = null
        if (authMode == AuthMode.SIGN_UP && signUpDisplayName.trim().length >= 2) {
            val parsedAge = signUpAgeText.trim().toIntOrNull()?.takeIf { it in 18..99 }
            PendingRegistrationPrefs.save(
                context = context,
                displayName = signUpDisplayName.trim(),
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

    fun handlePrimaryEmailAction() {
        errorMessage = null
        infoMessage = null
        val cleanEmail = emailOrIdentifier.trim()
        if (!Patterns.EMAIL_ADDRESS.matcher(cleanEmail).matches()) {
            errorMessage = "Please enter a valid email address, or tap Continue with Google below."
            return
        }
        if (password.length < 6) {
            errorMessage = "Password must be at least 6 characters."
            return
        }

        if (authMode == AuthMode.SIGN_UP) {
            val trimmedName = signUpDisplayName.trim()
            val trimmedAge = signUpAgeText.trim()
            val parsedAge = if (trimmedAge.isNotEmpty()) trimmedAge.toIntOrNull() else null
            if (trimmedName.length < 2) {
                errorMessage = "Enter a display name (at least 2 characters)."
                return
            }
            if (trimmedAge.isNotEmpty() && (parsedAge == null || parsedAge !in 18..99)) {
                errorMessage = "You must be 18 or older (18–99) to join Kindred."
                return
            }
            PendingRegistrationPrefs.save(
                context = context,
                displayName = trimmedName,
                age = parsedAge,
                gender = signUpGender
            )
            isLoading = true
            GoogleAuthHelper.signUpWithEmail(
                email = cleanEmail,
                password = password,
                displayName = trimmedName,
                onAuthSuccess = {
                    isLoading = false
                    onAuthSuccess()
                },
                onAuthError = { msg ->
                    isLoading = false
                    errorMessage = msg
                },
                scope = scope
            )
        } else {
            isLoading = true
            GoogleAuthHelper.signInWithEmail(
                email = cleanEmail,
                password = password,
                onAuthSuccess = {
                    isLoading = false
                    onAuthSuccess()
                },
                onAuthError = { msg ->
                    isLoading = false
                    errorMessage = msg
                },
                scope = scope
            )
        }
    }

    val fieldsReady = emailOrIdentifier.isNotBlank() && password.length >= 6

    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) {
        Image(
            painter = painterResource(com.example.R.drawable.app_startup_wallpaper),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        // Dark translucent overlay for enhanced readability of inputs and buttons
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.35f))
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
        ) {
        // Main Centered Form Area
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 420.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 28.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Classic Script Wordmark matching Instagram Login aesthetic
                Text(
                    text = stringResource(R.string.app_name),
                    fontFamily = GrandHotelFamily,
                    fontSize = 52.sp,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.testTag("auth_brand_wordmark")
                )

                Spacer(modifier = Modifier.height(28.dp))

                // Sign-Up Extra Fields (only visible when switching to Sign Up)
                AnimatedVisibility(visible = authMode == AuthMode.SIGN_UP) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.padding(bottom = 10.dp)
                    ) {
                        OutlinedTextField(
                            value = signUpDisplayName,
                            onValueChange = { signUpDisplayName = it.take(40) },
                            placeholder = {
                                Text("Full name or username", color = IgTextMuted, fontSize = 14.sp)
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(6.dp),
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Words,
                                imeAction = ImeAction.Next
                            ),
                            colors = instagramInputColors(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("auth_signup_name_input")
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = signUpAgeText,
                                onValueChange = { input ->
                                    signUpAgeText = input.filter { it.isDigit() }.take(2)
                                },
                                placeholder = {
                                    Text("Age (18+)", color = IgTextMuted, fontSize = 14.sp)
                                },
                                singleLine = true,
                                shape = RoundedCornerShape(6.dp),
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Number,
                                    imeAction = ImeAction.Next
                                ),
                                colors = instagramInputColors(),
                                modifier = Modifier
                                    .weight(0.38f)
                                    .testTag("auth_signup_age_input")
                            )

                            FlowRow(
                                modifier = Modifier.weight(0.62f),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                KindredConstants.GENDER_OPTIONS.forEach { option ->
                                    FilterChip(
                                        selected = signUpGender == option,
                                        onClick = { signUpGender = option },
                                        label = { Text(option, fontSize = 12.sp) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = IgLoginBlue,
                                            selectedLabelColor = Color.White,
                                            labelColor = IgTextPrimary,
                                            containerColor = IgInputBackground
                                        ),
                                        modifier = Modifier.testTag("auth_signup_gender_$option")
                                    )
                                }
                            }
                        }
                    }
                }

                // Email / Identifier Field
                OutlinedTextField(
                    value = emailOrIdentifier,
                    onValueChange = { emailOrIdentifier = it },
                    placeholder = {
                        Text(
                            text = "Phone number, username or email",
                            color = IgTextMuted,
                            fontSize = 14.sp
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(6.dp),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Next
                    ),
                    colors = instagramInputColors(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("auth_email_input")
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Password Field with Visibility Toggle
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    placeholder = {
                        Text(
                            text = "Password",
                            color = IgTextMuted,
                            fontSize = 14.sp
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(6.dp),
                    visualTransformation = if (passwordVisible) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                                contentDescription = if (passwordVisible) "Hide password" else "Show password",
                                tint = IgTextMuted
                            )
                        }
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done
                    ),
                    colors = instagramInputColors(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("auth_password_input")
                )

                // Forgot password? link (right-aligned)
                if (authMode == AuthMode.SIGN_IN) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        contentAlignment = Alignment.CenterEnd
                    ) {
                        Text(
                            text = "Forgot password?",
                            color = IgLoginBlue,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .clickable(enabled = !isLoading) {
                                    errorMessage = null
                                    infoMessage = null
                                    GoogleAuthHelper.sendPasswordReset(
                                        email = emailOrIdentifier,
                                        onSuccess = { msg -> infoMessage = msg },
                                        onError = { msg -> errorMessage = msg },
                                        scope = scope
                                    )
                                }
                                .padding(vertical = 4.dp)
                                .testTag("auth_forgot_password_button")
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Error or Info banner
                if (!errorMessage.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFFFEBEE),
                        border = BorderStroke(1.dp, Color(0xFFEF9A9A))
                    ) {
                        Text(
                            text = errorMessage!!,
                            color = Color(0xFFB71C1C),
                            fontSize = 13.sp,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                if (!infoMessage.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFE3F2FD),
                        border = BorderStroke(1.dp, Color(0xFF90CAF9))
                    ) {
                        Text(
                            text = infoMessage!!,
                            color = Color(0xFF0D47A1),
                            fontSize = 13.sp,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Primary Blue Log In / Sign Up Button
                Button(
                    onClick = { handlePrimaryEmailAction() },
                    enabled = !isLoading,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (fieldsReady) IgLoginBlue else IgLoginBlueSoft,
                        contentColor = Color.White,
                        disabledContainerColor = IgLoginBlueSoft,
                        disabledContentColor = Color.White
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("auth_login_button")
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            text = if (authMode == AuthMode.SIGN_UP) "Sign Up" else "Log In",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(22.dp))

                // OR Divider
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    HorizontalDivider(
                        modifier = Modifier.weight(1f),
                        color = IgInputBorder,
                        thickness = 1.dp
                    )
                    Text(
                        text = "OR",
                        color = IgTextMuted,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    HorizontalDivider(
                        modifier = Modifier.weight(1f),
                        color = IgInputBorder,
                        thickness = 1.dp
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Continue with Google CTA (matching "Continue as ..." social login style)
                TextButton(
                    onClick = { launchGoogleAuth() },
                    enabled = !isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag(
                            if (authMode == AuthMode.SIGN_UP) {
                                "google_sign_up_button"
                            } else {
                                "google_sign_in_button"
                            }
                        )
                ) {
                    Icon(
                        imageVector = Icons.Filled.AccountCircle,
                        contentDescription = null,
                        tint = IgLoginBlue,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (authMode == AuthMode.SIGN_UP) {
                            stringResource(R.string.create_account_with_google)
                        } else {
                            stringResource(R.string.sign_in_with_google)
                        },
                        color = IgLoginBlue,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // Bottom Footer Bar pinned at the bottom: "Don't have an account? Sign Up."
        HorizontalDivider(color = IgInputBorder, thickness = 0.8.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (authMode == AuthMode.SIGN_IN) {
                    "Don't have an account? "
                } else {
                    "Already have an account? "
                },
                color = IgTextMuted,
                fontSize = 13.sp
            )
            Text(
                text = if (authMode == AuthMode.SIGN_IN) "Sign Up." else "Log In.",
                color = IgLoginBlue,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clickable(enabled = !isLoading) {
                        errorMessage = null
                        infoMessage = null
                        authMode = if (authMode == AuthMode.SIGN_IN) {
                            AuthMode.SIGN_UP
                        } else {
                            AuthMode.SIGN_IN
                        }
                    }
                    .padding(vertical = 8.dp, horizontal = 4.dp)
                    .testTag("auth_switch_mode_text_button")
            )
        }
    }
}
}

@Composable
private fun instagramInputColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = IgTextPrimary,
    unfocusedTextColor = IgTextPrimary,
    focusedContainerColor = IgInputBackground,
    unfocusedContainerColor = IgInputBackground,
    focusedBorderColor = Color(0xFFA8A8A8),
    unfocusedBorderColor = IgInputBorder,
    cursorColor = IgLoginBlue
)
