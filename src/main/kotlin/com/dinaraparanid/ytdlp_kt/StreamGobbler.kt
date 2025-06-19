package com.dinaraparanid.ytdlp_kt

import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader

internal class StreamGobbler(private val buffer: StringBuffer, private val stream: InputStream, private val onLine: (String) -> Unit = {}) : Thread() {
    init {
        start()
    }

    override fun run() {
        try {
            BufferedReader(InputStreamReader(stream)).use { br ->
                br.lines().forEach {
                    line -> onLine(line)
                    buffer.append(line)
                }
            }
        } catch (_: IOException) {
        }
    }
}