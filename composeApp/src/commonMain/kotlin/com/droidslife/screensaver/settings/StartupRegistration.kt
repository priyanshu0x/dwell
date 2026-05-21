package com.droidslife.screensaver.settings

interface StartupRegistration {
    suspend fun setEnabled(enabled: Boolean)
}

expect fun createStartupRegistration(): StartupRegistration
