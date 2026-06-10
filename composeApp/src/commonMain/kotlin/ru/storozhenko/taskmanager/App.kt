package ru.storozhenko.taskmanager

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import ru.storozhenko.taskmanager.models.TaskModel
import ru.storozhenko.taskmanager.models.WorkspaceModel

sealed class Screen {
    object WorkspaceList : Screen()
    data class WorkspaceDetail(val workspace: WorkspaceModel) : Screen()
    data class TaskDetail(val task: TaskModel, val workspace: WorkspaceModel) : Screen()
    data class CreateTask(val workspace: WorkspaceModel) : Screen()
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
                    workspace        = s.workspace,
                    token            = token!!,
                    onBack           = { screen = Screen.WorkspaceList },
                    onNavigateToTask = { task -> screen = Screen.TaskDetail(task, s.workspace) },
                    onCreateTask     = { screen = Screen.CreateTask(s.workspace) }
                )
                is Screen.TaskDetail -> TaskDetailScreen(
                    task      = s.task,
                    workspace = s.workspace,
                    token     = token!!,
                    onBack    = { screen = Screen.WorkspaceDetail(s.workspace) }
                )
                is Screen.CreateTask -> TaskCreateEditScreen(
                    workspace     = s.workspace,
                    token         = token!!,
                    onBack        = { screen = Screen.WorkspaceDetail(s.workspace) },
                    onTaskCreated = { screen = Screen.WorkspaceDetail(s.workspace) }
                )
            }
        }
    }
}
