package com.saubh.deskbuddy.server

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

/**
 * "Start at login" on macOS: a per-user LaunchAgent that runs the stored launch command through
 * `/bin/sh -c` in the logged-in user's GUI session. The agent is loaded immediately and again at
 * login. It is restarted after an unsuccessful exit, while an intentional Quit (exit code 0) stays
 * quit until the next login.
 */
class MacLaunchAgent(
    private val plist: Path = Path.of(System.getProperty("user.home"), "Library", "LaunchAgents", "$LABEL.plist"),
) : StartupRegistration.Registry {

    override fun read(): String? = runCatching {
        if (!Files.exists(plist)) return null
        commandIn(Files.readString(plist))
    }.getOrNull()

    override fun write(value: String) {
        Files.createDirectories(plist.parent)
        Files.writeString(plist, plistFor(value))
    }

    override fun delete() {
        Files.deleteIfExists(plist)
    }

    /** Make an explicit enable action active now and at subsequent logins. */
    override fun enable() {
        val target = target()
        requireSuccess("enable", launchctl("enable", target))
        ensureLoaded()
    }

    /** Refresh/load the existing login agent without overriding a macOS-disabled service. */
    override fun ensureLoaded() {
        val domain = domain()
        val target = "$domain/$LABEL"
        if (launchctl("print", target).exitCode == 0) return
        val result = launchctl("bootstrap", domain, plist.toString())
        if (result.exitCode != 0 && launchctl("print", target).exitCode != 0) {
            throw IOException("Could not load $LABEL: ${result.output.trim()}")
        }
    }

    /** Disable future starts without killing the app that is currently serving the phone. */
    override fun disable() {
        requireSuccess("disable", launchctl("disable", target()))
    }

    companion object {
        const val LABEL = "com.saubh.konnekt"
        private const val EXEC = "exec "
        private val COMMAND = Regex("<string>-c</string>\\s*<string>(.*?)</string>", RegexOption.DOT_MATCHES_ALL)

        private data class LaunchctlResult(val exitCode: Int, val output: String)

        private fun domain(): String {
            val process = ProcessBuilder("/usr/bin/id", "-u").start()
            val uid = process.inputStream.bufferedReader().use { it.readText() }.trim()
            if (process.waitFor() != 0 || uid.isEmpty()) throw IOException("Could not determine the current macOS user")
            return "gui/$uid"
        }

        private fun target(): String = "${domain()}/$LABEL"

        private fun launchctl(vararg arguments: String): LaunchctlResult {
            val process = ProcessBuilder("/bin/launchctl", *arguments).redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader().use { it.readText() }
            return LaunchctlResult(process.waitFor(), output)
        }

        private fun requireSuccess(operation: String, result: LaunchctlResult) {
            if (result.exitCode != 0) throw IOException("launchctl $operation failed: ${result.output.trim()}")
        }

        /** `exec` so launchd tracks the app itself rather than a waiting shell. */
        fun plistFor(command: String): String = """
            |<?xml version="1.0" encoding="UTF-8"?>
            |<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
            |<plist version="1.0">
            |<dict>
            |    <key>Label</key>
            |    <string>$LABEL</string>
            |    <key>ProgramArguments</key>
            |    <array>
            |        <string>/bin/sh</string>
            |        <string>-c</string>
            |        <string>${escape(EXEC + command)}</string>
            |    </array>
            |    <key>RunAtLoad</key>
            |    <true/>
            |    <key>KeepAlive</key>
            |    <dict>
            |        <key>SuccessfulExit</key>
            |        <false/>
            |    </dict>
            |    <key>ProcessType</key>
            |    <string>Interactive</string>
            |</dict>
            |</plist>
            |""".trimMargin()

        /** The command [plistFor] stored, or null when the file was not written by it. */
        fun commandIn(plist: String): String? =
            COMMAND.find(plist)?.groupValues?.get(1)?.let(::unescape)?.removePrefix(EXEC)

        private fun escape(text: String) = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

        private fun unescape(text: String) = text.replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&")
    }
}
