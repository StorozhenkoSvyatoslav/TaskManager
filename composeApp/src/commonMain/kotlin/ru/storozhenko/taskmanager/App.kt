package ru.storozhenko.taskmanager

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import ru.storozhenko.taskmanager.models.WorkspaceModel

sealed class Screen {
    object WorkspaceList : Screen()
    data class WorkspaceDetail(val workspace: WorkspaceModel) : Screen()
}

@Composable
fun App() {
    MaterialTheme {
        var token  by remember { mutableStateOf(TokenStorage.load()) }
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
                is Screen.WorkspaceList -> WorkspaceListScreen(
                    token                = token!!,
                    onNavigateToWorkspace = { ws -> screen = Screen.WorkspaceDetail(ws) },
                    onLogout             = onLogout
                )
                is Screen.WorkspaceDetail -> WorkspaceDetailScreen(
                    workspace = s.workspace,
                    token     = token!!,
                    onBack    = { screen = Screen.WorkspaceList }
                )
            }
        }
    }
}
