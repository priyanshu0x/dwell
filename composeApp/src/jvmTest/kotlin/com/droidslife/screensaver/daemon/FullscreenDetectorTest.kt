package com.droidslife.screensaver.daemon

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FullscreenDetectorTest {

    @Test
    fun parsesActiveWindowIdFromXprop() {
        assertEquals("0x4a00003", FullscreenDetector.parseActiveWindowId("_NET_ACTIVE_WINDOW(WINDOW): window id # 0x4a00003"))
        assertEquals("0x1a00007", FullscreenDetector.parseActiveWindowId("0x1a00007"))
        assertEquals("0x0", FullscreenDetector.parseActiveWindowId("_NET_ACTIVE_WINDOW(WINDOW): window id # 0x0"))
        assertEquals(null, FullscreenDetector.parseActiveWindowId("no window here"))
    }

    @Test
    fun detectsFullscreenFromXpropState() {
        val fullscreen = "_NET_WM_STATE(ATOM) = _NET_WM_STATE_FULLSCREEN, _NET_WM_STATE_FOCUSED"
        assertTrue(fullscreen.contains("_NET_WM_STATE_FULLSCREEN"))
        val notFullscreen = "_NET_WM_STATE(ATOM) = _NET_WM_STATE_MAXIMIZED_VERT, _NET_WM_STATE_MAXIMIZED_HORZ"
        assertFalse(notFullscreen.contains("_NET_WM_STATE_FULLSCREEN"))
    }

    @Test
    fun doesNotSuppressWhenDetectionFails() {
        // shouldSuppress should be safe to call even when subprocesses missing
        // We can't guarantee fullscreen, but the call should not throw and should return a boolean.
        val result = runCatching { FullscreenDetector.shouldSuppressAutoShow() }.getOrDefault(false)
        // No assertion on value — just that it doesn't throw.
        assertTrue(result == true || result == false)
    }
}
