package com.jwidess.mcmurdomap

import android.content.Context
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import java.io.ByteArrayInputStream
import java.io.InputStream

class LocalAssetWebViewClient(
    private val context: Context,
    private val onPageFinishedCallback: () -> Unit = {}
) : WebViewClient() {

    override fun shouldInterceptRequest(
        view: WebView?,
        request: WebResourceRequest?
    ): WebResourceResponse? {
        val uri = request?.url ?: return null
        val host = uri.host ?: ""

        if (host == "mcmurdo.internal" || host == "localhost" || host == "appassets.androidplatform.net") {
            return handleAssetRequest(uri)
        }

        return super.shouldInterceptRequest(view, request)
    }

    private fun handleAssetRequest(uri: Uri): WebResourceResponse? {
        var path = uri.path?.removePrefix("/") ?: ""
        if (path.isEmpty() || path == "index.html" || path == "map") {
            path = "map_view.html"
        }

        val mimeType = getMimeType(path)

        return try {
            val stream: InputStream = try {
                context.assets.open(path)
            } catch (e: Exception) {
                if (path.startsWith("assets/img/") && !path.startsWith("assets/img/buildings/")) {
                    val buildingPath = path.replace("assets/img/", "assets/img/buildings/")
                    try {
                        context.assets.open(buildingPath)
                    } catch (e2: Exception) {
                        if (mimeType.startsWith("image/")) {
                            context.assets.open("assets/img/no-image.png")
                        } else throw e2
                    }
                } else if (mimeType.startsWith("image/")) {
                    context.assets.open("assets/img/no-image.png")
                } else {
                    return WebResourceResponse(
                        "text/plain",
                        "UTF-8",
                        404,
                        "Not Found",
                        mapOf("Access-Control-Allow-Origin" to "*"),
                        ByteArrayInputStream("Asset not found: $path".toByteArray())
                    )
                }
            }

            val headers = mutableMapOf(
                "Access-Control-Allow-Origin" to "*",
                "Cache-Control" to "no-cache"
            )

            WebResourceResponse(mimeType, "UTF-8", 200, "OK", headers, stream)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun getMimeType(path: String): String {
        val lower = path.lowercase()
        return when {
            lower.endsWith(".html") -> "text/html"
            lower.endsWith(".css") -> "text/css"
            lower.endsWith(".js") -> "application/javascript"
            lower.endsWith(".json") -> "application/json"
            lower.endsWith(".svg") -> "image/svg+xml"
            lower.endsWith(".png") -> "image/png"
            lower.endsWith(".jpg") || lower.endsWith(".jpeg") -> "image/jpeg"
            lower.endsWith(".woff2") -> "font/woff2"
            lower.endsWith(".pdf") -> "application/pdf"
            lower.endsWith(".ico") -> "image/x-icon"
            else -> "application/octet-stream"
        }
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        onPageFinishedCallback()
    }
}
