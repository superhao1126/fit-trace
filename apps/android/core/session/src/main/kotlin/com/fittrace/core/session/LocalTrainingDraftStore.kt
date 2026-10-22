package com.fittrace.core.session

import android.content.Context
import com.fittrace.core.domain.TrainingDraft
import com.fittrace.core.domain.TrainingDraftStore
import com.fittrace.core.model.TrainingExercise
import com.fittrace.core.model.TrainingSet
import java.security.MessageDigest
import org.json.JSONArray
import org.json.JSONObject

class LocalTrainingDraftStore(context: Context, accountId: String) : TrainingDraftStore {
    private val account =
        MessageDigest.getInstance("SHA-256").digest(accountId.toByteArray()).joinToString("") {
            "%02x".format(it)
        }
    private val preferences =
        context.getSharedPreferences("training-drafts-$account", Context.MODE_PRIVATE)

    override fun read(date: String): TrainingDraft? =
        runCatching {
                val root = JSONObject(preferences.getString(date, null) ?: return null)
                val items = root.getJSONArray("exercises")
                TrainingDraft(
                    root.getString("name"),
                    root.getString("minutes"),
                    (0 until items.length()).map { i ->
                        val e = items.getJSONObject(i)
                        val sets = e.getJSONArray("sets")
                        TrainingExercise(
                            e.getString("id"),
                            e.getString("name"),
                            e.getString("unit"),
                            e.getInt("rest"),
                            (0 until sets.length()).map { j ->
                                val s = sets.getJSONObject(j)
                                TrainingSet(
                                    s.getInt("reps"),
                                    s.getDouble("weight"),
                                    s.getBoolean("completed"),
                                )
                            },
                        )
                    },
                    if (root.has("restUntil")) root.getLong("restUntil") else null,
                )
            }
            .getOrNull()

    override fun write(date: String, draft: TrainingDraft) {
        val items = JSONArray()
        draft.exercises.forEach { exercise ->
            val sets = JSONArray()
            exercise.sets.forEach { set ->
                sets.put(
                    JSONObject()
                        .put("reps", set.reps)
                        .put("weight", set.weightKg)
                        .put("completed", set.completed)
                )
            }
            items.put(
                JSONObject()
                    .put("id", exercise.exerciseId)
                    .put("name", exercise.name)
                    .put("unit", exercise.unit)
                    .put("rest", exercise.restSeconds)
                    .put("sets", sets)
            )
        }
        val root =
            JSONObject()
                .put("name", draft.name)
                .put("minutes", draft.minutes)
                .put("exercises", items)
        draft.restUntil?.let { root.put("restUntil", it) }
        check(preferences.edit().putString(date, root.toString()).commit()) {
            "Unable to persist training draft"
        }
    }

    override fun favoriteExercises(): Set<String> =
        preferences.getStringSet("favorite-exercises", emptySet()).orEmpty().toSet()

    override fun setFavoriteExercises(ids: Set<String>) {
        check(preferences.edit().putStringSet("favorite-exercises", ids).commit()) {
            "Unable to persist favorites"
        }
    }

    override fun clear(date: String) {
        check(preferences.edit().remove(date).commit()) { "Unable to clear training draft" }
    }
}
