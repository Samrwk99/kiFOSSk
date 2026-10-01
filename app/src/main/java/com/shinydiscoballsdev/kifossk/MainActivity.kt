package com.shinydiscoballsdev.kifossk

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.content.ContentValues
import android.util.Base64
import android.webkit.CookieManager
import android.webkit.DownloadListener
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.io.File
import java.io.FileOutputStream

@SuppressLint("SetJavaScriptEnabled")
class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private val fileChooserResultCode = 1

    companion object {
        private const val PREFS_NAME = "sillytavern_prefs"
        const val EXTRA_RELOAD = "extra_reload"
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        results.forEach { (_, granted) ->
            if (granted) android.util.Log.d("Permissions", "granted")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestPermissions()
        setupWebView()
    }

    private fun requestPermissions() {
        val perms = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED) {
                perms.add(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) !=
            PackageManager.PERMISSION_GRANTED) {
            perms.add(android.Manifest.permission.CAMERA)
        }
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED) {
            perms.add(android.Manifest.permission.RECORD_AUDIO)
        }
        if (perms.isNotEmpty()) {
            requestPermissionLauncher.launch(perms.toTypedArray())
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        val url = KioskPrefs.getUrl(this)

        val orientation = KioskPrefs.getOrientation(this)
        when (orientation) {
            "landscape" -> requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            "portrait" -> requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            "auto" -> requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }

        if (KioskPrefs.getScreenOn(this)) {
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

        webView = WebView(this)
        webView.setBackgroundColor(android.graphics.Color.parseColor("#1a1a2e"))

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            builtInZoomControls = true
            displayZoomControls = false
            useWideViewPort = true
            loadWithOverviewMode = true
            cacheMode = WebSettings.LOAD_DEFAULT
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            allowFileAccess = true
            allowContentAccess = true
            mediaPlaybackRequiresUserGesture = false
        }

        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)

        webView.addJavascriptInterface(BlobDownloaderInterface(this), "BlobDownloader")

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url?.toString() ?: return false

                // Intercept blob: navigations (window.open, location=, etc)
                if (url.startsWith("blob:")) {
                    val safeUrl = url.replace("\"", "\\\"")
                    view?.evaluateJavascript(
                        """
                        (function() {
                            var blob = window.__blobRegistry && window.__blobRegistry["$safeUrl"];
                            if (blob) {
                                var reader = new FileReader();
                                reader.onloadend = function() {
                                    var base64 = reader.result.split(',')[1];
                                    var mime = blob.type || 'application/octet-stream';
                                    var filename = 'download';
                                    BlobDownloader.downloadBase64(base64, mime, filename);
                                };
                                reader.readAsDataURL(blob);
                            } else {
                                fetch("$safeUrl")
                                    .then(r => r.blob())
                                    .then(blob => {
                                        var reader = new FileReader();
                                        reader.onloadend = function() {
                                            var base64 = reader.result.split(',')[1];
                                            var mime = blob.type || 'application/octet-stream';
                                            BlobDownloader.downloadBase64(base64, mime, 'download');
                                        };
                                        reader.readAsDataURL(blob);
                                    })
                                    .catch(e => console.log('Blob fetch failed', e));
                            }
                        })();
                        """.trimIndent(), null
                    )
                    return true
                }

                // Intercept data: URLs
                if (url.startsWith("data:")) {
                    val commaIndex = url.indexOf(",")
                    if (commaIndex > 0) {
                        val header = url.substring(0, commaIndex)
                        val base64Data = url.substring(commaIndex + 1)
                        val mime = header.substringAfter("data:").substringBefore(";")
                        val filename = "download" + mimeToExtension(mime)
                        BlobDownloaderInterface(this@MainActivity).downloadBase64(base64Data, mime, filename)
                    }
                    return true
                }

                return false
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                injectBlobInterceptor()
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                this@MainActivity.filePathCallback?.onReceiveValue(null)
                this@MainActivity.filePathCallback = filePathCallback

                val intent = fileChooserParams?.createIntent() ?: return false
                try {
                    startActivityForResult(intent, fileChooserResultCode)
                } catch (e: Exception) {
                    this@MainActivity.filePathCallback = null
                    return false
                }
                return true
            }

            override fun onPermissionRequest(request: PermissionRequest?) {
                request?.grant(request.resources)
            }
        }

        webView.setDownloadListener(DownloadListener { url, userAgent, contentDisposition, mimeType, contentLength ->
            if (url.startsWith("blob:")) return@DownloadListener

            val request = DownloadManager.Request(Uri.parse(url))
            request.setMimeType(mimeType)
            val cookies = CookieManager.getInstance().getCookie(url)
            request.addRequestHeader("cookie", cookies)
            request.addRequestHeader("User-Agent", userAgent)
            request.setDescription("Downloading...")
            request.setTitle(URLUtil.guessFileName(url, contentDisposition, mimeType))
            request.allowScanningByMediaScanner()
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            request.setDestinationInExternalPublicDir(
                Environment.DIRECTORY_DOWNLOADS,
                URLUtil.guessFileName(url, contentDisposition, mimeType)
            )

            val dm = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            dm.enqueue(request)
            Toast.makeText(applicationContext, "Downloading file...", Toast.LENGTH_LONG).show()
        })

        setContentView(webView)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                startActivity(Intent(this@MainActivity, SettingsActivity::class.java))
            }
        })

        webView.loadUrl(url)
    }

    private fun injectBlobInterceptor() {
        val js = """
            (function() {
                if (window.__blobInterceptorInstalled) return;
                window.__blobInterceptorInstalled = true;
                window.__blobRegistry = window.__blobRegistry || {};

                var origCreateObjectURL = URL.createObjectURL;
                URL.createObjectURL = function(blob) {
                    var url = origCreateObjectURL.call(this, blob);
                    window.__blobRegistry[url] = blob;
                    return url;
                };

                var origRevokeObjectURL = URL.revokeObjectURL;
                URL.revokeObjectURL = function(url) {
                    delete window.__blobRegistry[url];
                    origRevokeObjectURL.call(this, url);
                };

                // Intercept programmatic clicks on <a download> with blob hrefs
                function interceptDownload(el) {
                    var href = el.getAttribute('href');
                    if (!href || !href.startsWith('blob:')) return false;
                    var blob = window.__blobRegistry[href];
                    var filename = el.getAttribute('download') || 'download';
                    if (blob) {
                        var reader = new FileReader();
                        reader.onloadend = function() {
                            var base64 = reader.result.split(',')[1];
                            var mime = blob.type || 'application/octet-stream';
                            BlobDownloader.downloadBase64(base64, mime, filename);
                        };
                        reader.readAsDataURL(blob);
                    } else {
                        fetch(href)
                            .then(r => r.blob())
                            .then(blob => {
                                var reader = new FileReader();
                                reader.onloadend = function() {
                                    var base64 = reader.result.split(',')[1];
                                    var mime = blob.type || 'application/octet-stream';
                                    BlobDownloader.downloadBase64(base64, mime, filename);
                                };
                                reader.readAsDataURL(blob);
                            })
                            .catch(e => console.log('Blob fetch failed', e));
                    }
                    return true;
                }

                document.addEventListener('click', function(e) {
                    var el = e.target.closest('a[download]');
                    if (!el) return;
                    if (interceptDownload(el)) {
                        e.preventDefault();
                        e.stopPropagation();
                    }
                }, true);

                // Also hook into anchor.click() calls
                var origClick = HTMLAnchorElement.prototype.click;
                HTMLAnchorElement.prototype.click = function() {
                    if (this.hasAttribute('download') && interceptDownload(this)) {
                        return;
                    }
                    return origClick.call(this);
                };
            })();
        """.trimIndent()
        webView.evaluateJavascript(js, null)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == fileChooserResultCode) {
            val results = if (data == null || resultCode != RESULT_OK) {
                null
            } else {
                WebChromeClient.FileChooserParams.parseResult(resultCode, data)
            }
            filePathCallback?.onReceiveValue(results)
            filePathCallback = null
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (::webView.isInitialized && intent?.getBooleanExtra(EXTRA_RELOAD, false) == true) {
            webView.loadUrl(KioskPrefs.getUrl(this))
        }
    }

    override fun onResume() {
        super.onResume()
        if (::webView.isInitialized) webView.onResume()
    }

    override fun onPause() {
        super.onPause()
        if (::webView.isInitialized) webView.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::webView.isInitialized) webView.destroy()
    }

    private fun mimeToExtension(mime: String): String {
        return when (mime.lowercase()) {
            "text/json", "application/json" -> ".json"
            "text/plain" -> ".txt"
            "text/html" -> ".html"
            "image/png" -> ".png"
            "image/jpeg", "image/jpg" -> ".jpg"
            "image/webp" -> ".webp"
            "image/gif" -> ".gif"
            "application/pdf" -> ".pdf"
            "application/zip" -> ".zip"
            else -> ""
        }
    }

    class BlobDownloaderInterface(private val context: Context) {
        @JavascriptInterface
        fun downloadBase64(base64Data: String, mimeType: String, filename: String) {
            try {
                val bytes = Base64.decode(base64Data, Base64.DEFAULT)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val values = ContentValues().apply {
                        put(MediaStore.Downloads.DISPLAY_NAME, filename)
                        put(MediaStore.Downloads.MIME_TYPE, mimeType)
                        put(MediaStore.Downloads.IS_PENDING, 1)
                    }
                    val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    uri?.let {
                        context.contentResolver.openOutputStream(it)?.use { os -> os.write(bytes) }
                        values.clear()
                        values.put(MediaStore.Downloads.IS_PENDING, 0)
                        context.contentResolver.update(it, values, null, null)
                    }
                } else {
                    val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    val file = File(downloadsDir, filename)
                    FileOutputStream(file).use { it.write(bytes) }
                    android.media.MediaScannerConnection.scanFile(
                        context,
                        arrayOf(file.absolutePath),
                        arrayOf(mimeType),
                        null
                    )
                }

                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(context, "Saved: $filename", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                android.util.Log.e("BlobDownload", "Failed to save blob", e)
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(context, "Download failed", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}