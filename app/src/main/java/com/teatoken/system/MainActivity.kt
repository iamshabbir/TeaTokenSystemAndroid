package com.teatoken.system

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import com.teatoken.system.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val webView: WebView get() = binding.webView

    /** True when the current main-frame load failed, so we keep the offline screen up. */
    private var mainFrameError = false

    private var fileCallback: ValueCallback<Array<Uri>>? = null
    private val fileChooser =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            fileCallback?.onReceiveValue(
                WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data)
            )
            fileCallback = null
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Keep content clear of the status / navigation bars (edge-to-edge on Android 15+).
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime()
            )
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            WindowInsetsCompat.CONSUMED
        }

        setupWebView()
        setupBackNavigation()

        binding.retryButton.setOnClickListener { loadPage() }

        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState)
        } else {
            loadPage()
        }
    }

    @SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
    private fun setupWebView() = with(webView) {
        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            loadsImagesAutomatically = true
            mediaPlaybackRequiresUserGesture = false
            cacheMode = WebSettings.LOAD_DEFAULT
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE

            // No zoom of any kind.
            setSupportZoom(false)
            builtInZoomControls = false
            displayZoomControls = false
            useWideViewPort = true
            loadWithOverviewMode = true

            // Ignore the system font-size setting so layout looks the same everywhere.
            textZoom = 100

            // target="_blank" / window.open load in this same WebView.
            setSupportMultipleWindows(false)
            javaScriptCanOpenWindowsAutomatically = false
        }

        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, true)
        }

        // Native feel: no overscroll glow/stretch, no scrollbars, no long-press menu / selection.
        overScrollMode = View.OVER_SCROLL_NEVER
        isVerticalScrollBarEnabled = false
        isHorizontalScrollBarEnabled = false
        isLongClickable = false
        isHapticFeedbackEnabled = false
        setOnLongClickListener { true }

        webViewClient = AppWebViewClient()
        webChromeClient = AppChromeClient()

        setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
            downloadFile(url, userAgent, contentDisposition, mimeType)
        }
    }

    private fun setupBackNavigation() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack() && !binding.offlineView.isVisible) {
                    webView.goBack()
                } else {
                    finish()
                }
            }
        })
    }

    private fun loadPage() {
        if (!NetworkUtils.isOnline(this)) {
            showOffline(true)
            return
        }
        showOffline(false)
        mainFrameError = false
        val current = webView.url
        if (current.isNullOrEmpty() || current == "about:blank") {
            webView.loadUrl(AppConfig.WEB_URL)
        } else {
            webView.reload()
        }
    }

    private fun showOffline(show: Boolean) {
        binding.offlineView.isVisible = show
        binding.webView.isVisible = !show
        if (show) binding.progressBar.isVisible = false
    }

    /** Opens non-web links (tel:, mailto:, whatsapp:, upi:, intent:...) in the matching app. */
    private fun handleExternalScheme(uri: Uri): Boolean {
        return try {
            val intent = if (uri.scheme == "intent") {
                Intent.parseUri(uri.toString(), Intent.URI_INTENT_SCHEME).apply {
                    addCategory(Intent.CATEGORY_BROWSABLE)
                    component = null
                    selector = null
                }
            } else {
                Intent(Intent.ACTION_VIEW, uri)
            }
            try {
                startActivity(intent)
            } catch (e: ActivityNotFoundException) {
                // intent:// links may carry a web fallback; open it inside the WebView.
                intent.getStringExtra("browser_fallback_url")?.let { webView.loadUrl(it) }
            }
            true
        } catch (e: Exception) {
            true // Swallow bad links instead of crashing or showing an error page.
        }
    }

    private fun downloadFile(url: String, userAgent: String, disposition: String, mime: String) {
        try {
            val fileName = URLUtil.guessFileName(url, disposition, mime)
            val request = DownloadManager.Request(Uri.parse(url)).apply {
                setMimeType(mime)
                addRequestHeader("cookie", CookieManager.getInstance().getCookie(url))
                addRequestHeader("User-Agent", userAgent)
                setTitle(fileName)
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
            }
            getSystemService(DownloadManager::class.java).enqueue(request)
            Toast.makeText(this, getString(R.string.downloading, fileName), Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, R.string.download_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private inner class AppWebViewClient : WebViewClient() {

        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val uri = request.url
            return when (uri.scheme?.lowercase()) {
                "http", "https" -> false          // Stay inside the WebView, never the browser.
                "about", "data", "blob", "javascript" -> false
                else -> handleExternalScheme(uri)
            }
        }

        override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
            binding.progressBar.isVisible = true
        }

        override fun onPageFinished(view: WebView, url: String?) {
            binding.progressBar.isVisible = false
            view.evaluateJavascript(NATIVE_FEEL_JS, null)
            if (!mainFrameError) showOffline(false)
        }

        override fun onReceivedError(
            view: WebView,
            request: WebResourceRequest,
            error: WebResourceError
        ) {
            if (request.isForMainFrame) {
                mainFrameError = true
                showOffline(true)
            }
        }
    }

    private inner class AppChromeClient : WebChromeClient() {

        override fun onProgressChanged(view: WebView, newProgress: Int) {
            binding.progressBar.progress = newProgress
            binding.progressBar.isVisible = newProgress < 100 && !binding.offlineView.isVisible
        }

        override fun onShowFileChooser(
            webView: WebView,
            filePathCallback: ValueCallback<Array<Uri>>,
            fileChooserParams: FileChooserParams
        ): Boolean {
            fileCallback?.onReceiveValue(null)
            fileCallback = filePathCallback
            return try {
                fileChooser.launch(fileChooserParams.createIntent())
                true
            } catch (e: ActivityNotFoundException) {
                fileCallback = null
                false
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webView.saveState(outState)
    }

    override fun onResume() {
        super.onResume()
        webView.onResume()
    }

    override fun onPause() {
        webView.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        binding.webView.apply {
            stopLoading()
            webChromeClient = null
            destroy()
        }
        super.onDestroy()
    }

    companion object {
        /**
         * Injected after every page load: locks the viewport (no pinch zoom, even if the
         * site allows it), blocks text selection and the long-press callout, and removes
         * the grey tap highlight. Form fields stay selectable so typing still works.
         */
        private const val NATIVE_FEEL_JS = """
            (function() {
              var m = document.querySelector('meta[name=viewport]');
              if (!m) { m = document.createElement('meta'); m.name = 'viewport';
                (document.head || document.documentElement).appendChild(m); }
              m.content = 'width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no';
              if (!document.getElementById('__native_feel')) {
                var s = document.createElement('style'); s.id = '__native_feel';
                s.innerHTML = '*{-webkit-user-select:none;user-select:none;' +
                  '-webkit-touch-callout:none;-webkit-tap-highlight-color:transparent;}' +
                  'input,textarea,[contenteditable],[contenteditable] *' +
                  '{-webkit-user-select:text;user-select:text;}' +
                  'html,body{overscroll-behavior:none;}';
                (document.head || document.documentElement).appendChild(s);
              }
              document.addEventListener('gesturestart', function(e){ e.preventDefault(); }, {passive:false});
              document.addEventListener('touchmove', function(e){
                if (e.touches && e.touches.length > 1) e.preventDefault(); }, {passive:false});
            })();
        """
    }
}
