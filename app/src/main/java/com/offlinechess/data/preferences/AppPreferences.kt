package com.offlinechess.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class ThemeMode(val storageValue: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark");

    companion object {
        fun fromStorage(value: String?): ThemeMode =
            entries.firstOrNull { it.storageValue == value } ?: SYSTEM
    }
}

data class AppPreferences(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val languageTag: String = "",
    val skillLevel: Int = 10,
    val playerColor: String = "white",
)

private val Context.settingsDataStore by preferencesDataStore(name = "offline_chess_settings")

class PreferencesRepository(private val context: Context) {
    val preferences: Flow<AppPreferences> = context.settingsDataStore.data.map { values ->
        AppPreferences(
            themeMode = ThemeMode.fromStorage(values[THEME_KEY]),
            languageTag = values[LANGUAGE_KEY].orEmpty(),
            skillLevel = values[SKILL_KEY] ?: 10,
            playerColor = values[PLAYER_COLOR_KEY] ?: "white",
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.settingsDataStore.edit { it[THEME_KEY] = mode.storageValue }
    }

    suspend fun setLanguageTag(languageTag: String) {
        require(languageTag in SUPPORTED_LANGUAGES) { "Unsupported language: $languageTag" }
        context.settingsDataStore.edit { it[LANGUAGE_KEY] = languageTag }
    }

    suspend fun useSystemLanguage() {
        context.settingsDataStore.edit { it.remove(LANGUAGE_KEY) }
    }

    suspend fun setSkillLevel(skillLevel: Int) {
        require(skillLevel in 0..20) { "Stockfish skill must be between 0 and 20" }
        context.settingsDataStore.edit { it[SKILL_KEY] = skillLevel }
    }

    suspend fun setPlayerColor(color: String) {
        require(color == "white" || color == "black") { "Player color must be white or black" }
        context.settingsDataStore.edit { it[PLAYER_COLOR_KEY] = color }
    }

    private companion object {
        val THEME_KEY = stringPreferencesKey("theme_mode")
        val LANGUAGE_KEY = stringPreferencesKey("language_tag")
        val SKILL_KEY = intPreferencesKey("skill_level")
        val PLAYER_COLOR_KEY = stringPreferencesKey("player_color")
        val SUPPORTED_LANGUAGES = setOf("en", "vi", "zh-CN", "es", "ko", "ja", "fr")
    }
}
