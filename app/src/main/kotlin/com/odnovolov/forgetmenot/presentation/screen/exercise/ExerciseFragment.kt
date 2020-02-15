package com.odnovolov.forgetmenot.presentation.screen.exercise

import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.*
import android.view.View.MeasureSpec
import android.widget.PopupWindow
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.odnovolov.forgetmenot.R
import com.odnovolov.forgetmenot.common.dp
import com.odnovolov.forgetmenot.common.getBackgroundResForLevelOfKnowledge
import com.odnovolov.forgetmenot.presentation.common.base.BaseFragment
import com.odnovolov.forgetmenot.presentation.screen.exercise.ExerciseCommand.*
import kotlinx.android.synthetic.main.fragment_exercise.*
import kotlinx.android.synthetic.main.popup_choose_hint.view.*
import org.koin.android.ext.android.getKoin
import org.koin.androidx.viewmodel.scope.viewModel

class ExerciseFragment : BaseFragment() {
    private val koinScope = getKoin().getOrCreateScope<ExerciseViewModel>(EXERCISE_SCOPE_ID)
    private val viewModel: ExerciseViewModel by koinScope.viewModel(this)
    private val controller: ExerciseController by koinScope.inject()
    private lateinit var chooseHintPopup: PopupWindow
    private lateinit var setLevelOfKnowledgePopup: PopupWindow
    private lateinit var intervalsAdapter: IntervalsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        (activity as AppCompatActivity).supportActionBar?.run {
            setShowHideAnimationEnabled(false)
            hide()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        createLevelOfKnowledgePopup()
        createChooseHintPopup()
        return inflater.inflate(R.layout.fragment_exercise, container, false)
    }

    private fun createLevelOfKnowledgePopup() {
        val onItemClick: (Int) -> Unit = { levelOfKnowledge: Int ->
            controller.onLevelOfKnowledgeSelected(levelOfKnowledge)
            setLevelOfKnowledgePopup.dismiss()
        }
        intervalsAdapter = IntervalsAdapter(onItemClick)
        val recycler: RecyclerView =
            View.inflate(context, R.layout.popup_set_level_of_knowledge, null) as RecyclerView
        recycler.adapter = intervalsAdapter
        setLevelOfKnowledgePopup = PopupWindow(context).apply {
            width = WindowManager.LayoutParams.WRAP_CONTENT
            height = WindowManager.LayoutParams.WRAP_CONTENT
            contentView = recycler
            setBackgroundDrawable(
                ColorDrawable(
                    ContextCompat.getColor(
                        requireContext(),
                        R.color.exercise_control_panel_popup_background
                    )
                )
            )
            elevation = 20f
            isOutsideTouchable = true
            isFocusable = true
        }
    }

