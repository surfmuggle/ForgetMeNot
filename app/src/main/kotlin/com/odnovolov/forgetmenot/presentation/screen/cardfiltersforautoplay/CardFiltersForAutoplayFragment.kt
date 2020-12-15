package com.odnovolov.forgetmenot.presentation.screen.cardfiltersforautoplay

import android.animation.*
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import android.widget.CheckBox
import androidx.core.content.ContextCompat
import com.appyvet.materialrangebar.RangeBar
import com.odnovolov.forgetmenot.R
import com.odnovolov.forgetmenot.R.color
import com.odnovolov.forgetmenot.presentation.common.base.BaseFragment
import com.odnovolov.forgetmenot.presentation.common.entity.DisplayedInterval
import com.odnovolov.forgetmenot.presentation.common.getGradeColorRes
import com.odnovolov.forgetmenot.presentation.common.needToCloseDiScope
import com.odnovolov.forgetmenot.presentation.common.showToast
import com.odnovolov.forgetmenot.presentation.common.uncover
import com.odnovolov.forgetmenot.presentation.screen.cardfiltersforautoplay.CardFiltersForAutoplayController.Command.ShowNoCardIsReadyForRepetitionMessage
import com.odnovolov.forgetmenot.presentation.screen.cardfiltersforautoplay.CardFiltersForAutoplayEvent.*
import kotlinx.android.synthetic.main.fragment_card_filters_for_autoplay.*
import kotlinx.coroutines.launch
import java.util.*
import kotlin.collections.ArrayList

class CardFiltersForAutoplayFragment : BaseFragment() {
    init {
        CardFiltersForAutoplayDiScope.reopenIfClosed()
    }

