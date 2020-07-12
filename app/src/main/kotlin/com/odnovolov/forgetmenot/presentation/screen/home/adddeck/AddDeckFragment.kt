package com.odnovolov.forgetmenot.presentation.screen.home.adddeck

import android.app.Activity
import android.app.Dialog
import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import com.odnovolov.forgetmenot.R
import com.odnovolov.forgetmenot.domain.entity.NameCheckResult
import com.odnovolov.forgetmenot.domain.entity.NameCheckResult.*
import com.odnovolov.forgetmenot.presentation.common.base.BaseFragment
import com.odnovolov.forgetmenot.presentation.common.needToCloseDiScope
import com.odnovolov.forgetmenot.presentation.common.observeText
import com.odnovolov.forgetmenot.presentation.common.showSoftInput
import com.odnovolov.forgetmenot.presentation.common.showToast
import com.odnovolov.forgetmenot.presentation.screen.home.adddeck.AddDeckController.Command.SetDialogText
import com.odnovolov.forgetmenot.presentation.screen.home.adddeck.AddDeckController.Command.ShowErrorMessage
import com.odnovolov.forgetmenot.presentation.screen.home.adddeck.AddDeckEvent.*
import kotlinx.android.synthetic.main.dialog_deck_name_input.view.*
import kotlinx.android.synthetic.main.fragment_adddeck.*
import kotlinx.android.synthetic.main.popup_add_deck.view.*
import kotlinx.coroutines.launch

class AddDeckFragment : BaseFragment() {
    init {
        AddDeckDiScope.reopenIfClosed()
    }

    private var controller: AddDeckController? = null
    private lateinit var deckNameInputDialog: AlertDialog
    private lateinit var deckNameDialogEditText: EditText
    private var pendingEvent: ContentReceived? = null
    private val addDeckDialog: Dialog by lazy(::createAddDeckDialog)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        setHasOptionsMenu(true)
        createDeckNameInputDialog()
        return inflater.inflate(R.layout.fragment_adddeck, container, false)
    }

    private fun createDeckNameInputDialog() {
        val contentView = View.inflate(context, R.layout.dialog_deck_name_input, null)
        deckNameDialogEditText = contentView.deckNameEditText
        deckNameDialogEditText.observeText { dialogText: String ->
            controller?.dispatch(DialogTextChanged(dialogText))
        }
        deckNameInputDialog = AlertDialog.Builder(requireContext())
            .setTitle(R.string.enter_deck_name)
            .setView(contentView)
            .setCancelable(false)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                controller?.dispatch(PositiveDialogButtonClicked)
            }
            .setNegativeButton(android.R.string.cancel) { _, _ ->
                controller?.dispatch(NegativeDialogButtonClicked)
            }
            .create()
            .apply { setOnShowListener { deckNameDialogEditText.showSoftInput() } }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewCoroutineScope!!.launch {
            val diScope = AddDeckDiScope.get()
            controller = diScope.controller
            val viewModel = diScope.viewModel
            observeViewModel(viewModel)
            controller!!.commands.observe(::executeCommand)
            pendingEvent?.let(controller!!::dispatch)
            pendingEvent = null
        }
    }

    private fun observeViewModel(viewModel: AddDeckViewModel) {
        with(viewModel) {
            isProcessing.observe { isProcessing ->
                progressBar.isVisible = isProcessing
            }
            isDialogVisible.observe { isDialogVisible ->
                deckNameInputDialog.run { if (isDialogVisible) show() else dismiss() }
            }
            nameCheckResult.observe { nameCheckResult: NameCheckResult ->
                deckNameDialogEditText.error = when (nameCheckResult) {
                    Ok -> null
                    Empty -> getString(R.string.error_message_empty_name)
                    Occupied -> getString(R.string.error_message_occupied_name)
                }
            }
            isPositiveButtonEnabled.observe { isPositiveButtonEnabled ->
                deckNameInputDialog.getButton(AlertDialog.BUTTON_POSITIVE)?.let { positiveButton ->
                    positiveButton.isEnabled = isPositiveButtonEnabled
                }
            }
        }
    }

    private fun executeCommand(command: AddDeckController.Command) {
        when (command) {
            is ShowErrorMessage -> {
                showToast(command.exception.message)
            }
            is SetDialogText -> {
                deckNameDialogEditText.setText(command.text)
                deckNameDialogEditText.selectAll()
            }
        }
    }

    // it is called from parent fragment
    fun addDeck() {
        addDeckDialog.show()
    }

    private fun createAddDeckDialog(): Dialog {
        val dialogView = View.inflate(requireContext(), R.layout.popup_add_deck, null).apply {
            loadFromFileButton.setOnClickListener {
                showFileChooser()
                addDeckDialog.dismiss()
            }
            createDeckButton.setOnClickListener {
                controller?.dispatch(CreateDeckButtonClicked)
                addDeckDialog.dismiss()
            }
        }
        return AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()
    }

    private fun showFileChooser() {
        val intent = Intent(Intent.ACTION_GET_CONTENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType("text/plain")
        startActivityForResult(intent, GET_CONTENT_REQUEST_CODE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, intent: Intent?) {
        super.onActivityResult(requestCode, resultCode, intent)
        if (resultCode != Activity.RESULT_OK
            || requestCode != GET_CONTENT_REQUEST_CODE
            || intent == null
        ) {
            return
        }
        val uri = intent.data
        val contentResolver = context?.contentResolver
        val inputStream = uri?.let { contentResolver?.openInputStream(uri) }
        if (uri == null || contentResolver == null || inputStream == null) return
        val fileName = getFileNameFromUri(uri, contentResolver)
        val event = ContentReceived(inputStream, fileName)
        if (controller == null) {
            pendingEvent = event
        } else {
            controller!!.dispatch(event)
        }
    }

    private fun getFileNameFromUri(uri: Uri, contentResolver: ContentResolver): String? {
        val cursor = contentResolver.query(uri, null, null, null, null)
        cursor.use {
            if (cursor == null || !cursor.moveToFirst()) {
                return null
            }
            val nameIndex: Int = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            return try {
                cursor.getString(nameIndex)
            } catch (e: Exception) {
                null
            }
        }
    }

    override fun onViewStateRestored(savedInstanceState: Bundle?) {
        super.onViewStateRestored(savedInstanceState)
        savedInstanceState?.run {
            getBundle(STATE_KEY_DECK_NAME_INPUT_DIALOG)
                ?.let(deckNameInputDialog::onRestoreInstanceState)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (::deckNameInputDialog.isInitialized) {
            outState.putBundle(
                STATE_KEY_DECK_NAME_INPUT_DIALOG,
                deckNameInputDialog.onSaveInstanceState()
            )
        }
    }

    override fun onResume() {
        super.onResume()
        if (::deckNameInputDialog.isInitialized && deckNameInputDialog.isShowing) {
            deckNameDialogEditText.showSoftInput()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (needToCloseDiScope()) {
            AddDeckDiScope.close()
        }
    }

    companion object {
        const val GET_CONTENT_REQUEST_CODE = 39
        const val STATE_KEY_DECK_NAME_INPUT_DIALOG = "deckNameInputDialog"
    }
}