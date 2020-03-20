package com.odnovolov.forgetmenot.presentation.screen.intervals.modifyinterval

import com.odnovolov.forgetmenot.persistence.serializablestate.ModifyIntervalDialogStateProvider
import com.odnovolov.forgetmenot.presentation.screen.intervals.INTERVALS_SCOPE_ID
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module
import org.koin.dsl.onClose

val modifyIntervalModule = module {
    scope<ModifyIntervalViewModel> {
        scoped { ModifyIntervalDialogStateProvider() }
        scoped { get<ModifyIntervalDialogStateProvider>().load() }
        scoped {
            ModifyIntervalController(
                intervalsSettings = getScope(INTERVALS_SCOPE_ID).get(),
                modifyIntervalDialogState = get(),
                store = get(),
                modifyIntervalsScreenStateProvider = get<ModifyIntervalDialogStateProvider>()
            )
        } onClose { it?.onCleared() }
        viewModel { ModifyIntervalViewModel(modifyIntervalDialogState = get()) }
    }
}

const val MODIFY_INTERVAL_SCOPE_ID = "MODIFY_INTERVAL_SCOPE_ID"