    private lateinit var viewModel: CardFiltersForAutoplayViewModel
    private var controller: CardFiltersForAutoplayController? = null
    private var isGradeRangeListenerEnabled = true

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_card_filters_for_autoplay, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewCoroutineScope!!.launch {
            val diScope = CardFiltersForAutoplayDiScope.getAsync() ?: return@launch
            viewModel = diScope.viewModel
            controller = diScope.controller
            setupView()
            observeViewModel()
            controller!!.commands.observe(::executeCommand)
        }
    }

    private fun setupView() {
        backButton.setOnClickListener {
            requireActivity().onBackPressed()
        }
        startPlayingButton.setOnClickListener {
            controller?.dispatch(StartPlayingButtonClicked)
        }
        setupStateFilter()
        setupGradeRangeFilter()
        setupLastTestedFilter()
    }

    private fun setupStateFilter() {
        availableForExerciseButton.setOnClickListener {
            controller?.dispatch(AvailableForExerciseCheckboxClicked)
        }
        awaitingButton.setOnClickListener {
            controller?.dispatch(AwaitingCheckboxClicked)
        }
        learnedButton.setOnClickListener {
            controller?.dispatch(LearnedCheckboxClicked)
        }
    }

    private fun setupGradeRangeFilter() {
        with(gradeRangeBar) {
            setOnRangeBarChangeListener(
                object : RangeBar.OnRangeBarChangeListener {
                    private var oldLeftPinValue: String? = leftPinValue
                    private var oldRightPinValue: String? = rightPinValue

                    override fun onTouchStarted(rangeBar: RangeBar?) {}

                    override fun onRangeChangeListener(
                        rangeBar: RangeBar?,
                        leftPinIndex: Int,
                        rightPinIndex: Int,
                        leftPinValue: String?,
                        rightPinValue: String?
                    ) {
                    }

                    override fun onTouchEnded(rangeBar: RangeBar?) {
                        if (isAnyPinValueChanged()) {
                            if (isGradeRangeListenerEnabled) {
                                val min = leftPinValue?.toInt() ?: return
                                val max = rightPinValue?.toInt() ?: return
                                controller?.dispatch(GradeRangeChanged(min..max))
                            }
                            updateGradeRangeSelectorColors()
                        }
                        updateOldPinValues()
                    }

                    private fun isAnyPinValueChanged(): Boolean =
                        leftPinValue != oldLeftPinValue || rightPinValue != oldRightPinValue

                    private fun updateOldPinValues() {
                        oldLeftPinValue = leftPinValue
                        oldRightPinValue = rightPinValue
                    }
                })
        }
    }

    private fun getGradeColor(grade: Int): Int {
        val resId = getGradeColorRes(grade)
        return ContextCompat.getColor(requireContext(), resId)
    }

    private fun setupLastTestedFilter() {
        lastTestedFromButton.setOnClickListener {
            controller?.dispatch(LastTestedFromButtonClicked)
        }
        lastTestedToButton.setOnClickListener {
            controller?.dispatch(LastTestedToButtonClicked)
        }
    }

    private fun observeViewModel() {
        with(viewModel) {
            matchingCardsNumber.observe { matchingCardsNumber: Int ->
                matchingCardsNumberTextView.text = matchingCardsNumber.toString()
                matchingCardsLabelTextView.text = resources.getQuantityString(
                    R.plurals.matching_cards_number_label,
                    matchingCardsNumber
                )
            }
            isAvailableForExerciseCheckboxChecked.observe { isChecked: Boolean ->
                updateCheckBox(availableForExerciseCheckBox, isChecked)
            }
            isAwaitingCheckboxChecked.observe { isChecked: Boolean ->
                updateCheckBox(awaitingCheckBox, isChecked)
            }
            isLearnedCheckboxChecked.observe { isChecked: Boolean ->
                updateCheckBox(learnedCheckBox, isChecked)
            }
            availableGradeRange.let(::adaptGradeBarToAvailableRange)
            selectedGradeRange.observe { gradeRange: IntRange ->
                isGradeRangeListenerEnabled = false
                gradeRangeBar.setRangePinsByValue(
                    gradeRange.first.toFloat(),
                    gradeRange.last.toFloat()
                )
                isGradeRangeListenerEnabled = true
            }
            lastTestedFromTimeAgo.observe { lastTestedFromTimeAgo: DisplayedInterval? ->
                lastTestedFromButton.text =
                    if (lastTestedFromTimeAgo == null) {
                        getString(R.string.zero_time).toLowerCase(Locale.ROOT)
                    } else {
                        val timeAgo: String = lastTestedFromTimeAgo.toString(requireContext())
                            .toLowerCase(Locale.ROOT)
                        getString(R.string.time_ago, timeAgo)
                    }
            }
            lastTestedToTimeAgo.observe { lastTestedToTimeAgo: DisplayedInterval? ->
                lastTestedToButton.text =
                    if (lastTestedToTimeAgo == null) {
                        getString(R.string.now).toLowerCase(Locale.ROOT)
                    } else {
                        val timeAgo: String =
                            lastTestedToTimeAgo.toString(requireContext()).toLowerCase(Locale.ROOT)
                        getString(R.string.time_ago, timeAgo)
                    }
            }
        }
    }

    private fun updateCheckBox(checkBox: CheckBox, isChecked: Boolean) {
        with(checkBox) {
            setChecked(isChecked)
            uncover()
        }
    }

    private fun adaptGradeBarToAvailableRange(availableGradeRange: IntRange) {
        with(gradeRangeBar) {
            tickStart = availableGradeRange.first.toFloat()
            tickEnd = availableGradeRange.last.toFloat()
            tickTopLabels = availableGradeRange
                .map { it.toString() }
                .toTypedArray()
            val list = (tickStart.toInt()..tickEnd.toInt())
                .map(::getGradeColor)
            setConnectingLineColors(ArrayList(list))
            updateGradeRangeSelectorColors()
        }
    }

    private fun updateGradeRangeSelectorColors() {
        with(gradeRangeBar) {
            leftSelectorColor = getGradeColor(leftPinValue.toInt())
            rightSelectorColor = getGradeColor(rightPinValue.toInt())
        }
    }

    private fun executeCommand(command: CardFiltersForAutoplayController.Command) {
        when (command) {
            ShowNoCardIsReadyForRepetitionMessage -> {
                showToast(R.string.toast_text_no_card_matches_filter_conditions)
                animateCounter()
            }
        }
    }

    private fun animateCounter() {
        val counterRotation = ObjectAnimator.ofFloat(
            matchingCardsNumberTextView, View.ROTATION,
            0f, -5f, 10f, -10f, 5f, 0f
        ).apply { duration = 600 }
        val titleColor = ContextCompat.getColor(requireContext(), color.title)
        val descriptionColor = ContextCompat.getColor(requireContext(), color.title_description)
        val issueColor = ContextCompat.getColor(requireContext(), color.issue)
        val counterPainting =
            ValueAnimator.ofObject(ArgbEvaluator(), titleColor, issueColor, titleColor)
                .apply {
                    duration = 900
                    addUpdateListener { animator: ValueAnimator ->
                        matchingCardsNumberTextView.setTextColor(
                            animator.animatedValue as Int
                        )
                    }
                }
        val descriptionPainting =
            ValueAnimator.ofObject(ArgbEvaluator(), descriptionColor, issueColor, descriptionColor)
                .apply {
                    duration = 900
                    addUpdateListener { animator: ValueAnimator ->
                        matchingCardsLabelTextView.setTextColor(
                            animator.animatedValue as Int
                        )
                    }
                }
        AnimatorSet().run {
            playTogether(counterRotation, counterPainting, descriptionPainting)
            interpolator = LinearInterpolator()
            start()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (needToCloseDiScope()) {
            CardFiltersForAutoplayDiScope.close()
        }
    }
}