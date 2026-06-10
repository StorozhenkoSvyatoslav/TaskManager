package ru.storozhenko.taskmanager

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlinx.coroutines.launch
import ru.storozhenko.taskmanager.models.TaskModel
import ru.storozhenko.taskmanager.models.WorkspaceModel

sealed class Screen {
    object WorkspaceList : Screen()
    data class WorkspaceDetail(val workspace: WorkspaceModel) : Screen()
    data class TaskDetail(val task: TaskModel, val workspace: WorkspaceModel) : Screen()
    data class CreateTask(val workspace: WorkspaceModel) : Screen()
    data class EditTask(val task: TaskModel, val workspace: WorkspaceModel) : Screen()
    object AdminPanel : Screen()
}

@OptIn(ExperimentalEncodingApi::class)
private fun parseUserIdFromToken(token: String): Int {
    return try {
        val payload = token.split(".").getOrNull(1) ?: return 0
        val base64  = payload.replace('-', '+').replace('_', '/')
        val padded  = base64 + "=".repeat((4 - base64.length % 4) % 4)
        val json    = Base64.decode(padded).decodeToString()
        Regex("\"id\"\\s*:\\s*(\\d+)").find(json)?.groupValues?.get(1)?.toInt() ?: 0
    } catch (_: Exception) { 0 }
}

@Composable
fun App() {
    MaterialTheme {
        var token         by remember { mutableStateOf(TokenStorage.load()) }
        var currentUserId by remember { mutableStateOf(token?.let { parseUserIdFromToken(it) } ?: 0) }
        var systemRole    by remember { mutableStateOf("USER") }
        var screen        by remember { mutableStateOf<Screen>(Screen.WorkspaceList) }
        val scope = rememberCoroutineScope()

        LaunchedEffect(token) {
            val jwt = token ?: return@LaunchedEffect
            AdminRepository(jwt).getMe()
                .onSuccess { systemRole = it.systemRole }
        }

        if (token == null) {
            AuthScreen(onAuthenticated = { jwt ->
                TokenStorage.save(jwt)
                token = jwt
                currentUserId = parseUserIdFromToken(jwt)
                screen = Screen.WorkspaceList
            })
        } else {
            val onLogout = {
                TokenStorage.clear()
                token = null
                currentUserId = 0
                systemRole = "USER"
            }
            ApiClient.onUnauthorized = onLogout

            when (val s = screen) {
                is Screen.WorkspaceList -> WorkspaceListScreen(
                    token                 = token!!,
                    systemRole            = systemRole,
                    onNavigateToWorkspace = { ws -> screen = Screen.WorkspaceDetail(ws) },
                    onNavigateToAdmin     = { screen = Screen.AdminPanel },
                    onLogout              = onLogout
                )
                is Screen.WorkspaceDetail -> WorkspaceDetailScreen(
                    workspace        = s.workspace,
                    token            = token!!,
                    currentUserId    = currentUserId,
                    onBack           = { screen = Screen.WorkspaceList },
                    onNavigateToTask = { task -> screen = Screen.TaskDetail(task, s.workspace) },
                    onCreateTask     = { screen = Screen.CreateTask(s.workspace) },
                    onEditTask       = { task -> screen = Screen.EditTask(task, s.workspace) }
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
                is Screen.EditTask -> TaskCreateEditScreen(
                    workspace     = s.workspace,
                    existingTask  = s.task,
                    token         = token!!,
                    onBack        = { screen = Screen.WorkspaceDetail(s.workspace) },
                    onTaskCreated = { screen = Screen.WorkspaceDetail(s.workspace) }
                )
                is Screen.AdminPanel -> AdminPanelScreen(
                    token         = token!!,
                    currentUserId = currentUserId,
                    onBack        = { screen = Screen.WorkspaceList }
                )
            }
        }
    }
}
