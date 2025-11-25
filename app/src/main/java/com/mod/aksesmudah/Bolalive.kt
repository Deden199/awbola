package com.mod.aksesmudah

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.content.pm.ActivityInfo
import android.widget.FrameLayout

import android.os.Bundle
import android.os.Message
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.*
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.CompositePageTransformer
import androidx.viewpager2.widget.MarginPageTransformer
import androidx.viewpager2.widget.ViewPager2
import com.bumptech.glide.Glide
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.util.Locale
import kotlin.math.abs

class Bolalive : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var bannerPager: ViewPager2
    private lateinit var bottomNav: BottomNavigationView
    private lateinit var bannerAdapter: BannerSliderAdapter
    // state untuk fullscreen video
    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null

    private val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }

    companion object {
        private const val TAG = "Bolalive"
        private const val CONFIG_COLLECTION = "app_config"
        private const val CONFIG_DOCUMENT = "bolalive"
    }

    private val defaultBannerImages = listOf(
        "https://via.placeholder.com/800x400.png?text=Banner+1",
        "https://via.placeholder.com/800x400.png?text=Banner+2",
        "https://via.placeholder.com/800x400.png?text=Banner+3"
    )
    private val bannerImages = mutableListOf<String>()

    private val defaultMenuUrls = listOf(
        "https://jalaa35.com/",          // Daftar
        "https://jalaa35.com/",          // Login
        "https://jalaa35.com/"           // Livechat
    )
    private val menuUrls = mutableListOf<String>()

    private val defaultInlineBannerImageUrl =
        "https://via.placeholder.com/320x100.png?text=Inline+Ads+Banner"
    private val defaultInlineBannerTargetUrl = defaultMenuUrls.first()

    private var inlineBannerImageUrl: String? = null
    private var inlineBannerTargetUrl: String? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_bolalive)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

        FirebaseApp.initializeApp(this)

        webView = findViewById(R.id.mainWebView)
        bannerPager = findViewById(R.id.bannerPager)
        bottomNav = findViewById(R.id.bottomNav)

        bannerImages.clear()
        bannerImages.addAll(defaultBannerImages)
        menuUrls.clear()
        menuUrls.addAll(defaultMenuUrls)

        // default inline banner (fallback kalau Firestore belum punya field)
        inlineBannerImageUrl = sanitizeUrl(defaultInlineBannerImageUrl)
        inlineBannerTargetUrl = sanitizeUrl(defaultInlineBannerTargetUrl)

        setupBannerSlider()
        setupBottomNav()
        setupWebView()

        seedFirestoreConfigIfMissing { fetchRemoteConfig() }
    }

    private fun setupBannerSlider() {
        bannerAdapter = BannerSliderAdapter(bannerImages) { url ->
            loadUrlIfValid(url)
        }
        bannerPager.adapter = bannerAdapter
        bannerPager.offscreenPageLimit = 3

        val transformer = CompositePageTransformer().apply {
            addTransformer(MarginPageTransformer(32))
            addTransformer { page, position ->
                val scale = 0.9f + (1 - abs(position)) * 0.1f
                page.scaleY = scale
                page.alpha = 0.8f + (1 - abs(position)) * 0.2f
            }
        }
        bannerPager.setPageTransformer(transformer)
    }

    private fun setupBottomNav() {
        val colorState = ContextCompat.getColorStateList(this, R.color.nav_item_color)
        if (colorState != null) {
            bottomNav.itemIconTintList = colorState
            bottomNav.itemTextColor = colorState
        }

        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.menu_home -> loadMenuUrl(0)
                R.id.menu_login -> loadMenuUrl(1)
                R.id.menu_livechat -> loadMenuUrl(2)
            }
            true
        }
    }

    private fun loadMenuUrl(index: Int) {
        val url = menuUrls.getOrNull(index) ?: defaultMenuUrls.getOrNull(index)
        val sanitized = sanitizeUrl(url)
        if (sanitized != null) {
            openInChrome(sanitized)
        } else if (!url.isNullOrBlank()) {
            Log.w(TAG, "Ignored invalid url: $url")
        }
    }

    private fun loadUrlIfValid(url: String?) {
        val sanitized = sanitizeUrl(url)
        if (sanitized != null) {
            webView.loadUrl(sanitized)
        } else if (!url.isNullOrBlank()) {
            Log.w(TAG, "Ignored invalid url: $url")
        }
    }

    private fun openInChrome(url: String) {
        val uri = Uri.parse(url)
        val chromeIntent = Intent(Intent.ACTION_VIEW, uri).apply {
            setPackage("com.android.chrome")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        try {
            startActivity(chromeIntent)
        } catch (notFound: ActivityNotFoundException) {
            val fallback = Intent(Intent.ACTION_VIEW, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                startActivity(fallback)
            } catch (e: ActivityNotFoundException) {
                Log.w(TAG, "No browser available to handle url: $url", e)
            }
        }
    }

    private fun sanitizeUrl(value: String?): String? {
        val trimmed = value?.trim() ?: return null
        if (trimmed.isEmpty()) return null
        return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) trimmed else null
    }

    private fun updateBannerImages(newUrls: List<String>) {
        if (!::bannerAdapter.isInitialized) return
        val sanitized = newUrls.mapNotNull { sanitizeUrl(it) }
        if (sanitized.isEmpty()) return
        bannerAdapter.updateItems(sanitized)
    }

    private fun updateMenuUrls(raw: Any?) {
        val candidates = when (raw) {
            is List<*> -> raw.map { it as? String }
            is Map<*, *> -> listOf(
                (raw["home"] ?: raw["daftar"]) as? String,
                raw["login"] as? String,
                (raw["livechat"] ?: raw["chat"]) as? String
            )
            else -> emptyList()
        }

        if (candidates.isEmpty()) return

        val merged = mutableListOf<String>()
        for (i in defaultMenuUrls.indices) {
            val sanitized = sanitizeUrl(candidates.getOrNull(i))
            if (sanitized != null) {
                merged.add(sanitized)
            } else {
                merged.add(defaultMenuUrls[i])
            }
        }

        menuUrls.clear()
        menuUrls.addAll(merged)
    }

    private fun fetchRemoteConfig() {
        firestore.collection(CONFIG_COLLECTION)
            .document(CONFIG_DOCUMENT)
            .get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    applyRemoteConfig(document)
                } else {
                    Log.w(TAG, "Remote config document missing")
                }
            }
            .addOnFailureListener { e ->
                Log.w(TAG, "Failed to load remote config", e)
            }
    }

    private fun seedFirestoreConfigIfMissing(onComplete: () -> Unit) {
        val hasGms = GoogleApiAvailability.getInstance()
            .isGooglePlayServicesAvailable(this) == ConnectionResult.SUCCESS

        if (!hasGms) {
            Log.w(TAG, "Google Play Services tidak tersedia, fallback seeding via REST")
            seedViaRest(onComplete)
            return
        }

        firestore.collection(CONFIG_COLLECTION)
            .document(CONFIG_DOCUMENT)
            .get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    onComplete()
                    return@addOnSuccessListener
                }

                val seedData = mapOf(
                    "bannerUrls" to defaultBannerImages,
                    "menuUrls" to defaultMenuUrls,
                    "webviewUrl" to defaultMenuUrls.firstOrNull(),
                    "iframeBannerImage" to defaultInlineBannerImageUrl,
                    "iframeBannerTarget" to defaultInlineBannerTargetUrl,
                    "seededAt" to FieldValue.serverTimestamp()
                )

                firestore.collection(CONFIG_COLLECTION)
                    .document(CONFIG_DOCUMENT)
                    .set(seedData)
                    .addOnSuccessListener {
                        Log.i(TAG, "Seeded default Firestore config: $CONFIG_COLLECTION/$CONFIG_DOCUMENT")
                        onComplete()
                    }
                    .addOnFailureListener { e ->
                        Log.w(TAG, "Failed to seed Firestore config", e)
                        onComplete()
                    }
            }
            .addOnFailureListener { e ->
                if (e is SecurityException && e.message?.contains("com.google.android.gms") == true) {
                    Log.w(TAG, "Seeding gagal karena GMS, coba REST", e)
                    seedViaRest(onComplete)
                } else {
                    Log.w(TAG, "Failed to check Firestore config state", e)
                    onComplete()
                }
            }
    }

    private fun seedViaRest(onComplete: () -> Unit) {
        Thread {
            try {
                val baseUrl =
                    "https://firestore.googleapis.com/v1/projects/${BuildConfig.FIREBASE_PROJECT_ID}/databases/(default)/documents/$CONFIG_COLLECTION/$CONFIG_DOCUMENT"
                val getUrl = URL("$baseUrl?key=${BuildConfig.FIREBASE_API_KEY}")
                val getConn = (getUrl.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 5000
                    readTimeout = 5000
                }

                if (getConn.responseCode == HttpURLConnection.HTTP_OK) {
                    Log.i(TAG, "REST: dokumen sudah ada, lewati seeding")
                    runOnUiThread { onComplete() }
                    return@Thread
                }

                if (getConn.responseCode == HttpURLConnection.HTTP_NOT_FOUND) {
                    val body = readResponseBody(getConn)
                    if (body.contains("database (default) does not exist", ignoreCase = true) ||
                        body.contains("does not exist for project", ignoreCase = true)
                    ) {
                        Log.w(
                            TAG,
                            "REST: Firestore database belum dibuat untuk project ${BuildConfig.FIREBASE_PROJECT_ID}. " +
                                    "Buat terlebih dahulu di console lalu coba lagi. Response: $body"
                        )
                    } else {
                        Log.w(TAG, "REST seeding gagal (404): $body")
                    }
                    runOnUiThread { onComplete() }
                    return@Thread
                }

                val payload = buildSeedJson()
                val postConn =
                    (URL("$baseUrl?key=${BuildConfig.FIREBASE_API_KEY}").openConnection() as HttpURLConnection).apply {
                        requestMethod = "PATCH"
                        doOutput = true
                        setRequestProperty("Content-Type", "application/json; charset=utf-8")
                        connectTimeout = 5000
                        readTimeout = 5000
                    }

                postConn.outputStream.use { it.write(payload.toByteArray()) }

                val success = postConn.responseCode in 200..299
                val response = readResponseBody(postConn)

                if (success) {
                    Log.i(TAG, "REST: seeded Firestore config")
                } else {
                    Log.w(TAG, "REST seeding failed: HTTP ${postConn.responseCode} $response")
                }
            } catch (ex: Exception) {
                Log.w(TAG, "REST seeding exception", ex)
            } finally {
                runOnUiThread { onComplete() }
            }
        }.start()
    }

    private fun readResponseBody(conn: HttpURLConnection): String {
        return try {
            val stream = conn.errorStream ?: conn.inputStream
            BufferedReader(InputStreamReader(stream)).use { it.readText() }
        } catch (_: Exception) {
            ""
        }
    }

    private fun buildSeedJson(): String {
        fun arrayJson(values: List<String>): String {
            return values.joinToString(prefix = "[", postfix = "]") {
                "{\"stringValue\":\"" + it + "\"}"
            }
        }

        val banners = arrayJson(defaultBannerImages)
        val menus = arrayJson(defaultMenuUrls)

        val timestamp = Instant.ofEpochMilli(System.currentTimeMillis()).toString()

        return """
            {"fields":{
                "bannerUrls":{"arrayValue":{"values":$banners}},
                "menuUrls":{"arrayValue":{"values":$menus}},
                "webviewUrl":{"stringValue":"${defaultMenuUrls.firstOrNull() ?: ""}"},
                "iframeBannerImage":{"stringValue":"${defaultInlineBannerImageUrl}"},
                "iframeBannerTarget":{"stringValue":"${defaultInlineBannerTargetUrl}"},
                "seededAt":{"timestampValue":"$timestamp"}
            }}
        """.trimIndent()
    }

    private fun applyRemoteConfig(document: DocumentSnapshot) {
        val bannerData = document.get("bannerUrls") ?: document.get("banners")
        if (bannerData is List<*>) {
            updateBannerImages(bannerData.mapNotNull { it as? String })
        }

        val menuData = document.get("menuUrls") ?: document.get("menus")
        updateMenuUrls(menuData)

        val iframeBannerImage = listOf(
            document.getString("iframeBannerImage"),
            document.getString("inlineBannerImage"),
            document.getString("inlineAdImage")
        ).firstNotNullOfOrNull { sanitizeUrl(it) }

        val iframeBannerTarget = listOf(
            document.getString("iframeBannerTarget"),
            document.getString("inlineBannerTarget"),
            document.getString("inlineAdTarget"),
            document.getString("iframeBannerUrl")
        ).firstNotNullOfOrNull { sanitizeUrl(it) }

        if (iframeBannerImage != null && iframeBannerTarget != null) {
            inlineBannerImageUrl = iframeBannerImage
            inlineBannerTargetUrl = iframeBannerTarget
        } else {
            Log.w(TAG, "Inline banner skipped: image or target url missing in config")
        }

        val startUrl = listOf(
            document.getString("webviewUrl"),
            document.getString("defaultUrl"),
            document.getString("homeUrl")
        ).firstOrNull { !it.isNullOrBlank() }

        if (startUrl != null) {
            loadUrlIfValid(startUrl)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        val wv = webView
        wv.setLayerType(WebView.LAYER_TYPE_HARDWARE, null)

        val s = wv.settings
        s.javaScriptEnabled = true
        s.domStorageEnabled = true
        s.loadWithOverviewMode = true
        s.useWideViewPort = true
        s.setSupportZoom(true)
        s.builtInZoomControls = true
        s.displayZoomControls = false
        s.allowFileAccess = true
        s.allowContentAccess = true
        s.javaScriptCanOpenWindowsAutomatically = false
        s.setSupportMultipleWindows(false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            s.safeBrowsingEnabled = true
        }
        s.mediaPlaybackRequiresUserGesture = true

        wv.webChromeClient = object : WebChromeClient() {
            override fun onJsAlert(
                view: WebView?,
                url: String?,
                message: String?,
                result: JsResult?
            ): Boolean {
                result?.confirm()
                return true
            }

            override fun onJsConfirm(
                view: WebView?,
                url: String?,
                message: String?,
                result: JsResult?
            ): Boolean {
                result?.cancel()
                return true
            }

            override fun onJsPrompt(
                view: WebView?,
                url: String?,
                message: String?,
                defaultValue: String?,
                result: JsPromptResult?
            ): Boolean {
                result?.cancel()
                return true
            }

            override fun onJsBeforeUnload(
                view: WebView?,
                url: String?,
                message: String?,
                result: JsResult?
            ): Boolean {
                result?.confirm()
                return true
            }

            override fun onCreateWindow(
                view: WebView?,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: Message?
            ): Boolean {
                return false
            }

            override fun onPermissionRequest(request: PermissionRequest?) {
                request?.deny()
            }

            override fun onGeolocationPermissionsShowPrompt(
                origin: String?,
                callback: GeolocationPermissions.Callback?
            ) {
                callback?.invoke(origin, false, false)
            }

            override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                if (view == null) {
                    callback?.onCustomViewHidden()
                    return
                }

                // kalau sudah ada customView aktif, tutup request baru
                if (customView != null) {
                    callback?.onCustomViewHidden()
                    return
                }

                val decor = window.decorView as FrameLayout

                customView = view
                customViewCallback = callback

                decor.addView(
                    customView,
                    FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
                )

                // sembunyikan UI lain saat fullscreen
                webView.visibility = View.GONE
                bannerPager.visibility = View.GONE
                bottomNav.visibility = View.GONE

                // paksa landscape ketika fullscreen
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            }

            override fun onHideCustomView() {
                val decor = window.decorView as FrameLayout

                customView?.let { decor.removeView(it) }
                customView = null

                // tampilkan lagi UI normal
                webView.visibility = View.VISIBLE
                bannerPager.visibility = View.VISIBLE
                bottomNav.visibility = View.VISIBLE

                // balik ke portrait
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

                customViewCallback?.onCustomViewHidden()
                customViewCallback = null

                super.onHideCustomView()
            }

        }

        wv.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val uri = request.url
                val scheme = uri.scheme ?: ""
                val url = uri.toString()
                val isMain = request.isForMainFrame

                // === khusus klik banner ads: buka Chrome ===
                if (scheme == "bolalive-banner") {
                    val target = uri.getQueryParameter("u")
                    if (!target.isNullOrBlank()) {
                        val decoded = Uri.decode(target)
                        try {
                            openInChrome(decoded)
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to open banner url in chrome: $decoded", e)
                        }
                    }
                    return true
                }

                if (scheme !in listOf("http", "https")) return true
                if (!isMain) return false
                view.loadUrl(url)
                return true
            }

            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                injectHideChrome(view)
                injectPopupKiller(view)
                injectPlayerDeAd(view)
                injectIframeBanner(view)
            }

            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest?
            ): WebResourceResponse? {
                val uri = request?.url ?: return super.shouldInterceptRequest(view, request)
                if (AdBlocker.isAdUrl(uri)) {
                    val accept = request.requestHeaders["Accept"] ?: ""
                    val mime = AdBlocker.guessMime(uri.toString(), accept)
                    val body = if (mime.startsWith("text/html"))
                        "<!doctype html><meta charset=utf-8>".toByteArray()
                    else
                        ByteArray(0)

                    return WebResourceResponse(
                        mime,
                        "utf-8",
                        204,
                        "No Content",
                        mapOf("Access-Control-Allow-Origin" to "*"),
                        ByteArrayInputStream(body)
                    )
                }
                return super.shouldInterceptRequest(view, request)
            }
        }
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }

    // ====== JS: buang param ?ad= dari iframe player bfh5.ygrbf.cc ======
    private fun injectPlayerDeAd(view: WebView) {
        val js = """
            (function() {
              try {
                function stripAdParamOnce(root) {
                  let touched = false;
                  (root || document).querySelectorAll('iframe#playiframe, iframe[src*="bfh5.ygrbf.cc"]').forEach(ifr => {
                    if (ifr.dataset && ifr.dataset.dead === "1") return;
                    const src = ifr.getAttribute('src') || '';
                    if (!src) return;
                    try {
                      const u = new URL(src, location.href);
                      if (u.hostname.includes('bfh5.ygrbf.cc') && (u.searchParams.has('ad') || /[?&]ad=/.test(src))) {
                        u.searchParams.delete('ad');
                        if (!ifr.hasAttribute('allow')) {
                          ifr.setAttribute('allow','autoplay');
                        }
                        ifr.setAttribute('src', u.toString());
                        ifr.dataset.dead = "1";
                        touched = true;
                      }
                    } catch(_) {}
                  });
                  return touched;
                }

                stripAdParamOnce(document);
                const mo = new MutationObserver(() => stripAdParamOnce(document));
                mo.observe(document.documentElement, { childList:true, subtree:true, attributes:true, attributeFilter:['src'] });

                setTimeout(() => stripAdParamOnce(document), 500);
                setTimeout(() => stripAdParamOnce(document), 1500);
              } catch(e) {}
            })();
        """.trimIndent()
        view.post { view.evaluateJavascript(js, null) }
    }

    // ====== CSS/JS: sembunyikan header/footer & fixed bars + target elemen spesifik ======
    private fun injectHideChrome(view: WebView) {
        val js = """
            (function() {
              try {
                const css = `
                  header, footer, #header, #footer, .header, .footer,
                  [role="banner"], [role="contentinfo"],
                  .site-header, .site-footer { display:none !important; visibility:hidden !important; height:0 !important; min-height:0 !important; }
                  html, body { margin:0 !important; padding:0 !important; }
                `;
                let styleEl = document.getElementById('__wv_hide_chrome');
                if (!styleEl) {
                  styleEl = document.createElement('style');
                  styleEl.id = '__wv_hide_chrome';
                  styleEl.type = 'text/css';
                  document.documentElement.appendChild(styleEl);
                }
                styleEl.textContent = css;

                function softHide(el) {
                  if (!el) return;
                  el.style.setProperty('display','none','important');
                  el.style.setProperty('visibility','hidden','important');
                  el.style.setProperty('height','0','important');
                  el.style.setProperty('min-height','0','important');
                  el.style.setProperty('pointer-events','none','important');
                  el.style.setProperty('opacity','0','important');
                }

                function removeAdIframes(){
                  const badHosts = [
                    'doubleclick.', 'googlesyndication.', 'googleadservices.',
                    'adservice.google.', 'ads.pubmatic.', 'adnxs.', 'rubiconproject.',
                    'criteo.', 'taboola.', 'outbrain.', 'zedo.', 'media.net',
                    'spotbet', 'trk.', 'aff', 'affiliate', 'track', 'promo', 'popunder', 'interstitial'
                  ];
                  const badPaths = ['ads','adserver','adservice','banner','promotions','promo/','tracking','tracker','click?','/pop','/popunder'];
                  const isBad = (u) => {
                    const url = (u || '').toLowerCase();
                    if (!url) return false;
                    return badHosts.some(h => url.includes(h)) || badPaths.some(p => url.includes(p));
                  };
                  let removed = false;
                  document.querySelectorAll('iframe').forEach(f => {
                    const s = f.getAttribute('src') || '';
                    if (isBad(s)) { f.remove(); removed = true; }
                  });
                  return removed;
                }

                function nukeTargetsOnce() {
                  const sels = [
                    '.maskClass',
                    '.centerViewClass',
                    '.bottomDownload',
                    '.topDownloadBox',
                    '.topDownload-keep-px',
                    '.product-html-class',
                    '.van-button.van-button--default.van-button--normal.van-button--round',
                    'img.kehuIcon',
                    'img.play_off_btn',
                    '.swipeBox',
                    '.van-swipe__indicators',
                    '.liveTimeDownload',
                    '.van-tabs__wrap',
                    '.van-tabs__nav',
                    '.van-notice-bar__content',
                    '#playerTabs',
                    '.rightListBox',
                    '.noticebar',
                    '.cover',        
                    '.countdown' , 
                    '.van-notice-bar',
                    '.van-notice-bar__wrap',
                    '.van-notice-bar__content'
                    
                  ];
                  let found = false;
                  sels.forEach(sel => {
                    const list = document.querySelectorAll(sel);
                    if (list.length) found = true;
                    list.forEach(n => {
                      if (sel === '#playerTabs') softHide(n); else n.remove();
                    });
                  });

                  document.querySelectorAll('button, .van-button, [role="button"]').forEach(btn => {
                    const t = (btn.textContent || '').trim().toLowerCase();
                    if (t === 'gabung') { btn.remove(); found = true; }
                  });

                  document.querySelectorAll('.btn, .bottomDownload .btn, .rightcon .btn, .topDownloadBox .btn').forEach(x => {
                    const t = (x.textContent || '').trim().toLowerCase();
                    if (t === 'unduh') { (x.closest('.bottomDownload, .topDownloadBox') || x).remove(); found = true; }
                  });

                  if (removeAdIframes()) found = true;

                  return found;
                }

                function hideFixedBars() {
                  let hit = false;
                  const MAXH = 200;
                  document.querySelectorAll('body *').forEach(n => {
                    const st = getComputedStyle(n);
                    if (!st) return;
                    if (st.position === 'fixed' || st.position === 'sticky') {
                      const r = n.getBoundingClientRect();
                      if (r.height && r.height < MAXH && (r.top <= 0 || Math.abs(innerHeight - r.bottom) <= 5)) {
                        softHide(n); hit = true;
                      }
                    }
                  });
                  return hit;
                }

                let didSomething = nukeTargetsOnce() || hideFixedBars();

                if (didSomething) {
                  const mo = new MutationObserver(() => {
                    nukeTargetsOnce();
                    hideFixedBars();
                  });
                  mo.observe(document.documentElement, { childList:true, subtree:true, attributes:true });
                  setTimeout(() => { nukeTargetsOnce(); hideFixedBars(); }, 800);
                  setTimeout(() => { nukeTargetsOnce(); hideFixedBars(); }, 2000);
                }
              } catch(e) {}
            })();
        """.trimIndent()
        view.post { view.evaluateJavascript(js, null) }
    }

    // ====== JS: blokir popup/overlay/modal + sweep ======
    private fun injectPopupKiller(view: WebView) {
        val js = """
            (function() {
              try {
                window.open = function(){ return null; };
                document.addEventListener('click', function(e) {
                  const a = e.target && e.target.closest && e.target.closest('a[target="_blank"]');
                  if (a && a.href) { e.preventDefault(); window.location.href = a.href; }
                }, true);

                const css = `
                  .modal, .modal-backdrop, .overlay, .popup, .pop, .lightbox,
                  [class*="modal"], [class*="overlay"], [id*="modal"], [id*="overlay"] {
                    display:none !important; visibility:hidden !important; opacity:0 !important; pointer-events:none !important;
                  }
                `;
                let styleEl = document.getElementById('__wv_popup_killer');
                if (!styleEl) {
                  styleEl = document.createElement('style');
                  styleEl.id = '__wv_popup_killer';
                  styleEl.type = 'text/css';
                  document.documentElement.appendChild(styleEl);
                }
                styleEl.textContent = css;

                function softHide(el) {
                  if (!el) return;
                  el.style.setProperty('display','none','important');
                  el.style.setProperty('visibility','hidden','important');
                  el.style.setProperty('height','0','important');
                  el.style.setProperty('min-height','0','important');
                  el.style.setProperty('pointer-events','none','important');
                  el.style.setProperty('opacity','0','important');
                }

                function removeAdIframes(){
                  const badHosts = [
                    'doubleclick.', 'googlesyndication.', 'googleadservices.',
                    'adservice.google.', 'ads.pubmatic.', 'adnxs.', 'rubiconproject.',
                    'criteo.', 'taboola.', 'outbrain.', 'zedo.', 'media.net',
                    'spotbet', 'trk.', 'aff', 'affiliate', 'track', 'promo', 'popunder', 'interstitial'
                  ];
                  const badPaths = ['ads','adserver','adservice','banner','promotions','promo/','tracking','tracker','click?','/pop','/popunder'];
                  const isBad = (u) => {
                    const url = (u || '').toLowerCase();
                    if (!url) return false;
                    return badHosts.some(h => url.includes(h)) || badPaths.some(p => url.includes(p));
                  };
                  let removed = false;
                  document.querySelectorAll('iframe').forEach(f => {
                    const s = f.getAttribute('src') || '';
                    if (isBad(s)) { f.remove(); removed = true; }
                  });
                  return removed;
                }

                function sweep() {
                  let hit = false;

                  [
                    '.maskClass',
                    '.centerViewClass',
                    '.bottomDownload',
                    '.product-html-class',
                    '.van-button.van-button--default.van-button--normal.van-button--round',
                    'img.kehuIcon',
                    '.van-tabs__wrap',
                    '.van-tabs__nav',
                    'img.play_off_btn',
                    '.swipeBox',
                    '.van-swipe__indicators',
                    '.liveTimeDownload',
                    '.rightListBox',
                    '.noticebar',
                     '.cover',        
                    '.countdown' , 
                    '.van-notice-bar',
                    '.van-notice-bar__wrap',
                    '.van-notice-bar__content'
                  ].forEach(sel => {
                    const list = document.querySelectorAll(sel);
                    if (list.length) hit = true;
                    list.forEach(n => n.remove());
                  });
                  document.querySelectorAll('#playerTabs').forEach(n => { softHide(n); hit = true; });

                  document.querySelectorAll('button, .van-button, [role="button"]').forEach(btn => {
                    const t = (btn.textContent || '').trim().toLowerCase();
                    if (t === 'gabung') { btn.remove(); hit = true; }
                  });

                  document.querySelectorAll('.btn, .bottomDownload .btn, .rightcon .btn').forEach(x => {
                    const t = (x.textContent || '').trim().toLowerCase();
                    if (t === 'unduh') { (x.closest('.bottomDownload') || x).remove(); hit = true; }
                  });

                  document.querySelectorAll('[onclick*="window.open"], [onmouseover*="window.open"]').forEach(n=>{
                    n.removeAttribute('onclick'); n.removeAttribute('onmouseover'); hit = true;
                  });

                  if (removeAdIframes()) hit = true;

                  return hit;
                }

                if (sweep()) {
                  const mo = new MutationObserver(() => { sweep(); });
                  mo.observe(document.documentElement, {
                    subtree:true, childList:true, attributes:true,
                    attributeFilter:['onclick','onmouseover','class','id','style']
                  });
                  setTimeout(sweep, 800);
                  setTimeout(sweep, 2000);
                }
              } catch(e) {}
            })();
        """.trimIndent()
        view.post { view.evaluateJavascript(js, null) }
    }

    private fun injectIframeBanner(view: WebView) {
        val imgUrl = inlineBannerImageUrl ?: return
        val clickUrl = inlineBannerTargetUrl ?: return
        val safeImg = imgUrl.jsEscaped()
        val safeClick = clickUrl.jsEscaped()

        val js = """
        (function() {
          try {
            var img = '$safeImg';
            var link = '$safeClick';
            if (!img || !link) return;

            var styleId = '__wv_iframe_banner_style';
            if (!document.getElementById(styleId)) {
              var css = ''
                + '.__wv_iframe_banner { margin:16px auto 10px; max-width:360px; width:95%;'
                + ' display:flex; flex-direction:column; border-radius:10px; overflow:hidden;'
                + ' box-shadow:0 6px 18px rgba(0,0,0,0.2); background:#0d0d0d; }'
                + '.__wv_iframe_banner img { width:100%; height:auto; display:block; object-fit:cover; }'
                + '.__wv_iframe_banner .__wv_iframe_banner_label { background:rgba(0,0,0,0.72);'
                + ' color:#fff; text-transform:uppercase; letter-spacing:1px; font-size:12px;'
                + ' font-weight:700; padding:6px 10px; }'
                + '.__wv_iframe_banner .__wv_iframe_banner_imgwrap { position:relative; }'
                + '.__wv_iframe_banner .__wv_iframe_banner_imgwrap::after { content:""; position:absolute; inset:0;'
                + ' box-shadow:inset 0 0 0 1px rgba(255,255,255,0.08); pointer-events:none; }'
                + '.__wv_iframe_banner a { text-decoration:none; }';

              var styleEl = document.createElement('style');
              styleEl.id = styleId;
              styleEl.type = 'text/css';
              styleEl.appendChild(document.createTextNode(css));
              document.documentElement.appendChild(styleEl);
            }

            function buildBanner() {
              var wrapper = document.createElement('div');
              wrapper.className = '__wv_iframe_banner';
              wrapper.setAttribute('role','presentation');

              var anchor = document.createElement('a');
              // custom scheme supaya Android buka via Chrome
              var encoded = encodeURIComponent(link);
              anchor.href = 'bolalive-banner://open?u=' + encoded;
              anchor.target = '_self';
              anchor.rel = 'noopener noreferrer';

              var label = document.createElement('div');
              label.className = '__wv_iframe_banner_label';
              label.textContent = 'ADS';

              var imgWrap = document.createElement('div');
              imgWrap.className = '__wv_iframe_banner_imgwrap';

              var image = document.createElement('img');
              image.loading = 'lazy';
              image.src = img;
              image.alt = 'ads banner';

              imgWrap.appendChild(image);
              anchor.appendChild(label);
              anchor.appendChild(imgWrap);
              wrapper.appendChild(anchor);
              return wrapper;
            }

            function insertBelowVideoBox(root) {
              var doc = root || document;
              var vbList = doc.getElementsByClassName('videoBox');
              if (vbList.length === 0) {
                return false;
              }
              var vb = vbList[0];
              if (vb.getAttribute('data-wv-banner-below') === '1') {
                return false;
              }
              var banner = buildBanner();
              if (vb.parentNode) {
                vb.parentNode.insertBefore(banner, vb.nextSibling);
                vb.setAttribute('data-wv-banner-below', '1');
                return true;
              }
              return false;
            }

            function fallbackToBody() {
              if (!document.body) return false;
              if (document.body.getAttribute('data-wv-banner-body') === '1') return false;
              var banner = buildBanner();
              document.body.appendChild(banner);
              document.body.setAttribute('data-wv-banner-body', '1');
              return true;
            }

            function sweep(root) {
              var inserted = insertBelowVideoBox(root);
              if (!inserted) {
                fallbackToBody();
              }
            }

            sweep(document);

            var mo = new MutationObserver(function(muts) {
              for (var i = 0; i < muts.length; i++) {
                var m = muts[i];
                if (!m.addedNodes) continue;
                for (var j = 0; j < m.addedNodes.length; j++) {
                  var n = m.addedNodes[j];
                  if (n && n.nodeType === 1) {
                    sweep(n);
                  }
                }
              }
            });
            mo.observe(document.documentElement, { childList:true, subtree:true });
          } catch(e) {}
        })();
    """.trimIndent()

        view.post { view.evaluateJavascript(js, null) }
    }

    private fun String.jsEscaped(): String =
        this.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n")
}

