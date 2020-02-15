package com.odnovolov.forgetmenot.domain.interactor.exercise

import com.odnovolov.forgetmenot.domain.architecturecomponents.FlowableState
import com.odnovolov.forgetmenot.domain.architecturecomponents.SUID
import com.odnovolov.forgetmenot.domain.entity.Card
import com.odnovolov.forgetmenot.domain.entity.Pronunciation
import com.odnovolov.forgetmenot.domain.entity.Speaker
import com.odnovolov.forgetmenot.domain.interactor.exercise.Exercise.Answer.*
import com.soywiz.klock.DateTime

class Exercise(
    val state: State,
    private val speaker: Speaker
) {
    class State(
        exerciseCards: List<ExerciseCard>,
        currentPosition: Int = 0,
        questionSelection: String = "",
        answerSelection: String = ""
    ) : FlowableState<State>() {
        var exerciseCards: List<ExerciseCard> by me(exerciseCards)
        var currentPosition: Int by me(currentPosition)
        var questionSelection: String by me(questionSelection)
        var answerSelection: String by me(answerSelection)
    }

    lateinit var currentExerciseCard: ExerciseCard
    private lateinit var currentPronunciation: Pronunciation

    init {
        setCurrentPosition(state.currentPosition)
    }

    fun setCurrentPosition(position: Int) {
        if (position < state.exerciseCards.size) {
            state.currentPosition = position
            currentExerciseCard = state.exerciseCards[state.currentPosition]
            setCurrentPronunciation()
            speakQuestionIfNeed()
        }
    }

    private fun speakQuestionIfNeed() {
        if (currentExerciseCard.base.deck.exercisePreference.pronunciation.questionAutoSpeak) {
            speakQuestion()
        }
    }

    private fun setCurrentPronunciation() {
        val associatedPronunciation = currentExerciseCard.base.deck.exercisePreference.pronunciation
        currentPronunciation = if (currentExerciseCard.base.isReverse) {
            with(associatedPronunciation) {
                Pronunciation(
                    id = -1,
                    questionLanguage = answerLanguage,
                    questionAutoSpeak = answerAutoSpeak,
                    answerLanguage = questionLanguage,
                    answerAutoSpeak = questionAutoSpeak,
                    doNotSpeakTextInBrackets = doNotSpeakTextInBrackets
                )
            }
        } else {
            associatedPronunciation
        }
    }

    fun showQuestion() {
        currentExerciseCard.base.isQuestionDisplayed = true
    }

    fun setQuestionSelection(selection: String) {
        state.questionSelection = selection
        state.answerSelection = ""
    }

    fun setAnswerSelection(selection: String) {
        state.answerSelection = selection
        state.questionSelection = ""
    }

    fun setIsCardLearned(isLearned: Boolean) {
        currentExerciseCard.base.card.isLearned = isLearned
    }

    fun speak() {
        when {
            hasQuestionSelection() -> speakQuestionSelection()
            hasAnswerSelection() -> speakAnswerSelection()
            isAnswered() -> speakAnswer()
            else -> speakQuestion()
        }
    }

    private fun hasAnswerSelection(): Boolean = state.answerSelection.isNotEmpty()
    private fun hasQuestionSelection(): Boolean = state.questionSelection.isNotEmpty()
    private fun isAnswered(): Boolean = currentExerciseCard.base.isAnswerCorrect != null

    private fun speakQuestionSelection() {
        speaker.speak(
            state.questionSelection,
            currentPronunciation.questionLanguage
        )
    }

    private fun speakAnswerSelection() {
        speaker.speak(
            state.answerSelection,
            currentPronunciation.answerLanguage
        )
    }

    private fun speakQuestion() {
        with(currentExerciseCard.base) {
            val question = if (isReverse) card.answer else card.question
            speaker.speak(question, currentPronunciation.questionLanguage)
        }
    }

    private fun speakAnswer() {
        with(currentExerciseCard.base) {
            val answer = if (isReverse) card.question else card.answer
            speaker.speak(answer, currentPronunciation.answerLanguage)
        }
    }

    fun setLevelOfKnowledge(levelOfKnowledge: Int) {
        currentExerciseCard.base.card.levelOfKnowledge = levelOfKnowledge
        currentExerciseCard.base.isLevelOfKnowledgeEditedManually = true
    }

    fun answer(answer: Answer) {
        if (!isAnswerRelevant(answer)) return
        when (answer) {
            Show -> setAnswerCorrect()
            Remember -> setAnswerCorrect()
            NotRemember -> setAnswerWrong()
            is Variant -> checkVariant(answer.variantIndex)
            is Entry -> checkEntry(answer.userAnswer)
        }
    }

    private fun isAnswerRelevant(answer: Answer): Boolean {
        return when (answer) {
            Show -> currentExerciseCard is OffTestExerciseCard
            Remember -> currentExerciseCard is ManualTestExerciseCard
            NotRemember -> currentExerciseCard is ManualTestExerciseCard
            is Variant -> currentExerciseCard is QuizTestExerciseCard
            is Entry -> currentExerciseCard is EntryTestExerciseCard
        }
    }

    private fun checkVariant(variantIndex: Int) {
        val quizExerciseCard = currentExerciseCard as QuizTestExerciseCard
        if (quizExerciseCard.selectedVariantIndex != null
            || variantIndex >= quizExerciseCard.variants.size
        ) return
        quizExerciseCard.selectedVariantIndex = variantIndex
        val selectedCardId = quizExerciseCard.variants[variantIndex]!!.id
        val isVariantCorrect = selectedCardId == quizExerciseCard.base.card.id
        if (isVariantCorrect) setAnswerCorrect()
        else setAnswerWrong()
    }

    private fun checkEntry(userAnswer: String?) {
        val entryExerciseCard = currentExerciseCard as EntryTestExerciseCard
        entryExerciseCard.userAnswer = userAnswer
        val correctAnswer = with(entryExerciseCard.base) {
            if (isReverse) card.question else card.answer
        }
        val isUserAnswerCorrect = userAnswer?.trim() == correctAnswer.trim()
        if (isUserAnswerCorrect) setAnswerCorrect()
        else setAnswerWrong()
    }

    private fun setAnswerCorrect() {
        if (currentExerciseCard.base.isAnswerCorrect == true) return
        speakAnswerIfNeed()
        incrementLapIfCardIsAnsweredForTheFirstTime()
        currentExerciseCard.base.isAnswerCorrect = true
        showQuestion()
        updateLevelOfKnowledge()
        deleteAllRetestedCards()
        updateLastAnsweredAt()
    }

    private fun setAnswerWrong() {
        if (currentExerciseCard.base.isAnswerCorrect == false) return
        speakAnswerIfNeed()
        incrementLapIfCardIsAnsweredForTheFirstTime()
        currentExerciseCard.base.isAnswerCorrect = false
        showQuestion()
        updateLevelOfKnowledge()
        addExerciseCardToRetestIfNeed()
        updateLastAnsweredAt()
    }

    private fun speakAnswerIfNeed() {
        with(currentExerciseCard.base) {
            if (currentPronunciation.answerAutoSpeak && isAnswerCorrect == null) {
                val answer: String = if (isReverse) card.question else card.answer
                speaker.speak(answer, currentPronunciation.answerLanguage)
            }
        }
    }

    private fun incrementLapIfCardIsAnsweredForTheFirstTime() {
        val isAnsweredForTheFirstTime = state.exerciseCards
            .any { it.base.card.id == currentExerciseCard.base.card.id && it.base.isAnswerCorrect != null }
            .not()
        if (isAnsweredForTheFirstTime) {
            currentExerciseCard.base.card.lap++
        }
    }

    private fun updateLevelOfKnowledge() {
        if (currentExerciseCard.base.isLevelOfKnowledgeEditedManually) return
        var numberOfCorrect = 0
        var numberOfWrong = 0
        state.exerciseCards.forEach {
            if (it.base.card.id == currentExerciseCard.base.card.id) {
                when (it.base.isAnswerCorrect) {
                    true -> numberOfCorrect++
                    false -> numberOfWrong++
                }
            }
        }
        if (numberOfWrong > 0) {
            currentExerciseCard.base.card.levelOfKnowledge =
                maxOf(0, currentExerciseCard.base.initialLevelOfKnowledge - numberOfWrong)
        } else if (numberOfCorrect > 0) {
            currentExerciseCard.base.card.levelOfKnowledge =
                currentExerciseCard.base.initialLevelOfKnowledge + 1
        }
    }

    private fun deleteAllRetestedCards() {
        if (hasExerciseCardToRetest()) {
            state.exerciseCards = state.exerciseCards
                .filterIndexed { index, exerciseCard ->
                    exerciseCard.base.card.id != currentExerciseCard.base.card.id
                            || exerciseCard.base.card.id == currentExerciseCard.base.card.id
                            && index <= state.currentPosition
                }
        }
    }

    private fun addExerciseCardToRetestIfNeed() {
        if (hasExerciseCardToRetest()) return
        val baseExerciseCard = with(currentExerciseCard.base) {
            ExerciseCard.Base(
                id = SUID.id(),
                card = card,
                deck = deck,
                isReverse = isReverse,
                isQuestionDisplayed = deck.exercisePreference.isQuestionDisplayed,
                initialLevelOfKnowledge = initialLevelOfKnowledge,
                isLevelOfKnowledgeEditedManually = isLevelOfKnowledgeEditedManually
            )
        }
        val retestedExerciseCard: ExerciseCard = when (currentExerciseCard) {
            is OffTestExerciseCard -> OffTestExerciseCard(baseExerciseCard)
            is ManualTestExerciseCard -> ManualTestExerciseCard(baseExerciseCard)
            is QuizTestExerciseCard -> {
                val variants: List<Card?> = with(baseExerciseCard) {
                    QuizComposer.compose(card, deck, isReverse)
                }
                QuizTestExerciseCard(baseExerciseCard, variants)
            }
            is EntryTestExerciseCard -> EntryTestExerciseCard(baseExerciseCard)
            else -> throw AssertionError()
        }
        state.exerciseCards += retestedExerciseCard
    }

    private fun hasExerciseCardToRetest(): Boolean {
        return state.exerciseCards
            .drop(state.currentPosition + 1)
            .any { it.base.card.id == currentExerciseCard.base.card.id }
    }

    private fun updateLastAnsweredAt() {
        currentExerciseCard.base.card.lastAnsweredAt = DateTime.now()
    }

    sealed class Answer {
        object Show : Answer()
        object Remember : Answer()
        object NotRemember : Answer()
        class Variant(val variantIndex: Int) : Answer()
        class Entry(val userAnswer: String?) : Answer()
    }
}