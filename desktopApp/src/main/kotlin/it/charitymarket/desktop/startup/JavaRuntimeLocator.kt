package it.charitymarket.desktop.startup

import java.io.File
import java.util.concurrent.TimeUnit



data class DetectedJavaRuntime(
    val javaExecutable: File,
    val version: Int,
    val source: JavaRuntimeSource
)


enum class JavaRuntimeSource {
    PRIVATE,
    JAVA_HOME,
    PATH,
    WINDOWS_REGISTRY
}


object JavaRuntimeLocator {

    private const val MINIMUM_JAVA_VERSION = 17

    fun findCompatibleRuntime(
        privateRuntimeDirectory: File?
    ): DetectedJavaRuntime? {
        val candidates = buildList {
            privateRuntimeDirectory
                ?.resolve("bin/java.exe")
                ?.let {
                    add(
                        it to JavaRuntimeSource.PRIVATE
                    )
                }

            System.getenv("JAVA_HOME")
                ?.takeIf { it.isNotBlank() }
                ?.let(::File)
                ?.resolve("bin/java.exe")
                ?.let {
                    add(
                        it to JavaRuntimeSource.JAVA_HOME
                    )
                }

            findJavaOnPath()?.let {
                add(
                    it to JavaRuntimeSource.PATH
                )
            }

            findJavaInRegistry().forEach {
                add(
                    it to
                            JavaRuntimeSource.WINDOWS_REGISTRY
                )
            }
        }

        return candidates
            .distinctBy {
                it.first.absolutePath.lowercase()
            }
            .mapNotNull { (file, source) ->
                detectRuntime(
                    file = file,
                    source = source
                )
            }
            .firstOrNull {
                it.version >=
                        MINIMUM_JAVA_VERSION
            }
    }

    private fun detectRuntime(
        file: File,
        source: JavaRuntimeSource
    ): DetectedJavaRuntime? {
        if (!file.isFile) {
            return null
        }

        val process = runCatching {
            ProcessBuilder(
                file.absolutePath,
                "-version"
            )
                .redirectErrorStream(true)
                .start()
        }.getOrNull() ?: return null

        val finished = process.waitFor(
            10,
            TimeUnit.SECONDS
        )

        if (!finished) {
            process.destroyForcibly()
            return null
        }

        val output = process
            .inputStream
            .bufferedReader()
            .use {
                it.readText()
            }

        val version =
            parseMajorVersion(output)
                ?: return null

        return DetectedJavaRuntime(
            javaExecutable = file,
            version = version,
            source = source
        )
    }

    private fun parseMajorVersion(
        output: String
    ): Int? {
        val match = Regex(
            """version\s+"([^"]+)""""
        ).find(output)
            ?: Regex(
                """(?:openjdk|java)\s+(\d+)"""
            ).find(output)

        val versionText =
            match?.groupValues?.getOrNull(1)
                ?: return null

        val firstNumber =
            versionText
                .substringBefore(".")
                .toIntOrNull()
                ?: return null

        return if (firstNumber == 1) {
            versionText
                .split(".")
                .getOrNull(1)
                ?.toIntOrNull()
        } else {
            firstNumber
        }
    }

    private fun findJavaOnPath(): File? {
        val process = runCatching {
            ProcessBuilder(
                "where.exe",
                "java.exe"
            )
                .redirectErrorStream(true)
                .start()
        }.getOrNull() ?: return null

        if (!process.waitFor(
                5,
                TimeUnit.SECONDS
            )
        ) {
            process.destroyForcibly()
            return null
        }

        return process
            .inputStream
            .bufferedReader()
            .readLines()
            .firstOrNull()
            ?.trim()
            ?.takeIf {
                it.isNotBlank()
            }
            ?.let(::File)
    }

    private fun findJavaInRegistry(): List<File> {
        /*
         * Implement only when your project already has
         * a Windows-registry dependency.
         *
         * JAVA_HOME and PATH cover most installations.
         */
        return emptyList()
    }
}