/** Ad-blocker: blok host/keyword + tebak MIME agar respons 204 aman untuk semua type */
object AdBlocker {
    private val blockedHosts = setOf(
        "doubleclick.net","googleadservices.com","googlesyndication.com",
        "adservice.google.com","adservice.google.co.id","ads.pubmatic.com",
        "adnxs.com","rubiconproject.com","criteo.com","taboola.com","outbrain.com",
        "scorecardresearch.com","zedo.com","media.net",
        "spb", "spotbet", "adserver", "popads"
    )

    private val blockedPathKeywords = listOf(
        "/ads","/adserver","/adservice","/gampad","/advert","/banner",
        "/promotions","/promo/","/tracking","/tracker","/click?","popunder",
        "interstitial","preroll","midroll","postroll","/aff","/affiliate","utm_","adtag",
        "spotbet"
    )

    private val blockedExtensions = listOf(
        ".doubleclick.", ".ads?", ".ads/", ".adserver", ".js?ad", "/ad.js", "/ads.js"
    )

    fun isAdUrl(uri: Uri): Boolean {
        val host = (uri.host ?: "").lowercase(Locale.ROOT)
        val url = uri.toString().lowercase(Locale.ROOT)
        if (blockedHosts.any { host == it || host.endsWith(".$it") || host.contains(it) }) return true
        if (blockedPathKeywords.any { url.contains(it) }) return true
        if (blockedExtensions.any { url.contains(it) }) return true
        if (host.startsWith("ads.") || host.startsWith("ad.")) return true
        return false
    }

