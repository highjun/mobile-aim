package dev.sangjun.mobileaim.logging

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import java.io.File

/**
 * Where every MobileAim-written file for this session lives, under a single custom top-level
 * folder rather than nested in the standard `Pictures`/`Movies` roots — see
 * [dev.sangjun.mobileaim.app.ui.MainScreen]'s `MediaRoot` (same value, duplicated here since photo
 * capture and sensor logging are unrelated modules that shouldn't otherwise depend on each other).
 */
internal const val SensorMediaRoot = "MobileAim/sensors"
private const val CaptureLogFileName = "captures.csv"

/**
 * `captures.csv` is a single file appended to across the app's entire lifetime (not one per
 * session, unlike the motion logs) — so unlike a plain [File], which can just be reopened by path,
 * writing it through MediaStore means resolving the *same* row's [Uri] every time: query for it by
 * name once, insert it if it doesn't exist yet, then reuse.
 */
class CaptureLogWriter(private val context: Context) : ICaptureLogWriter {
    // Cached after the first resolve so repeated captures in one process don't requery MediaStore.
    private var cachedUri: Uri? = null

    override fun append(row: CaptureRow) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                appendViaMediaStore(row)
            } else {
                appendViaFile(row)
            }
        } catch (error: Exception) {
            Log.w(TAG, "Failed to append capture row", error)
        }
    }

    private fun appendViaMediaStore(row: CaptureRow) {
        val resolver = context.contentResolver
        val uri = cachedUri ?: (findExistingUri(resolver) ?: insertNewUri(resolver)).also { cachedUri = it }
        val needsHeader = !hasContent(resolver, uri)
        resolver.openOutputStream(uri, "wa")?.bufferedWriter()?.use { writer ->
            if (needsHeader) writer.appendLine(CaptureRow.CSV_HEADER)
            writer.appendLine(row.toCsvLine())
        }
    }

    private fun hasContent(resolver: ContentResolver, uri: Uri): Boolean {
        resolver.openInputStream(uri)?.use { return it.read() != -1 }
        return false
    }

    private fun findExistingUri(resolver: ContentResolver): Uri? {
        val projection = arrayOf(MediaStore.Downloads._ID)
        val selection = "${MediaStore.Downloads.DISPLAY_NAME} = ? AND ${MediaStore.Downloads.RELATIVE_PATH} = ?"
        // MediaStore always normalizes a stored RELATIVE_PATH to end with a trailing slash.
        val args = arrayOf(CaptureLogFileName, "$SensorMediaRoot/")
        resolver.query(MediaStore.Downloads.EXTERNAL_CONTENT_URI, projection, selection, args, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID))
                return ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, id)
            }
        }
        return null
    }

    private fun insertNewUri(resolver: ContentResolver): Uri {
        val contentValues = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, CaptureLogFileName)
            put(MediaStore.Downloads.MIME_TYPE, "text/csv")
            put(MediaStore.Downloads.RELATIVE_PATH, SensorMediaRoot)
        }
        return resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            ?: error("Failed to create $CaptureLogFileName in MediaStore")
    }

    private fun appendViaFile(row: CaptureRow) {
        val dir = File(context.getExternalFilesDir(null), SensorMediaRoot).apply { mkdirs() }
        val file = File(dir, CaptureLogFileName)
        val needsHeader = !file.exists()
        file.appendText(
            buildString {
                if (needsHeader) appendLine(CaptureRow.CSV_HEADER)
                appendLine(row.toCsvLine())
            },
        )
    }

    private companion object {
        const val TAG = "MobileAimCaptureLog"
    }
}
