package com.example.ui.components

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ai.AiPhotoGenUiState
import com.example.ai.AiPhotoStylePreset
import com.example.ai.AiPhotoStylePresets
import com.example.ui.theme.ChampagneGold
import com.example.ui.theme.CoralRose
import com.example.ui.theme.EmeraldMint
import com.example.util.VerificationAnalyzer
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiPhotoStudioSheet(
    state: AiPhotoGenUiState,
    subjectGender: String?,
    existingProfilePhotos: List<String>,
    onGenerate: (
        preset: AiPhotoStylePreset,
        customDetails: String,
        subjectGender: String?,
        referenceBitmap: Bitmap?
    ) -> Unit,
    onApplyPhoto: (dataUrl: String) -> Unit,
    onResetState: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        AiPhotoStudioContent(
            state = state,
            subjectGender = subjectGender,
            existingProfilePhotos = existingProfilePhotos,
            onGenerate = onGenerate,
            onApplyPhoto = onApplyPhoto,
            onResetState = onResetState
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AiPhotoStudioContent(
    state: AiPhotoGenUiState,
    subjectGender: String?,
    existingProfilePhotos: List<String>,
    onGenerate: (
        preset: AiPhotoStylePreset,
        customDetails: String,
        subjectGender: String?,
        referenceBitmap: Bitmap?
    ) -> Unit,
    onApplyPhoto: (dataUrl: String) -> Unit,
    onResetState: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedPreset by remember { mutableStateOf(AiPhotoStylePresets.PRESETS.first()) }
    var customPrompt by remember { mutableStateOf("") }
    var referenceBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var referenceLabel by remember { mutableStateOf<String?>(null) }

    val referencePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val decoded = VerificationAnalyzer.decodeUriToBitmap(context, uri)
                if (decoded != null) {
                    referenceBitmap = decoded
                    referenceLabel = "Picked photo"
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .padding(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = CoralRose.copy(alpha = 0.16f),
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.AutoAwesome,
                        contentDescription = null,
                        tint = CoralRose,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.ai_studio_title),
                    style = MaterialTheme.typography.headlineSmall
                )
                Text(
                    text = "Generate or restyle studio-grade portraits with Gemini",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Text("1. Choose Portrait Style", style = MaterialTheme.typography.titleSmall)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AiPhotoStylePresets.PRESETS.forEach { preset ->
                val isSelected = selectedPreset.id == preset.id
                FilterChip(
                    selected = isSelected,
                    onClick = { selectedPreset = preset },
                    label = { Text(preset.title) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = CoralRose.copy(alpha = 0.2f),
                        selectedLabelColor = CoralRose
                    ),
                    modifier = Modifier.testTag("ai_preset_${preset.id}")
                )
            }
        }

        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Text(
                text = "${selectedPreset.title}: ${selectedPreset.subtitle}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
            )
        }

        Text("2. Styling Details (Optional)", style = MaterialTheme.typography.titleSmall)
        OutlinedTextField(
            value = customPrompt,
            onValueChange = { if (it.length <= 180) customPrompt = it },
            label = { Text("Outfit, setting, or lighting notes") },
            placeholder = { Text("e.g., linen jacket, warm espresso bar background, natural candid smile") },
            minLines = 2,
            maxLines = 3,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("ai_photo_prompt_input")
        )

        Text("3. Likeness Reference Photo (Optional)", style = MaterialTheme.typography.titleSmall)
        Text(
            text = "Attach a selfie or existing profile photo so Gemini preserves your likeness while upgrading lighting and setting.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = {
                    referencePicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                modifier = Modifier
                    .height(48.dp)
                    .testTag("ai_pick_reference_button")
            ) {
                Icon(
                    imageVector = Icons.Filled.AddPhotoAlternate,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (referenceBitmap == null) "Pick Selfie" else "Replace")
            }

            if (existingProfilePhotos.isNotEmpty() && referenceBitmap == null) {
                OutlinedButton(
                    onClick = {
                        val decoded = VerificationAnalyzer.decodeBitmapFromDataUrl(existingProfilePhotos.first())
                        if (decoded != null) {
                            referenceBitmap = decoded
                            referenceLabel = "Main profile photo"
                        }
                    },
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("ai_use_existing_reference_button")
                ) {
                    Text("Use Main Photo")
                }
            }

            referenceBitmap?.let { bmp ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "Selected reference photo",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .border(1.dp, EmeraldMint, RoundedCornerShape(10.dp))
                    )
                    Text(
                        text = referenceLabel ?: "Attached",
                        style = MaterialTheme.typography.labelSmall,
                        color = EmeraldMint
                    )
                    IconButton(
                        onClick = {
                            referenceBitmap = null
                            referenceLabel = null
                        },
                        modifier = Modifier.testTag("ai_clear_reference_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Remove reference photo",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        // Result / Status Preview Area
        when (state) {
            is AiPhotoGenUiState.Idle -> Unit

            is AiPhotoGenUiState.Loading -> {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("ai_loading_card")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(color = CoralRose)
                        Text(
                            text = "Synthesizing studio portrait with Gemini...",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = "This usually takes 15–45 seconds. Keep this sheet open while your portrait renders.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            is AiPhotoGenUiState.Error -> {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("ai_error_banner")
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ErrorOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Could not generate portrait",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = state.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }

            is AiPhotoGenUiState.Success -> {
                Card(
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("ai_result_card")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.CheckCircle,
                                contentDescription = null,
                                tint = EmeraldMint,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "${state.styleTitle} Ready",
                                style = MaterialTheme.typography.titleSmall
                            )
                            if (state.usedReferencePhoto) {
                                Surface(
                                    shape = RoundedCornerShape(50),
                                    color = ChampagneGold.copy(alpha = 0.18f)
                                ) {
                                    Text(
                                        text = "Reference matched",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = ChampagneGold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        Image(
                            bitmap = state.bitmap.asImageBitmap(),
                            contentDescription = "AI generated dating profile photo preview",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(0.85f)
                                .clip(RoundedCornerShape(18.dp))
                        )

                        Button(
                            onClick = { onApplyPhoto(state.dataUrl) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("ai_use_photo_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = CoralRose,
                                contentColor = Color.White
                            )
                        ) {
                            Text(
                                text = stringResource(R.string.btn_use_ai_photo),
                                fontWeight = FontWeight.Bold
                            )
                        }

                        OutlinedButton(
                            onClick = {
                                onResetState()
                                onGenerate(selectedPreset, customPrompt, subjectGender, referenceBitmap)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("ai_regenerate_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Generate Another Variation")
                        }
                    }
                }
            }
        }

        if (state !is AiPhotoGenUiState.Success) {
            Button(
                onClick = {
                    onGenerate(selectedPreset, customPrompt, subjectGender, referenceBitmap)
                },
                enabled = state !is AiPhotoGenUiState.Loading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("ai_generate_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = CoralRose,
                    contentColor = Color.White
                )
            ) {
                Icon(
                    imageVector = Icons.Filled.AutoAwesome,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.btn_generate_ai_photo),
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
