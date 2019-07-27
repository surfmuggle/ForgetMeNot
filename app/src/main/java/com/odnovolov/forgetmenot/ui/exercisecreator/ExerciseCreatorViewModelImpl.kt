package com.odnovolov.forgetmenot.ui.exercisecreator

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.odnovolov.forgetmenot.common.LiveEvent
import com.odnovolov.forgetmenot.entity.ExerciseCard
import com.odnovolov.forgetmenot.ui.exercisecreator.ExerciseCreatorViewModel.*
import com.odnovolov.forgetmenot.ui.exercisecreator.ExerciseCreatorViewModel.Action.NavigateToExercise
import com.odnovolov.forgetmenot.ui.exercisecreator.ExerciseCreatorViewModel.Event.DeckButtonClicked
import java.util.*

class ExerciseCreatorViewModelImpl(
    private val dao: ExerciseCreatorDao
) : ViewModel(), ExerciseCreatorViewModel {

    class Factory(val dao: ExerciseCreatorDao) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel?> create(modelClass: Class<T>): T {
            return ExerciseCreatorViewModelImpl(dao) as T
        }
    }

    private val isProcessing = MutableLiveData(false)

    override val state = State(
        isProcessing
    )

    private val actionSender = LiveEvent<Action>()
    override val action = actionSender

    override fun onEvent(event: Event) {
        when (event) {
            is DeckButtonClicked -> {
                if (isProcessing.value == true) {
                    return
                }
                isProcessing.value = true
                try {
                    val deck = dao.getDeck(event.deckId)
                    val exerciseCards: List<ExerciseCard> = deck.cards
                        .filter { card -> !card.isLearned }
                        .map { card -> ExerciseCard(card = card) }
                        .sortedBy { exerciseCard -> exerciseCard.card.lap }
                    if (exerciseCards.isNotEmpty()) {
                        dao.deleteAllExerciseCards()
                        dao.insertExerciseCards(exerciseCards)
                        dao.updateLastOpenedAt(Calendar.getInstance(), deck.id)
                        actionSender.send(NavigateToExercise)
                    }
                } finally {
                    isProcessing.value = false
                }
            }
        }
    }

}