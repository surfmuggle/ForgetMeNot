package com.odnovolov.forgetmenot.presentation.screen.deckchooser

import com.odnovolov.forgetmenot.domain.entity.Deck
import com.odnovolov.forgetmenot.domain.entity.ExistingDeck
import com.odnovolov.forgetmenot.domain.entity.GlobalState
import com.odnovolov.forgetmenot.domain.entity.NewDeck
import com.odnovolov.forgetmenot.presentation.common.LongTermStateSaver
import com.odnovolov.forgetmenot.presentation.common.Navigator
import com.odnovolov.forgetmenot.presentation.common.ShortTermStateProvider
import com.odnovolov.forgetmenot.presentation.common.base.BaseController
import com.odnovolov.forgetmenot.presentation.screen.cardseditor.CardsEditorDiScope
import com.odnovolov.forgetmenot.presentation.screen.cardseditor.CardsEditorEvent.DeckToCopyCardToIsSelected
import com.odnovolov.forgetmenot.presentation.screen.cardseditor.CardsEditorEvent.DeckToMoveCardToIsSelected
import com.odnovolov.forgetmenot.presentation.screen.deckchooser.DeckChooserEvent.*
import com.odnovolov.forgetmenot.presentation.screen.deckchooser.DeckChooserScreenState.Purpose.*
import com.odnovolov.forgetmenot.presentation.screen.deckeditor.DeckEditorDiScope
import com.odnovolov.forgetmenot.presentation.screen.deckeditor.DeckEditorEvent.DeckToCopyCardsToIsSelected
import com.odnovolov.forgetmenot.presentation.screen.deckeditor.DeckEditorEvent.DeckToMoveCardsToIsSelected
import com.odnovolov.forgetmenot.presentation.screen.fileimport.FileImportDiScope
import com.odnovolov.forgetmenot.presentation.screen.fileimport.cardsfile.CardsFileEvent.TargetDeckIsSelected
import com.odnovolov.forgetmenot.presentation.screen.home.DeckReviewPreference
import com.odnovolov.forgetmenot.presentation.screen.home.DeckSorting.Direction.Asc
import com.odnovolov.forgetmenot.presentation.screen.home.DeckSorting.Direction.Desc
import com.odnovolov.forgetmenot.presentation.screen.home.HomeDiScope
import com.odnovolov.forgetmenot.presentation.screen.home.HomeEvent.DeckToMergeIntoIsSelected
import com.odnovolov.forgetmenot.presentation.screen.renamedeck.RenameDeckDiScope
import com.odnovolov.forgetmenot.presentation.screen.renamedeck.RenameDeckDialogPurpose.ToCreateNewForDeckChooser
import com.odnovolov.forgetmenot.presentation.screen.renamedeck.RenameDeckDialogState

class DeckChooserController(
    private val deckReviewPreference: DeckReviewPreference,
    private val screenState: DeckChooserScreenState,
    private val globalState: GlobalState,
    private val navigator: Navigator,
    private val longTermStateSaver: LongTermStateSaver,
    private val screenStateProvider: ShortTermStateProvider<DeckChooserScreenState>
) : BaseController<DeckChooserEvent, Nothing>() {
    override fun handle(event: DeckChooserEvent) {
        when (event) {
            CancelButtonClicked -> {
                navigator.navigateUp()
            }

            is SearchTextChanged -> {
                screenState.searchText = event.searchText
            }

            SortingDirectionButtonClicked -> {
                with(deckReviewPreference) {
                    val newDirection = if (deckSorting.direction == Asc) Desc else Asc
                    deckSorting = deckSorting.copy(direction = newDirection)
                }
            }

            is SortByButtonClicked -> {
                with(deckReviewPreference) {
                    deckSorting = if (event.criterion == deckSorting.criterion) {
                        val newDirection = if (deckSorting.direction == Asc) Desc else Asc
                        deckSorting.copy(direction = newDirection)
                    } else {
                        deckSorting.copy(criterion = event.criterion)
                    }
                }
            }

            is DeckButtonClicked -> {
                val deck: Deck = globalState.decks.first { it.id == event.deckId }
                when (screenState.purpose) {
                    ToImportCards -> {
                        FileImportDiScope.getOrRecreate().cardsFileController
                            .dispatch(TargetDeckIsSelected(deck))
                    }
                    ToMergeInto -> {
                        val abstractDeck = ExistingDeck(deck)
                        HomeDiScope.getOrRecreate().controller
                            .dispatch(DeckToMergeIntoIsSelected(abstractDeck))
                    }
                    ToMoveCard -> {
                        val abstractDeck = ExistingDeck(deck)
                        CardsEditorDiScope.getOrRecreate().controller
                            .dispatch(DeckToMoveCardToIsSelected(abstractDeck))
                    }
                    ToCopyCard -> {
                        val abstractDeck = ExistingDeck(deck)
                        CardsEditorDiScope.getOrRecreate().controller
                            .dispatch(DeckToCopyCardToIsSelected(abstractDeck))
                    }
                    ToMoveCardsInDeckEditor -> {
                        val abstractDeck = ExistingDeck(deck)
                        DeckEditorDiScope.getOrRecreate().controller
                            .dispatch(DeckToMoveCardsToIsSelected(abstractDeck))
                    }
                    ToCopyCardsInDeckEditor -> {
                        val abstractDeck = ExistingDeck(deck)
                        DeckEditorDiScope.getOrRecreate().controller
                            .dispatch(DeckToCopyCardsToIsSelected(abstractDeck))
                    }
                }
                navigator.navigateUp()
            }

            AddDeckButtonClicked -> {
                navigator.showRenameDeckDialogFromDeckChooser {
                    val dialogState = RenameDeckDialogState(purpose = ToCreateNewForDeckChooser)
                    RenameDeckDiScope.create(dialogState)
                }
            }

            is SubmittedNewDeckName -> {
                when (screenState.purpose) {
                    ToImportCards -> {}
                    ToMergeInto -> {
                        val abstractDeck = NewDeck(event.deckName)
                        HomeDiScope.getOrRecreate().controller
                            .dispatch(DeckToMergeIntoIsSelected(abstractDeck))
                    }
                    ToMoveCard -> {
                        val abstractDeck = NewDeck(event.deckName)
                        CardsEditorDiScope.getOrRecreate().controller
                            .dispatch(DeckToMoveCardToIsSelected(abstractDeck))
                    }
                    ToCopyCard -> {
                        val abstractDeck = NewDeck(event.deckName)
                        CardsEditorDiScope.getOrRecreate().controller
                            .dispatch(DeckToCopyCardToIsSelected(abstractDeck))
                    }
                    ToMoveCardsInDeckEditor -> {
                        val abstractDeck = NewDeck(event.deckName)
                        DeckEditorDiScope.getOrRecreate().controller
                            .dispatch(DeckToMoveCardsToIsSelected(abstractDeck))
                    }
                    ToCopyCardsInDeckEditor -> {
                        val abstractDeck = NewDeck(event.deckName)
                        DeckEditorDiScope.getOrRecreate().controller
                            .dispatch(DeckToCopyCardsToIsSelected(abstractDeck))
                    }
                }
                navigator.navigateUp()
            }
        }
    }

    override fun saveState() {
        longTermStateSaver.saveStateByRegistry()
        screenStateProvider.save(screenState)
    }
}