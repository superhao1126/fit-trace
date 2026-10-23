package com.fittrace.android.di

import android.content.Context
import android.util.Log
import com.fittrace.core.data.DefaultAuthRepository
import com.fittrace.core.data.DefaultDashboardRepository
import com.fittrace.core.data.DefaultHealthRepository
import com.fittrace.core.data.DefaultProfileRepository
import com.fittrace.core.domain.AuthRepository
import com.fittrace.core.domain.DashboardRepository
import com.fittrace.core.domain.HealthRepository
import com.fittrace.core.domain.ProfileRepository
import com.fittrace.core.network.NetworkClient
import com.fittrace.core.network.NetworkLogSink
import com.fittrace.core.network.NetworkLogStage
import com.fittrace.core.session.KeystoreSessionStore

/** 唯一的应用级依赖装配入口，feature 只接收领域接口。 */
class AppContainer(context: Context, baseUrl: String, debugLogging: Boolean = false) {
    private val appContext = context.applicationContext

    fun trainingDraftStore(accountId: String): com.fittrace.core.domain.TrainingDraftStore =
        com.fittrace.core.session.LocalTrainingDraftStore(appContext, accountId)

    private val network =
        NetworkClient(
            baseUrl,
            logSink =
                if (debugLogging)
                    NetworkLogSink { event ->
                        if (
                            event.stage == NetworkLogStage.FAILURE || (event.statusCode ?: 0) >= 400
                        )
                            event.logLines().forEach { Log.w("FitTrace.Http", it) }
                        else event.logLines().forEach { Log.d("FitTrace.Http", it) }
                    }
                else null,
        )
    private val sessionAuth = DefaultAuthRepository(network.authApi, KeystoreSessionStore(context))
    val biometricRepository: com.fittrace.core.domain.BiometricRepository =
        com.fittrace.core.data.DefaultBiometricRepository(
            network.deviceCredentialApi,
            sessionAuth,
            com.fittrace.core.session.BiometricKeyStore(appContext) {
                when (
                    androidx.biometric.BiometricManager.from(appContext)
                        .canAuthenticate(
                            androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
                        )
                ) {
                    androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS ->
                        com.fittrace.core.model.BiometricCapability.AVAILABLE
                    androidx.biometric.BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED ->
                        com.fittrace.core.model.BiometricCapability.NOT_ENROLLED
                    androidx.biometric.BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE ->
                        com.fittrace.core.model.BiometricCapability.UNSUPPORTED
                    else -> com.fittrace.core.model.BiometricCapability.TEMPORARILY_UNAVAILABLE
                }
            },
            if (baseUrl.isNotBlank() && appContext.packageName.contains(".dev")) "android-dev"
            else "android-prod",
            android.os.Build.MODEL.take(80),
        )
    private val records =
        com.fittrace.core.data.DefaultRecordsRepository(network.recordsApi, sessionAuth)
    val recordsRepository: com.fittrace.core.domain.RecordsRepository = records
    val trainingRepository: com.fittrace.core.domain.TrainingRepository = records
    val healthRepository: HealthRepository = DefaultHealthRepository(network.healthApi)
    val authRepository: AuthRepository = sessionAuth
    val profileRepository: ProfileRepository =
        DefaultProfileRepository(network.profileApi, sessionAuth)
    val dashboardRepository: DashboardRepository =
        DefaultDashboardRepository(network.dashboardApi, sessionAuth)
}
