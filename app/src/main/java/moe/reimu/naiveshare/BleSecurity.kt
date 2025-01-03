package moe.reimu.naiveshare

import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.interfaces.ECPrivateKey
import java.security.interfaces.ECPublicKey
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object BleSecurity {
    private val localPrivateKey: ECPrivateKey
    private val localPublicKey: ECPublicKey

    init {
        val kg = KeyPairGenerator.getInstance("EC")
        kg.initialize(256)
        val kp = kg.generateKeyPair()
        localPublicKey = kp.public as ECPublicKey
        localPrivateKey = kp.private as ECPrivateKey
    }

    fun deriveSessionKey(publicKey: String): SessionCipher {
        val kf = KeyFactory.getInstance("EC")
        val otherPublicKey =
            kf.generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(publicKey)))
        val agreement = KeyAgreement.getInstance("ECDH")
        agreement.init(localPrivateKey)
        agreement.doPhase(otherPublicKey, true)
        val secret = agreement.generateSecret("TlsPremasterSecret")
        return SessionCipher(SecretKeySpec(secret.encoded, "AES"))
    }

    fun getEncodedPublicKey(): String {
        return Base64.getEncoder().encodeToString(localPublicKey.encoded)
    }


    class SessionCipher(private val key: SecretKeySpec) {
        fun decrypt(encodedData: String): String {
            val data = Base64.getDecoder().decode(encodedData)
            val cipher = Cipher.getInstance("AES/CTR/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, IvParameterSpec("0102030405060708".toByteArray()))
            return cipher.doFinal(data).decodeToString()
        }

        fun encrypt(data: String): String {
            val cipher = Cipher.getInstance("AES/CTR/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key, IvParameterSpec("0102030405060708".toByteArray()))
            return Base64.getEncoder()
                .encodeToString(cipher.doFinal(data.toByteArray(Charsets.UTF_8)))
        }
    }
}