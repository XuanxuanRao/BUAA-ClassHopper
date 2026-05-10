package top.aidanrao.buaa_classhopper.activity

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import dagger.hilt.android.AndroidEntryPoint
import top.aidanrao.buaa_classhopper.R
import top.aidanrao.buaa_classhopper.data.vpn.VpnCookieJar
import top.aidanrao.buaa_classhopper.data.vpn.VpnEndpoints
import top.aidanrao.buaa_classhopper.data.vpn.VpnPreferences
import javax.inject.Inject

/**
 * 通过 WebView 让用户登录 sso.buaa.edu.cn（经由 d.buaa.edu.cn 的 CAS）。
 * 登录成功后从 CookieManager 抓取 d.buaa.edu.cn 的 session cookie 持久化，
 * 并可选地保存学号/密码供以后参考（用户若在 WebView 内点击了记住密码功能）。
 */
@AndroidEntryPoint
class VpnLoginActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "VpnLoginActivity"
    }

    @Inject lateinit var vpnPreferences: VpnPreferences
    @Inject lateinit var vpnCookieJar: VpnCookieJar

    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private lateinit var statusText: TextView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_vpn_login)

        webView = findViewById(R.id.web_view)
        progressBar = findViewById(R.id.progress_bar)
        statusText = findViewById(R.id.status_text)

        findViewById<ImageView>(R.id.back_button).setOnClickListener { finish() }
        findViewById<Button>(R.id.clear_button).setOnClickListener {
            performFullSignOut {
                Toast.makeText(this, "已清除 VPN 登录状态", Toast.LENGTH_SHORT).show()
                webView.loadUrl(VpnEndpoints.VPN_CAS_LOGIN_URL)
            }
        }

        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            useWideViewPort = true
            loadWithOverviewMode = true
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                progressBar.progress = newProgress
                progressBar.visibility = if (newProgress >= 100) View.GONE else View.VISIBLE
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                Log.d(TAG, "onPageStarted: $url")
                statusText.text = "加载中：${url ?: ""}"
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                Log.d(TAG, "onPageFinished: $url")
                if (url == null) return
                when {
                    isLoginSuccessUrl(url) -> onLoginSuccess()
                    isCasLoginPage(url) -> {
                        // 仍停留在 CAS 登录页：可能是首次进入，也可能是凭据校验失败被打回登录页
                        statusText.text = "未登录，请输入北航统一身份认证账号密码后登录"
                    }
                    else -> statusText.text = "当前页面：$url"
                }
            }

            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                // 让 WebView 自己处理跳转
                return false
            }
        }

        // 进入本页面 == 用户希望（重新）登录，强制清空一切残留状态后再加载 CAS 页面，
        // 避免用之前的 session/cookie 直接被网关放行。
        performFullSignOut {
            webView.loadUrl(VpnEndpoints.VPN_CAS_LOGIN_URL)
        }
    }

    /**
     * 登录成功的判定：
     * 1. URL 已经回到 d.buaa.edu.cn 域下；
     * 2. URL 不在 CAS 登录/认证路径上（即用户已经离开了 /login、/authserver、/cas 等页面）。
     *
     * 之所以不直接读 CASTGC cookie：CASTGC 通常带 HttpOnly 标记，
     * Android WebView 的 CookieManager 默认无法读取 HttpOnly cookie。
     * 而 CAS 在凭据错误时会把用户保留在登录页，不会跳转到 portal，
     * 因此 "URL 离开登录路径" 是一个稳定可靠的成功信号。
     */
    private fun isLoginSuccessUrl(url: String): Boolean {
        if (!url.startsWith(VpnEndpoints.VPN_HOME_PREFIX)) return false
        return !isCasLoginPage(url)
    }

    private fun isCasLoginPage(url: String): Boolean {
        return url.contains("/login", ignoreCase = true) ||
                url.contains("/authserver", ignoreCase = true) ||
                url.contains("/cas/", ignoreCase = true)
    }

    private var loginHandled = false
    private fun onLoginSuccess() {
        if (loginHandled) return
        loginHandled = true
        vpnCookieJar.persistCookies()
        statusText.text = "VPN 登录成功，已保存会话"
        Toast.makeText(this, "VPN 登录成功", Toast.LENGTH_SHORT).show()
        setResult(RESULT_OK)
        finish()
    }

    /**
     * 彻底清理 WebView 内的登录痕迹，避免点击"重新登录"后 CAS 用残留 session 直接放行：
     * - WebView 缓存、表单数据、历史记录
     * - HTML5 LocalStorage / SessionStorage
     * - 所有 cookie（CookieManager.removeAllCookies 是异步的，必须在回调里再 loadUrl）
     * - 持久化的 VPN cookie SharedPreferences
     */
    private fun performFullSignOut(then: () -> Unit) {
        loginHandled = false
        webView.stopLoading()
        webView.clearCache(true)
        webView.clearFormData()
        webView.clearHistory()
        WebStorage.getInstance().deleteAllData()
        vpnPreferences.clearVpnCookies()
        val cm = CookieManager.getInstance()
        cm.removeAllCookies {
            cm.flush()
            then()
        }
    }

    override fun onDestroy() {
        webView.stopLoading()
        webView.destroy()
        super.onDestroy()
    }
}
