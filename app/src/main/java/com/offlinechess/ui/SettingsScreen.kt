package com.offlinechess.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.offlinechess.R
import com.offlinechess.data.preferences.AppPreferences
import com.offlinechess.data.preferences.ThemeMode

private val Languages = listOf(
    "vi" to "Tiếng Việt",
    "en" to "English",
    "zh-CN" to "简体中文",
    "es" to "Español",
    "ko" to "한국어",
    "ja" to "日本語",
    "fr" to "Français",
)

@Composable
fun SettingsScreen(
    preferences: AppPreferences,
    onBack: () -> Unit,
    onThemeChange: (ThemeMode) -> Unit,
    onLanguageChange: (String) -> Unit,
    onSkillChange: (Int) -> Unit,
    onPlayerColorChange: (String) -> Unit,
) {
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = stringResource(R.string.settings),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(12.dp))
                SectionTitle(stringResource(R.string.language))
                ChoiceRow(
                    title = stringResource(R.string.language_system),
                    selected = preferences.languageTag.isBlank(),
                    onClick = { onLanguageChange("") },
                )
                Languages.forEach { (tag, name) ->
                    ChoiceRow(
                        title = name,
                        selected = preferences.languageTag == tag,
                        onClick = { onLanguageChange(tag) },
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                SectionTitle(stringResource(R.string.theme))
                listOf(
                    ThemeMode.SYSTEM to R.string.theme_system,
                    ThemeMode.LIGHT to R.string.theme_light,
                    ThemeMode.DARK to R.string.theme_dark,
                ).forEach { (mode, title) ->
                    ChoiceRow(
                        title = stringResource(title),
                        selected = preferences.themeMode == mode,
                        onClick = { onThemeChange(mode) },
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                SectionTitle(stringResource(R.string.play_as))
                listOf(
                    "white" to R.string.white,
                    "black" to R.string.black,
                ).forEach { (color, title) ->
                    ChoiceRow(
                        title = stringResource(title),
                        selected = preferences.playerColor == color,
                        onClick = { onPlayerColorChange(color) },
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                SectionTitle(stringResource(R.string.ai_strength))
                Text(
                    text = stringResource(R.string.skill_format, preferences.skillLevel),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Slider(
                    value = preferences.skillLevel.toFloat(),
                    onValueChange = { onSkillChange(it.toInt()) },
                    valueRange = 0f..20f,
                    steps = 19,
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun ChoiceRow(title: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(role = Role.RadioButton, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(title, modifier = Modifier.padding(start = 8.dp))
    }
}
