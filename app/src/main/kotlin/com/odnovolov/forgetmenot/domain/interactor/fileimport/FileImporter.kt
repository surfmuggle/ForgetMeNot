package com.odnovolov.forgetmenot.domain.interactor.fileimport

import com.odnovolov.forgetmenot.domain.architecturecomponents.CopyableList
import com.odnovolov.forgetmenot.domain.architecturecomponents.FlowMaker
import com.odnovolov.forgetmenot.domain.architecturecomponents.toCopyableList
import com.odnovolov.forgetmenot.domain.entity.*
import com.odnovolov.forgetmenot.domain.entity.NameCheckResult.Ok
import com.odnovolov.forgetmenot.domain.generateId
import com.odnovolov.forgetmenot.domain.interactor.deckeditor.checkDeckName
import com.odnovolov.forgetmenot.domain.interactor.fileimport.FileImporter.ImportResult.Failure
import com.odnovolov.forgetmenot.domain.interactor.fileimport.FileImporter.ImportResult.Success
import com.odnovolov.forgetmenot.domain.interactor.fileimport.Parser.CardMarkup
import com.odnovolov.forgetmenot.domain.removeFirst
import java.nio.charset.Charset

class FileImporter(
    val state: State,
    private val globalState: GlobalState,
) {
    class State(
        files: List<CardsFile>,
        currentPosition: Int = 0
    ) : FlowMaker<State>() {
        var files: List<CardsFile> by flowMaker(files)
        var currentPosition: Int by flowMaker(currentPosition)

        companion object {
            fun fromFiles(files: List<ImportedFile>): State {
                val cardsFile: List<CardsFile> =
                    files.map { (fileName: String, content: ByteArray) ->
                        val charset: Charset = Charset.defaultCharset()
                        val format = when (fileName.substringAfterLast('.', "")) {
                            "tsv" -> FileFormat.CSV_TDF
                            "csv" -> FileFormat.CSV_DEFAULT
                            else -> FileFormat.FMN_FORMAT
                        }
                        val text: String = content.toString(charset)
                            .normalizeForParser(format.parser)
                        val parseResult = format.parser.parse(text)
                        val errorRanges: List<IntRange> = parseResult.errorRanges
                        val cardPrototypes: List<CardPrototype> =
                            parseResult.cardMarkups.map { cardMarkup: CardMarkup ->
                                val question: String = cardMarkup.questionText
                                val answer: String = cardMarkup.answerText
                                CardPrototype(
                                    id = generateId(),
                                    question,
                                    answer,
                                    isSelected = true
                                )
                            }
                        val deckName = fileName.substringBeforeLast(".")
                        val deckWhereToAdd: AbstractDeck = NewDeck(deckName)
                        CardsFile(
                            id = generateId(),
                            sourceBytes = content,
                            charset,
                            text,
                            format,
                            errorRanges,
                            cardPrototypes,
                            deckWhereToAdd
                        )
                    }
                return State(cardsFile)
            }
        }
    }

    private val currentFile get() = with(state) { files[currentPosition] }

    fun setCurrentPosition(position: Int) {
        if (position !in 0..state.files.lastIndex || position == state.currentPosition) return
        state.currentPosition = position
    }

    fun skip() {
        with(state) {
            if (files.size <= 1) return
            val position = currentPosition
            if (currentPosition == files.lastIndex) {
                currentPosition--
            }
            files = files.toMutableList().apply {
                removeAt(position)
            }
        }
    }

    fun setDeckWhereToAdd(deckWhereToAdd: AbstractDeck) {
        currentFile.deckWhereToAdd = deckWhereToAdd
    }

    fun setCharset(newCharset: Charset) {
        with(currentFile) {
            if (charset == newCharset) return
            val text: String = sourceBytes.toString(newCharset).normalizeForParser(format.parser)
            updateText(text)
            charset = newCharset
        }
    }

    fun setFormat(format: FileFormat) {
        currentFile.format = format
        updateText(currentFile.text)
    }

    fun updateText(text: String): Parser.ParserResult {
        val parseResult: Parser.ParserResult = currentFile.format.parser.parse(text)
        currentFile.text = text
        currentFile.errorRanges = parseResult.errorRanges
        val oldCardPrototypes: MutableList<CardPrototype> =
            currentFile.cardPrototypes.toMutableList()
        currentFile.cardPrototypes = parseResult.cardMarkups.map { cardMarkup: CardMarkup ->
            val question: String = cardMarkup.questionText
            val answer: String = cardMarkup.answerText
            oldCardPrototypes.removeFirst { cardPrototype: CardPrototype ->
                cardPrototype.question == question && cardPrototype.answer == answer
            } ?: CardPrototype(
                id = generateId(),
                question,
                answer,
                isSelected = true
            )
        }
        return parseResult
    }

    fun invertSelection(cardPrototypeId: Long) {
        with(currentFile) {
            cardPrototypes = cardPrototypes.map { cardPrototype: CardPrototype ->
                if (cardPrototypeId == cardPrototype.id)
                    cardPrototype.copy(isSelected = !cardPrototype.isSelected) else
                    cardPrototype
            }
        }
    }

    fun selectAll() {
        with(currentFile) {
            cardPrototypes = cardPrototypes.map { cardPrototype: CardPrototype ->
                if (cardPrototype.isSelected)
                    cardPrototype else
                    cardPrototype.copy(isSelected = true)
            }
        }
    }

    fun import(): List<ImportResult> {
        return state.files.map { cardsFile: CardsFile ->
            val deckWhereToAdd = cardsFile.deckWhereToAdd
            if (deckWhereToAdd is NewDeck) {
                val nameCheckResult: NameCheckResult =
                    checkDeckName(deckWhereToAdd.deckName, globalState)
                if (nameCheckResult != Ok) {
                    return@map Failure
                }
            }
            val cardPrototypes: List<CardPrototype> = cardsFile.cardPrototypes
            if (cardPrototypes.isEmpty()) {
                return@map Failure
            }
            val newCards: CopyableList<Card> = cardPrototypes
                .filter { cardPrototype -> cardPrototype.isSelected }
                .map { cardPrototype -> cardPrototype.toCard() }
                .toCopyableList()
            when (deckWhereToAdd) {
                is NewDeck -> {
                    val deck = Deck(
                        id = generateId(),
                        name = deckWhereToAdd.deckName,
                        cards = newCards
                    )
                    globalState.decks = (globalState.decks + deck).toCopyableList()
                    return@map Success(deck)
                }
                is ExistingDeck -> {
                    deckWhereToAdd.deck.cards =
                        (deckWhereToAdd.deck.cards + newCards).toCopyableList()
                    return@map Success(deckWhereToAdd.deck)
                }
                else -> error(ERROR_MESSAGE_UNKNOWN_IMPLEMENTATION_OF_ABSTRACT_DECK)
            }
        }
    }

    sealed class ImportResult {
        class Success(val deck: Deck) : ImportResult()
        object Failure : ImportResult()
    }

    companion object {
        private val unwantedEOL by lazy { Regex("""\r\n?""") }

        fun String.normalizeForParser(parser: Parser): String {
            return when (parser) {
                is FmnFormatParser -> {
                    removePrefix("\uFEFF").replace(unwantedEOL, "\n")
                }
                else -> {
                    removePrefix("\uFEFF")
                }
            }
        }
    }
}