    fun guessMime(url: String, acceptHeader: String): String {
        val u = url.lowercase(Locale.ROOT)
        return when {
            u.endsWith(".js") || acceptHeader.contains("application/javascript") -> "application/javascript"
            u.endsWith(".css") || acceptHeader.contains("text/css") -> "text/css"
            u.endsWith(".png") -> "image/png"
            u.endsWith(".jpg") || u.endsWith(".jpeg") -> "image/jpeg"
            u.endsWith(".gif") -> "image/gif"
            u.endsWith(".webp") -> "image/webp"
            u.endsWith(".svg") || acceptHeader.contains("image/svg") -> "image/svg+xml"
            u.endsWith(".json") || acceptHeader.contains("application/json") -> "application/json"
            acceptHeader.contains("text/html") || u.endsWith(".html") || u.endsWith(".htm") -> "text/html"
            else -> "text/plain"
        }
    }
}

/** Adapter slider banner */
class BannerSliderAdapter(
    private val items: MutableList<String>,
    private val onClick: (String) -> Unit
) : RecyclerView.Adapter<BannerSliderAdapter.BannerViewHolder>() {

    inner class BannerViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val img: ImageView = view.findViewById(R.id.imgBanner)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BannerViewHolder {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_banner, parent, false)
        return BannerViewHolder(v)
    }

    override fun onBindViewHolder(holder: BannerViewHolder, position: Int) {
        val url = items[position]
        Glide.with(holder.img.context).load(url).into(holder.img)
        holder.itemView.setOnClickListener { onClick(url) }
    }

    override fun getItemCount(): Int = items.size

    fun updateItems(newItems: List<String>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }
}
