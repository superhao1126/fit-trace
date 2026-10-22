package com.fittrace.core.domain

import com.fittrace.core.model.TrainingExercise

data class TrainingDraft(
    val name: String,
    val minutes: String,
    val exercises: List<TrainingExercise>,
    val restUntil: Long? = null,
)

/** Caller supplies an account-scoped store; drafts are separated by recording date. */
interface TrainingDraftStore {
    fun read(date: String): TrainingDraft?

    fun write(date: String, draft: TrainingDraft)

    fun favoriteExercises(): Set<String>

    fun setFavoriteExercises(ids: Set<String>)

    fun clear(date: String)
}
