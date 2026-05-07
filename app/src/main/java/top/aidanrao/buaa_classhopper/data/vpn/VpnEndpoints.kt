package top.aidanrao.buaa_classhopper.data.vpn

/**
 * VPN 相关 URL 常量，保持与 docs/client.py 的参考实现一致。
 */
object VpnEndpoints {
    const val VPN_HOST = "d.buaa.edu.cn"

    const val ICLASS_DIRECT_8346 = "https://iclass.buaa.edu.cn:8346/"
    const val ICLASS_DIRECT_8347 = "https://iclass.buaa.edu.cn:8347/"

    const val VPN_PATH_8346 =
        "/https-8346/77726476706e69737468656265737421f9f44d9d342326526b0988e29d51367ba018/"
    const val VPN_PATH_8347 =
        "/https-8347/77726476706e69737468656265737421f9f44d9d342326526b0988e29d51367ba018/"

    const val ICLASS_VPN_8346 = "https://$VPN_HOST$VPN_PATH_8346"
    const val ICLASS_VPN_8347 = "https://$VPN_HOST$VPN_PATH_8347"

    /** VPN 网关的 CAS 登录入口，登录后会自动带着 cookie 跳回 d.buaa.edu.cn。 */
    const val VPN_CAS_LOGIN_URL =
        "https://d.buaa.edu.cn/https/77726476706e69737468656265737421e3e44ed225256951300d8db9d6562d/login" +
                "?service=https%3A%2F%2Fd.buaa.edu.cn%2Flogin%3Fcas_login%3Dtrue"

    /** 登录成功后页面会跳到 d.buaa.edu.cn 首页。 */
    const val VPN_HOME_PREFIX = "https://d.buaa.edu.cn/"
}
