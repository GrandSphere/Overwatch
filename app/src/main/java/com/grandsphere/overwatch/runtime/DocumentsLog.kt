package com.grandsphere.overwatch.runtime

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

object DocumentsLog {
    private const val TAG = "DocumentsLog"
    const val FILE_NAME = "overwatch-log.txt"
    private const val RELATIVE_DIR = "Documents/Overwatch/"
    private val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
    private val mediaUris = ConcurrentHashMap<String, Uri>()

    fun overwatchDirs(context: Context): List<File> = listOfNotNull(
        context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)?.let { File(it, "Overwatch") },
        File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "Overwatch"),
    )

    fun publicDir(): File =
        File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "Overwatch")

    fun describeTargets(context: Context): String {
        val files = overwatchDirs(context).joinToString { it.absolutePath }
        return "files=$files mediaStore=${Build.VERSION.SDK_INT >= 29} relative=$RELATIVE_DIR"
    }

    fun resolveExistingNamed(context: Context, fileName: String): File? {
        val candidates = overwatchDirs(context).map { File(it, fileName) }
        return candidates
            .filter { it.exists() && it.isFile && it.length() > 0L }
            .maxByOrNull { it.length() }
            ?: candidates.firstOrNull { it.exists() && it.isFile }
    }

    fun append(context: Context, configName: String, message: String) {
        val line = "${stamp.format(Date())}\t$configName\t$message\n"
        appendRaw(context, line)
    }

    fun appendRaw(context: Context, line: String) {
        writeNamed(context, FILE_NAME, line)
    }

    fun writeNamed(context: Context, fileName: String, text: String, maxBytes: Long = 0L) {
        var wrote = false
        var publicWrote = false
        val publicPath = runCatching { publicDir().canonicalPath }.getOrNull()
        for (dir in overwatchDirs(context)) {
            val file = File(dir, fileName)
            if (maxBytes > 0L) rotateIfNeeded(file, maxBytes)
            if (appendToFile(file, text)) {
                wrote = true
                val dirPath = runCatching { dir.canonicalPath }.getOrNull()
                if (publicPath != null && dirPath == publicPath) publicWrote = true
            }
        }
        if (Build.VERSION.SDK_INT >= 29 && !publicWrote) {
            if (appendViaMediaStore(context, fileName, text)) wrote = true
        }
        if (!wrote) Log.w(TAG, "write failed $fileName")
    }

    fun resolveExistingFile(context: Context): File? = resolveExistingNamed(context, FILE_NAME)

    fun ensureShareableCopy(context: Context): File? {
        val src = resolveExistingFile(context) ?: return null
        if (src.length() <= 0L) return null
        val dest = File(context.cacheDir, FILE_NAME)
        src.copyTo(dest, overwrite = true)
        return dest
    }

    private fun rotateIfNeeded(file: File, maxBytes: Long) {
        if (!file.exists() || file.length() < maxBytes) return
        val bak = File(file.parentFile, "${file.name}.1")
        bak.delete()
        file.renameTo(bak)
    }

    private fun appendToFile(file: File, text: String): Boolean {
        return runCatching {
            val dir = file.parentFile ?: return false
            if (!dir.exists()) dir.mkdirs()
            file.appendText(text)
            true
        }.onFailure {
            Log.w(TAG, "append failed ${file.path}", it)
        }.getOrDefault(false)
    }

    private fun appendViaMediaStore(context: Context, fileName: String, text: String): Boolean {
        if (Build.VERSION.SDK_INT < 29) return false
        return runCatching {
            val resolver = context.contentResolver
            val collections = listOf(
                MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
                MediaStore.Files.getContentUri("external"),
            )
            val cached = mediaUris[fileName]
            if (cached != null && writeStream(resolver, cached, text)) {
                clearPending(resolver, cached)
                return true
            }
            mediaUris.remove(fileName)
            for (collection in collections) {
                val existing = findMediaStoreUri(resolver, collection, fileName)
                val target = existing ?: insertMediaStore(resolver, collection, fileName) ?: continue
                if (!writeStream(resolver, target, text)) continue
                clearPending(resolver, target)
                mediaUris[fileName] = target
                return true
            }
            false
        }.onFailure {
            Log.w(TAG, "MediaStore write failed $fileName", it)
        }.getOrDefault(false)
    }

    private fun writeStream(
        resolver: android.content.ContentResolver,
        uri: Uri,
        text: String,
    ): Boolean {
        resolver.openOutputStream(uri, "wa")?.use { out ->
            out.write(text.toByteArray(Charsets.UTF_8))
            out.flush()
        } ?: return false
        return true
    }

    private fun insertMediaStore(
        resolver: android.content.ContentResolver,
        collection: Uri,
        fileName: String,
    ): Uri? {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
            put(MediaStore.MediaColumns.RELATIVE_PATH, RELATIVE_DIR)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        return resolver.insert(collection, values)
    }

    private fun clearPending(resolver: android.content.ContentResolver, uri: Uri) {
        resolver.update(
            uri,
            ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
            null,
            null,
        )
    }

    private fun findMediaStoreUri(
        resolver: android.content.ContentResolver,
        collection: Uri,
        fileName: String,
    ): Uri? {
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.RELATIVE_PATH,
        )
        val selection = "${MediaStore.MediaColumns.DISPLAY_NAME}=?"
        resolver.query(collection, projection, selection, arrayOf(fileName), null)?.use { cursor ->
            val idIdx = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val pathIdx = cursor.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
            while (cursor.moveToNext()) {
                val path = if (pathIdx >= 0) cursor.getString(pathIdx).orEmpty() else ""
                if (path.contains("Overwatch")) {
                    return ContentUris.withAppendedId(collection, cursor.getLong(idIdx))
                }
            }
        }
        return null
    }
}
