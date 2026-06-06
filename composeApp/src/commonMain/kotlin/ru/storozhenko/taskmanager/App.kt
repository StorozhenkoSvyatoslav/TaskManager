package ru.storozhenko.taskmanager

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
fun App() {
    MaterialTheme {
        var token by remember { mutableStateOf<String?>(null) }

        if (token == null) {
            AuthScreen(onAuthenticated = { token = it })
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Authenticated! Ready to build the main app.")
            }
        }
    }
}