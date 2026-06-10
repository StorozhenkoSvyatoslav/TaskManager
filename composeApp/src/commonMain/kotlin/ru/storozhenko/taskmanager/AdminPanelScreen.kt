package ru.storozhenko.taskmanager

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import ru.storozhenko.taskmanager.models.UpdateWorkspaceRequest
import ru.storozhenko.taskmanager.models.UserProfileModel
import ru.storozhenko.taskmanager.models.WorkspaceModel

private val ApBg          = Color(0xFFF8FAFC)
private val ApBorder      = Color(0xFFE2E8F0)
private val ApBlue        = Color(0xFF1976D2)
private val ApPurple      = Color(0xFF7C3AED)
private val ApTextPrimary = Color(0xFF1E293B)
private val ApTextMuted   = Color(0xFF64748B)

private val ApAdminBg  = Color(0xFFFEF3C7); private val ApAdminFg  = Color(0xFFD97706)
private val ApUserBg   = Color(0xFFF1F5F9); private val ApUserFg   = Color(0xFF475569)
private val ApBannedBg = Color(0xFFFEE2E2); private val ApBannedFg = Color(0xFFDC2626)
private val ApPublicBg = Color(0xFFE0F2FE); private val ApPublicFg = Color(0xFF0369A1)
private val ApPrivBg   = Color(0xFFF1F5F9); private val ApPrivFg   = Color(0xFF475569)

private val ApAvatarPalette = listOf(
    Color(0xFF8B5CF6), Color(0xFFEC4899), Color(0xFF3B82F6),
    Color(0xFF10B981), Color(0xFFF59E0B), Color(0xFFEF4444)
)
private fun avatarColor(id: Int) = ApAvatarPalette[id % ApAvatarPalette.size]

@Composable
fun AdminPanelScreen(
    token: String,
    currentUserId: Int,
    onBack: () -> Unit
) {
    val repo  = remember(token) { AdminRepository(token) }
    val scope = rememberCoroutineScope()

    var selectedTab by remember { mutableIntStateOf(0) }

    // Users state
    var users         by remember { mutableStateOf<List<UserProfileModel>>(emptyList()) }
    var usersLoading  by remember { mutableStateOf(true) }
    var usersError    by remember { mutableStateOf<String?>(null) }
    var usersRefresh  by remember { mutableIntStateOf(0) }
    var actingUserIds by remember { mutableStateOf<Set<Int>>(emptySet()) }

    // Workspaces state
    var workspaces       by remember { mutableStateOf<List<WorkspaceModel>>(emptyList()) }
    var wsLoading        by remember { mutableStateOf(true) }
    var wsError          by remember { mutableStateOf<String?>(null) }
    var wsRefresh        by remember { mutableIntStateOf(0) }
    var deletingWsIds    by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var editingWs        by remember { mutableStateOf<WorkspaceModel?>(null) }
    var deleteConfirmWs  by remember { mutableStateOf<WorkspaceModel?>(null) }

    LaunchedEffect(usersRefresh) {
        usersLoading = true; usersError = null
        repo.getUsers().onSuccess { users = it }.onFailure { usersError = it.message }
        usersLoading = false
    }

    LaunchedEffect(wsRefresh) {
        wsLoading = true; wsError = null
        repo.getAllWorkspaces().onSuccess { workspaces = it }.onFailure { wsError = it.message }
        wsLoading = false
    }

    Box(
        modifier = Modifier.fillMaxSize().background(ApBg),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(modifier = Modifier.widthIn(max = 1920.dp).fillMaxSize()) {

            ApHeaderBar(onBack = onBack)

            Surface(color = Color.White, modifier = Modifier.fillMaxWidth()) {
                PrimaryTabRow(
                    selectedTabIndex = selectedTab,
                    containerColor   = Color.White,
                    contentColor     = ApPurple
                ) {
                    listOf(
                        "Users (${users.size})",
                        "Workspaces (${workspaces.size})"
                    ).forEachIndexed { i, title ->
                        Tab(selected = selectedTab == i, onClick = { selectedTab = i }, text = { Text(title) })
                    }
                }
            }

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when (selectedTab) {
                    0 -> ApUsersTab(
                        users         = users,
                        isLoading     = usersLoading,
                        error         = usersError,
                        currentUserId = currentUserId,
                        actingUserIds = actingUserIds,
                        onRetry       = { usersRefresh++ },
                        onToggleRole  = { user ->
                            val newRole = if (user.systemRole == "ADMIN") "USER" else "ADMIN"
                            scope.launch {
                                actingUserIds = actingUserIds + user.id
                                repo.updateUserRole(user.id, newRole).onSuccess { usersRefresh++ }
                                actingUserIds = actingUserIds - user.id
                            }
                        },
                        onToggleBan   = { user ->
                            scope.launch {
                                actingUserIds = actingUserIds + user.id
                                repo.banUser(user.id, !user.isBanned).onSuccess { usersRefresh++ }
                                actingUserIds = actingUserIds - user.id
                            }
                        }
                    )
                    1 -> ApWorkspacesTab(
                        workspaces    = workspaces,
                        isLoading     = wsLoading,
                        error         = wsError,
                        deletingIds   = deletingWsIds,
                        onRetry       = { wsRefresh++ },
                        onEdit        = { editingWs = it },
                        onDelete      = { deleteConfirmWs = it }
                    )
                }
            }
        }
    }

    editingWs?.let { ws ->
        ApEditWorkspaceDialog(
            workspace = ws,
            onDismiss = { editingWs = null },
            onSave    = { name, desc ->
                scope.launch {
                    repo.updateWorkspace(ws.id, UpdateWorkspaceRequest(name, desc))
                        .onSuccess { wsRefresh++; editingWs = null }
                }
            }
        )
    }

    deleteConfirmWs?.let { ws ->
        ApDeleteConfirmDialog(
            workspaceName = ws.name,
            onDismiss     = { deleteConfirmWs = null },
            onConfirm     = {
                scope.launch {
                    deletingWsIds = deletingWsIds + ws.id
                    deleteConfirmWs = null
                    repo.deleteWorkspace(ws.id).onSuccess { wsRefresh++ }
                    deletingWsIds = deletingWsIds - ws.id
                }
            }
        )
    }
}

