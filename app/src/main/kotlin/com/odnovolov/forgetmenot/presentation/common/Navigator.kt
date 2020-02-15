package com.odnovolov.forgetmenot.presentation.common

import android.app.Activity
import android.app.Application
import android.os.Bundle
import androidx.navigation.NavController
import androidx.navigation.findNavController
import com.odnovolov.forgetmenot.R

interface Navigator {
    fun navigateToExercise()
    fun navigateToEditCard()
}

class NavigatorImpl : Navigator, Application.ActivityLifecycleCallbacks {
    private var navController: NavController? = null

    override fun navigateToExercise() {
        navController?.navigate(R.id.action_home_screen_to_exercise_screen)
    }

    override fun navigateToEditCard() {
        navController?.navigate(R.id.action_exercise_screen_to_edit_card_screen)
    }

    override fun onActivityStarted(activity: Activity) {
        navController = activity.findNavController(R.id.nav_host_fragment)
    }

    override fun onActivityStopped(activity: Activity) {
        navController = null
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    override fun onActivityResumed(activity: Activity) {}
    override fun onActivityPaused(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {}
}