package com.fittrace.feature.main

import com.fittrace.core.domain.*
import com.fittrace.core.model.*

internal open class RecordsRepositoryFake : RecordsRepository {
    override suspend fun bodies(
        page: Int,
        from: String?,
        to: String?,
    ): AppResult<RecordPage<BodyEntry>> = AppResult.Success(RecordPage(emptyList(), 0))

    override suspend fun body(id: String): AppResult<BodyEntry> =
        AppResult.Failure(AppError.Network)

    override suspend fun saveBody(input: BodyInput, id: String?): AppResult<BodyEntry> =
        AppResult.Failure(AppError.Network)

    override suspend fun meals(
        page: Int,
        from: String?,
        to: String?,
    ): AppResult<RecordPage<MealEntry>> = AppResult.Success(RecordPage(emptyList(), 0))

    override suspend fun meal(id: String): AppResult<MealEntry> =
        AppResult.Failure(AppError.Network)

    override suspend fun saveMeal(input: MealInput, id: String?): AppResult<MealEntry> =
        AppResult.Failure(AppError.Network)

    override suspend fun workouts(
        page: Int,
        from: String?,
        to: String?,
    ): AppResult<RecordPage<WorkoutEntry>> = AppResult.Success(RecordPage(emptyList(), 0))

    override suspend fun workout(id: String): AppResult<WorkoutEntry> =
        AppResult.Failure(AppError.Network)

    override suspend fun saveWorkout(input: WorkoutInput, id: String?): AppResult<WorkoutEntry> =
        AppResult.Failure(AppError.Network)

    override suspend fun photos(
        page: Int,
        from: String?,
        to: String?,
    ): AppResult<RecordPage<PhotoEntry>> = AppResult.Success(RecordPage(emptyList(), 0))

    override suspend fun photo(id: String): AppResult<PhotoEntry> =
        AppResult.Failure(AppError.Network)

    override suspend fun savePhoto(input: PhotoInput): AppResult<PhotoEntry> =
        AppResult.Failure(AppError.Network)

    override suspend fun upload(bytes: ByteArray, mimeType: String): AppResult<String> =
        AppResult.Failure(AppError.Network)

    override suspend fun delete(kind: RecordKind, id: String): AppResult<Unit> =
        AppResult.Failure(AppError.Network)

    override suspend fun insights(days: Int, tzOffset: Int): AppResult<PeriodInsights> =
        AppResult.Success(PeriodInsights(days, null, 0, 0, 0, 0))

    override suspend fun trends(days: Int): AppResult<BodyTrends> =
        AppResult.Success(
            BodyTrends(
                days,
                "2026-10-01",
                "2026-10-20",
                TrendSeries(emptyList()),
                TrendSeries(emptyList()),
                TrendSeries(emptyList()),
            )
        )
}

internal open class TrainingRepositoryFake : TrainingRepository {
    override suspend fun templates(): AppResult<List<TrainingTemplate>> =
        AppResult.Success(emptyList())

    override suspend fun saveTemplate(
        name: String,
        minutes: Int,
        exercises: List<TrainingExercise>,
        id: String?,
    ): AppResult<TrainingTemplate> = AppResult.Failure(AppError.Network)

    override suspend fun plan(): AppResult<TrainingPlan> =
        AppResult.Success(TrainingPlan(false, (0..6).map { TrainingPlanDay(it, null) }))

    override suspend fun savePlan(days: List<TrainingPlanDay>): AppResult<List<TrainingPlanDay>> =
        AppResult.Success(days)
}
