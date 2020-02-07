package com.odnovolov.forgetmenot.persistence.globalstate.writingchanges

import com.odnovolov.forgetmenot.common.database.database
import com.odnovolov.forgetmenot.domain.architecturecomponents.PropertyChangeRegistry
import com.odnovolov.forgetmenot.domain.architecturecomponents.PropertyChangeRegistry.Change.PropertyValueChange
import com.odnovolov.forgetmenot.domain.entity.*
import com.odnovolov.forgetmenot.persistence.globalstate.writingchanges.IntervalSchemePropertyChangeHandler.insertIntervals
import com.odnovolov.forgetmenot.persistence.toIntervalSchemeDb
import com.odnovolov.forgetmenot.persistence.toPronunciationDb

object ExercisePreferencePropertyChangeHandler {
    private val queries = database.exercisePreferenceQueries

    fun handle(change: PropertyChangeRegistry.Change) {
        if (change !is PropertyValueChange) return
        val exercisePreferenceId = change.propertyOwnerId
        if (!queries.exists(exercisePreferenceId).executeAsOne()) return
        when (change.property) {
            ExercisePreference::name -> {
                val name = change.newValue as String
                queries.updateName(name, exercisePreferenceId)
            }
            ExercisePreference::randomOrder -> {
                val randomOrder = change.newValue as Boolean
                queries.updateRandomOrder(randomOrder, exercisePreferenceId)
            }
            ExercisePreference::testMethod -> {
                val testMethod = change.newValue as TestMethod
                queries.updateTestMethod(testMethod, exercisePreferenceId)
            }
            ExercisePreference::intervalScheme -> {
                val linkedIntervalScheme = change.newValue as IntervalScheme?
                insertIntervalSchemeIfNotExists(linkedIntervalScheme)
                queries.updateIntervalSchemeId(linkedIntervalScheme?.id, exercisePreferenceId)

                val unlinkedIntervalScheme = change.oldValue as IntervalScheme?
                if (unlinkedIntervalScheme != null) {
                    // todo: check whether it must be deleted
                }
            }
            ExercisePreference::pronunciation -> {
                val linkedPronunciation = change.newValue as Pronunciation
                insertPronunciationIfNotExists(linkedPronunciation)
                queries.updatePronunciationId(linkedPronunciation.id, exercisePreferenceId)

                // todo: check whether oldValue should be deleted
            }
            ExercisePreference::isQuestionDisplayed -> {
                val isQuestionDisplayed = change.newValue as Boolean
                queries.updateIsQuestionDisplayed(isQuestionDisplayed, exercisePreferenceId)
            }
            ExercisePreference::cardReverse -> {
                val cardReverse = change.newValue as CardReverse
                queries.updateCardReverse(cardReverse, exercisePreferenceId)
            }
        }
    }

    fun insertIntervalSchemeIfNotExists(intervalScheme: IntervalScheme?) {
        if (intervalScheme != null) {
            val exists = intervalScheme.id == IntervalScheme.Default.id
                    || database.intervalSchemeQueries.exists(intervalScheme.id).executeAsOne()
            if (!exists) {
                val intervalSchemeDb = intervalScheme.toIntervalSchemeDb()
                database.intervalSchemeQueries.insert(intervalSchemeDb)
                insertIntervals(intervalScheme.intervals, intervalScheme.id)
            }
        }
    }

    fun insertPronunciationIfNotExists(pronunciation: Pronunciation) {
        val exists = pronunciation.id == Pronunciation.Default.id
                || database.pronunciationQueries.exists(pronunciation.id).executeAsOne()
        if (!exists) {
            val pronunciationDb = pronunciation.toPronunciationDb()
            database.pronunciationQueries.insert(pronunciationDb)
        }
    }
}