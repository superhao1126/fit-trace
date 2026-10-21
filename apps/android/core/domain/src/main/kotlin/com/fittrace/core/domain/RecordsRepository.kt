package com.fittrace.core.domain

import com.fittrace.core.model.*

interface RecordsRepository {
    suspend fun bodies(
        page: Int,
        from: String? = null,
        to: String? = null,
    ): AppResult<RecordPage<BodyEntry>>

    suspend fun body(id: String): AppResult<BodyEntry>

    suspend fun saveBody(input: BodyInput, id: String? = null): AppResult<BodyEntry>

    suspend fun meals(
        page: Int,
        from: String? = null,
        to: String? = null,
    ): AppResult<RecordPage<MealEntry>>

    suspend fun meal(id: String): AppResult<MealEntry>

    suspend fun saveMeal(input: MealInput, id: String? = null): AppResult<MealEntry>

    suspend fun workouts(
        page: Int,
        from: String? = null,
        to: String? = null,
    ): AppResult<RecordPage<WorkoutEntry>>

    suspend fun workout(id: String): AppResult<WorkoutEntry>

    suspend fun saveWorkout(input: WorkoutInput, id: String? = null): AppResult<WorkoutEntry>

    suspend fun photos(
        page: Int,
        from: String? = null,
        to: String? = null,
    ): AppResult<RecordPage<PhotoEntry>>

    suspend fun photo(id: String): AppResult<PhotoEntry>

    suspend fun savePhoto(input: PhotoInput): AppResult<PhotoEntry>

    suspend fun upload(bytes: ByteArray, mimeType: String): AppResult<String>

    suspend fun delete(kind: RecordKind, id: String): AppResult<Unit>

    suspend fun trends(days: Int): AppResult<BodyTrends>
}
