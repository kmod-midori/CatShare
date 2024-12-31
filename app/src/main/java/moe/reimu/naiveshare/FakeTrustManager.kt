package moe.reimu.naiveshare

import android.annotation.SuppressLint
import java.security.cert.X509Certificate
import javax.net.ssl.X509TrustManager

@SuppressLint("CustomX509TrustManager", "TrustAllX509TrustManager")
class FakeTrustManager : X509TrustManager {

    override fun checkClientTrusted(chain: Array<out X509Certificate>, authType: String) {

    }

    override fun checkServerTrusted(chain: Array<out X509Certificate>, authType: String) {

    }

    override fun getAcceptedIssuers(): Array<X509Certificate> {
        return emptyArray()
    }
}