// ─── Tabs ────────────────────────────────────────────────────────────────────

@Composable
private fun ApUsersTab(
    users: List<UserProfileModel>,
    isLoading: Boolean,
    error: String?,
    currentUserId: Int,
    actingUserIds: Set<Int>,
    onRetry: () -> Unit,
    onToggleRole: (UserProfileModel) -> Unit,
    onToggleBan: (UserProfileModel) -> Unit
) {
    when {
        isLoading -> ApCenter { CircularProgressIndicator(color = ApPurple) }
        error != null -> ApErrorState(error, onRetry)
        users.isEmpty() -> ApCenter { Text("No users found", color = ApTextMuted) }
        else -> LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 40.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(users, key = { it.id }) { user ->
                ApUserCard(
                    user          = user,
                    isSelf        = user.id == currentUserId,
                    isActing      = user.id in actingUserIds,
                    onToggleRole  = { onToggleRole(user) },
                    onToggleBan   = { onToggleBan(user) }
                )
            }
        }
    }
}

@Composable
private fun ApWorkspacesTab(
    workspaces: List<WorkspaceModel>,
    isLoading: Boolean,
    error: String?,
    deletingIds: Set<Int>,
    onRetry: () -> Unit,
    onEdit: (WorkspaceModel) -> Unit,
    onDelete: (WorkspaceModel) -> Unit
) {
    when {
        isLoading -> ApCenter { CircularProgressIndicator(color = ApPurple) }
        error != null -> ApErrorState(error, onRetry)
        workspaces.isEmpty() -> ApCenter { Text("No workspaces found", color = ApTextMuted) }
        else -> LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 40.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(workspaces, key = { it.id }) { ws ->
                ApWorkspaceCard(
                    workspace   = ws,
                    isDeleting  = ws.id in deletingIds,
                    onEdit      = { onEdit(ws) },
                    onDelete    = { onDelete(ws) }
                )
            }
        }
    }
}

// ─── Cards ───────────────────────────────────────────────────────────────────

