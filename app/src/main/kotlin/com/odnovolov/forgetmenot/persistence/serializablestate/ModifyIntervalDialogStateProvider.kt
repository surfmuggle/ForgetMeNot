package com.odnovolov.forgetmenot.persistence.serializablestate

import com.odnovolov.forgetmenot.persistence.serializablestate.ModifyIntervalDialogStateProvider.SerializableModifyIntervalDialogState
import com.odnovolov.forgetmenot.presentation.screen.intervals.DisplayedInterval
import com.odnovolov.forgetmenot.presentation.screen.intervals.DisplayedInterval.IntervalUnit
import com.odnovolov.forgetmenot.presentation.screen.intervals.modifyinterval.ModifyIntervalDialogState
import kotlinx.serialization.Serializable

class ModifyIntervalDialogStateProvider :
    BaseSerializableStateProvider<ModifyIntervalDialogState, SerializableModifyIntervalDialogState>() {
    @Serializable
    data class SerializableModifyIntervalDialogState(
        val targetLevelOfKnowledge: Int,
        val intervalInputValue: Int?,
        val intervalUnit: IntervalUnit
    )

    override val serializer = SerializableModifyIntervalDialogState.serializer()
    override val serializableClassName = SerializableModifyIntervalDialogState::class.java.name

    override fun toSerializable(state: ModifyIntervalDialogState) =
        SerializableModifyIntervalDialogState(
            targetLevelOfKnowledge = state.targetLevelOfKnowledge,
            intervalInputValue = state.displayedInterval.value,
            intervalUnit = state.displayedInterval.intervalUnit
        )

    override fun toOriginal(
        serializableState: SerializableModifyIntervalDialogState
    ): ModifyIntervalDialogState {
        val intervalInputData = DisplayedInterval(
            serializableState.intervalInputValue,
            serializableState.intervalUnit
        )
        return ModifyIntervalDialogState(
            serializableState.targetLevelOfKnowledge,
            intervalInputData
        )
    }
}