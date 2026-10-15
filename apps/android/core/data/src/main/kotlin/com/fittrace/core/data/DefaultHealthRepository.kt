package com.fittrace.core.data

import com.fittrace.core.domain.HealthRepository
import com.fittrace.core.model.AppError
import com.fittrace.core.model.AppResult
import com.fittrace.core.model.HealthStatus
import com.fittrace.core.network.HealthApi
import java.io.IOException
import kotlinx.coroutines.CancellationException
import retrofit2.HttpException

class DefaultHealthRepository(private val api: HealthApi) : HealthRepository {
    override suspend fun getHealth(): AppResult<HealthStatus> =
        try {
            val response = api.getHealth()
            when {
                response.code != "OK" ->
                    AppResult.Failure(AppError.Business(response.code, response.message))
                response.data?.status != "ok" -> AppResult.Failure(AppError.InvalidResponse)
                else -> AppResult.Success(HealthStatus(available = true))
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: HttpException) {
            AppResult.Failure(AppError.Http(error.code()))
        } catch (error: IOException) {
            AppResult.Failure(AppError.Network)
        } catch (error: IllegalArgumentException) {
            // Kotlin Serialization 的数据格式异常，不将无效响应当作业务成功。
            AppResult.Failure(AppError.InvalidResponse)
        }
}
