package ru.storozhenko.taskmanager

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

sealed class Screen {
    object WorkspaceList : Screen()
    data class WorkspaceDetail(val workspaceId: Int) : Screen()
}

@Composable
fun App() {
    MaterialTheme {
        var token by remember { mutableStateOf(TokenStorage.load()) }
        var screen by remember { mutableStateOf<Screen>(Screen.WorkspaceList) }

        if (token == null) {
            AuthScreen(onAuthenticated = { jwt ->
                TokenStorage.save(jwt)
                token = jwt
                screen = Screen.WorkspaceList
            })
        } else {
            val onLogout = {
                TokenStorage.clear()
                token = null
            }

            when (val s = screen) {
                is Screen.WorkspaceList -> WorkspaceListPlaceholder(
                    token = token!!,
                    onNavigateToWorkspace = { id -> screen = Screen.WorkspaceDetail(id) },
                    onLogout = onLogout
                )
                is Screen.WorkspaceDetail -> WorkspaceDetailPlaceholder(
                    workspaceId = s.workspaceId,
                    onBack = { screen = Screen.WorkspaceList }
                )
            }
        }
    }
}

// Заглушки — будут заменены реальными экранами после согласования дизайна
@Composable
private fun WorkspaceListPlaceholder(
    token: String,
    onNavigateToWorkspace: (Int) -> Unit,
    onLogout: () -> Unit
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("WorkspaceListScreen — в разработке")
    }
}

@Composable
private fun WorkspaceDetailPlaceholder(
    workspaceId: Int,
    onBack: () -> Unit
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("WorkspaceDetailScreen #$workspaceId — в разработке")
    }
}