package com.fittrace.core.session

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import android.util.Base64
import com.fittrace.core.domain.SessionStore
import com.fittrace.core.model.AuthSession
import com.fittrace.core.model.AuthUser
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject

/** 仅保存加密会话；密码、邮箱验证码和人机验证凭据从不落盘。 */
class KeystoreSessionStore(context: Context) : SessionStore {
    private val file = AtomicFile(File(context.noBackupFilesDir, "auth-session.enc"))
    private val keyAlias = "fittrace.auth.session.v1"

    override suspend fun read(): AuthSession? =
        withContext(Dispatchers.IO) {
            val bytes =
                try {
                    file.readFully()
                } catch (_: FileNotFoundException) {
                    return@withContext null
                }
            try {
                val envelope = JSONObject(String(bytes, Charsets.UTF_8))
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(
                    Cipher.DECRYPT_MODE,
                    key(),
                    GCMParameterSpec(128, Base64.decode(envelope.getString("iv"), Base64.NO_WRAP)),
                )
                val data =
                    JSONObject(
                        String(
                            cipher.doFinal(
                                Base64.decode(envelope.getString("data"), Base64.NO_WRAP)
                            ),
                            Charsets.UTF_8,
                        )
                    )
                val user = data.getJSONObject("user")
                AuthSession(
                    data.getString("accessToken"),
                    data.getLong("expiresAt"),
                    data.getString("refreshToken"),
                    AuthUser(
                        user.getString("id"),
                        user.getString("email"),
                        if (user.isNull("nickname")) null else user.getString("nickname"),
                        if (user.isNull("hasPassword")) null else user.getBoolean("hasPassword"),
                    ),
                )
            } catch (_: GeneralSecurityException) {
                file.delete()
                runCatching {
                    KeyStore.getInstance("AndroidKeyStore").apply {
                        load(null)
                        deleteEntry(keyAlias)
                    }
                }
                null
            } catch (_: IllegalArgumentException) {
                file.delete()
                null
            } catch (_: JSONException) {
                file.delete()
                null
            }
        }

    override suspend fun write(session: AuthSession) =
        withContext(Dispatchers.IO) {
            try {
                val user =
                    JSONObject()
                        .put("id", session.user.id)
                        .put("email", session.user.email)
                        .put("nickname", session.user.nickname ?: JSONObject.NULL)
                        .put("hasPassword", session.user.hasPassword ?: JSONObject.NULL)
                val plain =
                    JSONObject()
                        .put("accessToken", session.accessToken)
                        .put("expiresAt", session.accessTokenExpiresAt)
                        .put("refreshToken", session.refreshToken)
                        .put("user", user)
                        .toString()
                        .toByteArray(Charsets.UTF_8)
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.ENCRYPT_MODE, key())
                val envelope =
                    JSONObject()
                        .put("iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
                        .put("data", Base64.encodeToString(cipher.doFinal(plain), Base64.NO_WRAP))
                        .toString()
                        .toByteArray(Charsets.UTF_8)
                val stream = file.startWrite()
                try {
                    stream.write(envelope)
                    file.finishWrite(stream)
                } catch (error: IOException) {
                    file.failWrite(stream)
                    throw error
                }
            } catch (error: GeneralSecurityException) {
                throw IOException("会话加密保存失败", error)
            }
        }

    override suspend fun clear(): Unit =
        withContext(Dispatchers.IO) {
            file.delete()
            if (file.baseFile.exists()) throw IOException("会话清理失败")
        }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(keyAlias, null) as? SecretKey)?.let {
            return it
        }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            .apply {
                init(
                    KeyGenParameterSpec.Builder(
                            keyAlias,
                            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                        )
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .build()
                )
            }
            .generateKey()
    }
}
