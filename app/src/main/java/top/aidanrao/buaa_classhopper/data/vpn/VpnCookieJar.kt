package top.aidanrao.buaa_classhopper.data.vpn

import android.util.Log
import android.webkit.CookieManager
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 在 OkHttp 与 WebView CookieManager 之间同步 VPN cookie。
 *
 * - WebView 登录后 CookieManager 会持有 d.buaa.edu.cn 的 cookie；
 * - OkHttp 在发起请求时从 CookieManager 读取 cookie 作为 header；
 * - OkHttp 响应中如果 Set-Cookie 了新 cookie，再写回 CookieManager，使两端保持一致。
 *
 * 同时持久化到 [VpnPreferences] 以便进程重启后继续使用。
 */
@Singleton
class VpnCookieJar @Inject constructor(
    private val vpnPreferences: VpnPreferences
) : CookieJar {

    companion object {
        private const val TAG = "VpnCookieJar"
    }

    init {
        // 启动时把持久化的 cookie 注入 WebView CookieManager
        restoreCookiesFromPrefs()
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        if (!url.host.endsWith(VpnEndpoints.VPN_HOST)) return emptyList()
        val cookieHeader = CookieManager.getInstance().getCookie(url.toString()) ?: return emptyList()
        val cookies = mutableListOf<Cookie>()
        cookieHeader.split(";").forEach { raw ->
            val trimmed = raw.trim()
            if (trimmed.isEmpty()) return@forEach
            val parsed = Cookie.parse(url, "$trimmed; Path=/") ?: return@forEach
            cookies.add(parsed)
        }
        return cookies
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (!url.host.endsWith(VpnEndpoints.VPN_HOST)) return
        val manager = CookieManager.getInstance()
        cookies.forEach { cookie ->
            try {
                manager.setCookie("https://${cookie.domain}", cookie.toString())
            } catch (e: Exception) {
                Log.w(TAG, "setCookie failed: ${cookie.name}", e)
            }
        }
        manager.flush()
        persistCookies()
    }

    /** 将 WebView 当前的 d.buaa.edu.cn cookie 序列化并持久化。 */
    fun persistCookies() {
        val cookieHeader = CookieManager.getInstance()
            .getCookie("https://${VpnEndpoints.VPN_HOST}/")
        vpnPreferences.saveVpnCookies(cookieHeader)
    }

    /** 从持久化的字符串中恢复 cookie 到 WebView CookieManager。 */
    private fun restoreCookiesFromPrefs() {
        val stored = vpnPreferences.getVpnCookies() ?: return
        val manager = CookieManager.getInstance()
        manager.setAcceptCookie(true)
        stored.split(";").forEach { raw ->
            val trimmed = raw.trim()
            if (trimmed.isNotEmpty()) {
                manager.setCookie("https://${VpnEndpoints.VPN_HOST}/", trimmed)
            }
        }
        manager.flush()
    }

    fun hasVpnCookies(): Boolean {
        val header = CookieManager.getInstance().getCookie("https://${VpnEndpoints.VPN_HOST}/")
        return !header.isNullOrBlank()
    }

    fun clear() {
        val manager = CookieManager.getInstance()
        manager.removeAllCookies(null)
        manager.flush()
        vpnPreferences.clearVpnCookies()
    }
}
