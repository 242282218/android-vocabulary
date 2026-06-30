package com.zzz.androidvocab.feature.settings

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.zzz.androidvocab.core.model.ExportResult
import java.io.File

internal fun Context.shareExport(result: ExportResult) {
    val file = File(result.absolutePath)
    val uri =
        FileProvider.getUriForFile(
            this,
            "$packageName.fileprovider",
            file,
        )
    val shareIntent =
        Intent(Intent.ACTION_SEND).apply {
            type = EXPORT_MIME_TYPE
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, result.fileName)
            putExtra(Intent.EXTRA_TITLE, result.fileName)
            clipData = ClipData.newUri(contentResolver, result.fileName, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    startActivity(Intent.createChooser(shareIntent, "分享导出文件"))
}

private const val EXPORT_MIME_TYPE = "application/json"
