package com.saubh.deskbuddy.ui

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.saubh.deskbuddy.AppGraph
import com.saubh.deskbuddy.DeskBuddyApplication
import com.saubh.deskbuddy.ui.apps.AppsViewModel
import com.saubh.deskbuddy.ui.connect.DevicesViewModel
import com.saubh.deskbuddy.ui.home.HomeViewModel
import com.saubh.deskbuddy.ui.keyboard.KeyboardViewModel
import com.saubh.deskbuddy.ui.media.MediaViewModel
import com.saubh.deskbuddy.ui.share.ShareViewModel
import com.saubh.deskbuddy.ui.standby.StandbyViewModel

/**
 * Builds every screen ViewModel with constructor-injected dependencies from [AppGraph], the
 * app's single dependency container. Screens use `viewModel(factory = AppViewModelProvider.Factory)`.
 */
object AppViewModelProvider {
    val Factory: ViewModelProvider.Factory = viewModelFactory {
        initializer { DevicesViewModel(graph().session, graph().autoConnector) }
        initializer {
            val g = graph()
            HomeViewModel(g.session, g.media, g.apps, g.share, g.files, g.power, g.standbyPrefs)
        }
        initializer { MediaViewModel(graph().media) }
        initializer { AppsViewModel(graph().apps, graph().runningApps) }
        initializer { ShareViewModel(graph().share, graph().files) }
        initializer { KeyboardViewModel(graph().session, graph().keyboard, graph().keyboardHub) }
        initializer {
            val g = graph()
            StandbyViewModel(
                connection = g.session.uiState,
                savedDesktops = g.session.savedDesktops,
                media = g.media,
                apps = g.apps.state,
                activateApp = g.apps::activate,
                running = g.runningApps,
                power = g.power,
                faces = g.standbyPrefs,
            )
        }
    }
}

private fun CreationExtras.graph(): AppGraph =
    (checkNotNull(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]) as DeskBuddyApplication).graph
