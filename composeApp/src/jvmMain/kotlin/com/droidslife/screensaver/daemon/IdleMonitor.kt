package com.droidslife.screensaver.daemon

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

interface IdleMonitor {
    fun idleTimeMillis(): Long
}

sealed interface IdleState {
    data object Active : IdleState
    data object Idle : IdleState
}

fun IdleMonitor.watch(thresholdMillis: Long, pollMillis: Long = 1_000L): Flow<IdleState> = flow {
    var last: IdleState? = null
    while (true) {
        val next = if (idleTimeMillis() >= thresholdMillis) IdleState.Idle else IdleState.Active
        if (next != last) {
            emit(next)
            last = next
        }
        delay(pollMillis)
    }
}

fun createIdleMonitor(): IdleMonitor {
    val osName = System.getProperty("os.name").lowercase()
    return when {
        osName.contains("windows") -> WindowsIdleMonitor()
        osName.contains("linux") -> LinuxIdleMonitor()
        else -> UnsupportedIdleMonitor
    }
}

private object UnsupportedIdleMonitor : IdleMonitor {
    override fun idleTimeMillis(): Long = 0L
}
