package dev.sangjun.mobileaim.feature.capture

import android.content.Context
import android.util.Log
import java.io.File

class CaptureLogWriter(private val context: Context) : ICaptureLogWriter {
    override fun append(row: CaptureRow) {
        try {
            val dir = File(context.getExternalFilesDir(null), "sessions").apply { mkdirs() }
            val file = File(dir, "captures.csv")
            val needsHeader = !file.exists()
            file.appendText(
                buildString {
                    if (needsHeader) appendLine(CaptureRow.CSV_HEADER)
                    appendLine(row.toCsvLine())
                },
            )
        } catch (error: Exception) {
            Log.w(TAG, "Failed to append capture row", error)
        }
    }

    private companion object {
        const val TAG = "MobileAimCaptureLog"
    }
}
