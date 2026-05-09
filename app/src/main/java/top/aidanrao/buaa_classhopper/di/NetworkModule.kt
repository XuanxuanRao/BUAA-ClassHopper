package top.aidanrao.buaa_classhopper.di

import android.content.Context
import top.aidanrao.buaa_classhopper.data.api.AnnouncementApi
import top.aidanrao.buaa_classhopper.data.api.AuthApi
import top.aidanrao.buaa_classhopper.data.api.FallbackApi
import top.aidanrao.buaa_classhopper.data.api.IclassApi
import top.aidanrao.buaa_classhopper.data.api.LabApi
import top.aidanrao.buaa_classhopper.data.api.QRCodeApi
import top.aidanrao.buaa_classhopper.data.api.RateMonitorApi
import top.aidanrao.buaa_classhopper.data.api.UserApi
import top.aidanrao.buaa_classhopper.data.api.interceptor.AuthInterceptor
import top.aidanrao.buaa_classhopper.data.api.interceptor.LoggingInterceptor
import top.aidanrao.buaa_classhopper.data.api.interceptor.SslTrustManager
import top.aidanrao.buaa_classhopper.data.api.interceptor.TokenAuthenticator
import top.aidanrao.buaa_classhopper.data.repository.TokenManager
import top.aidanrao.buaa_classhopper.data.vpn.VpnCookieJar
import top.aidanrao.buaa_classhopper.data.vpn.VpnEndpoints
import top.aidanrao.buaa_classhopper.data.vpn.VpnPreferences
import top.aidanrao.buaa_classhopper.data.vpn.VpnSessionInterceptor
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    
    private const val BASE_URL = "http://39.105.96.112/api/"
    private const val ICLASS_BASE_URL = VpnEndpoints.ICLASS_DIRECT_8347
    private const val FALLBACK_BASE_URL = "https://101.42.43.228/"

    const val CLIENT_VPN = "vpnClient"
    const val API_ICLASS_DIRECT = "iclassDirect"
    const val API_ICLASS_VPN = "iclassVpn"

    @Provides
    @Singleton
    fun provideGson(): Gson {
        return GsonBuilder()
            .setDateFormat("yyyy-MM-dd HH:mm:ss")
            .registerTypeAdapter(LocalDateTime::class.java, LocalDateTimeAdapter())
            .create()
    }

    @Provides
    @Singleton
    @Named("authClient")
    fun provideAuthOkHttpClient(
        loggingInterceptor: LoggingInterceptor
    ): OkHttpClient {
        return OkHttpClient.Builder()
            .sslSocketFactory(SslTrustManager.getUnsafeSslSocketFactory(), SslTrustManager.getUnsafeTrustManager())
            .hostnameVerifier { _, _ -> true }
            .addInterceptor(loggingInterceptor)
            .build()
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(
        authInterceptor: AuthInterceptor,
        loggingInterceptor: LoggingInterceptor,
        tokenAuthenticator: TokenAuthenticator
    ): OkHttpClient {
        return OkHttpClient.Builder()
            .sslSocketFactory(SslTrustManager.getUnsafeSslSocketFactory(), SslTrustManager.getUnsafeTrustManager())
            .hostnameVerifier { _, _ -> true }
            .addInterceptor(authInterceptor)
            .addInterceptor(loggingInterceptor)
            .authenticator(tokenAuthenticator)
            .build()
    }

    @Provides
    @Singleton
    fun provideAuthApi(gson: Gson, @Named("authClient") okHttpClient: OkHttpClient): AuthApi {
        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(AuthApi::class.java)
    }

    @Provides
    @Singleton
    fun provideUserApi(gson: Gson, okHttpClient: OkHttpClient): UserApi {
        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(UserApi::class.java)
    }

    @Provides
    @Singleton
    fun provideAnnouncementApi(gson: Gson, okHttpClient: OkHttpClient): AnnouncementApi {
        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(AnnouncementApi::class.java)
    }

    @Provides
    @Singleton
    @Named(CLIENT_VPN)
    fun provideVpnOkHttpClient(
        loggingInterceptor: LoggingInterceptor,
        vpnCookieJar: VpnCookieJar,
        vpnPreferences: VpnPreferences
    ): OkHttpClient {
        return OkHttpClient.Builder()
            .sslSocketFactory(SslTrustManager.getUnsafeSslSocketFactory(), SslTrustManager.getUnsafeTrustManager())
            .hostnameVerifier { _, _ -> true }
            .cookieJar(vpnCookieJar)
            .addInterceptor(VpnSessionInterceptor(vpnCookieJar, vpnPreferences))
            .addInterceptor(loggingInterceptor)
            .build()
    }

    @Provides
    @Singleton
    @Named(API_ICLASS_DIRECT)
    fun provideIclassApi(gson: Gson, okHttpClient: OkHttpClient): IclassApi {
        return Retrofit.Builder()
            .baseUrl(ICLASS_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(IclassApi::class.java)
    }

    @Provides
    @Singleton
    @Named(API_ICLASS_VPN)
    fun provideIclassVpnApi(
        gson: Gson,
        @Named(CLIENT_VPN) okHttpClient: OkHttpClient
    ): IclassApi {
        return Retrofit.Builder()
            .baseUrl(VpnEndpoints.ICLASS_VPN_8347)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(IclassApi::class.java)
    }

    @Provides
    @Singleton
    fun provideFallbackApi(gson: Gson, okHttpClient: OkHttpClient): FallbackApi {
        return Retrofit.Builder()
            .baseUrl(FALLBACK_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(FallbackApi::class.java)
    }

    @Provides
    @Singleton
    fun provideQRCodeApi(gson: Gson, okHttpClient: OkHttpClient): QRCodeApi {
        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(QRCodeApi::class.java)
    }

    @Provides
    @Singleton
    fun provideLabApi(gson: Gson, okHttpClient: OkHttpClient): LabApi {
        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(LabApi::class.java)
    }

    @Provides
    @Singleton
    fun provideRateMonitorApi(gson: Gson, okHttpClient: OkHttpClient): RateMonitorApi {
        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(RateMonitorApi::class.java)
    }

    @Provides
    @Singleton
    fun provideAuthInterceptor(tokenManager: TokenManager): AuthInterceptor {
        return AuthInterceptor(tokenManager)
    }

    @Provides
    @Singleton
    fun provideTokenAuthenticator(tokenManager: TokenManager): TokenAuthenticator {
        return TokenAuthenticator(tokenManager)
    }

    @Provides
    @Singleton
    fun provideLoggingInterceptor(): LoggingInterceptor {
        return LoggingInterceptor()
    }

    @Provides
    @Singleton
    fun provideTokenManager(
        @ApplicationContext context: Context,
        authApi: AuthApi
    ): TokenManager {
        return TokenManager(context, authApi)
    }
}

class LocalDateTimeAdapter : com.google.gson.JsonSerializer<LocalDateTime>, com.google.gson.JsonDeserializer<LocalDateTime> {
    private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    
    override fun serialize(src: LocalDateTime?, typeOfSrc: java.lang.reflect.Type?, context: com.google.gson.JsonSerializationContext?): com.google.gson.JsonElement {
        return com.google.gson.JsonPrimitive(src?.format(formatter))
    }
    
    override fun deserialize(json: com.google.gson.JsonElement?, typeOfT: java.lang.reflect.Type?, context: com.google.gson.JsonDeserializationContext?): LocalDateTime? {
        return json?.asString?.let { LocalDateTime.parse(it, formatter) }
    }
}
