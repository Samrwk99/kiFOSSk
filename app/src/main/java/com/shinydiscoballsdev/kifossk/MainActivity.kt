package com.shinydiscoballsdev.kifossk

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import android.webkit.WebSettings
import android.graphics.Bitmap

@SuppressLint("SetJavaScriptEnabled")
class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var gestureDetector: GestureDetector
    private var refreshHandler: Handler? = null
    private var refreshRunnable: Runnable? = null

    // Gesture hardening fields
    private var lastSettingsOpenTime = 0L
    private val SETTINGS_COOLDOWN_MS = 10_000L  // 10 seconds
    private var currentUrl = ""  // Track URL for retry

    private val navHandler = Handler(Looper.getMainLooper())
    private var watchdogRunnable: Runnable? = null
    companion object {
        private const val PAGE_LOAD_TIMEOUT_MS = 20_000L
    }

    private fun applyKeepScreenOnFlag() {
        val keepScreenOn = KioskPrefs.getScreenOn(this)
        if (keepScreenOn) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Wake screen and show over lockscreen (API 27+)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }

        // Issue #3: respect user's Keep Screen On preference
        applyKeepScreenOnFlag()

        // Block back navigation — kiosk mode
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Do nothing — kiosk mode
            }
        })

        val isFirstRun = KioskPrefs.isFirstRun(this)

        if (isFirstRun) {
            // Don't clear first_run yet - wait until URL is validated
            startActivity(Intent(this, SettingsActivity::class.java))
            finish()
        } else {
            setupWebView()
        }
    }

    @SuppressLint("SetJavaScriptEnabled", "MissingPermission")
    private fun setupWebView() {

        val url = KioskPrefs.getUrl(this)
        currentUrl = url
        val orientation = KioskPrefs.getOrientation(this)

        when (orientation) {
            "landscape" -> requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            "portrait" -> requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            "auto" -> requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }

        hideSystemUI()

        webView = WebView(this)
        webView.setBackgroundColor(android.graphics.Color.parseColor("#1a1a2e"))
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            builtInZoomControls = false
            useWideViewPort = true
            loadWithOverviewMode = true
            cacheMode = WebSettings.LOAD_NO_CACHE
        }
        webView.setOnLongClickListener { true }

        // SINGLE WebViewClient — the old duplicate dead assignment is gone.
        webView.webViewClient = object : WebViewClient() {

            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)

                if (url.startsWith("file:") || url.startsWith("about:") || url.startsWith("data:")) return

                // Re-arm: one watchdog per navigation, never stacked
                watchdogRunnable?.let { navHandler.removeCallbacks(it) }

                watchdogRunnable = Runnable {
                    android.util.Log.w("KioskLoad", "WATCHDOG: no page completion for $url in ${PAGE_LOAD_TIMEOUT_MS / 1000}s — forcing waiting page")
                    currentUrl = KioskPrefs.getUrl(this@MainActivity)
                    loadWaitingPage(currentUrl, failedOverNetwork = true)
                }.also { navHandler.postDelayed(it, PAGE_LOAD_TIMEOUT_MS) }
            }


            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)

                // Load completed — stand down the watchdog
                watchdogRunnable?.let { navHandler.removeCallbacks(it) }
                watchdogRunnable = null
                // Guard JS injection — documentElement can be null on
                // about:blank (waiting page) and chrome-error:// pages
                view.evaluateJavascript(
                    "if (document.documentElement) {" +
                            "document.documentElement.style.webkitUserSelect = 'none';" +
                            "document.documentElement.style.userSelect = 'none';}",
                    null
                )

                // Empty-content watchdog
                if (!url.startsWith("file:") && !url.startsWith("about:") && !url.startsWith("data:")) {
                    view.evaluateJavascript(
                        "(document.body && document.body.innerText.trim().length > 0) ? 'ok' : 'empty'"
                    ) { result ->
                        if (result == "\"empty\"") {
                            android.util.Log.i("KioskLoad", "onPageFinished: empty body for $url — fallback to waiting page")
                            currentUrl = KioskPrefs.getUrl(this@MainActivity)
                            loadWaitingPage(currentUrl, failedOverNetwork = true)
                        }
                    }
                }
                // Auto-refresh setup (runs on every page load)
                setupAutoRefresh()
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                super.onReceivedError(view, request, error)

                // Self-immunity: never react to errors on our own waiting page
                val urlStr = request?.url?.toString() ?: ""
                if (urlStr.startsWith("about:") || urlStr.startsWith("file:///android_asset/")) return

                // FIX #3: only main-frame failures trigger the waiting page;
                // subresource 404s must not hijack the kiosk
                if (request?.isForMainFrame == true) {
                    currentUrl = KioskPrefs.getUrl(this@MainActivity)
                    loadWaitingPage(currentUrl, failedOverNetwork = true)
                }
            }
        }
        setContentView(webView)

        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {

            override fun onDown(e: MotionEvent): Boolean {
                return true  // Required: signals GestureDetector to track this gesture sequence
            }

            override fun onLongPress(e: MotionEvent) {
                val now = System.currentTimeMillis()

                // Debounce: minimum 10 seconds between setting accesses
                if (now - lastSettingsOpenTime < SETTINGS_COOLDOWN_MS) return
                lastSettingsOpenTime = now
                startActivity(Intent(this@MainActivity, SettingsActivity::class.java))
            }
        })

        // Network check
        val isConnected = try {
            val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            @Suppress("DEPRECATION")
            connectivityManager.activeNetworkInfo?.isConnected == true
        } catch (e: Exception) {
            false
        }

        if (isConnected) {
            webView.loadUrl(url)
        } else {
            loadWaitingPage(url)
        }
    }

    @Suppress("DEPRECATION")
    private fun hideSystemUI() {
        window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                        View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                        View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                        View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                        View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                        View.SYSTEM_UI_FLAG_FULLSCREEN
                )
    }

    // ─── AUTO-REFRESH LOGIC ─────────────────────────────────────────────
    private fun setupAutoRefresh() {
        // FIX #2: clear ALL pending posts unconditionally, then schedule
        // exactly one — kills the double-reschedule drift
        refreshHandler?.removeCallbacksAndMessages(null)

        if (!KioskPrefs.isAutoRefreshEnabled(this)) return

        val intervalSeconds = KioskPrefs.getAutoRefreshInterval(this)
        val intervalMs = intervalSeconds * 1000L

        if (refreshHandler == null) refreshHandler = Handler(Looper.getMainLooper())

        refreshRunnable = object : Runnable {
            override fun run() {
                if (KioskPrefs.isAutoRefreshEnabled(this@MainActivity)) {
                    // Read URL fresh each tick — no stale captured copies
                    currentUrl = KioskPrefs.getUrl(this@MainActivity)
                    webView.loadUrl(currentUrl)
                    refreshHandler?.postDelayed(this, intervalMs)  // simple self-reschedule
                }
            }
        }

        refreshHandler?.postDelayed(refreshRunnable!!, intervalMs)
    }

    private fun stopAutoRefresh() {
        refreshHandler?.removeCallbacksAndMessages(null)
        refreshHandler = null
        refreshRunnable = null
    }

    override fun onPause() {
        stopAutoRefresh()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        // Refresh loop resumes in onPageFinished() after page loads
        applyKeepScreenOnFlag()
    }

    override fun dispatchTouchEvent(event: MotionEvent?): Boolean {
        if (event != null) {
            gestureDetector.onTouchEvent(event)
        }
        return super.dispatchTouchEvent(event)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hideSystemUI()
        }
    }

    override fun onDestroy() {
        stopAutoRefresh()
        navHandler.removeCallbacksAndMessages(null)
        NetworkRetryHelper.stopWaiting()
        NetworkRetryHelper.stopHostRetry()
        super.onDestroy()
    }

    @SuppressLint("SetTextI18n")
    private fun loadWaitingPage(targetUrl: String, failedOverNetwork: Boolean = false) {
        webView.setBackgroundColor(android.graphics.Color.parseColor("#1a1a2e"))

        // Issue #2 FIX: loadUrl with real asset instead of loadDataWithBaseURL
        val encodedUrl = android.net.Uri.encode(targetUrl)
        webView.loadUrl("file:///android_asset/waiting.html?url=$encodedUrl")

        if (failedOverNetwork) {
            NetworkRetryHelper.startHostRetry(this, targetUrl) { url -> webView.loadUrl(url) }
        } else {
            NetworkRetryHelper.startWaitingForNetwork(this, targetUrl) { url -> webView.loadUrl(url) }
        }
    }
}