package com.patrollink

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.patrollink.data.local.AndroidKeystoreSecureStore
import com.patrollink.data.local.UiSettingsStore
import com.patrollink.presentation.PatrolViewModel
import com.patrollink.presentation.navigation.PatrolApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: PatrolViewModel = viewModel(
                factory = PatrolViewModelFactory(this@MainActivity)
            )
            PatrolApp(viewModel)
        }
    }
}

private class PatrolViewModelFactory(
    private val activity: ComponentActivity
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return PatrolViewModel(
            secureStore = AndroidKeystoreSecureStore(activity.applicationContext),
            settingsStore = UiSettingsStore(activity.applicationContext)
        ) as T
    }
}
