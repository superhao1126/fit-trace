package com.fittrace.core.data

import com.fittrace.core.domain.*
import com.fittrace.core.model.*
import com.fittrace.core.network.*
import java.util.UUID
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class DefaultBiometricRepository(
    private val api: DeviceCredentialApi,
    private val auth: DefaultAuthRepository,
    private val keys: DeviceKeyStore,
    private val scope: String,
    private val deviceName: String,
) : BiometricRepository {
    private val requests = AuthRequests()
    private val mutex = Mutex()

    private fun failure(code: String, message: String) =
        AppResult.Failure(AppError.Business(code, message))

    override suspend fun status(checkServer: Boolean): AppResult<BiometricState> =
        mutex.withLock {
            try {
                var binding = keys.read()
                val capability = keys.capability()
                val invalid = binding != null && !keys.usable(binding.alias)
                var enabled = binding != null && !binding.pending && !invalid
                if (checkServer && binding != null) {
                    val account = (auth.sessionStatus.value as? SessionStatus.SignedIn)?.user
                    if (account?.id != binding.userId)
                        return@withLock failure("DEVICE_ACCOUNT_MISMATCH", "请先移除其他账号的本机凭据")
                    val result = auth.authorized { token -> requests.call { api.list(token) } }
                    if (result is AppResult.Failure) return@withLock result
                    val exists =
                        (result as AppResult.Success).data.any {
                            it.id == binding!!.id && it.scope == scope
                        }
                    enabled = exists && !invalid
                    if (exists && binding.pending) {
                        binding = binding.copy(pending = false)
                        keys.write(binding)
                    }
                }
                AppResult.Success(BiometricState(capability, binding, enabled, invalid))
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                AppResult.Failure(AppError.Storage)
            }
        }

    override suspend fun enable(emailCode: String?, signer: BiometricSigner): AppResult<Unit> =
        mutex.withLock {
            val user =
                (auth.sessionStatus.value as? SessionStatus.SignedIn)?.user
                    ?: return@withLock failure("UNAUTHORIZED", "请先登录")
            if (keys.capability() != BiometricCapability.AVAILABLE)
                return@withLock failure("BIOMETRIC_UNAVAILABLE", "请先在系统设置中录入可用的强生物识别")
            if (keys.read() != null)
                return@withLock failure("DEVICE_BINDING_EXISTS", "请先检查或关闭现有本机凭据，再重新开启")
            val alias = "fittrace-biometric-${UUID.randomUUID()}"
            var pendingSaved = false
            try {
                val publicKey = keys.create(alias)
                val options =
                    auth.authorized { token ->
                        requests.call {
                            api.registrationOptions(
                                token,
                                DeviceRegistrationRequest(
                                    publicKey,
                                    scope,
                                    deviceName,
                                    emailCode?.takeIf { it.isNotBlank() },
                                ),
                            )
                        }
                    }
                if (options is AppResult.Failure) return@withLock options
                val challenge = (options as AppResult.Success).data
                val id =
                    challenge.credentialId
                        ?: return@withLock AppResult.Failure(AppError.InvalidResponse)
                val signed = signer.sign(alias, challenge.payload)
                if (signed is AppResult.Failure) return@withLock signed
                // 在可能产生服务端绑定的请求前落盘，超时/进程退出后仍能查询恢复。
                keys.write(DeviceBinding(id, user.id, user.email, alias, pending = true))
                pendingSaved = true
                val result =
                    auth.authorized { token ->
                        requests.call {
                            api.registrationVerify(
                                token,
                                DeviceVerifyRequest(
                                    challenge.challengeId,
                                    (signed as AppResult.Success).data,
                                ),
                            )
                        }
                    }
                if (result is AppResult.Failure) return@withLock result
                if ((result as AppResult.Success).data.id != id)
                    return@withLock AppResult.Failure(AppError.InvalidResponse)
                withContext(NonCancellable) {
                    keys.write(DeviceBinding(id, user.id, user.email, alias))
                }
                AppResult.Success(Unit)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                AppResult.Failure(AppError.Storage)
            } finally {
                if (!pendingSaved) keys.delete(alias)
            }
        }

    override suspend fun login(signer: BiometricSigner): AppResult<Unit> =
        mutex.withLock {
            val binding =
                keys.read() ?: return@withLock failure("DEVICE_CREDENTIAL_INVALID", "请使用邮箱登录")
            if (binding.pending || !keys.usable(binding.alias))
                return@withLock failure("DEVICE_CREDENTIAL_INVALID", "请使用邮箱登录后重新开启")
            val options = requests.call { api.loginOptions(DeviceLoginRequest(binding.id, scope)) }
            if (options is AppResult.Failure) return@withLock options
            val challenge = (options as AppResult.Success).data
            val signature = signer.sign(binding.alias, challenge.payload)
            if (signature is AppResult.Failure) return@withLock signature
            val result =
                requests.call {
                    api.loginVerify(
                        DeviceVerifyRequest(
                            challenge.challengeId,
                            (signature as AppResult.Success).data,
                        )
                    )
                }
            if (result is AppResult.Failure) return@withLock result
            val session = (result as AppResult.Success).data
            if (session.user.id != binding.userId)
                return@withLock AppResult.Failure(AppError.InvalidResponse)
            auth.acceptDeviceSession(session)
        }

    override suspend fun disable(): AppResult<Unit> =
        mutex.withLock {
            val binding = keys.read() ?: return@withLock AppResult.Success(Unit)
            val user = (auth.sessionStatus.value as? SessionStatus.SignedIn)?.user
            if (user?.id != binding.userId)
                return@withLock failure("DEVICE_ACCOUNT_MISMATCH", "请移除本机凭据，原账号的服务端绑定需由原账号撤销")
            val result =
                auth.authorized { token -> requests.unit { api.revoke(token, binding.id) } }
            if (result is AppResult.Failure) return@withLock result
            clearLocal()
        }

    override suspend fun removeLocal(): AppResult<Unit> = mutex.withLock { clearLocal() }

    private suspend fun clearLocal(): AppResult<Unit> =
        withContext(NonCancellable) {
            try {
                keys.read()?.let { keys.delete(it.alias) }
                keys.clear()
                AppResult.Success(Unit)
            } catch (_: Exception) {
                AppResult.Failure(AppError.Storage)
            }
        }
}
