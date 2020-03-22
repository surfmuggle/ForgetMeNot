package com.odnovolov.forgetmenot.domain.interactor.exercise

import com.odnovolov.forgetmenot.domain.entity.Card
import com.odnovolov.forgetmenot.domain.entity.CardReverse
import com.odnovolov.forgetmenot.domain.entity.Deck
import com.odnovolov.forgetmenot.domain.entity.GlobalState
import com.odnovolov.forgetmenot.domain.entity.TestMethod.*
import com.odnovolov.forgetmenot.domain.flattenWithShallowShuffling
import com.odnovolov.forgetmenot.domain.generateId
import com.odnovolov.forgetmenot.domain.isCardAvailableForExercise

class ExerciseStateCreator(
    private val globalState: GlobalState
) {
    fun create(deckIds: List<Long>, isWalkingMode: Boolean): Exercise.State {
        val exerciseCards: List<ExerciseCard> = globalState.decks
            .filter { deck -> deck.id in deckIds }
            .map { deck ->
                val isRandom = deck.exercisePreference.randomOrder
                deck.cards
                    .filter { card ->
                        isCardAvailableForExercise(card, deck.exercisePreference.intervalScheme)
                    }
                    .let { cards: List<Card> -> if (isRandom) cards.shuffled() else cards }
                    .sortedBy { card: Card -> card.lap }
                    .map { card -> cardToExerciseCard(card, deck, isWalkingMode) }
            }
            .flattenWithShallowShuffling()
        if (exerciseCards.isEmpty()) throw NoCardIsReadyForExercise
        return Exercise.State(exerciseCards, isWalkingMode = isWalkingMode)
    }

    private fun cardToExerciseCard(
        card: Card,
        deck: Deck,
        isWalkingMode: Boolean
    ): ExerciseCard {
        val isReverse = when (deck.exercisePreference.cardReverse) {
            CardReverse.Off -> false
            CardReverse.On -> true
            CardReverse.EveryOtherLap -> (card.lap % 2) == 1
        }
        val baseExerciseCard = ExerciseCard.Base(
            id = generateId(),
            card = card,
            deck = deck,
            isReverse = isReverse,
            isQuestionDisplayed = deck.exercisePreference.isQuestionDisplayed,
            initialLevelOfKnowledge = card.levelOfKnowledge
        )
        return when (deck.exercisePreference.testMethod) {
            Off -> OffTestExerciseCard(baseExerciseCard)
            Manual -> ManualTestExerciseCard(baseExerciseCard)
            Quiz -> {
                if (isWalkingMode) {
                    ManualTestExerciseCard(baseExerciseCard)
                } else {
                    val variants: List<Card?> = QuizComposer.compose(card, deck, isReverse)
                    QuizTestExerciseCard(baseExerciseCard, variants)
                }
            }
            Entry -> {
                if (isWalkingMode) {
                    ManualTestExerciseCard(baseExerciseCard)
                } else {
                    EntryTestExerciseCard(baseExerciseCard)
                }
            }
        }
    }

    object NoCardIsReadyForExercise : Exception("No card is ready for exercise")
}