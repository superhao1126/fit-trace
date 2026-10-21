package com.fittrace.core.network

import kotlinx.serialization.Serializable
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Response
import retrofit2.http.*

interface RecordsApi {
    @GET("insights/overview")
    suspend fun insights(
        @Header("Authorization") token: String,
        @Query("days") days: Int,
        @Query("tzOffset") tzOffset: Int,
    ): Response<ApiEnvelope<PeriodInsightsDto>>

    @GET("training/templates")
    suspend fun templates(
        @Header("Authorization") token: String
    ): Response<ApiEnvelope<List<TrainingTemplateDto>>>

    @POST("training/templates")
    suspend fun createTemplate(
        @Header("Authorization") token: String,
        @Body input: TrainingTemplateInputDto,
    ): Response<ApiEnvelope<TrainingTemplateDto>>

    @PATCH("training/templates/{id}")
    suspend fun updateTemplate(
        @Header("Authorization") token: String,
        @Path("id") id: String,
        @Body input: TrainingTemplateInputDto,
    ): Response<ApiEnvelope<TrainingTemplateDto>>

    @GET("training/plan/state")
    suspend fun plan(@Header("Authorization") token: String): Response<ApiEnvelope<TrainingPlanDto>>

    @PUT("training/plan")
    suspend fun savePlan(
        @Header("Authorization") token: String,
        @Body input: TrainingPlanInputDto,
    ): Response<ApiEnvelope<List<TrainingPlanDayDto>>>

    @GET("body-records")
    suspend fun bodies(
        @Header("Authorization") token: String,
        @Query("page") page: Int,
        @Query("from") from: String?,
        @Query("to") to: String?,
    ): Response<ApiEnvelope<List<BodyEntryDto>>>

    @GET("body-records/{id}")
    suspend fun body(
        @Header("Authorization") token: String,
        @Path("id") id: String,
    ): Response<ApiEnvelope<BodyEntryDto>>

    @POST("body-records")
    suspend fun createBody(
        @Header("Authorization") token: String,
        @Body input: BodyInputDto,
    ): Response<ApiEnvelope<BodyEntryDto>>

    @PATCH("body-records/{id}")
    suspend fun updateBody(
        @Header("Authorization") token: String,
        @Path("id") id: String,
        @Body input: BodyInputDto,
    ): Response<ApiEnvelope<BodyEntryDto>>

    @GET("meals")
    suspend fun meals(
        @Header("Authorization") token: String,
        @Query("page") page: Int,
        @Query("from") from: String?,
        @Query("to") to: String?,
    ): Response<ApiEnvelope<List<MealEntryDto>>>

    @GET("meals/{id}")
    suspend fun meal(
        @Header("Authorization") token: String,
        @Path("id") id: String,
    ): Response<ApiEnvelope<MealEntryDto>>

    @POST("meals")
    suspend fun createMeal(
        @Header("Authorization") token: String,
        @Body input: MealInputDto,
    ): Response<ApiEnvelope<MealEntryDto>>

    @PATCH("meals/{id}")
    suspend fun updateMeal(
        @Header("Authorization") token: String,
        @Path("id") id: String,
        @Body input: MealInputDto,
    ): Response<ApiEnvelope<MealEntryDto>>

    @GET("workouts")
    suspend fun workouts(
        @Header("Authorization") token: String,
        @Query("page") page: Int,
        @Query("from") from: String?,
        @Query("to") to: String?,
    ): Response<ApiEnvelope<List<WorkoutEntryDto>>>

    @GET("workouts/{id}")
    suspend fun workout(
        @Header("Authorization") token: String,
        @Path("id") id: String,
    ): Response<ApiEnvelope<WorkoutEntryDto>>

    @POST("workouts")
    suspend fun createWorkout(
        @Header("Authorization") token: String,
        @Body input: WorkoutInputDto,
    ): Response<ApiEnvelope<WorkoutEntryDto>>

    @PATCH("workouts/{id}")
    suspend fun updateWorkout(
        @Header("Authorization") token: String,
        @Path("id") id: String,
        @Body input: WorkoutInputDto,
    ): Response<ApiEnvelope<WorkoutEntryDto>>

    @GET("progress-photos")
    suspend fun photos(
        @Header("Authorization") token: String,
        @Query("page") page: Int,
        @Query("from") from: String?,
        @Query("to") to: String?,
    ): Response<ApiEnvelope<List<PhotoEntryDto>>>

    @GET("progress-photos/{id}")
    suspend fun photo(
        @Header("Authorization") token: String,
        @Path("id") id: String,
    ): Response<ApiEnvelope<PhotoEntryDto>>

    @POST("progress-photos")
    suspend fun createPhoto(
        @Header("Authorization") token: String,
        @Body input: PhotoInputDto,
    ): Response<ApiEnvelope<PhotoEntryDto>>

