package `in`.codelif.jportal.session

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import `in`.codelif.ktjiit.auth.Session
import `in`.codelif.ktjiit.http.Transport
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * the portal token sealed with a keystore aes-gcm key. lives in no-backup
 * storage so it never leaves the device through cloud backups.
 */
class SessionStore(context: Context) {
    private val file = File(context.noBackupFilesDir, "session.bin")

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return gen.generateKey()
    }

    fun load(): Session? = runCatching {
        if (!file.exists()) return null
        val blob = file.readBytes()
        val iv = blob.copyOfRange(0, 12)
        val cipher = Cipher.getInstance(TRANSFORM).apply { init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv)) }
        val json = String(cipher.doFinal(blob, 12, blob.size - 12))
        Transport.json.decodeFromString(Session.serializer(), json)
    }.getOrElse {
        // key wiped (restore to a new device, keystore reset): treat as signed out
        file.delete()
        null
    }

    fun save(session: Session) {
        val cipher = Cipher.getInstance(TRANSFORM).apply { init(Cipher.ENCRYPT_MODE, key()) }
        val sealed = cipher.doFinal(Transport.json.encodeToString(Session.serializer(), session).toByteArray())
        val tmp = File(file.parentFile, "session.bin.tmp")
        tmp.writeBytes(cipher.iv + sealed)
        tmp.renameTo(file)
    }

    fun clear() {
        file.delete()
    }

    private companion object {
        const val ALIAS = "jportal-session"
        const val TRANSFORM = "AES/GCM/NoPadding"
    }
}
