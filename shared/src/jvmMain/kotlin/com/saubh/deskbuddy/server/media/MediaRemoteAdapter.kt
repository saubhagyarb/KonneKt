package com.saubh.deskbuddy.server.media

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.concurrent.TimeUnit

/**
 * The bundled `mediaremote-adapter` (BSD-3, github.com/ungive/mediaremote-adapter). It runs under the
 * system perl, which macOS 15.4+ still lets use MediaRemote, so it sees every app's now playing (browsers too).
 */
class MediaRemoteAdapter(
    private val script: Path,
    private val framework: Path,
    private val testClient: Path,
    private val run: (List<String>, Long) -> Int = ::runQuiet,
) {
    /** The app packager strips executable mode from resources, so run a private temporary copy. */
    private val executableTestClient: Path by lazy {
        if (Files.isExecutable(testClient)) {
            testClient
        } else {
            val copy = Files.createTempFile("deskbuddy-mediaremote-test-client-", ".bin")
            try {
                Files.copy(testClient, copy, StandardCopyOption.REPLACE_EXISTING)
                check(copy.toFile().setExecutable(true, true)) { "Could not make the MediaRemote test client executable" }
                copy.toFile().deleteOnExit()
                copy
            } catch (failure: Throwable) {
                runCatching { Files.deleteIfExists(copy) }
                throw failure
            }
        }
    }

    /** Passing the optional test client for every command is supported by the upstream CLI. */
    fun command(vararg args: String): List<String> =
        listOf(PERL, script.toString(), framework.toString(), executableTestClient.toString()) + args

    fun startStream(): Process =
        ProcessBuilder(command("stream", "--micros", "--debounce=100")).redirectError(ProcessBuilder.Redirect.DISCARD).start()

    fun isWorking(): Boolean = run(command("test"), TEST_TIMEOUT_MS) == 0

    fun send(id: Int): Boolean = run(command("send", id.toString()), TIMEOUT_MS) == 0

    fun seek(positionMs: Long): Boolean =
        run(command("seek", (positionMs.coerceAtLeast(0) * 1_000).toString()), TIMEOUT_MS) == 0

    companion object {
        const val SEND_TOGGLE = 2
        const val SEND_NEXT = 4
        const val SEND_PREVIOUS = 5
        private const val PERL = "/usr/bin/perl"
        private const val TIMEOUT_MS = 3_000L
        private const val TEST_TIMEOUT_MS = 8_000L

        /** Compose sets the resource property for packaged apps; Gradle's hot-reload run may omit it. */
        fun locate(
            resourcesDir: String? = System.getProperty("compose.application.resources.dir"),
            workingDir: String? = System.getProperty("user.dir"),
        ): MediaRemoteAdapter? {
            val roots = buildList {
                resourcesDir?.let { add(Path.of(it)) }
                workingDir?.let { working ->
                    val dir = Path.of(working)
                    // `./gradlew :desktopApp:run` runs from desktopApp; IDEs may use the repo root.
                    add(dir.resolve("resources"))
                    add(dir.resolve("desktopApp/resources"))
                    dir.parent?.let { add(it.resolve("desktopApp/resources")) }
                }
            }
            // Compose may flatten the per-OS resources directory when preparing the app bundle.
            // Accept both that runtime layout and the source-tree `macos/` layout.
            val candidates = roots.flatMap { listOf(it.resolve("macos"), it) }.distinct()
            return candidates.firstNotNullOfOrNull { root ->
                val script = root.resolve("mediaremote-adapter.pl")
                val framework = root.resolve("MediaRemoteAdapter.framework")
                val testClient = root.resolve("MediaRemoteAdapterTestClient")
                if (Files.isRegularFile(script) && Files.isDirectory(framework) && Files.isRegularFile(testClient)) {
                    MediaRemoteAdapter(script, framework, testClient)
                } else {
                    null
                }
            }
        }

        private fun runQuiet(command: List<String>, timeoutMs: Long = TIMEOUT_MS): Int = runCatching {
            val process = ProcessBuilder(command)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start()
            if (!process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)) {
                process.destroyForcibly()
                -1
            } else {
                process.exitValue()
            }
        }.getOrDefault(-1)
    }
}