    @DELETE("{path}/{id}")
    suspend fun delete(
        @Header("Authorization") token: String,
        @Path("path") path: String,
        @Path("id") id: String,
    ): Response<ApiEnvelope<String>>

    @GET("body-records/trends")
    suspend fun trends(
        @Header("Authorization") token: String,
        @Query("days") days: Int,
    ): Response<ApiEnvelope<BodyTrendsDto>>

    @Multipart
    @POST("uploads")
    suspend fun upload(
        @Header("Authorization") token: String,
        @Part file: MultipartBody.Part,
    ): Response<ApiEnvelope<UploadDto>>
}

@Serializable
data class BodyEntryDto(
    val id: String,
    val weight: Double,
    val bodyFat: Double? = null,
    val waist: Double? = null,
    val chest: Double? = null,
    val hip: Double? = null,
    val recordedAt: String,
    val note: String? = null,
)

@Serializable
data class BodyInputDto(
    val weight: Double,
    val bodyFat: Double?,
    val waist: Double?,
    val chest: Double?,
    val hip: Double?,
    val recordedAt: String,
    val note: String?,
)

@Serializable
data class FoodEntryDto(val name: String, val amount: String? = null, val calories: Int? = null)

@Serializable
data class MealEntryDto(
    val id: String,
    val type: String,
    val recordedAt: String,
    val foods: List<FoodEntryDto>,
    val totalCalories: Double?,
    val note: String? = null,
    val imageUrl: String? = null,
)

@Serializable
data class MealInputDto(
    val type: String,
    val recordedAt: String,
    val foods: List<FoodEntryDto>,
    val note: String?,
)

@Serializable
data class WorkoutEntryDto(
    val id: String,
    val type: String,
    val name: String,
    val startedAt: String,
    val durationMinutes: Int,
    val calories: Int? = null,
    val note: String? = null,
    val exercises: List<TrainingExerciseDto>? = null,
)

@Serializable
data class WorkoutInputDto(
    val type: String,
    val name: String,
    val startedAt: String,
    val durationMinutes: Int,
    val calories: Int?,
    val note: String?,
    val exercises: List<TrainingExerciseDto>? = null,
)

@Serializable
data class PhotoEntryDto(
    val id: String,
    val type: String,
    val imageUrl: String,
    val recordedAt: String,
    val note: String? = null,
)

@Serializable
data class PhotoInputDto(
    val type: String,
    val imageUrl: String,
    val recordedAt: String,
    val note: String?,
)

@Serializable data class UploadDto(val url: String)

@Serializable data class TrendPointDto(val recordedAt: String, val value: Double)

@Serializable data class TrendSeriesDto(val points: List<TrendPointDto>)

@Serializable
data class BodyTrendsDto(
    val days: Int,
    val from: String,
    val to: String,
    val weight: TrendSeriesDto,
    val bodyFat: TrendSeriesDto,
    val waist: TrendSeriesDto,
)

fun imagePart(bytes: ByteArray, mimeType: String): MultipartBody.Part {
    require(bytes.isNotEmpty() && bytes.size <= 6 * 1024 * 1024)
    require(mimeType in setOf("image/jpeg", "image/png", "image/webp"))
    return MultipartBody.Part.createFormData(
        "file",
        "photo.${mimeType.substringAfter('/')}",
        bytes.toRequestBody(mimeType.toMediaType()),
    )
}

@Serializable
data class TrainingSetDto(val reps: Int, val weightKg: Double, val completed: Boolean)

@Serializable
data class TrainingExerciseDto(
    val exerciseId: String,
    val name: String,
    val unit: String,
    val restSeconds: Int,
    val sets: List<TrainingSetDto>,
)

@Serializable
data class TrainingTemplateDto(
    val id: String,
    val name: String,
    val durationMinutes: Int,
    val exercises: List<TrainingExerciseDto>,
)

@Serializable
data class TrainingTemplateInputDto(
    val name: String,
    val durationMinutes: Int,
    val exercises: List<TrainingExerciseDto>,
)

@Serializable
data class TrainingPlanDto(val configured: Boolean, val days: List<TrainingPlanDayDto>)

@Serializable data class TrainingPlanDayDto(val weekday: Int, val templateId: String?)

@Serializable data class TrainingPlanInputDto(val days: List<TrainingPlanDayDto>)

@Serializable
data class PeriodInsightsDto(
    val days: Int,
    val calories: InsightCaloriesDto,
    val meals: InsightMealsDto,
    val workouts: InsightWorkoutsDto,
)

@Serializable data class InsightCaloriesDto(val average: Double?)

@Serializable data class InsightMealsDto(val count: Int, val recordedDays: Int)

@Serializable data class InsightWorkoutsDto(val count: Int, val totalMinutes: Int)
