package com.example.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.auth.PendingRegistrationPrefs
import com.example.model.KindredConstants
import com.example.model.PublicProfile
import com.example.ui.components.CountryAndCitySelector
import com.example.ui.theme.CoralRose
import com.example.ui.theme.EmeraldMint
import com.example.ui.theme.PlumObsidian
import com.example.util.DobUtils
import com.example.util.DobValidationResult
import com.example.util.LocationAndGeoHelper
import com.example.util.ResolvedDeviceLocation
import com.example.util.VerificationAnalyzer
import kotlinx.coroutines.launch

/**
 * Mandatory profile setup gate shown after registration or sign-in when a user has not yet
 * completed their profile details & exact Date of Birth (DD-MM-YYYY).
 *
 * Strictly never prefills Day, Month, Year, or Age.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ProfileSetupScreen(
    existingProfile: PublicProfile?,
    defaultDisplayName: String?,
    latestLocation: ResolvedDeviceLocation?,
    isBusy: Boolean,
    onSaveSetup: (
        displayName: String,
        age: Int,
        dateOfBirth: String,
        gender: String,
        bio: String,
        cityLabel: String,
        country: String,
        latitude: Double,
        longitude: Double,
        interests: List<String>,
        profilePhotos: List<String>
    ) -> Unit,
    onSignOut: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pendingDraft = remember(existingProfile) {
        if (existingProfile == null) {
            PendingRegistrationPrefs.get(context)
        } else {
            null
        }
    }

    val parsedExistingDob = remember(existingProfile?.dateOfBirth) {
        DobUtils.parseDob(existingProfile?.dateOfBirth)
    }

    var fullName by remember(existingProfile?.displayName, defaultDisplayName, pendingDraft) {
        mutableStateOf(
            existingProfile?.displayName?.takeIf { it.isNotBlank() && it != "Kindred Member" }
                ?: pendingDraft?.displayName?.takeIf { it.isNotBlank() }
                ?: defaultDisplayName?.takeIf { it.isNotBlank() && it != "Kindred Member" }
                ?: ""
        )
    }

    // Strict empty initial DOB dropdowns (never prefilled for new registrations)
    var selectedDay by remember(parsedExistingDob) { mutableStateOf<Int?>(parsedExistingDob?.first) }
    var selectedMonth by remember(parsedExistingDob) { mutableStateOf<Int?>(parsedExistingDob?.second) }
    var selectedYear by remember(parsedExistingDob) { mutableStateOf<Int?>(parsedExistingDob?.third) }

    var dayExpanded by remember { mutableStateOf(false) }
    var monthExpanded by remember { mutableStateOf(false) }
    var yearExpanded by remember { mutableStateOf(false) }

    var selectedGender by remember(existingProfile?.gender, pendingDraft) {
        mutableStateOf(
            existingProfile?.gender?.takeIf { it in KindredConstants.GENDER_OPTIONS }
                ?: pendingDraft?.gender?.takeIf { it in KindredConstants.GENDER_OPTIONS }
                ?: KindredConstants.GENDER_OPTIONS.first()
        )
    }
    var bio by remember(existingProfile?.bio) { mutableStateOf(existingProfile?.bio.orEmpty()) }
    var country by remember(existingProfile?.country, latestLocation?.countryName) {
        mutableStateOf(
            existingProfile?.country?.takeIf { it.isNotBlank() }
                ?: latestLocation?.countryName?.takeIf { it.isNotBlank() }
                ?: KindredConstants.COUNTRY_OPTIONS.first()
        )
    }
    var city by remember(existingProfile?.cityLabel, latestLocation?.cityLabel) {
        mutableStateOf(
            existingProfile?.cityLabel?.takeIf { it.isNotBlank() }
                ?: latestLocation?.cityLabel?.takeIf { it.isNotBlank() }
                ?: ""
        )
    }
    var latitude by remember(existingProfile?.coarseLatitude, latestLocation?.exactLatitude) {
        mutableStateOf(latestLocation?.exactLatitude ?: existingProfile?.coarseLatitude ?: 37.7749)
    }
    var longitude by remember(existingProfile?.coarseLongitude, latestLocation?.exactLongitude) {
        mutableStateOf(latestLocation?.exactLongitude ?: existingProfile?.coarseLongitude ?: -122.4194)
    }
    var isFetchingGps by remember { mutableStateOf(false) }

    LaunchedEffect(latestLocation) {
        val loc = latestLocation ?: return@LaunchedEffect
        if (loc.isLiveGps) {
            latitude = loc.exactLatitude
            longitude = loc.exactLongitude
            if (city.isBlank() && loc.cityLabel.isNotBlank()) {
                city = loc.cityLabel
            }
            if (loc.countryName.isNotBlank()) {
                country = loc.countryName
            }
        }
    }

    val selectedInterests = remember(existingProfile?.interests) {
        mutableStateListOf<String>().apply {
            addAll(existingProfile?.interests.orEmpty().take(8))
        }
    }
    val uploadedPhotos = remember(existingProfile?.photoDataUrl, existingProfile?.profilePhotos) {
        mutableStateListOf<String>().apply {
            addAll(existingProfile?.allProfilePhotos().orEmpty().take(KindredConstants.MAX_PROFILE_PHOTOS))
        }
    }
    var validationError by remember { mutableStateOf<String?>(null) }

    val dobValidation = remember(selectedDay, selectedMonth, selectedYear) {
        DobUtils.validateDob(selectedDay, selectedMonth, selectedYear)
    }
    val validDob = dobValidation as? DobValidationResult.Valid
    val invalidDob = dobValidation as? DobValidationResult.Invalid

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(KindredConstants.MAX_PROFILE_PHOTOS)
    ) { uris ->
        if (uris.isNotEmpty()) {
            scope.launch {
                uris.forEach { uri ->
                    if (uploadedPhotos.size < KindredConstants.MAX_PROFILE_PHOTOS) {
                        val encoded = VerificationAnalyzer.compressUriToDataUrl(context, uri)
                        if (!encoded.isNullOrBlank() && encoded !in uploadedPhotos) {
                            uploadedPhotos.add(encoded)
                        }
                    }
                }
            }
        }
    }

    fun fetchGps() {
        isFetchingGps = true
        scope.launch {
            val resolved = LocationAndGeoHelper.fetchCurrentLocation(context)
            isFetchingGps = false
            if (resolved != null) {
                latitude = resolved.exactLatitude
                longitude = resolved.exactLongitude
                if (resolved.cityLabel.isNotBlank()) city = resolved.cityLabel
                if (resolved.countryName.isNotBlank()) country = resolved.countryName
            }
        }
    }

    val locationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val granted = grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true ||
            grants[Manifest.permission.ACCESS_FINE_LOCATION] == true
        if (granted) fetchGps()
    }

    val yearOptions = remember { DobUtils.yearOptions() }
    val validDayCount = remember(selectedMonth, selectedYear) {
        if (selectedMonth != null && selectedYear != null) {
            DobUtils.daysInMonth(selectedMonth!!, selectedYear!!)
        } else {
            31
        }
    }
    val dayOptions = remember(validDayCount) { (1..validDayCount).toList() }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .testTag("profile_setup_screen"),
        contentWindowInsets = WindowInsets.safeDrawing,
        bottomBar = {
            Surface(
                tonalElevation = 6.dp,
                shadowElevation = 8.dp,
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .imePadding()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (!validationError.isNullOrBlank()) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("setup_validation_error")
                        ) {
                            Text(
                                text = validationError!!,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }

                    Button(
                        onClick = {
                            val trimmedName = fullName.trim()
                            val trimmedBio = bio.trim()
                            val trimmedCity = city.trim()
                            val trimmedCountry = country.trim()
                            when {
                                trimmedName.length < 2 -> {
                                    validationError = "Please enter your full name (at least 2 characters)."
                                }
                                validDob == null -> {
                                    validationError = invalidDob?.message
                                        ?: "Please select your Day, Month, and Year of birth (DD-MM-YYYY)."
                                }
                                trimmedBio.length < 10 -> {
                                    validationError = "Write a short bio (at least 10 characters) so members get to know you."
                                }
                                trimmedCountry.isBlank() || trimmedCity.isBlank() -> {
                                    validationError = "Please select your country and city."
                                }
                                else -> {
                                    validationError = null
                                    PendingRegistrationPrefs.clear(context)
                                    onSaveSetup(
                                        trimmedName,
                                        validDob.age,
                                        validDob.dobFormatted,
                                        selectedGender,
                                        trimmedBio,
                                        trimmedCity,
                                        trimmedCountry,
                                        latitude,
                                        longitude,
                                        selectedInterests.toList(),
                                        uploadedPhotos.toList()
                                    )
                                }
                            }
                        },
                        enabled = !isBusy,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CoralRose,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .testTag("setup_save_profile_button")
                    ) {
                        if (isBusy) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                        }
                        Text(
                            text = "Save Profile & Continue",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Gradient welcome header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(PlumObsidian, CoralRose.copy(alpha = 0.85f))
                        )
                    )
                    .padding(horizontal = 20.dp, vertical = 22.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Complete Your Profile",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Tell the Kindred community who you are before joining the feed.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.9f)
                        )
                    }
                    TextButton(
                        onClick = onSignOut,
                        colors = ButtonDefaults.textButtonColors(contentColor = Color.White),
                        modifier = Modifier.testTag("setup_sign_out_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Logout,
                            contentDescription = "Sign out",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Sign Out")
                    }
                }
            }

            Column(
                modifier = Modifier
                    .widthIn(max = 600.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Full Name
                OutlinedTextField(
                    value = fullName,
                    onValueChange = { fullName = it.take(40) },
                    label = { Text("Full Name *") },
                    placeholder = { Text("Enter your name") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("setup_full_name_input")
                )

                // Date of Birth (3 separate empty dropdowns in DD-MM-YYYY order)
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Filled.Cake,
                                    contentDescription = "Date of Birth",
                                    tint = CoralRose,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Date of Birth (DD-MM-YYYY) *",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (validDob != null) {
                                    EmeraldMint.copy(alpha = 0.14f)
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                }
                            ) {
                                Text(
                                    text = if (validDob != null) {
                                        "Age: ${validDob.age}"
                                    } else {
                                        "Age: --"
                                    },
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = if (validDob != null) EmeraldMint else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                        .testTag("setup_computed_age_badge")
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // DAY (DD)
                            ExposedDropdownMenuBox(
                                expanded = dayExpanded,
                                onExpandedChange = { dayExpanded = !dayExpanded },
                                modifier = Modifier.weight(0.95f)
                            ) {
                                OutlinedTextField(
                                    value = selectedDay?.toString()?.padStart(2, '0') ?: "",
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Day") },
                                    placeholder = { Text("DD") },
                                    trailingIcon = {
                                        Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Select Day")
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                        .fillMaxWidth()
                                        .testTag("setup_dob_day_dropdown")
                                )
                                ExposedDropdownMenu(
                                    expanded = dayExpanded,
                                    onDismissRequest = { dayExpanded = false }
                                ) {
                                    dayOptions.forEach { d ->
                                        DropdownMenuItem(
                                            text = { Text(d.toString().padStart(2, '0')) },
                                            onClick = {
                                                selectedDay = d
                                                dayExpanded = false
                                            },
                                            modifier = Modifier.testTag("dob_day_option_$d")
                                        )
                                    }
                                }
                            }

                            // MONTH (MM)
                            ExposedDropdownMenuBox(
                                expanded = monthExpanded,
                                onExpandedChange = { monthExpanded = !monthExpanded },
                                modifier = Modifier.weight(1.1f)
                            ) {
                                val monthLabel = selectedMonth?.let { m ->
                                    DobUtils.MONTH_OPTIONS.firstOrNull { it.number == m }?.label
                                        ?: m.toString().padStart(2, '0')
                                } ?: ""
                                OutlinedTextField(
                                    value = monthLabel,
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Month") },
                                    placeholder = { Text("MM") },
                                    trailingIcon = {
                                        Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Select Month")
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                        .fillMaxWidth()
                                        .testTag("setup_dob_month_dropdown")
                                )
                                ExposedDropdownMenu(
                                    expanded = monthExpanded,
                                    onDismissRequest = { monthExpanded = false }
                                ) {
                                    DobUtils.MONTH_OPTIONS.forEach { option ->
                                        DropdownMenuItem(
                                            text = { Text(option.label) },
                                            onClick = {
                                                selectedMonth = option.number
                                                val currentDay = selectedDay
                                                if (currentDay != null) {
                                                    val maxD = DobUtils.daysInMonth(option.number, selectedYear ?: 2000)
                                                    if (currentDay > maxD) selectedDay = maxD
                                                }
                                                monthExpanded = false
                                            },
                                            modifier = Modifier.testTag("dob_month_option_${option.number}")
                                        )
                                    }
                                }
                            }

                            // YEAR (YYYY)
                            ExposedDropdownMenuBox(
                                expanded = yearExpanded,
                                onExpandedChange = { yearExpanded = !yearExpanded },
                                modifier = Modifier.weight(1.15f)
                            ) {
                                OutlinedTextField(
                                    value = selectedYear?.toString() ?: "",
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Year") },
                                    placeholder = { Text("YYYY") },
                                    trailingIcon = {
                                        Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Select Year")
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                        .fillMaxWidth()
                                        .testTag("setup_dob_year_dropdown")
                                )
                                ExposedDropdownMenu(
                                    expanded = yearExpanded,
                                    onDismissRequest = { yearExpanded = false }
                                ) {
                                    yearOptions.forEach { y ->
                                        DropdownMenuItem(
                                            text = { Text(y.toString()) },
                                            onClick = {
                                                selectedYear = y
                                                val m = selectedMonth
                                                val d = selectedDay
                                                if (m != null && d != null) {
                                                    val maxD = DobUtils.daysInMonth(m, y)
                                                    if (d > maxD) selectedDay = maxD
                                                }
                                                yearExpanded = false
                                            },
                                            modifier = Modifier.testTag("dob_year_option_$y")
                                        )
                                    }
                                }
                            }
                        }

                        if (invalidDob != null && selectedDay != null && selectedMonth != null && selectedYear != null) {
                            Text(
                                text = invalidDob.message,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.testTag("setup_dob_error_label")
                            )
                        }
                    }
                }

                // Gender Selection
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "I identify as *",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        KindredConstants.GENDER_OPTIONS.forEach { option ->
                            FilterChip(
                                selected = selectedGender == option,
                                onClick = { selectedGender = option },
                                label = { Text(option) },
                                leadingIcon = if (selectedGender == option) {
                                    {
                                        Icon(
                                            imageVector = Icons.Filled.CheckCircle,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                } else null,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("setup_gender_chip_${option.lowercase()}")
                            )
                        }
                    }
                }

                // Country & City
                CountryAndCitySelector(
                    country = country,
                    cityLabel = city,
                    onCountryChange = { country = it },
                    onCityChange = { city = it },
                    showGpsButton = true,
                    isFetchingGps = isFetchingGps,
                    onRequestGps = {
                        if (LocationAndGeoHelper.hasLocationPermission(context)) {
                            fetchGps()
                        } else {
                            locationLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_COARSE_LOCATION,
                                    Manifest.permission.ACCESS_FINE_LOCATION
                                )
                            )
                        }
                    },
                    testTagPrefix = "setup"
                )

                // Bio / About Me
                OutlinedTextField(
                    value = bio,
                    onValueChange = { bio = it.take(400) },
                    label = { Text("Bio / About Me *") },
                    placeholder = { Text("Share your interests, creative focus, or what you'd like to post...") },
                    minLines = 3,
                    maxLines = 5,
                    supportingText = { Text("${bio.length}/400 (min 10 chars)") },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("setup_bio_input")
                )

                // Interests
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Interests (pick up to 8)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        KindredConstants.INTEREST_OPTIONS.forEach { topic ->
                            val picked = topic in selectedInterests
                            FilterChip(
                                selected = picked,
                                onClick = {
                                    if (picked) {
                                        selectedInterests.remove(topic)
                                    } else if (selectedInterests.size < 8) {
                                        selectedInterests.add(topic)
                                    }
                                },
                                label = { Text(topic) }
                            )
                        }
                    }
                }

                // Optional Profile Photos (up to 12)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Profile Photos (${uploadedPhotos.size}/${KindredConstants.MAX_PROFILE_PHOTOS})",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        TextButton(
                            onClick = {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            modifier = Modifier.testTag("setup_add_photo_button")
                        ) {
                            Icon(Icons.Filled.AddAPhoto, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Add Photos")
                        }
                    }

                    if (uploadedPhotos.isNotEmpty()) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            itemsIndexed(uploadedPhotos) { index, photo ->
                                Box(
                                    modifier = Modifier
                                        .size(88.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
                                ) {
                                    val bmp = remember(photo) { VerificationAnalyzer.decodeBase64DataUrl(photo) }
                                    if (bmp != null) {
                                        Image(
                                            bitmap = bmp,
                                            contentDescription = "Setup photo ${index + 1}",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        AsyncImage(
                                            model = photo,
                                            contentDescription = "Setup photo ${index + 1}",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(4.dp)
                                            .size(22.dp)
                                            .clip(CircleShape)
                                            .background(Color.Black.copy(alpha = 0.65f))
                                            .clickable { uploadedPhotos.removeAt(index) },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Close,
                                            contentDescription = "Remove photo",
                                            tint = Color.White,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}
