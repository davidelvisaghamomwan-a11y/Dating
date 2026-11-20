package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.LocationCity
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.model.KindredConstants
import com.example.ui.theme.CoralRose

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CountryAndCitySelector(
    country: String,
    cityLabel: String,
    onCountryChange: (String) -> Unit,
    onCityChange: (String) -> Unit,
    showGpsButton: Boolean = false,
    isFetchingGps: Boolean = false,
    onRequestGps: (() -> Unit)? = null,
    testTagPrefix: String = "profile"
) {
    var countryDropdownExpanded by remember { mutableStateOf(false) }
    val suggestedCities = remember(country) { KindredConstants.citiesForCountry(country) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        ExposedDropdownMenuBox(
            expanded = countryDropdownExpanded,
            onExpandedChange = { countryDropdownExpanded = !countryDropdownExpanded },
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = country,
                onValueChange = { if (it.length <= 80) onCountryChange(it) },
                label = { Text("Country") },
                placeholder = { Text("Select or enter country") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Public,
                        contentDescription = null,
                        tint = CoralRose,
                        modifier = Modifier.size(18.dp)
                    )
                },
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = countryDropdownExpanded)
                },
                singleLine = true,
                modifier = Modifier
                    .menuAnchor(MenuAnchorType.PrimaryEditable, enabled = true)
                    .fillMaxWidth()
                    .testTag("${testTagPrefix}_country_input")
            )

            val filteredCountries = remember(country) {
                val query = country.trim()
                if (query.isBlank() || query in KindredConstants.COUNTRY_OPTIONS) {
                    KindredConstants.COUNTRY_OPTIONS
                } else {
                    val matches = KindredConstants.COUNTRY_OPTIONS.filter {
                        it.contains(query, ignoreCase = true)
                    }
                    matches.ifEmpty { KindredConstants.COUNTRY_OPTIONS }
                }
            }

            ExposedDropdownMenu(
                expanded = countryDropdownExpanded,
                onDismissRequest = { countryDropdownExpanded = false }
            ) {
                filteredCountries.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option) },
                        onClick = {
                            onCountryChange(option)
                            val optionCities = KindredConstants.citiesForCountry(option)
                            if (optionCities.isNotEmpty() && cityLabel !in optionCities) {
                                onCityChange(optionCities.first())
                            }
                            countryDropdownExpanded = false
                        },
                        modifier = Modifier.testTag("${testTagPrefix}_country_option_$option")
                    )
                }
            }
        }

        if (suggestedCities.isNotEmpty()) {
            Text(
                text = "Popular cities in ${country.ifBlank { "your country" }}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                suggestedCities.forEach { cityOption ->
                    val isSelected = cityLabel.equals(cityOption, ignoreCase = true)
                    FilterChip(
                        selected = isSelected,
                        onClick = { onCityChange(cityOption) },
                        label = { Text(cityOption) },
                        leadingIcon = if (isSelected) {
                            {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        } else null,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = CoralRose.copy(alpha = 0.2f),
                            selectedLabelColor = CoralRose,
                            selectedLeadingIconColor = CoralRose
                        ),
                        modifier = Modifier.testTag("${testTagPrefix}_city_chip_$cityOption")
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedTextField(
                value = cityLabel,
                onValueChange = { if (it.length <= 80) onCityChange(it) },
                label = { Text("City / Neighborhood") },
                placeholder = { Text("Enter city") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.LocationCity,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                },
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .testTag("${testTagPrefix}_city_input")
            )

            if (showGpsButton && onRequestGps != null) {
                OutlinedButton(
                    onClick = onRequestGps,
                    enabled = !isFetchingGps,
                    modifier = Modifier
                        .height(54.dp)
                        .testTag("${testTagPrefix}_gps_button")
                ) {
                    if (isFetchingGps) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            Icons.Filled.MyLocation,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("GPS")
                    }
                }
            }
        }
    }
}
