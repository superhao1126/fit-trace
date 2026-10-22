package com.fittrace.core.data

import com.fittrace.core.domain.RecordsRepository
import com.fittrace.core.domain.TrainingRepository
import com.fittrace.core.model.*
import com.fittrace.core.network.*
import retrofit2.Response

class DefaultRecordsRepository(
    private val api: RecordsApi,
    private val auth: DefaultAuthRepository,
) : RecordsRepository, TrainingRepository {

    override suspend fun templates(): AppResult<List<TrainingTemplate>> =
        auth.authorized { token ->
            requests.call { api.templates(token) }.map { it.map { template -> template.model() } }
        }

    override suspend fun saveTemplate(
        name: String,
        minutes: Int,
        exercises: List<TrainingExercise>,
        id: String?,
    ): AppResult<TrainingTemplate> =
        auth.authorized { token ->
            val input = TrainingTemplateInputDto(name, minutes, exercises.map { it.dto() })
            requests
                .call {
                    if (id == null) api.createTemplate(token, input)
                    else api.updateTemplate(token, id, input)
                }
                .map { it.model() }
        }

    override suspend fun plan(): AppResult<TrainingPlan> =
        auth.authorized { token ->
            requests
                .call { api.plan(token) }
                .map {
                    TrainingPlan(
                        it.configured,
                        it.days.map { d -> TrainingPlanDay(d.weekday, d.templateId) },
                    )
                }
        }

    override suspend fun savePlan(days: List<TrainingPlanDay>): AppResult<List<TrainingPlanDay>> =
        auth.authorized { token ->
            requests
                .call {
                    api.savePlan(
                        token,
                        TrainingPlanInputDto(
                            days.map { TrainingPlanDayDto(it.weekday, it.templateId) }
                        ),
                    )
                }
                .map { it.map { d -> TrainingPlanDay(d.weekday, d.templateId) } }
        }

    private val requests = AuthRequests()

    private suspend fun <T, R> page(
        block: suspend () -> Response<ApiEnvelope<List<T>>>,
        convert: (T) -> R,
    ): AppResult<RecordPage<R>> {
        var total: Int? = null
        return requests
            .call { block().also { total = it.body()?.meta?.total } }
            .map { list ->
                RecordPage(
                    list.map(convert),
                    requireNotNull(total).also { require(it >= list.size) },
                )
            }
    }

    override suspend fun bodies(
        page: Int,
        from: String?,
        to: String?,
    ): AppResult<RecordPage<BodyEntry>> =
        auth.authorized { token -> page({ api.bodies(token, page, from, to) }, { it.model() }) }

    override suspend fun body(id: String): AppResult<BodyEntry> =
        auth.authorized { token -> requests.call { api.body(token, id) }.map { it.model() } }

    override suspend fun saveBody(input: BodyInput, id: String?): AppResult<BodyEntry> =
        auth.authorized { token ->
            val dto =
                BodyInputDto(
                    input.weight,
                    input.bodyFat,
                    input.waist,
                    input.chest,
                    input.hip,
                    input.recordedAt,
                    input.note,
                )
            requests
                .call {
                    if (id == null) api.createBody(token, dto) else api.updateBody(token, id, dto)
                }
                .map { it.model() }
        }

    override suspend fun meals(
        page: Int,
        from: String?,
        to: String?,
    ): AppResult<RecordPage<MealEntry>> =
        auth.authorized { token -> page({ api.meals(token, page, from, to) }, { it.model() }) }

    override suspend fun mealFavorites(): AppResult<List<MealFavorite>> =
        auth.authorized { token ->
            requests
                .call { api.mealFavorites(token) }
                .map { list ->
                    list.map {
                        MealFavorite(
                            it.id,
                            it.name,
                            it.foods.map { f ->
                                FoodEntry(f.name, f.amount, f.calories, f.aiGenerated)
                            },
                        )
                    }
                }
        }

    override suspend fun saveMealFavorite(
        name: String,
        foods: List<FoodEntry>,
    ): AppResult<MealFavorite> =
        auth.authorized { token ->
            requests
                .call {
                    api.saveMealFavorite(
                        token,
                        MealFavoriteInputDto(
                            name,
                            foods.map {
                                FoodEntryDto(it.name, it.amount, it.calories, it.aiGenerated)
                            },
                        ),
                    )
                }
                .map {
                    MealFavorite(
                        it.id,
                        it.name,
                        it.foods.map { f -> FoodEntry(f.name, f.amount, f.calories, f.aiGenerated) },
                    )
                }
        }

    override suspend fun removeMealFavorite(id: String): AppResult<Unit> =
        auth.authorized { token -> requests.unit { api.removeMealFavorite(token, id) } }

    override suspend fun recognizeMealText(description: String): AppResult<FoodRecognition> =
        auth.authorized { token ->
            requests
                .call { api.recognizeMealText(token, FoodTextInputDto(description)) }
                .map { it.recognition() }
        }

    override suspend fun recognizeMealPhoto(
        imageUrl: String,
        hint: String?,
    ): AppResult<FoodRecognition> =
        auth.authorized { token ->
            requests
                .call { api.recognizeMealPhoto(token, FoodPhotoInputDto(imageUrl, hint)) }
                .map { it.recognition() }
        }

    override suspend fun meal(id: String): AppResult<MealEntry> =
        auth.authorized { token -> requests.call { api.meal(token, id) }.map { it.model() } }

    override suspend fun saveMeal(input: MealInput, id: String?): AppResult<MealEntry> =
        auth.authorized { token ->
            val dto =
                MealInputDto(
                    input.type,
                    input.recordedAt,
                    input.foods.map {
                        FoodEntryDto(it.name, it.amount, it.calories, it.aiGenerated)
                    },
                    input.note,
                    input.imageUrl,
                )
            requests
                .call {
                    if (id == null) api.createMeal(token, dto) else api.updateMeal(token, id, dto)
                }
                .map { it.model() }
        }

    override suspend fun workouts(
        page: Int,
        from: String?,
        to: String?,
    ): AppResult<RecordPage<WorkoutEntry>> =
        auth.authorized { token -> page({ api.workouts(token, page, from, to) }, { it.model() }) }

    override suspend fun workout(id: String): AppResult<WorkoutEntry> =
        auth.authorized { token -> requests.call { api.workout(token, id) }.map { it.model() } }

    override suspend fun saveWorkout(input: WorkoutInput, id: String?): AppResult<WorkoutEntry> =
        auth.authorized { token ->
            val dto =
                WorkoutInputDto(
                    input.type,
                    input.name,
                    input.startedAt,
                    input.durationMinutes,
                    input.calories,
                    input.note,
                    input.exercises?.map { it.dto() },
                )
            requests
                .call {
                    if (id == null) api.createWorkout(token, dto)
                    else api.updateWorkout(token, id, dto)
                }
                .map { it.model() }
        }

    override suspend fun photos(
        page: Int,
        from: String?,
        to: String?,
    ): AppResult<RecordPage<PhotoEntry>> =
        auth.authorized { token -> page({ api.photos(token, page, from, to) }, { it.model() }) }

    override suspend fun photo(id: String): AppResult<PhotoEntry> =
        auth.authorized { token -> requests.call { api.photo(token, id) }.map { it.model() } }

    override suspend fun savePhoto(input: PhotoInput): AppResult<PhotoEntry> =
        auth.authorized { token ->
            requests
                .call {
                    api.createPhoto(
                        token,
                        PhotoInputDto(input.type, input.imageUrl, input.recordedAt, input.note),
                    )
                }
                .map { it.model() }
        }

    override suspend fun delete(kind: RecordKind, id: String): AppResult<Unit> =
        auth.authorized { token ->
            requests.unit {
                api.delete(
                    token,
                    when (kind) {
                        RecordKind.BODY -> "body-records"
                        RecordKind.MEAL -> "meals"
                        RecordKind.WORKOUT -> "workouts"
                        RecordKind.PHOTO -> "progress-photos"
                    },
                    id,
                )
            }
        }

    override suspend fun upload(bytes: ByteArray, mimeType: String): AppResult<String> =
        auth.authorized { token ->
            requests.call { api.upload(token, imagePart(bytes, mimeType)) }.map { it.url }
        }

    override suspend fun insights(days: Int, tzOffset: Int): AppResult<PeriodInsights> =
        auth.authorized { token ->
            requests
                .call { api.insights(token, days, tzOffset) }
                .map {
                    PeriodInsights(
                        it.days,
                        it.calories.average,
                        it.meals.count,
                        it.meals.recordedDays,
                        it.workouts.count,
                        it.workouts.totalMinutes,
                    )
                }
        }

    override suspend fun trends(days: Int): AppResult<BodyTrends> =
        auth.authorized { token ->
            requests
                .call { api.trends(token, days) }
                .map {
                    BodyTrends(
                        it.days,
                        it.from,
                        it.to,
                        it.weight.model(),
                        it.bodyFat.model(),
                        it.waist.model(),
                    )
                }
        }
}

