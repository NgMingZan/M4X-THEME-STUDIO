package com.m4x.themestudio

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.m4x.themestudio.ui.M4XThemeStudioApp
import com.m4x.themestudio.ui.ThemeViewModel
import com.m4x.themestudio.ui.theme.M4XTheme

class MainActivity : ComponentActivity() {
    private val viewModel: ThemeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            M4XTheme {
                M4XThemeStudioApp(viewModel)
            }
        }
    }
}
