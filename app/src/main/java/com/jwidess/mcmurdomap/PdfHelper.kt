package com.jwidess.mcmurdomap

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

object PdfHelper {

    fun openBuildingPdf(context: Context, pdfFileName: String) {
        try {
            val pdfDir = File(context.cacheDir, "pdfs")
            if (!pdfDir.exists()) {
                pdfDir.mkdirs()
            }

            val targetFile = File(pdfDir, pdfFileName)

            // Extract from offline assets if not present or empty
            if (!targetFile.exists() || targetFile.length() == 0L) {
                val assetPath = "assets/documents/$pdfFileName"
                try {
                    context.assets.open(assetPath).use { input ->
                        FileOutputStream(targetFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Specification PDF not found: $pdfFileName", Toast.LENGTH_SHORT).show()
                    return
                }
            }

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                targetFile
            )

            val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/pdf")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            val chooserIntent = Intent.createChooser(viewIntent, "Open Specification Sheet").apply {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            try {
                context.startActivity(chooserIntent)
            } catch (e: ActivityNotFoundException) {
                Toast.makeText(context, "No PDF viewer app found on device", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Error opening PDF: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }
}
