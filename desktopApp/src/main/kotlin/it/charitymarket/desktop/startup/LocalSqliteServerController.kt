package it.charitymarket.desktop.startup

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import it.charitymarket.shared.config.serverConnectionCandidates
import java.io.IOException
import java.io.File
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.TimeUnit
import kotlin.concurrent.timer


class LocalSqliteServerController {

    private val processLock = Any()
    @Volatile
    private var localServerProcess: Process? = null

    @Volatile
    private var lastLocalConfig: LocalSqliteServerConfig? = null

    @Volatile
    private var lastLogFile: File? = null

    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(3))
        .build()

    init {
        Runtime.getRuntime().addShutdownHook(
            Thread {
                stopLocalServerBlocking(
                    timeoutSeconds = 10
                )
            }
        )
    }

    suspend fun connectToServer(
        rawBaseUrl: String
    ): ServerConnection = withContext(Dispatchers.IO) {
        val candidateBaseUrls = serverConnectionCandidates(rawBaseUrl)

        candidateBaseUrls.forEachIndexed { index, baseUrl ->
            try {
                verifyHealth(baseUrl)
            } catch (exception: Exception) {
                if (
                    exception.isNetworkConnectionFailure() &&
                    index < candidateBaseUrls.lastIndex
                ) {
                    return@forEachIndexed
                }
                throw exception
            }

            return@withContext ServerConnection(
                baseUrl = baseUrl,
                mode = RemoteClientMode
            )
        }

        error("No server address candidates were available.")
    }

    suspend fun startLocalServer(
        config: LocalSqliteServerConfig
    ): ServerConnection = withContext(Dispatchers.IO) {
        validateLocalConfiguration(config)

        check(!isLocalServerRunning()) {
            "A local server is already running."
        }
        checkPortAvailable(config.port)

        val serverDirectory = resolveServerDirectory()
        val serverJar = serverDirectory.resolve("quarkus-run.jar")

        check(serverJar.isFile) {
            "quarkus-run.jar was not found at ${serverJar.absolutePath}"
        }

        val dataDirectory = Path.of(config.dataDirectory)
            .toAbsolutePath()
            .normalize()

        Files.createDirectories(dataDirectory)

        val databaseFile = dataDirectory.resolve(
            "charity-market.db"
        )

        val logFile = dataDirectory.resolve(
            "charity-market-server.log"
        ).toFile()

        lastLocalConfig = config.copy(
            dataDirectory = dataDirectory.toString(),
            administratorUsername =
                config.administratorUsername.trim()
        )
        lastLogFile = logFile

        val jdbcPath = databaseFile
            .toString()
            .replace('\\', '/')

        val jdbcUrl = "jdbc:sqlite:$jdbcPath"

        val javaExecutable = resolveJavaExecutable()

        val command = listOf(
            javaExecutable.absolutePath,

            "-Dquarkus.profile=sqlite",
            "-Dquarkus.http.host=0.0.0.0",
            "-Dquarkus.http.port=${config.port}",

            "-Dquarkus.datasource.jdbc.url=$jdbcUrl",
            "-Dquarkus.datasource.jdbc.min-size=1",
            "-Dquarkus.datasource.jdbc.max-size=1",

            "-Dcharity.bootstrap.admin.username=" +
                    config.administratorUsername.trim(),

            "-jar",
            serverJar.absolutePath
        )

        val processBuilder = ProcessBuilder(command)
            .directory(serverDirectory)
            .redirectErrorStream(true)
            .redirectOutput(
                ProcessBuilder.Redirect.appendTo(logFile)
            )

        /*
         * Do not place the administrator password
         * in the process command line.
         */
        processBuilder.environment().apply {
            put(
                "CHARITY_BOOTSTRAP_ADMIN_PASSWORD",
                config.administratorPassword
            )

            put(
                "CHARITY_BOOTSTRAP_ADMIN_MUST_CHANGE_PASSWORD",
                "false"
            )
        }

        val process = processBuilder.start()

        synchronized(processLock) {
            localServerProcess = process
        }

        process.onExit().thenRun {
            clearProcessReference(process)
        }

        val baseUrl = "http://127.0.0.1:${config.port}"

        try {
            waitForServer(
                process = process,
                baseUrl = baseUrl,
                logFile = logFile
            )
        } catch (exception: Exception) {
            stopLocalServer()
            throw exception
        }

        ServerConnection(
            baseUrl = baseUrl,
            mode = LocalHostMode(
                dataDirectory = dataDirectory.toString(),
                port = config.port,
                logFile = logFile.absolutePath
            )
        )
    }

    fun stopLocalServer() {
        stopLocalServerBlocking(
            timeoutSeconds = 10
        )
    }

    suspend fun stopLocalServerGracefully(
        timeoutSeconds: Long = 10
    ): LocalServerShutdownResult =
        withContext(Dispatchers.IO) {
            stopLocalServerBlocking(
                timeoutSeconds
            )
        }

    suspend fun restartLastLocalServer(): ServerConnection =
        withContext(Dispatchers.IO) {
            val config = lastLocalConfig
                ?: throw IllegalStateException(
                    "No local server configuration is available for restart."
                )

            startLocalServer(config)
        }

    fun lastLocalLogFile(): String? {
        return lastLogFile?.absolutePath
    }

    fun isLocalServerRunning(): Boolean =
        synchronized(processLock) {
            localServerProcess?.isAlive == true
        }

    private fun stopLocalServerBlocking(
        timeoutSeconds: Long
    ): LocalServerShutdownResult {
        val process = synchronized(processLock) {
            localServerProcess
        } ?: return LocalServerShutdownResult(
            status =
                LocalServerShutdownStatus.ALREADY_STOPPED,
            message =
                "The local server was not running."
        )

        if (!process.isAlive) {
            clearProcessReference(process)

            return LocalServerShutdownResult(
                status =
                    LocalServerShutdownStatus.ALREADY_STOPPED,
                message =
                    "The local server had already stopped."
            )
        }

        return try {
            /*
             * Ask Quarkus to stop normally first.
             */
            process.destroy()

            val stoppedGracefully =
                process.waitFor(
                    timeoutSeconds,
                    TimeUnit.SECONDS
                )

            if (stoppedGracefully || !process.isAlive) {
                clearProcessReference(process)

                LocalServerShutdownResult(
                    status =
                        LocalServerShutdownStatus
                            .STOPPED_GRACEFULLY,
                    message =
                        "The local server stopped gracefully."
                )
            } else {
                /*
                 * On Windows this terminates the complete
                 * Java process tree.
                 */
                forceStopProcessTree(process)

                val stoppedForcibly =
                    process.waitFor(
                        10,
                        TimeUnit.SECONDS
                    )

                if (stoppedForcibly || !process.isAlive) {
                    clearProcessReference(process)

                    LocalServerShutdownResult(
                        status =
                            LocalServerShutdownStatus
                                .FORCED_STOP,
                        message =
                            "The local server did not stop in time and was forced to exit."
                    )
                } else {
                    /*
                     * Do not clear the reference here.
                     * The process is still alive.
                     */
                    LocalServerShutdownResult(
                        status =
                            LocalServerShutdownStatus.FAILED,
                        message =
                            "The local server process is still running."
                    )
                }
            }
        } catch (exception: Exception) {
            if (!process.isAlive) {
                clearProcessReference(process)
            }

            LocalServerShutdownResult(
                status =
                    LocalServerShutdownStatus.FAILED,
                message =
                    exception.message
                        ?: "The local server shutdown failed."
            )
        }
    }

    private fun validateLocalConfiguration(
        config: LocalSqliteServerConfig
    ) {
        require(config.port in 1..65535) {
            "The server port must be between 1 and 65535."
        }

        require(config.dataDirectory.isNotBlank()) {
            "The data directory is required."
        }

        require(config.administratorUsername.isNotBlank()) {
            "The administrator username is required."
        }

        require(
            config.administratorPassword.length >= 8
        ) {
            "The administrator password must contain at least 8 characters."
        }
    }

    private fun checkPortAvailable(port: Int) {
        try {
            ServerSocket().use { socket ->
                socket.reuseAddress = true

                socket.bind(
                    InetSocketAddress(
                        "0.0.0.0",
                        port
                    )
                )
            }
        } catch (exception: Exception) {
            throw IllegalStateException(
                "Port $port is already in use.",
                exception
            )
        }
    }

    private fun resolveJavaExecutable(): File {
        val runtime =
            JavaRuntimeLocator.findCompatibleRuntime(
                privateRuntimeDirectory = null
            )

        return runtime?.javaExecutable
            ?: throw IllegalStateException(
                "Java 17 or newer is required to host the local server. " +
                        "Install Java and restart Charity Market."
            )
    }

    private fun resolveServerDirectory(): File {
        val candidates = mutableListOf<File>()

        /*
         * Used by an installed Compose desktop application.
         */
        System.getProperty(
            "compose.application.resources.dir"
        )
            ?.takeIf { it.isNotBlank() }
            ?.let { resourcesDirectory ->
                candidates += File(
                    resourcesDirectory,
                    "local-server"
                )
            }

        /*
         * Development mode when working directory is the
         * root charity-market project.
         */
        val workingDirectory = File(
            System.getProperty("user.dir")
        )

        candidates += File(
            workingDirectory,
            "server/target/quarkus-app"
        )

        /*
         * Development mode when working directory is
         * desktopApp.
         */
        candidates += File(
            workingDirectory,
            "../server/target/quarkus-app"
        )

        /*
         * Development fallback for a server explicitly synced into
         * the desktop application resources.
         */
        candidates += File(
            workingDirectory,
            "desktopApp/app-resources/common/local-server"
        )
        candidates += File(
            workingDirectory,
            "app-resources/common/local-server"
        )

        return candidates
            .map { it.absoluteFile.normalize() }
            .firstOrNull {
                File(it, "quarkus-run.jar").isFile
            }
            ?: throw IllegalStateException(
                buildString {
                    appendLine(
                        "The packaged SQLite server was not found."
                    )

                    appendLine("Searched:")

                    candidates.forEach {
                        appendLine(
                            " - ${it.absolutePath}"
                        )
                    }

                    appendLine()
                    appendLine("Build it with:")
                    appendLine(
                        ".\\server\\mvnw.cmd clean package \"-Psqlite\" \"-DskipTests\""
                    )
                }
            )
    }

    private fun verifyHealth(baseUrl: String) {
        val request = HttpRequest.newBuilder()
            .uri(
                URI.create("$baseUrl/q/health")
            )
            .timeout(Duration.ofSeconds(5))
            .GET()
            .build()

        val response = httpClient.send(
            request,
            HttpResponse.BodyHandlers.ofString()
        )

        check(response.statusCode() in 200..299) {
            "The server health check returned HTTP ${response.statusCode()}."
        }
    }

    private fun waitForServer(
        process: Process,
        baseUrl: String,
        logFile: File
    ) {
        val timeoutAt =
            System.nanoTime() +
                    Duration.ofSeconds(30).toNanos()

        var lastError: Exception? = null

        while (System.nanoTime() < timeoutAt) {
            if (!process.isAlive) {
                throw IllegalStateException(
                    buildString {
                        appendLine(
                            "The local server exited during startup."
                        )

                        appendLine()
                        appendLine(readLogTail(logFile))
                    }
                )
            }

            try {
                verifyHealth(baseUrl)
                return
            } catch (exception: Exception) {
                lastError = exception
            }

            Thread.sleep(300)
        }

        throw IllegalStateException(
            buildString {
                appendLine(
                    "The local server did not become ready within 30 seconds."
                )

                if (lastError?.message != null) {
                    appendLine(lastError.message)
                }

                appendLine()
                appendLine(readLogTail(logFile))
            }
        )
    }

    private fun readLogTail(
        logFile: File
    ): String {
        if (!logFile.isFile) {
            return "No server log was created."
        }

        return runCatching {
            logFile.readLines()
                .takeLast(30)
                .joinToString(
                    separator = System.lineSeparator()
                )
        }.getOrElse {
            "Unable to read ${logFile.absolutePath}"
        }
    }

    private fun clearProcessReference(
        process: Process
    ) {
        synchronized(processLock) {
            if (localServerProcess === process) {
                localServerProcess = null
            }
        }
    }

    private fun forceStopProcessTree(
        process: Process
    ) {
        val isWindows =
            System.getProperty("os.name")
                .contains(
                    "Windows",
                    ignoreCase = true
                )

        if (isWindows) {
            /*
             * /T terminates child processes.
             * /F forces termination.
             */
            runCatching {
                val taskKillProcess = ProcessBuilder(
                    "taskkill.exe",
                    "/PID",
                    process.pid().toString(),
                    "/T",
                    "/F"
                )
                    .redirectErrorStream(true)
                    .redirectOutput(
                        ProcessBuilder.Redirect.DISCARD
                    )
                    .start()

                taskKillProcess.waitFor(
                    10,
                    TimeUnit.SECONDS
                )
            }

            if (!process.isAlive) {
                return
            }
        }

        /*
         * Cross-platform fallback.
         */
        runCatching {
            process.descendants().forEach { child ->
                runCatching {
                    child.destroyForcibly()
                }
            }
        }

        if (process.isAlive) {
            process.destroyForcibly()
        }
    }

    private fun Exception.isNetworkConnectionFailure(): Boolean =
        this is IOException
}
