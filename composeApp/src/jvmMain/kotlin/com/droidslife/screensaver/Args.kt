package com.droidslife.screensaver

data class Args(val mode: LaunchMode) {
    companion object {
        fun parse(rawArgs: Array<String>): Args {
            val first = rawArgs.firstOrNull()?.lowercase()
            return Args(
                mode = when (first) {
                    "--show", "/s" -> LaunchMode.Show
                    "/p" -> LaunchMode.Preview
                    "/c" -> LaunchMode.Config
                    "--daemon", null -> LaunchMode.Daemon
                    else -> LaunchMode.Daemon
                }
            )
        }
    }
}

enum class LaunchMode {
    Daemon,
    Show,
    Preview,
    Config,
}
