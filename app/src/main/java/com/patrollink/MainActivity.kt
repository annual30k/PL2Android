package com.patrollink

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.patrollink.presentation.PatrolViewModel
import com.patrollink.presentation.navigation.PatrolApp
import com.patrollink.presentation.permission.PermissionGate
import com.patrollink.presentation.theme.PatrolTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PatrolTheme {
                val viewModel: PatrolViewModel = viewModel()
                PermissionGate {
                    PatrolApp(viewModel)
                }
            }
        }
    }
}
