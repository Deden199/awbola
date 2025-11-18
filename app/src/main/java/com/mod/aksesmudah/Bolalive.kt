package com.mod.aksesmudah

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Message
import android.webkit.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.bumptech.glide.Glide
import com.google.android.material.bottomnavigation.BottomNavigationView
import android.util.Log
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.FrameLayout
import java.io.ByteArrayInputStream
import java.util.Locale

import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue

class Bolalive : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var bannerPager: ViewPager2
    private lateinit var bottomNav: BottomNavigationView
    private lateinit var bannerAdapter: BannerSliderAdapter
    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null
    private var fullScreenContainer: FrameLayout? = null
    private var originalOrientation: Int = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED

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

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Pakai layout dengan banner + webview + bottom nav
        setContentView(R.layout.activity_bolalive)

        FirebaseApp.initializeApp(this)

        webView = findViewById(R.id.mainWebView)
        bannerPager = findViewById(R.id.bannerPager)
        bottomNav = findViewById(R.id.bottomNav)

        bannerImages.clear()
        bannerImages.addAll(defaultBannerImages)
        menuUrls.clear()
        menuUrls.addAll(defaultMenuUrls)

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
    }

    private fun setupBottomNav() {
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
                Log.w(TAG, "Failed to check Firestore config state", e)
                onComplete()
            }
    }

    private fun applyRemoteConfig(document: DocumentSnapshot) {
        val bannerData = document.get("bannerUrls") ?: document.get("banners")
        if (bannerData is List<*>) {
            updateBannerImages(bannerData.mapNotNull { it as? String })
        }

        val menuData = document.get("menuUrls") ?: document.get("menus")
        updateMenuUrls(menuData)

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

                if (customView != null) {
                    callback?.onCustomViewHidden()
                    return
                }

                customView = view
                customViewCallback = callback
                originalOrientation = requestedOrientation
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE

                fullScreenContainer = FrameLayout(this@Bolalive).apply {
                    setBackgroundColor(Color.BLACK)
                    addView(
                        view,
                        ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    )
                }

                findViewById<ViewGroup>(android.R.id.content).addView(fullScreenContainer)
                updateChromeVisibility(isFullscreen = true)
            }

            override fun onHideCustomView() {
                exitFullscreen()
            }
        }

        wv.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val isMain = request.isForMainFrame
                val url = request.url.toString()
                if (request.url.scheme !in listOf("http", "https")) return true
                if (!isMain) return false
                view.loadUrl(url)
                return true
            }

            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                injectHideChrome(view)
                injectPopupKiller(view)
                injectPlayerDeAd(view)
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
        if (customView != null) {
            exitFullscreen()
            return
        }

        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    private fun exitFullscreen() {
        fullScreenContainer?.let { container ->
            container.removeAllViews()
            findViewById<ViewGroup>(android.R.id.content).removeView(container)
        }
        fullScreenContainer = null
        customView = null
        customViewCallback?.onCustomViewHidden()
        customViewCallback = null
        requestedOrientation = originalOrientation
        updateChromeVisibility(isFullscreen = false)
    }

    private fun updateChromeVisibility(isFullscreen: Boolean) {
        val visibility = if (isFullscreen) View.GONE else View.VISIBLE
        bannerPager.visibility = visibility
        bottomNav.visibility = visibility
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }

    // ====== JS: buang param ?ad= dari iframe player bfh5.ygrbf.cc (matikan iklan 3–5 detik) ======
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

    // ====== CSS/JS: sembunyikan header/footer & fixed bars + target elemen spesifik (guarded) ======
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
                    '.product-html-class',
                    '.van-button.van-button--default.van-button--normal.van-button--round',
                    'img.kehuIcon',
                    'img.play_off_btn',
                    '.van-tabs__wrap',
                    '.van-tabs__nav',
                    '.swipeBox',
                    '.van-swipe__indicators',
                    '.liveTimeDownload',
                    '.rightListBox',
                    '.noticebar',
                    '.van-notice-bar',
                    '.van-notice-bar__wrap',
                    '.van-notice-bar__content',
                    '#playerTabs'
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

                  document.querySelectorAll('.btn, .bottomDownload .btn, .rightcon .btn').forEach(x => {
                    const t = (x.textContent || '').trim().toLowerCase();
                    if (t === 'unduh') { (x.closest('.bottomDownload') || x).remove(); found = true; }
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

    // ====== JS: blokir popup/overlay/modal + netralisir window.open + sweep selektif (guarded) ======
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
                    'img.play_off_btn',
                    '.van-tabs__wrap',
                    '.van-tabs__nav',
                    '.swipeBox',
                    '.van-swipe__indicators',
                    '.liveTimeDownload',
                    '.rightListBox',
                    '.noticebar',
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
}

/** Ad-blocker: blok host/keyword + tebak MIME agar respons 204 aman untuk semua type */
object AdBlocker {
    private val blockedHosts = setOf(
        "doubleclick.net","googleadservices.com","googlesyndication.com",
        "adservice.google.com","adservice.google.co.id","ads.pubmatic.com",
        "adnxs.com","rubiconproject.com","criteo.com","taboola.com","outbrain.com",
        "scorecardresearch.com","zedo.com","media.net",
        // Tambahan promo/iklan
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