    private fun createChooseHintPopup() {
        val content = View.inflate(requireContext(), R.layout.popup_choose_hint, null).apply {
            hintAsQuizButton.setOnClickListener {
                controller.onHintAsQuizButtonClicked()
                chooseHintPopup.dismiss()
            }
            maskLettersButton.setOnClickListener {
                controller.onMaskLettersButtonClicked()
                chooseHintPopup.dismiss()
            }
        }
        content.measure(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        chooseHintPopup = PopupWindow(context).apply {
            width = content.measuredWidth
            height = content.measuredHeight
            contentView = content
            setBackgroundDrawable(
                ColorDrawable(
                    ContextCompat.getColor(
                        requireContext(),
                        R.color.exercise_control_panel_popup_background
                    )
                )
            )
            elevation = 20f
            isOutsideTouchable = true
            isFocusable = true
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupView()
        observeViewModel()
        controller.commands.observe(::executeCommand)
    }

    private fun setupView() {
        setupViewPagerAdapter()
        setupControlPanel()
        //setupWalkingModeIfEnabled()
    }

    private fun setupViewPagerAdapter() {
        exerciseViewPager.adapter = ExerciseCardsAdapter(fragment = this)
        exerciseViewPager.registerOnPageChangeCallback(onPageChangeCallback)
    }

    private fun setupControlPanel() {
        notAskButton.setOnClickListener { controller.onSetCardLearnedButtonClicked() }
        undoButton.setOnClickListener { controller.onUndoButtonClicked() }
        speakButton.setOnClickListener { controller.onSpeakButtonClicked() }
        editCardButton.setOnClickListener { controller.onEditCardButtonClicked() }
        hintButton.setOnClickListener { controller.onHintButtonClicked() }
        levelOfKnowledgeButton.setOnClickListener { controller.onLevelOfKnowledgeButtonClicked() }
    }

    private fun observeViewModel() {
        with(viewModel) {
            val exerciseCardsAdapter = exerciseViewPager.adapter as ExerciseCardsAdapter
            // we help ViewPager restore its state
            exerciseCardsAdapter.exerciseCardIds = exerciseCardsIdsAtStart
            exerciseCardIds.observe { exerciseCardsAdapter.exerciseCardIds = it }
            isCurrentExerciseCardLearned.observe { isCurrentCardLearned ->
                isCurrentCardLearned ?: return@observe
                notAskButton.visibility = if (isCurrentCardLearned) View.GONE else View.VISIBLE
                undoButton.visibility = if (isCurrentCardLearned) View.VISIBLE else View.GONE
            }
            isHintButtonVisible.observe { isVisible: Boolean ->
                hintButton.visibility = if (isVisible) View.VISIBLE else View.GONE
            }
            levelOfKnowledgeForCurrentCard.observe { levelOfKnowledge: Int? ->
                levelOfKnowledge ?: return@observe
                if (levelOfKnowledge == -1) {
                    levelOfKnowledgeTextView.visibility = View.GONE
                } else {
                    val backgroundRes = getBackgroundResForLevelOfKnowledge(levelOfKnowledge)
                    levelOfKnowledgeTextView.setBackgroundResource(backgroundRes)
                    levelOfKnowledgeTextView.text = levelOfKnowledge.toString()
                    levelOfKnowledgeTextView.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun executeCommand(command: ExerciseCommand) {
        when (command) {
            MoveToNextPosition -> {
                val nextPosition = exerciseViewPager.currentItem + 1
                exerciseViewPager.setCurrentItem(nextPosition, true)
            }
            MoveToPreviousPosition -> {
                val previousPosition = exerciseViewPager.currentItem - 1
                exerciseViewPager.setCurrentItem(previousPosition, true)
            }
            ShowChooseHintPopup -> {
                showChooseHintPopup()
            }
            is ShowLevelOfKnowledgePopup -> {
                showLevelOfKnowledgePopup(command.intervalItems)
            }
            ShowIntervalsAreOffMessage -> {
                Toast.makeText(
                    requireContext(),
                    R.string.toast_text_intervals_are_off,
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun showChooseHintPopup() {
        val hintButtonLocation = IntArray(2).also { hintButton.getLocationOnScreen(it) }
        val x = hintButtonLocation[0] + (hintButton.width / 2) - (chooseHintPopup.width / 2)
        val y = hintButtonLocation[1] + hintButton.height - 8.dp - chooseHintPopup.height
        chooseHintPopup.showAtLocation(
            hintButton.rootView,
            Gravity.NO_GRAVITY,
            x,
            y
        )
    }

    private fun showLevelOfKnowledgePopup(intervalItems: List<IntervalItem>) {
        intervalsAdapter.intervalItems = intervalItems
        val content = setLevelOfKnowledgePopup.contentView
        content.measure(
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        )
        val location = IntArray(2)
        levelOfKnowledgeButton.getLocationOnScreen(location)
        val x = location[0] + levelOfKnowledgeButton.width - 8.dp - content.measuredWidth
        val y = location[1] + levelOfKnowledgeButton.height - 8.dp - content.measuredHeight
        setLevelOfKnowledgePopup.showAtLocation(
            levelOfKnowledgeButton.rootView,
            Gravity.NO_GRAVITY,
            x,
            y
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        exerciseViewPager.adapter = null
        exerciseViewPager.unregisterOnPageChangeCallback(onPageChangeCallback)
        (setLevelOfKnowledgePopup.contentView as RecyclerView).adapter = null
        /*if (viewModel.isWalkingMode) {
            (activity as MainActivity).keyEventInterceptor = null
        }*/
    }

    override fun onDestroy() {
        super.onDestroy()
        (activity as AppCompatActivity).supportActionBar?.show()
        if (isRemoving) {
            controller.onFragmentRemoving()
        }
    }

    private val onPageChangeCallback = object : ViewPager2.OnPageChangeCallback() {
        override fun onPageSelected(position: Int) {
            controller.onPageSelected(position)
        }
    }
}