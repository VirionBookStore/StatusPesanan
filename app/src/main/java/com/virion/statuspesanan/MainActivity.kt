package com.virion.statuspesanan

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import java.io.File

class MainActivity : ComponentActivity() {
    private lateinit var webView: WebView
    private val appUrl = "https://virionbookstore.github.io/StatusPesanan/"
    private var backPressedTime: Long = 0

    inner class AndroidShareInterface {
        @JavascriptInterface
        fun shareFile(fileName: String, base64Data: String) {
            runOnUiThread {
                try {
                    val safeName = fileName.replace(Regex("[\\\\/:*?"<>|]"), "_")
                    val shareDir = File(cacheDir, "shared")
                    if (!shareDir.exists()) shareDir.mkdirs()
                    shareDir.listFiles()?.forEach { it.delete() }

                    val file = File(shareDir, safeName)
                    val bytes = Base64.decode(base64Data, Base64.DEFAULT)
                    file.outputStream().use { it.write(bytes) }

                    val uri: Uri = FileProvider.getUriForFile(
                        this@MainActivity,
                        "$packageName.fileprovider",
                        file
                    )

                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        putExtra(Intent.EXTRA_TEXT, "Berikut adalah file laporan pesanan.")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }

                    startActivity(Intent.createChooser(shareIntent, "Bagikan file laporan"))
                } catch (e: Exception) {
                    Toast.makeText(
                        this@MainActivity,
                        "Gagal membagikan file: " + e.message,
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webView)

        ViewCompat.setOnApplyWindowInsetsListener(webView) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(view.paddingLeft, systemBars.top, view.paddingRight, systemBars.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(webView)

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            cacheMode = WebSettings.LOAD_DEFAULT
            allowFileAccess = false
            allowContentAccess = true
            userAgentString = userAgentString + " StatusPesananAPK"
        }

        webView.addJavascriptInterface(AndroidShareInterface(), "AndroidInterface")

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean {
                val url = request.url.toString()
                if (url.startsWith("https://") || url.startsWith("http://")) return false
                return try {
                    startActivity(Intent(Intent.ACTION_VIEW, request.url))
                    true
                } catch (_: Exception) {
                    false
                }
            }
        }

        webView.loadUrl(appUrl)

        onBackPressedDispatcher.addCallback(this) {
            if (webView.canGoBack()) {
                webView.goBack()
            } else {
                if (backPressedTime + 2000 > System.currentTimeMillis()) {
                    finish()
                } else {
                    Toast.makeText(
                        this@MainActivity,
                        "Tekan kembali sekali lagi untuk keluar",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                backPressedTime = System.currentTimeMillis()
            }
        }
    }

    override fun onDestroy() {
        webView.removeJavascriptInterface("AndroidInterface")
        webView.destroy()
        super.onDestroy()
    }
}
