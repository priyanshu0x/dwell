package com.droidslife.screensaver

import kotlin.test.Test
import kotlin.test.assertEquals

class ArgsTest {
    @Test
    fun emptyArgsDefaultToDaemon() {
        assertEquals(LaunchMode.Daemon, Args.parse(emptyArray(), executablePath = "Screen Saver App.exe").mode)
    }

    @Test
    fun emptyScrArgsOpenScreensaver() {
        assertEquals(LaunchMode.Screensaver, Args.parse(emptyArray(), executablePath = "Screen Saver App.scr").mode)
    }

    @Test
    fun showArgOpensTrayBackedDashboard() {
        assertEquals(LaunchMode.Show, Args.parse(arrayOf("--show")).mode)
    }

    @Test
    fun screensaverArgOpensOneShotDashboard() {
        assertEquals(LaunchMode.Screensaver, Args.parse(arrayOf("/s")).mode)
    }

    @Test
    fun screensaverControlArgsMapToExpectedModes() {
        assertEquals(LaunchMode.Preview, Args.parse(arrayOf("/p")).mode)
        assertEquals(LaunchMode.Config, Args.parse(arrayOf("/c")).mode)
    }

    @Test
    fun unknownArgsDefaultToDaemon() {
        assertEquals(LaunchMode.Daemon, Args.parse(arrayOf("--unknown")).mode)
    }
}
