package com.fittrace.core.session

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import android.util.Base64
import com.fittrace.core.domain.DeviceKeyStore
import com.fittrace.core.model.*
import java.io.File
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import org.json.JSONObject

class BiometricKeyStore(context: Context, private val availability: () -> BiometricCapability) :
    DeviceKeyStore {
    private val file = AtomicFile(File(context.noBackupFilesDir, "biometric-device.json"))

    private fun store() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    override fun capability() = availability()

    override fun read(): DeviceBinding? {
        if (!file.baseFile.exists()) return null
        val json = JSONObject(file.openRead().use { it.readBytes().toString(Charsets.UTF_8) })
        return DeviceBinding(
            json.getString("id"),
            json.getString("userId"),
            json.getString("email"),
            json.getString("alias"),
            json.optBoolean("pending"),
        )
    }

    override fun write(binding: DeviceBinding) {
        val json =
            JSONObject()
                .put("id", binding.id)
                .put("userId", binding.userId)
                .put("email", binding.email)
                .put("alias", binding.alias)
                .put("pending", binding.pending)
        val out = file.startWrite()
        try {
            out.write(json.toString().toByteArray(Charsets.UTF_8))
            file.finishWrite(out)
        } catch (e: Exception) {
            file.failWrite(out)
            throw e
        }
    }

    override fun create(alias: String): String {
        val builder =
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN)
                .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                .setDigests(KeyProperties.DIGEST_SHA256)
                .setUserAuthenticationRequired(true)
                .setInvalidatedByBiometricEnrollment(true)
        if (Build.VERSION.SDK_INT >= 30)
            builder.setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
        else {
            @Suppress("DEPRECATION") builder.setUserAuthenticationValidityDurationSeconds(-1)
        }
        val pair =
            KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore")
                .apply { initialize(builder.build()) }
                .generateKeyPair()
        return Base64.encodeToString(pair.public.encoded, Base64.NO_WRAP)
    }

    override fun usable(alias: String): Boolean =
        try {
            val key = store().getKey(alias, null) as? java.security.PrivateKey
            key != null &&
                run {
                    Signature.getInstance("SHA256withECDSA").initSign(key)
                    true
                }
        } catch (_: Exception) {
            false
        }

    override fun delete(alias: String) {
        store().deleteEntry(alias)
    }

    override fun clear() {
        file.delete()
    }
}
