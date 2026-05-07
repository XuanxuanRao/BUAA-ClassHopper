package top.aidanrao.buaa_classhopper.activity

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
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
            vpnCookieJar.clear()
            webView.loadUrl(VpnEndpoints.VPN_CAS_LOGIN_URL)
            Toast.makeText(this, "已清除 VPN 登录状态", Toast.LENGTH_SHORT).show()
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
                statusText.text = "当前页面：${url ?: ""}"
                if (url != null && isLoginSuccessUrl(url)) {
                    onLoginSuccess()
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

        webView.loadUrl(VpnEndpoints.VPN_CAS_LOGIN_URL)
    }

    private fun isLoginSuccessUrl(url: String): Boolean {
        // 登录成功后 CAS 会重定向回 d.buaa.edu.cn，且此时已经携带了 session cookie
        if (!url.startsWith(VpnEndpoints.VPN_HOME_PREFIX)) return false
        // 过滤掉还在登录流程的页面
        if (url.contains("/login?") && !url.contains("cas_login=true")) return false
        val cookie = CookieManager.getInstance().getCookie("https://${VpnEndpoints.VPN_HOST}/")
        return !cookie.isNullOrBlank()
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

    override fun onDestroy() {
        webView.stopLoading()
        webView.destroy()
        super.onDestroy()
    }
}
