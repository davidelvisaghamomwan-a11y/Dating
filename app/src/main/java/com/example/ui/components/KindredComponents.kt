package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.R
import com.example.ui.theme.ChampagneGold
import com.example.ui.theme.PlayfairDisplayFamily
import com.example.util.LocationAndGeoHelper
import com.example.util.MediaImageHelper
import kotlin.math.abs

@Composable
fun ProfileAvatarImage(
    photoDataUrl: String?,
    displayName: String,
    seed: String,
    modifier: Modifier = Modifier,
    shape: Shape = CircleShape,
    initialsFontSizeSp: Int = 28
) {
    val imageBitmap = remember(photoDataUrl) {
        MediaImageHelper.decodeBase64DataUrl(photoDataUrl)
    }

    val palette = remember(seed) {
        val palettes = listOf(
            listOf(Color(0xFF8E244D), Color(0xFFE84855), Color(0xFFF4A261)),
            listOf(Color(0xFF283048), Color(0xFF5D4157), Color(0xFFA8CABA)),
            listOf(Color(0xFF42275A), Color(0xFF734B6D), Color(0xFFE84855)),
            listOf(Color(0xFF1F4037), Color(0xFF2EC4B6), Color(0xFFF4A261)),
            listOf(Color(0xFF3A1C71), Color(0xFFD76D77), Color(0xFFFFAF7B)),
            listOf(Color(0xFF141E30), Color(0xFF243B55), Color(0xFF2EC4B6)),
            listOf(Color(0xFF4A154B), Color(0xFFB32635), Color(0xFFFFB3B8)),
            listOf(Color(0xFF0F2027), Color(0xFF203A43), Color(0xFF2C5364)),
            listOf(Color(0xFF614385), Color(0xFF516395), Color(0xFFF4A261)),
            listOf(Color(0xFF355C7D), Color(0xFF6C5B7B), Color(0xFFC06C84)),
            listOf(Color(0xFF134E5E), Color(0xFF71B280), Color(0xFFE8C547)),
            listOf(Color(0xFF5A3F37), Color(0xFF9E5A63), Color(0xFFC97B84))
        )
        palettes[abs(seed.hashCode()) % palettes.size]
    }

    val initials = remember(displayName) {
        displayName.trim()
            .split(" ")
            .filter { it.isNotBlank() }
            .take(2)
            .joinToString("") { it.first().uppercaseChar().toString() }
            .ifBlank { "K" }
    }

    val isRemoteHttp = remember(photoDataUrl) {
        photoDataUrl?.startsWith("http://", ignoreCase = true) == true ||
            photoDataUrl?.startsWith("https://", ignoreCase = true) == true
    }

    Box(
        modifier = modifier
            .clip(shape)
            .background(Brush.linearGradient(palette)),
        contentAlignment = Alignment.Center
    ) {
        if (imageBitmap != null) {
            Image(
                bitmap = imageBitmap,
                contentDescription = stringResource(R.string.cd_profile_photo),
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                filterQuality = FilterQuality.High
            )
        } else if (isRemoteHttp && !photoDataUrl.isNullOrBlank()) {
            AsyncImage(
                model = photoDataUrl,
                contentDescription = stringResource(R.string.cd_profile_photo),
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Text(
                text = initials,
                fontFamily = PlayfairDisplayFamily,
                fontWeight = FontWeight.Bold,
                fontSize = initialsFontSizeSp.sp,
                color = Color.White
            )
        }
    }
}

@Composable
fun LocationDistancePill(
    cityLabel: String,
    distanceKm: Double?,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = Color.Black.copy(alpha = 0.42f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.LocationOn,
                contentDescription = null,
                tint = ChampagneGold,
                modifier = Modifier.size(14.dp)
            )
            val label = if (distanceKm != null) {
                "${LocationAndGeoHelper.formatDistanceLabel(distanceKm)} • $cityLabel"
            } else {
                cityLabel
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
