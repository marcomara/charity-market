package it.charitymarket.desktop.startup

enum class StartupMode {
    CONNECT_TO_SERVER,
    HOST_LOCAL_SQLITE
}

sealed interface ServerConnectionMode

data object RemoteClientMode : ServerConnectionMode

data class LocalHostMode(
    val dataDirectory: String,
    val port: Int,
    val logFile: String
) : ServerConnectionMode

data class ServerConnection(
    val baseUrl: String,
    val mode: ServerConnectionMode
) {
    val isLocalServer: Boolean
        get() = mode is LocalHostMode
}

data class LocalSqliteServerConfig(
    val dataDirectory: String,
    val port: Int,
    val administratorUsername: String,
    val administratorPassword: String
)

data class StartupLoginCredentials(
    val username: String,
    val password: String
)

enum class LocalServerShutdownStatus {
    ALREADY_STOPPED,
    STOPPED_GRACEFULLY,
    FORCED_STOP,
    FAILED
}

data class LocalServerShutdownResult(
    val status: LocalServerShutdownStatus,
    val message: String
)
