package com.droidslife.screensaver.settings

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ProfileModelTest {
    @Test
    fun catalogIncludesCurrentAndBuiltInProfiles() {
        val profiles = profileCatalogFor(SettingsModel())

        assertEquals(PROFILE_CURRENT_ID, profiles.first().id)
        assertTrue(profiles.any { it.id == PROFILE_WORK_ID })
        assertTrue(profiles.any { it.id == PROFILE_FOCUS_ID })
        assertTrue(profiles.any { it.id == PROFILE_NIGHT_ID })
        assertTrue(profiles.any { it.id == PROFILE_PRESENTATION_ID })
        assertTrue(profiles.any { it.id == PROFILE_DESK_CLOCK_ID })
    }

    @Test
    fun normalizationMigratesEmptyProfileListToBuiltInRecords() {
        val settings = SettingsModel().withNormalizedProfiles()

        assertTrue(settings.profiles.any { it.id == PROFILE_WORK_ID && it.kind == ProfileKind.BuiltIn })
        assertTrue(settings.profiles.any { it.id == PROFILE_PRESENTATION_ID && it.kind == ProfileKind.BuiltIn })
        assertFalse(settings.profiles.any { it.id == PROFILE_CURRENT_ID })
    }

    @Test
    fun currentProfileReflectsExistingManualSettings() {
        val settings = SettingsModel(
            mode = Mode.Console,
            consoleVariant = ConsoleVariant.Dark,
            enabledWidgetIds = setOf("custom.widget"),
            idleTimeoutSeconds = 90,
        )

        val current = assertNotNull(profileById(settings, PROFILE_CURRENT_ID))
        assertEquals(Mode.Console, current.settings.mode)
        assertEquals(ConsoleVariant.Dark, current.settings.consoleVariant)
        assertEquals(setOf("custom.widget"), current.settings.enabledWidgetIds)
        assertEquals(90, current.settings.idleTimeoutSeconds)
    }

    @Test
    fun presentationProfileHidesPrivateWidgetsByDefault() {
        val presentation = assertNotNull(profileById(SettingsModel(), PROFILE_PRESENTATION_ID))
        assertFalse("com.droidslife.screensaver.todos" in presentation.settings.enabledWidgetIds)
        assertFalse("com.droidslife.screensaver.expenses" in presentation.settings.enabledWidgetIds)
        assertFalse("com.droidslife.screensaver.calendar" in presentation.settings.enabledWidgetIds)
        assertTrue("com.droidslife.screensaver.clock" in presentation.settings.enabledWidgetIds)
    }

    @Test
    fun applyingProfilePreservesProviderSecretsAndWidgetConfig() {
        val widgetConfig = JsonObject(mapOf("apiKey" to JsonPrimitive("widget.secret")))
        val settings = SettingsModel(
            weatherApiKeySecretId = "weather.custom",
            backendApiKeySecretId = "backend.custom",
            widgetConfigs = mapOf("widget" to widgetConfig),
            widgetSecretVersions = mapOf("widget.secret" to 4L),
        )
        val profile = assertNotNull(profileById(settings, PROFILE_FOCUS_ID))
        val applied = profile.settings.applyTo(settings).copy(activeProfileId = profile.id)

        assertEquals(PROFILE_FOCUS_ID, applied.activeProfileId)
        assertEquals("weather.custom", applied.weatherApiKeySecretId)
        assertEquals("backend.custom", applied.backendApiKeySecretId)
        assertEquals(settings.widgetConfigs, applied.widgetConfigs)
        assertEquals(settings.widgetSecretVersions, applied.widgetSecretVersions)
    }

    @Test
    fun customProfilesCanBeCreatedRenamedAndDeleted() {
        val created = SettingsModel(
            mode = Mode.Console,
            consoleVariant = ConsoleVariant.Dark,
            enabledWidgetIds = setOf("custom.widget"),
        ).createCustomProfileFromCurrent("Client Work")

        val custom = assertNotNull(profileCatalogFor(created).firstOrNull { it.name == "Client Work" })
        assertEquals(custom.id, created.activeProfileId)
        assertEquals(Mode.Console, custom.settings.mode)
        assertEquals(setOf("custom.widget"), custom.settings.enabledWidgetIds)

        val renamed = created.renameCustomProfile(custom.id, "Deep Work")
        assertEquals("Deep Work", assertNotNull(profileById(renamed, custom.id)).name)

        val deleted = renamed.deleteCustomProfile(custom.id)
        assertEquals(PROFILE_CURRENT_ID, deleted.activeProfileId)
        assertFalse(profileCatalogFor(deleted).any { it.id == custom.id })
    }

    @Test
    fun duplicateProfileCreatesStableCustomCopyAndAppliesIt() {
        val duplicated = SettingsModel().duplicateProfile(PROFILE_PRESENTATION_ID)
        val copy = assertNotNull(profileCatalogFor(duplicated).firstOrNull { it.id.startsWith("custom-presentation-copy") })

        assertEquals(copy.id, duplicated.activeProfileId)
        assertEquals(PROFILE_PRESENTATION_ID.let { assertNotNull(profileById(SettingsModel(), it)).settings }, copy.settings)
        assertFalse("com.droidslife.screensaver.todos" in duplicated.enabledWidgetIds)
    }

    @Test
    fun activeProfileSettingsSyncWhenProfileControlledFieldsChange() {
        val active = SettingsModel()
            .applyProfile(PROFILE_FOCUS_ID)
            .copy(showSeconds = true, widgetConfigs = mapOf("widget" to JsonObject(mapOf("apiKey" to JsonPrimitive("secret")))))
            .syncActiveProfileSettings()

        val focus = assertNotNull(profileById(active, PROFILE_FOCUS_ID))
        assertTrue(focus.settings.showSeconds)
        assertEquals(active.widgetConfigs, mapOf("widget" to JsonObject(mapOf("apiKey" to JsonPrimitive("secret")))))
    }

    @Test
    fun resetBuiltInProfileRestoresDefaultSettings() {
        val changed = SettingsModel()
            .applyProfile(PROFILE_FOCUS_ID)
            .copy(consoleWidgetBorderStyle = ConsoleWidgetBorderStyle.Shadow)
            .syncActiveProfileSettings()

        assertEquals(ConsoleWidgetBorderStyle.Shadow, assertNotNull(profileById(changed, PROFILE_FOCUS_ID)).settings.consoleWidgetBorderStyle)

        val reset = changed.resetBuiltInProfile(PROFILE_FOCUS_ID)
        val focus = assertNotNull(profileById(reset, PROFILE_FOCUS_ID))
        assertEquals(ConsoleWidgetBorderStyle.Borderless, focus.settings.consoleWidgetBorderStyle)
        assertEquals(ConsoleWidgetBorderStyle.Borderless, reset.consoleWidgetBorderStyle)
    }
}
