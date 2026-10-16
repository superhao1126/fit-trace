package com.fittrace.core.data

import com.fittrace.core.domain.DashboardRepository
import com.fittrace.core.model.*
import com.fittrace.core.network.*
import java.time.LocalDate
import java.time.OffsetDateTime

class DefaultDashboardRepository(
    private val api: DashboardApi,
    private val auth: DefaultAuthRepository,
) : DashboardRepository {
    private val requests = AuthRequests()

    override suspend fun overview(date: String, timezoneOffset: Int): AppResult<Dashboard> =
        auth.authorized { token ->
            val overview = requests.call { api.overview(token, date, timezoneOffset) }
            if (overview is AppResult.Failure) return@authorized overview
            val profile = requests.call { api.profile(token) }
            if (profile is AppResult.Failure) return@authorized profile
            val goal = (profile as AppResult.Success).data.goal

            overview.map { dto ->
                require(dto.date == date)
                LocalDate.parse(dto.date)
                val today = dto.today
                require(
                    listOf(
                            today.mealCount,
                            today.workoutCount,
                            today.workoutMinutes,
                            today.photoCount,
                        )
                        .all { it >= 0 }
                )
                require(today.calories?.let { it.isFinite() && it >= 0 } != false)
                val records =
                    buildList {
                            today.bodyRecord?.let {
                                require(it.weight.isFinite() && it.weight > 0)
                                add(
                                    DiaryRecord(
                                        it.id,
                                        RecordKind.BODY,
                                        "身体数据",
                                        "体重记录",
                                        "${it.weight.number()} kg",
                                        it.recordedAt,
                                    )
                                )
                            }
                            today.mealPreview.forEach {
                                add(
                                    DiaryRecord(
                                        it.id,
                                        RecordKind.MEAL,
                                        mealName(it.type),
                                        it.foodNames.joinToString("、"),
                                        it.totalCalories?.let { n -> "${n.number()} kcal" }
                                            ?: "热量未填写",
                                        it.recordedAt,
                                    )
                                )
                            }
                            today.workoutPreview.forEach {
                                add(
                                    DiaryRecord(
                                        it.id,
                                        RecordKind.WORKOUT,
                                        it.name?.takeIf(String::isNotBlank) ?: workoutName(it.type),
                                        "训练",
                                        "${it.durationMinutes} 分钟",
                                        it.startedAt,
                                    )
                                )
                            }
                            today.photoPreview.forEach {
                                add(
                                    DiaryRecord(
                                        it.id,
                                        RecordKind.PHOTO,
                                        photoName(it.type),
                                        "身材记录",
                                        "",
                                        it.recordedAt,
                                    )
                                )
                            }
                        }
                        .onEach { OffsetDateTime.parse(it.recordedAt) }
                        .sortedBy { OffsetDateTime.parse(it.recordedAt).toInstant() }
                val trend = dto.recentTrend
                Dashboard(
                    dto.date,
                    today.bodyRecord?.weight,
                    today.calories,
                    today.workoutMinutes,
                    today.workoutCount,
                    today.mealCount,
                    today.photoCount,
                    records,
                    DashboardTrend(
                        trend.from,
                        trend.to,
                        trend.weightChange,
                        trend.bodyRecordedDays,
                        trend.mealRecordedDays,
                        trend.workoutCount,
                        trend.workoutMinutes,
                    ),
                    goal?.let {
                        require(it.targetWeight.isFinite() && it.targetWeight > 0)
                        DashboardGoal(it.type, it.targetWeight)
                    },
                )
            }
        }
}

private fun Double.number(): String = if (this % 1.0 == 0.0) toLong().toString() else toString()

private fun mealName(type: String) =
    when (type) {
        "BREAKFAST" -> "早餐"
        "LUNCH" -> "午餐"
        "DINNER" -> "晚餐"
        "SNACK" -> "加餐"
        else -> "饮食记录"
    }

private fun workoutName(type: String) =
    when (type) {
        "STRENGTH" -> "力量训练"
        "CARDIO" -> "有氧训练"
        "RUNNING" -> "跑步"
        "CYCLING" -> "骑行"
        "SWIMMING" -> "游泳"
        else -> "训练记录"
    }

private fun photoName(type: String) =
    when (type) {
        "FRONT" -> "正面照片"
        "SIDE" -> "侧面照片"
        "BACK" -> "背面照片"
        else -> "身材照片"
    }
