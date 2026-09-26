package com.virion.statuspesanan

import android.content.ClipData
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Intent
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import java.io.File
import java.io.FileNotFoundException

class MainActivity : ComponentActivity() {
    private lateinit var webView: WebView
    private val appUrl = "https://virionbookstore.github.io/StatusPesanan/"
    private var backPressedTime: Long = 0

    inner class AndroidShareInterface {
        @JavascriptInterface
        fun shareFile(fileName: String, base64Data: String) {
            runOnUiThread {
                try {
                    val safeName = fileName.replace(Regex("[^A-Za-z0-9._ -]"), "_")
                    val shareDir = File(cacheDir, "shared")
                    if (!shareDir.exists()) shareDir.mkdirs()
                    shareDir.listFiles()?.forEach { it.delete() }

                    val file = File(shareDir, safeName)
                    val bytes = Base64.decode(base64Data, Base64.DEFAULT)
                    file.outputStream().use { it.write(bytes) }

                    val uri = Uri.parse(
                        "content://" + packageName + ".sharedfiles/" + Uri.encode(safeName)
                    )

                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        putExtra(Intent.EXTRA_TEXT, "Berikut adalah file laporan pesanan.")
                        clipData = ClipData.newRawUri("Laporan Pesanan", uri)
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
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url.toString()
                if (url.startsWith("https://") || url.startsWith("http://")) return false
                return try {
                    startActivity(Intent(Intent.ACTION_VIEW, request.url))
                    true
                } catch (_: Exception) { false }
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
                    Toast.makeText(this@MainActivity, "Tekan kembali sekali lagi untuk keluar", Toast.LENGTH_SHORT).show()
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

class ShareFileProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun getType(uri: Uri): String =
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (mode != "r") throw SecurityException("Read only")
        val context = context ?: throw IllegalStateException("Context tidak tersedia")
        val name = Uri.decode(uri.lastPathSegment ?: "")
        val safeName = name.replace(Regex("[^A-Za-z0-9._ -]"), "_")
        val file = File(File(context.cacheDir, "shared"), safeName)
        if (!file.exists()) throw FileNotFoundException(file.absolutePath)
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor {
        val context = context ?: throw IllegalStateException("Context tidak tersedia")
        val name = Uri.decode(uri.lastPathSegment ?: "")
        val file = File(File(context.cacheDir, "shared"), name)
        val cursor = MatrixCursor(arrayOf("_display_name", "_size"))
        if (file.exists()) cursor.addRow(arrayOf(file.name, file.length()))
        return cursor
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = throw UnsupportedOperationException("Read only")
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = throw UnsupportedOperationException("Read only")
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = throw UnsupportedOperationException("Read only")
}