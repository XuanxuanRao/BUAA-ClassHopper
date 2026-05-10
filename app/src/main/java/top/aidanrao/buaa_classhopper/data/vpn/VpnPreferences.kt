package top.aidanrao.buaa_classhopper.data.vpn

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 持有 VPN 相关的设置：开关、SSO 学号密码、VPN session cookie。
 * 敏感字段使用 EncryptedSharedPreferences 加密保存。
 */
@Singleton
class VpnPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "VpnPreferences"
        private const val PLAIN_PREFS = "course_checkin_settings"
        private const val SECURE_PREFS = "vpn_secure_prefs"

        const val KEY_VPN_ENABLED = "vpn_enabled"
        private const val KEY_SSO_USERNAME = "sso_username"
        private const val KEY_SSO_PASSWORD = "sso_password"
        private const val KEY_VPN_COOKIES = "vpn_cookies"
    }

    private val plainPrefs: SharedPreferences =
        context.getSharedPreferences(PLAIN_PREFS, Context.MODE_PRIVATE)

    private val securePrefs: SharedPreferences by lazy { createSecurePrefs() }

    private fun createSecurePrefs(): SharedPreferences {
        return try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                SECURE_PREFS,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            // 加密初始化失败时，回退到明文保存，避免 Crash
            Log.w(TAG, "EncryptedSharedPreferences init failed, fallback to plain.", e)
            context.getSharedPreferences(SECURE_PREFS + "_plain", Context.MODE_PRIVATE)
        }
    }

    var isVpnEnabled: Boolean
        get() = plainPrefs.getBoolean(KEY_VPN_ENABLED, false)
        set(value) = plainPrefs.edit { putBoolean(KEY_VPN_ENABLED, value) }

    var ssoUsername: String?
        get() = securePrefs.getString(KEY_SSO_USERNAME, null)
        set(value) = securePrefs.edit { putString(KEY_SSO_USERNAME, value) }

    var ssoPassword: String?
        get() = securePrefs.getString(KEY_SSO_PASSWORD, null)
        set(value) = securePrefs.edit { putString(KEY_SSO_PASSWORD, value) }

    fun hasSsoCredentials(): Boolean {
        return !ssoUsername.isNullOrBlank() && !ssoPassword.isNullOrBlank()
    }

    fun clearSsoCredentials() {
        securePrefs.edit {
            remove(KEY_SSO_USERNAME)
            remove(KEY_SSO_PASSWORD)
        }
    }

    /** 保存 VPN session cookie（序列化为 name=value; domain; ...） */
    fun saveVpnCookies(serialized: String?) {
        securePrefs.edit {
            if (serialized.isNullOrBlank()) remove(KEY_VPN_COOKIES)
            else putString(KEY_VPN_COOKIES, serialized)
        }
    }

    fun getVpnCookies(): String? = securePrefs.getString(KEY_VPN_COOKIES, null)

    fun clearVpnCookies() {
        securePrefs.edit { remove(KEY_VPN_COOKIES) }
    }
}
