package org.onevault.app.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import java.security.SecureRandom

/** Private device vault; use a separate export passphrase for portability. */
class LocalCrypto(private val alias: String = "OneVault.v1") {
    private fun deviceKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val present = store.getKey(alias, null) as? SecretKey
        if (present != null) return present
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).build())
        }.generateKey()
    }
    fun seal(clear: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, deviceKey())
        val raw = cipher.iv + cipher.doFinal(clear.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(raw, Base64.NO_WRAP)
    }
    fun open(sealed: String): String {
        val bytes = Base64.decode(sealed, Base64.NO_WRAP)
        require(bytes.size >= 12 + 16)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, deviceKey(), GCMParameterSpec(128, bytes.copyOfRange(0,12)))
        return String(cipher.doFinal(bytes.copyOfRange(12,bytes.size)), Charsets.UTF_8)
    }
}

object PortableBackupCrypto {
    private const val ITERATIONS = 240_000
    private val rng = SecureRandom()
    private fun key(passphrase: CharArray, salt: ByteArray): SecretKeySpec {
        val params = PBEKeySpec(passphrase, salt, ITERATIONS, 256)
        return try {
            SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(params).encoded, "AES")
        } finally { params.clearPassword() }
    }
    fun encrypt(clear: ByteArray, passphrase: CharArray): ByteArray {
        require(passphrase.size >= 12) { "Use at least 12 characters for export" }
        val salt = ByteArray(16).also(rng::nextBytes)
        val iv = ByteArray(12).also(rng::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(passphrase,salt), GCMParameterSpec(128, iv))
        cipher.updateAAD("OneVaultBackup:v1".toByteArray())
        return "OVB1".toByteArray() + salt + iv + cipher.doFinal(clear)
    }
    fun decrypt(sealed: ByteArray, passphrase: CharArray): ByteArray {
        require(sealed.size > 48 && String(sealed.copyOfRange(0,4)) == "OVB1") { "Invalid backup format" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(passphrase,sealed.copyOfRange(4,20)),
            GCMParameterSpec(128, sealed.copyOfRange(20,32)))
        cipher.updateAAD("OneVaultBackup:v1".toByteArray())
        return cipher.doFinal(sealed.copyOfRange(32,sealed.size))
    }
}
