package com.fittrace.feature.main

import android.content.Context
import org.json.JSONArray

data class CatalogFood(
    val name: String,
    val category: String,
    val image: String,
    val kcal: Double,
    val aliases: String,
)

data class CatalogExercise(
    val id: String,
    val name: String,
    val bodyPart: String,
    val equipment: String,
    val unit: String,
    val steps: List<String>,
    val tips: String,
    val images: List<String> = emptyList(),
    val mediaSourceUrl: String = "",
    val sourceUrl: String = "",
)

internal fun catalogFoods(context: Context): List<CatalogFood> {
    val array =
        JSONArray(context.assets.open("catalog/foods.json").bufferedReader().use { it.readText() })
    return (0 until array.length()).map { index ->
        array.getJSONObject(index).let {
            CatalogFood(
                it.getString("name"),
                it.getString("category"),
                "file:///android_asset/catalog/" + it.getString("imageUrl").substringAfterLast('/'),
                it.getDouble("kcalPer100g"),
                it.getJSONArray("aliases").toString(),
            )
        }
    }
}

internal fun catalogExercises(context: Context): List<CatalogExercise> {
    val mediaArray =
        JSONArray(
            context.assets.open("catalog/exercise-media.json").bufferedReader().use {
                it.readText()
            }
        )
    val media =
        (0 until mediaArray.length()).associate { index ->
            val entry = mediaArray.getJSONObject(index)
            entry.getString("id") to entry
        }
    val array =
        JSONArray(
            context.assets.open("catalog/exercises.json").bufferedReader().use { it.readText() }
        )
    return (0 until array.length()).map { index ->
        array.getJSONObject(index).let { obj ->
            val steps = obj.getJSONArray("steps")
            CatalogExercise(
                obj.getString("id"),
                obj.getString("name"),
                obj.getString("bodyPart"),
                obj.getString("equipment"),
                obj.getString("unit"),
                (0 until steps.length()).map(steps::getString),
                obj.getString("tips"),
                media[obj.getString("id")]
                    ?.getJSONArray("images")
                    ?.let { images ->
                        (0 until images.length()).map {
                            "file:///android_asset/catalog/" + images.getString(it)
                        }
                    }
                    .orEmpty(),
                media[obj.getString("id")]?.getString("sourceUrl").orEmpty(),
                obj.optString("sourceUrl"),
            )
        }
    }
}
