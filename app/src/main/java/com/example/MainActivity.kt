package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.ui.theme.MyApplicationTheme
import com.example.webport.core.WebPortRepository
import com.example.webport.ui.WebPortMainScreen
import com.example.webport.ui.WebPortViewModel

class MainActivity : ComponentActivity() {
  private val repository by lazy { WebPortRepository(applicationContext) }
  private val viewModel by lazy { WebPortViewModel(repository) }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      MyApplicationTheme {
        Surface(
          modifier = Modifier.fillMaxSize(),
          color = MaterialTheme.colorScheme.background
        ) {
          WebPortMainScreen(viewModel = viewModel)
        }
      }
    }
  }
}

