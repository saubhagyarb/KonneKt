package com.saubh.deskbuddy.server

import com.saubh.deskbuddy.server.keyboard.MacKeyboardActuator

/** Permissions and system access states for desktop OSes (macOS & Windows). */
data class DesktopPermissionState(
    val isMac: Boolean = HostOs.isMac(),
    val isWindows: Boolean = HostOs.isWindows(),
    val macAccessibilityGranted: Boolean = true,
    val macAutomationGranted: Boolean = true,
) {
    val allMacPermissionsGranted: Boolean
        get() = !isMac || (macAccessibilityGranted && macAutomationGranted)
}

object DesktopPermissions {
    fun check(): DesktopPermissionState {
        val isMac = HostOs.isMac()
        val isWindows = HostOs.isWindows()
        if (!isMac) {
            return DesktopPermissionState(isMac = false, isWindows = isWindows)
        }
        val accessibility = MacKeyboardActuator.isTrusted()
        val automation = runCatching {
            Osascript.run("""tell application "System Events" to return true""", timeoutMs = 2000L) == "true"
        }.getOrDefault(false)

        return DesktopPermissionState(
            isMac = true,
            isWindows = isWindows,
            macAccessibilityGranted = accessibility,
            macAutomationGranted = automation,
        )
    }

    fun openMacAccessibilitySettings() {
        if (!HostOs.isMac()) return
        runCatching {
            ProcessBuilder("open", "x-apple.systempreferences:com.apple.preference.security?Privacy_Accessibility").start()
        }
    }

    fun openMacAutomationSettings() {
        if (!HostOs.isMac()) return
        // Trigger native macOS prompt if not yet requested
        runCatching {
            Osascript.run("""tell application "System Events" to return true""")
        }
        runCatching {
            ProcessBuilder("open", "x-apple.systempreferences:com.apple.preference.security?Privacy_Automation").start()
        }
    }

    fun openWindowsFirewallSettings() {
        if (!HostOs.isWindows()) return
        runCatching {
            ProcessBuilder("cmd", "/c", "start", "ms-settings:network").start()
        }
    }
}
