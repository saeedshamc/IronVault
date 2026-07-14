package com.example.presentation.screens

import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.security.ClipboardUtils
import com.example.data.security.PasswordGenerator
import com.example.ui.theme.StrengthMedium
import com.example.ui.theme.StrengthStrong
import com.example.ui.theme.StrengthVeryStrong
import com.example.ui.theme.StrengthWeak

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PasswordGeneratorScreen() {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    var length by remember { mutableStateOf(16f) }
    var includeUpper by remember { mutableStateOf(true) }
    var includeLower by remember { mutableStateOf(true) }
    var includeNumbers by remember { mutableStateOf(true) }
    var includeSymbols by remember { mutableStateOf(true) }
    var excludeAmbiguous by remember { mutableStateOf(false) }
    var pronounceableMode by remember { mutableStateOf(false) }

    var generatedPassword by remember { mutableStateOf("") }

    // Re-generate password when parameters modify
    fun regenerate() {
        generatedPassword = if (pronounceableMode) {
            PasswordGenerator.generatePronounceable(length.toInt(), includeNumbers)
        } else {
            PasswordGenerator.generateRandom(
                length = length.toInt(),
                includeUpper = includeUpper,
                includeLower = includeLower,
                includeNumbers = includeNumbers,
                includeSymbols = includeSymbols,
                excludeAmbiguous = excludeAmbiguous
            )
        }
    }

    // Initial trigger
    LaunchedEffect(length, includeUpper, includeLower, includeNumbers, includeSymbols, excludeAmbiguous, pronounceableMode) {
        regenerate()
    }

    // Live entropy estimate
    val strengthResult = remember(generatedPassword, includeUpper, includeLower, includeNumbers, includeSymbols) {
        PasswordGenerator.estimateStrength(
            generatedPassword,
            includeUpper = if (pronounceableMode) false else includeUpper,
            includeLower = if (pronounceableMode) true else includeLower,
            includeNumbers = if (pronounceableMode) includeNumbers else includeNumbers,
            includeSymbols = if (pronounceableMode) false else includeSymbols
        )
    }

    val strengthColor by animateColorAsState(
        targetValue = when (strengthResult.strength) {
            PasswordGenerator.PasswordStrength.WEAK -> StrengthWeak
            PasswordGenerator.PasswordStrength.MEDIUM -> StrengthMedium
            PasswordGenerator.PasswordStrength.STRONG -> StrengthStrong
            PasswordGenerator.PasswordStrength.VERY_STRONG -> StrengthVeryStrong
        }
    )

    val strengthLabel = when (strengthResult.strength) {
        PasswordGenerator.PasswordStrength.WEAK -> stringResource(R.string.strength_weak)
        PasswordGenerator.PasswordStrength.MEDIUM -> stringResource(R.string.strength_medium)
        PasswordGenerator.PasswordStrength.STRONG -> stringResource(R.string.strength_strong)
        PasswordGenerator.PasswordStrength.VERY_STRONG -> stringResource(R.string.strength_very_strong)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Output Card Display
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Password display
                Text(
                    text = generatedPassword.ifEmpty { "..." },
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Action buttons (Copy and Regenerate)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    IconButton(onClick = { regenerate() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Regenerate")
                    }
                    IconButton(
                        onClick = {
                            if (generatedPassword.isNotEmpty()) {
                                ClipboardUtils.copyToClipboardSecurely(context, "Generated Password", generatedPassword)
                                Toast.makeText(context, context.getString(R.string.password_copied), Toast.LENGTH_SHORT).show()
                            }
                        },
                        enabled = generatedPassword.isNotEmpty()
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy Password")
                    }
                }

                // Strength Progressive bar
                if (generatedPassword.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = strengthLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = strengthColor,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = stringResource(R.string.entropy_estimate, strengthResult.entropy),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { ((strengthResult.entropy / 100.0).coerceIn(0.1, 1.0)).toFloat() },
                        color = strengthColor,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Length Slider
        Text(
            text = stringResource(R.string.length_label, length.toInt()),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Slider(
            value = length,
            onValueChange = { length = it },
            valueRange = 8f..64f,
            steps = 55,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Generator Mode Toggle (Random vs Memorizable)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = stringResource(R.string.pronounceable_mode),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Switch(
                checked = pronounceableMode,
                onCheckedChange = { pronounceableMode = it }
            )
        }

        Divider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))

        // Advanced charset toggles (Only when pronounceable mode is disabled)
        if (!pronounceableMode) {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // Lowercase
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.include_lowercase), style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = includeLower,
                        onCheckedChange = {
                            if (it || includeUpper || includeNumbers || includeSymbols) includeLower = it
                        }
                    )
                }

                // Uppercase
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.include_uppercase), style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = includeUpper,
                        onCheckedChange = {
                            if (it || includeLower || includeNumbers || includeSymbols) includeUpper = it
                        }
                    )
                }

                // Numbers
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.include_numbers), style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = includeNumbers,
                        onCheckedChange = {
                            if (it || includeLower || includeUpper || includeSymbols) includeNumbers = it
                        }
                    )
                }

                // Symbols
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.include_symbols), style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = includeSymbols,
                        onCheckedChange = {
                            if (it || includeLower || includeUpper || includeNumbers) includeSymbols = it
                        }
                    )
                }

                // Ambiguous
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.exclude_ambiguous), style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = excludeAmbiguous,
                        onCheckedChange = { excludeAmbiguous = it }
                    )
                }
            }
        } else {
            // Pronounceable options
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.include_numbers), style = MaterialTheme.typography.bodyMedium)
                Switch(
                    checked = includeNumbers,
                    onCheckedChange = { includeNumbers = it }
                )
            }
        }
    }
}
