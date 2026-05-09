package top.aidanrao.buaa_classhopper.data.vpn

import okhttp3.Interceptor
import okhttp3.Response

/**
 * 抛出该异常表示 VPN session 已失效，需要用户重新 SSO 登录。
 */
class VpnSessionExpiredException(message: String) : java.io.IOException(message)

/**
 * 当 VPN 网关返回 CAS 登录页面（即 session 失效）时，抛出 [VpnSessionExpiredException]。
 *
 * 识别规则：
 * 1. 发起的请求是 d.buaa.edu.cn；
 * 2. 响应 Content-Type 为 text/html；
 *
 * 同时清除已持久化的失效 cookie，避免后续接口继续失败。
 */
class VpnSessionInterceptor(
    private val vpnCookieJar: VpnCookieJar,
    private val vpnPreferences: VpnPreferences
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)

        if (!request.url.host.endsWith(VpnEndpoints.VPN_HOST)) {
            return response
        }

        val contentType = response.header("Content-Type").orEmpty().lowercase()
        // iClass 业务接口都返回 JSON，一旦收到 HTML 基本可以断定是被 VPN 网关拦回了登录页
        if (contentType.contains("text/html")) {
            response.close()
            // 清掉失效的 cookie，让用户重新登录
            vpnCookieJar.clear()
            vpnPreferences.isVpnEnabled = false
            throw VpnSessionExpiredException("VPN 会话已失效，请重新通过 SSO 登录")
        }

        return response
    }
}
