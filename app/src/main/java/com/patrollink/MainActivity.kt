package com.patrollink

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.patrollink.data.RuntimeDependencyFactory
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
        val dependencies = RuntimeDependencyFactory.create(activity.applicationContext)
        return PatrolViewModel(
            coordinator = dependencies.coordinator,
            secureStore = dependencies.secureStore,
            settingsStore = dependencies.settingsStore,
            onSessionChanged = dependencies.tokenStore::update
        ) as T
    }
}
