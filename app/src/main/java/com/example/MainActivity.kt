package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import com.example.data.ScriptDatabase
import com.example.data.ScriptRepository
import com.example.ui.DashboardScreen
import com.example.ui.ScriptViewModel
import com.example.ui.ViewModelFactory
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    
    // Initialize Database & Repository
    val database = ScriptDatabase.getDatabase(this)
    val repository = ScriptRepository(database.scriptDao())
    
    // Construct ViewModel
    val viewModel = ViewModelProvider(
      this, 
      ViewModelFactory(repository)
    )[ScriptViewModel::class.java]

    setContent {
      MyApplicationTheme {
        DashboardScreen(viewModel = viewModel)
      }
    }
  }
}
