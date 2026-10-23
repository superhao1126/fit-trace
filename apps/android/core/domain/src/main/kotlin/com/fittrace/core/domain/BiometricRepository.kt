package com.fittrace.core.domain

import com.fittrace.core.model.*

interface DeviceKeyStore {
    fun capability(): BiometricCapability

    fun read(): DeviceBinding?

    fun write(binding: DeviceBinding)

    fun create(alias: String): String

    fun usable(alias: String): Boolean

    fun delete(alias: String)

    fun clear()
}

/** 由 app 的系统验证宿主实现；签名必须由强生物认证授权的密钥执行。 */
interface BiometricSigner {
    suspend fun sign(alias: String, payload: String): AppResult<String>
}

interface BiometricRepository {
    suspend fun status(checkServer: Boolean): AppResult<BiometricState>

    suspend fun enable(emailCode: String?, signer: BiometricSigner): AppResult<Unit>

    suspend fun login(signer: BiometricSigner): AppResult<Unit>

    suspend fun disable(): AppResult<Unit>

    suspend fun removeLocal(): AppResult<Unit>
}
