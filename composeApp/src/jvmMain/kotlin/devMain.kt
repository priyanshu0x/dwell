import androidx.compose.ui.window.application
import com.droidslife.screensaver.ui.LinuxWindowManagerHints

fun main() {
    LinuxWindowManagerHints.configureDwellAppClassName()
    application {
        runDwell(devMode = true)
    }
}
