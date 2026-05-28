package com.droidslife.screensaver

data class Args(val mode: LaunchMode) {
    companion object {
        fun parse(
            rawArgs: Array<String>,
            executablePath: String = ProcessHandle.current().info().command().orElse(""),
        ): Args {
            val first = rawArgs.firstOrNull()?.lowercase()
            return Args(
                mode = when (first) {
                    "--show" -> LaunchMode.Show
                    "/s" -> LaunchMode.Screensaver
                    "/p" -> LaunchMode.Preview
                    "/c" -> LaunchMode.Config
                    "--daemon" -> LaunchMode.Daemon
                    null -> if (executablePath.endsWith(".scr", ignoreCase = true)) {
                        LaunchMode.Screensaver
                    } else {
                        LaunchMode.Daemon
                    }
                    else -> LaunchMode.Daemon
                }
            )
        }
    }
}

enum class LaunchMode {
    Daemon,
    Show,
    Screensaver,
    Preview,
    Config,
}
