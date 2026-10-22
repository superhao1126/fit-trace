package com.fittrace.feature.main

import com.fittrace.core.domain.RecordsRepository
import com.fittrace.core.model.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal data class MealToolsState(
    val recent: List<MealEntry> = emptyList(),
    val favorites: List<MealFavorite> = emptyList(),
    val loading: Boolean = false,
    val busy: Boolean = false,
    val recentError: String? = null,
    val favoritesError: String? = null,
    val error: String? = null,
    val notice: String? = null,
    val recognition: FoodRecognition? = null,
)

internal class MealToolsController(
    private val repository: RecordsRepository,
    private val scope: CoroutineScope,
) {
    private val mutable = MutableStateFlow(MealToolsState())
    val state = mutable.asStateFlow()

    fun load() {
        if (mutable.value.loading) return
        mutable.value =
            mutable.value.copy(loading = true, recentError = null, favoritesError = null)
        scope.launch {
            val recent = repository.meals(1)
            val favorites = repository.mealFavorites()
            mutable.value =
                mutable.value.copy(
                    loading = false,
                    recent = (recent as? AppResult.Success)?.data?.entries ?: mutable.value.recent,
                    favorites = (favorites as? AppResult.Success)?.data ?: mutable.value.favorites,
                    recentError = (recent as? AppResult.Failure)?.error?.recordMessage(),
                    favoritesError = (favorites as? AppResult.Failure)?.error?.recordMessage(),
                )
        }
    }

    fun error(message: String) {
        mutable.value = mutable.value.copy(error = message, notice = null)
    }

    fun recognize(text: String, imageUrl: String? = null) {
        if (mutable.value.busy) return
        if (imageUrl == null && text.trim().length !in 1..500) {
            error("请填写 1–500 字的餐食描述")
            return
        }
        if (imageUrl != null && text.length > 200) {
            error("照片补充说明不能超过 200 字")
            return
        }
        mutable.value =
            mutable.value.copy(busy = true, recognition = null, error = null, notice = null)
        scope.launch {
            when (
                val result =
                    if (imageUrl == null) repository.recognizeMealText(text.trim())
                    else repository.recognizeMealPhoto(imageUrl, text.trim().ifBlank { null })
            ) {
                is AppResult.Success ->
                    mutable.value =
                        mutable.value.copy(
                            busy = false,
                            recognition = result.data,
                            error = if (result.data.foods.isEmpty()) "没有识别到食物，请补充描述或手动添加" else null,
                        )
                is AppResult.Failure ->
                    mutable.value =
                        mutable.value.copy(busy = false, error = result.error.recordMessage())
            }
        }
    }

    fun suggestion(index: Int, food: FoodEntry?) {
        val result = mutable.value.recognition ?: return
        val foods = result.foods.mapIndexedNotNull { i, old -> if (i == index) food else old }
        mutable.value = mutable.value.copy(recognition = result.copy(foods = foods))
    }

    fun clearRecognition() {
        mutable.value = mutable.value.copy(recognition = null, error = null)
    }

    fun favorite(name: String, foods: List<FoodEntry>) {
        if (mutable.value.busy || mutable.value.loading) return
        if (name.trim().length !in 1..60 || foods.size !in 1..50) {
            error("填写 1–60 字的常用餐名称，并添加 1–50 项食物")
            return
        }
        mutable.value = mutable.value.copy(busy = true, error = null, notice = null)
        scope.launch {
            when (val result = repository.saveMealFavorite(name.trim(), foods)) {
                is AppResult.Success ->
                    mutable.value =
                        mutable.value.copy(
                            busy = false,
                            favorites = listOf(result.data) + mutable.value.favorites,
                            notice = "已保存为常用餐",
                        )
                is AppResult.Failure ->
                    mutable.value =
                        mutable.value.copy(busy = false, error = result.error.recordMessage())
            }
        }
    }

    fun removeFavorite(id: String) {
        if (mutable.value.busy || mutable.value.loading) return
        mutable.value = mutable.value.copy(busy = true, error = null)
        scope.launch {
            when (val result = repository.removeMealFavorite(id)) {
                is AppResult.Success ->
                    mutable.value =
                        mutable.value.copy(
                            busy = false,
                            favorites = mutable.value.favorites.filter { it.id != id },
                        )
                is AppResult.Failure ->
                    mutable.value =
                        mutable.value.copy(busy = false, error = result.error.recordMessage())
            }
        }
    }
}
