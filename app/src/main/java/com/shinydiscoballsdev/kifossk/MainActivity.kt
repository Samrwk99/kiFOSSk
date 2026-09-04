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
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.OnBackPressedCallback

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

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

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
        }
        webView.setOnLongClickListener { true }
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                view.evaluateJavascript(
                    "document.documentElement.style.webkitUserSelect = 'none';" +
                            "document.documentElement.style.userSelect = 'none';",
                    null
                )

                // Auto-refresh setup (runs on every page load)
                setupAutoRefresh(url)
            }
        }
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                view.evaluateJavascript(
                    "document.documentElement.style.webkitUserSelect = 'none';" +
                            "document.documentElement.style.userSelect = 'none';",
                    null
                )
                setupAutoRefresh(url)
            }

            override fun onReceivedError(view: WebView?, request: android.webkit.WebResourceRequest?, error: android.webkit.WebResourceError?) {
                super.onReceivedError(view, request, error)
                if (request?.isForMainFrame == true) {
                    currentUrl = KioskPrefs.getUrl(this@MainActivity)
                    loadWaitingPage(currentUrl)
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
    private fun setupAutoRefresh(currentUrl: String) {
        // Stop any existing refresh loop first
        stopAutoRefresh()

        if (!KioskPrefs.isAutoRefreshEnabled(this)) return

        val intervalSeconds = KioskPrefs.getAutoRefreshInterval(this)
        val intervalMs = intervalSeconds * 1000L

        refreshHandler = Handler(Looper.getMainLooper())
        refreshRunnable = object : Runnable {
            override fun run() {
                if (KioskPrefs.isAutoRefreshEnabled(this@MainActivity)) {
                    webView.loadUrl(currentUrl) // Reload current URL
                    setupAutoRefresh(currentUrl) // Re-schedule (recursive)
                }
            }
        }

        refreshHandler?.postDelayed(refreshRunnable!!, intervalMs)
    }

    private fun stopAutoRefresh() {
        refreshRunnable?.let { refreshHandler?.removeCallbacks(it) }
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
        NetworkRetryHelper.stopWaiting()
        super.onDestroy()
    }

    @SuppressLint("SetTextI18n")
    private fun loadWaitingPage(targetUrl: String) {
        val waitingHtml = NetworkRetryHelper.createWaitingPage(targetUrl)

        webView.setBackgroundColor(android.graphics.Color.parseColor("#1a1a2e"))
        webView.loadDataWithBaseURL(null, waitingHtml, "text/html", "UTF-8", null)

        NetworkRetryHelper.startWaitingForNetwork(
            context = this,
            targetUrl = targetUrl,
            onConnected = { url ->
                webView.loadUrl(url)
            }
        )
    }
}