@Composable
private fun ApUserCard(
    user: UserProfileModel,
    isSelf: Boolean,
    isActing: Boolean,
    onToggleRole: () -> Unit,
    onToggleBan: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(1.dp),
        colors   = CardDefaults.cardColors(
            containerColor = if (user.isBanned) Color(0xFFFFF5F5) else Color.White
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(42.dp).background(avatarColor(user.id), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(user.username.take(2).uppercase(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(user.username, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = ApTextPrimary)
                    if (isSelf) { Spacer(Modifier.width(6.dp)); Text("(you)", fontSize = 12.sp, color = ApTextMuted) }
                }
                Text(user.email, fontSize = 13.sp, color = ApTextMuted)
            }
            Spacer(Modifier.width(10.dp))
            ApRoleChip(user.systemRole)
            if (user.isBanned) { Spacer(Modifier.width(6.dp)); ApBannedChip() }
            Spacer(Modifier.width(10.dp))
            if (isActing) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp, color = ApPurple)
            } else if (!isSelf) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onToggleRole,
                        modifier = Modifier,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = if (user.systemRole == "ADMIN") Color(0xFFEF4444) else ApBlue
                        ),
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(if (user.systemRole == "ADMIN") "Revoke Admin" else "Make Admin", fontSize = 12.sp)
                    }
                    OutlinedButton(
                        onClick = onToggleBan,
                        modifier = Modifier,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = if (user.isBanned) Color(0xFF16A34A) else Color(0xFFDC2626)
                        ),
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(if (user.isBanned) "Unban" else "Ban", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun ApWorkspaceCard(
    workspace: WorkspaceModel,
    isDeleting: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val isPublic = workspace.visibility == "PUBLIC"
    Card(
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(1.dp),
        colors    = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(42.dp).background(avatarColor(workspace.id), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(workspace.name.take(2).uppercase(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        workspace.name,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        color = ApTextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .background(if (isPublic) ApPublicBg else ApPrivBg, RoundedCornerShape(4.dp))
                            .padding(horizontal = 7.dp, vertical = 3.dp)
                    ) {
                        Text(
                            if (isPublic) "Public" else "Private",
                            color = if (isPublic) ApPublicFg else ApPrivFg,
                            fontSize = 11.sp, fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("ID: ${workspace.id}", fontSize = 12.sp, color = ApTextMuted)
                    Text("Owner: ${workspace.ownerId}", fontSize = 12.sp, color = ApTextMuted)
                    if (!isPublic && workspace.inviteCode != null) {
                        Text("Code: ${workspace.inviteCode}", fontSize = 12.sp, color = ApTextMuted)
                    }
                }
                workspace.description?.takeIf { it.isNotBlank() }?.let { desc ->
                    Text(desc, fontSize = 12.sp, color = ApTextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.width(10.dp))
            if (isDeleting) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp, color = Color(0xFFDC2626))
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onEdit,
                        modifier = Modifier,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ApBlue),
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) { Text("Edit", fontSize = 12.sp) }
                    OutlinedButton(
                        onClick = onDelete,
                        modifier = Modifier,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFDC2626)),
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) { Text("Delete", fontSize = 12.sp) }
                }
            }
        }
    }
}

// ─── Dialogs ─────────────────────────────────────────────────────────────────

@Composable
private fun ApEditWorkspaceDialog(
    workspace: WorkspaceModel,
    onDismiss: () -> Unit,
    onSave: (name: String, description: String?) -> Unit
) {
    var name by remember { mutableStateOf(workspace.name) }
    var desc by remember { mutableStateOf(workspace.description ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Workspace", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("Name *") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(), isError = name.isBlank()
                )
                OutlinedTextField(
                    value = desc, onValueChange = { desc = it },
                    label = { Text("Description") }, maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { if (name.isNotBlank()) onSave(name.trim(), desc.trim().ifBlank { null }) },
                enabled = name.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = ApBlue),
                shape = RoundedCornerShape(8.dp)
            ) { Text("Save") }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss, modifier = Modifier,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = ApTextMuted),
                shape = RoundedCornerShape(8.dp)
            ) { Text("Cancel") }
        }
    )
}

@Composable
private fun ApDeleteConfirmDialog(
    workspaceName: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete Workspace", fontWeight = FontWeight.Bold, color = Color(0xFFDC2626)) },
        text = {
            Text(
                "Delete \"$workspaceName\"? This will permanently delete all tasks, comments, and attachments. This action cannot be undone.",
                fontSize = 14.sp
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                shape = RoundedCornerShape(8.dp)
            ) { Text("Delete") }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss, modifier = Modifier,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = ApTextMuted),
                shape = RoundedCornerShape(8.dp)
            ) { Text("Cancel") }
        }
    )
}

// ─── Chips & helpers ─────────────────────────────────────────────────────────

@Composable
private fun ApRoleChip(role: String) {
    val isAdmin = role == "ADMIN"
    Box(
        modifier = Modifier
            .background(if (isAdmin) ApAdminBg else ApUserBg, RoundedCornerShape(4.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(role, color = if (isAdmin) ApAdminFg else ApUserFg, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ApBannedChip() {
    Box(
        modifier = Modifier
            .background(ApBannedBg, RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text("BANNED", color = ApBannedFg, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ApHeaderBar(onBack: () -> Unit) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 40.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = onBack, modifier = Modifier,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = ApTextMuted),
                shape = RoundedCornerShape(8.dp)
            ) { Text("← Back", fontSize = 13.sp) }
            Spacer(Modifier.width(16.dp))
            Box(
                modifier = Modifier.size(36.dp).background(ApPurple, CircleShape),
                contentAlignment = Alignment.Center
            ) { Text("A", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold) }
            Spacer(Modifier.width(10.dp))
            Text("Admin Panel", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = ApTextPrimary)
        }
        HorizontalDivider(color = ApBorder)
    }
}

@Composable
private fun ApCenter(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}

@Composable
private fun ApErrorState(error: String, onRetry: () -> Unit) {
    ApCenter {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(error, color = Color(0xFFEF4444), fontSize = 15.sp)
            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(containerColor = ApPurple),
                shape = RoundedCornerShape(8.dp)
            ) { Text("Retry") }
        }
    }
}
