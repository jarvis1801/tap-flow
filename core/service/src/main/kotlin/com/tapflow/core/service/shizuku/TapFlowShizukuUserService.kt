package com.tapflow.core.service.shizuku

import android.os.ParcelFileDescriptor
import androidx.annotation.Keep
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlin.system.exitProcess

@Keep
class TapFlowShizukuUserService : IShizukuShellService.Stub() {

  override fun execCommand(command: String?): Int {
    if (command.isNullOrBlank()) return -1
    return runCatching {
      val process = ProcessBuilder("sh", "-c", command).redirectErrorStream(true).start()
      process.inputStream.bufferedReader().use { it.readText() }
      process.waitFor()
    }.getOrElse { -1 }
  }

  override fun execCommandOutput(command: String?): String {
    if (command.isNullOrBlank()) return ""
    return runCatching {
      val process = ProcessBuilder("sh", "-c", command).redirectErrorStream(true).start()
      val output =
        BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
          reader.readText()
        }
      process.waitFor()
      output.trim()
    }.getOrElse { "" }
  }

  override fun captureScreencapPipe(): ParcelFileDescriptor? {
    return runCatching {
      val pipe = ParcelFileDescriptor.createPipe()
      val readEnd = pipe[0]
      val writeEnd = pipe[1]
      Thread {
        try {
          val process = ProcessBuilder("screencap", "-p").start()
          ParcelFileDescriptor.AutoCloseOutputStream(writeEnd).use { out ->
            process.inputStream.use { input ->
              input.copyTo(out)
            }
          }
          process.waitFor()
        } catch (_: Throwable) {
          runCatching { writeEnd.close() }
        }
      }.start()
      readEnd
    }.getOrNull()
  }

  override fun destroy() {
    exitProcess(0)
  }
}
