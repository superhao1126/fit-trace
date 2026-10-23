package com.fittrace.core.network

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import retrofit2.Response
import retrofit2.http.*

@Serializable
data class DeviceRegistrationRequest(
    val publicKey: String,
    val scope: String,
    val deviceName: String,
    val emailCode: String? = null,
)

@Serializable data class DeviceLoginRequest(val credentialId: String, val scope: String)

@Serializable data class DeviceVerifyRequest(val challengeId: String, val signature: String)

@Serializable
data class DeviceChallengeDto(
    val challengeId: String,
    val payload: String,
    val expiresAt: String,
    val credentialId: String? = null,
)

@Serializable
data class DeviceCredentialDto(val id: String, val scope: String, val deviceName: String)

interface DeviceCredentialApi {
    @POST("auth/device-credentials/registration-options")
    suspend fun registrationOptions(
        @Header("Authorization") token: String,
        @Body input: DeviceRegistrationRequest,
    ): Response<ApiEnvelope<DeviceChallengeDto>>

    @POST("auth/device-credentials/registration-verify")
    suspend fun registrationVerify(
        @Header("Authorization") token: String,
        @Body input: DeviceVerifyRequest,
    ): Response<ApiEnvelope<DeviceCredentialDto>>

    @POST("auth/device-credentials/login-options")
    suspend fun loginOptions(
        @Body input: DeviceLoginRequest
    ): Response<ApiEnvelope<DeviceChallengeDto>>

    @POST("auth/device-credentials/login-verify")
    suspend fun loginVerify(@Body input: DeviceVerifyRequest): Response<ApiEnvelope<AuthSessionDto>>

    @GET("auth/device-credentials")
    suspend fun list(
        @Header("Authorization") token: String
    ): Response<ApiEnvelope<List<DeviceCredentialDto>>>

    @DELETE("auth/device-credentials/{id}")
    suspend fun revoke(
        @Header("Authorization") token: String,
        @Path("id") id: String,
    ): Response<ApiEnvelope<JsonElement>>
}
