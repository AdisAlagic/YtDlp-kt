package com.dinaraparanid.ytdlp_kt

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException

object YtDlp : CoroutineScope by CoroutineScope(Dispatchers.IO) {
    private val json = Json { ignoreUnknownKeys = true }

    @Volatile
    private var isYoutubeDLUpdateTaskStarted = false

    private fun buildCommand(command: String, isPythonExecutable: Boolean) =
        "${if (isPythonExecutable) "python3 " else ""}yt-dlp $command"

    private fun executeWithResponseOrThrow(request: YtDlpRequest, isPythonExecutable: Boolean, onEachLine: (String) -> Unit = {}): YtDlpResponse {
        val directory = request.directory
        val options = request.options
        val outBuffer = StringBuffer() //stdout
        val errBuffer = StringBuffer() //stderr

        val startTime = System.nanoTime()
        val command = buildCommand(request.buildOptions(), isPythonExecutable)
        val commandArr = java.lang.String(buildCommand(request.buildOptions(), isPythonExecutable)).split(" ")

        val processBuilder = ProcessBuilder(*commandArr).also { builder ->
            directory?.let(::File)?.let(builder::directory)
        }

        val process = try {
            processBuilder.start()
        } catch (e: IOException) {
            throw YtDlpException(e)
        }

        val outStream = process.inputStream
        val errStream = process.errorStream

        StreamGobbler(outBuffer, outStream, onEachLine)
        StreamGobbler(errBuffer, errStream, onEachLine)

        val exitCode = try {
            process.waitFor()
        } catch (e: InterruptedException) {
            throw YtDlpException(e)
        }

        val out = outBuffer.toString()
        val err = errBuffer.toString()

        if (exitCode > 0)
            throw YtDlpException(err)

        val elapsedTime = ((System.nanoTime() - startTime) / 1000000).toInt()
        return YtDlpResponse(command, options, directory, exitCode, elapsedTime, out, err)
    }

    /**
     * Executes provided request or returns
     * [YtDlpRequestStatus.Error] if something went wrong.
     * Blocks current thread until the end of execution
     * @param request request to execute
     * @return [YtDlpRequestStatus.Success] with [YtDlpResponse]
     * or [YtDlpRequestStatus.Error] if something went wrong
     */

    @JvmStatic
    @JvmName("execute")
    fun execute(request: YtDlpRequest, isPythonExecutable: Boolean, onEachLine: (String) -> Unit = {}) =
        kotlin.runCatching {
            YtDlpRequestStatus.Success(executeWithResponseOrThrow(request, isPythonExecutable, onEachLine))
        }.getOrElse {  exception ->
            ConversionException(exception).error
        }

    /**
     * Executes provided request asynchronously or returns
     * [YtDlpRequestStatus.Error] if something went wrong
     * @param request request to execute
     * @return [YtDlpRequestStatus.Success] with [YtDlpResponse]
     * or [YtDlpRequestStatus.Error] if something went wrong
     */

    @JvmStatic
    @JvmName("executeAsync")
    fun executeAsync(request: YtDlpRequest, isPythonExecutable: Boolean, onEachLine: (String) -> Unit = {}) = async {
        execute(request, isPythonExecutable, onEachLine)
    }

    /**
     * Updates yt-dlp on the device.
     * Blocks current thread until the end of execution
     */

    @JvmStatic
    @JvmName("update")
    fun update(isPythonExecutable: Boolean) {
        if (isYoutubeDLUpdateTaskStarted)
            return

        isYoutubeDLUpdateTaskStarted = true
        Runtime.getRuntime().exec(buildCommand("-U", isPythonExecutable)).waitFor()
        isYoutubeDLUpdateTaskStarted = false
    }

    /** Updates yt-dlp on the device asynchronously */

    @JvmStatic
    @JvmName("updateAsync")
    fun updateAsync(isPythonExecutable: Boolean) = launch { update(isPythonExecutable) }

    /**
     * Gets [VideoInfo] by url or returns
     * [YtDlpRequestStatus.Error] if something went wrong.
     * Blocks current thread until the end of execution
     * @param url url of searchable video
     * @return [YtDlpRequestStatus.Success] with [VideoInfo]
     * or [YtDlpRequestStatus.Error] if something went wrong
     */

    @JvmStatic
    @JvmName("getVideoData")
    fun getVideoData(url: String, isPythonExecutable: Boolean, onEachLine: (String) -> Unit = {}) =
        kotlin.runCatching {
            YtDlpRequest(url)
                .apply {
                    setOption("--dump-json")
                    setOption("--no-playlist")
                }
                .let { executeWithResponseOrThrow(it, isPythonExecutable, onEachLine) }
                .let(YtDlpResponse::out)
                .let<String, VideoInfo>(json::decodeFromString)
                .withFileNameWithoutExt
                .let(YtDlpRequestStatus::Success)
        }.getOrElse { exception ->
            ConversionException(exception).error
        }

    /**
     * Gets [VideoInfo] by url asynchronously or returns
     * [YtDlpRequestStatus.Error] if something went wrong.
     * @param url url of searchable video
     * @return [YtDlpRequestStatus.Success] with [VideoInfo]
     * or [YtDlpRequestStatus.Error] if something went wrong
     */

    @JvmStatic
    @JvmName("getVideoDataAsync")
    fun getVideoDataAsync(url: String, isPythonExecutable: Boolean) = async {
        getVideoData(url, isPythonExecutable)
    }
}