private fun BodyEntryDto.model() =
    BodyEntry(id, weight, bodyFat, waist, chest, hip, recordedAt, note)

private fun MealEntryDto.model() =
    MealEntry(
        id,
        type,
        recordedAt,
        foods.map { FoodEntry(it.name, it.amount, it.calories, it.aiGenerated) },
        totalCalories,
        note,
        imageUrl,
    )

private fun WorkoutEntryDto.model() =
    WorkoutEntry(
        id,
        type,
        name,
        startedAt,
        durationMinutes,
        calories,
        note,
        exercises?.map { it.model() },
    )

private fun PhotoEntryDto.model() = PhotoEntry(id, type, imageUrl, recordedAt, note)

private fun TrendSeriesDto.model() = TrendSeries(points.map { TrendPoint(it.recordedAt, it.value) })

private fun TrainingExercise.dto() =
    TrainingExerciseDto(
        exerciseId,
        name,
        unit,
        restSeconds,
        sets.map { TrainingSetDto(it.reps, it.weightKg, it.completed) },
    )

private fun TrainingExerciseDto.model() =
    TrainingExercise(
        exerciseId,
        name,
        unit,
        restSeconds,
        sets.map { TrainingSet(it.reps, it.weightKg, it.completed) },
    )

private fun TrainingTemplateDto.model() =
    TrainingTemplate(id, name, durationMinutes, exercises.map { it.model() })

private fun FoodRecognitionDto.recognition() =
    FoodRecognition(
        provider,
        foods.map { FoodEntry(it.name, it.estimatedAmount, it.estimatedCalories, true) },
    )
