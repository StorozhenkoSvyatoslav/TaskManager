package ru.storozhenko.taskmanager

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import ru.storozhenko.taskmanager.models.TaskModel
import ru.storozhenko.taskmanager.models.WorkspaceMemberModel
import ru.storozhenko.taskmanager.models.WorkspaceModel
import ru.storozhenko.taskmanager.models.UpdateWorkspaceRequest
import ru.storozhenko.taskmanager.models.WorkspaceStats
import kotlin.time.Clock
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.material3.ExperimentalMaterial3Api

// ── Colors ────────────────────────────────────────────────────────────────────
private val WdPageBg    = Color(0xFFF8FAFC)
private val WdBorder    = Color(0xFFE2E8F0)
private val WdBlue      = Color(0xFF1976D2)
private val WdSkyBg     = Color(0xFFE0F2FE)
private val WdSkyFg     = Color(0xFF0369A1)
private val WdTextPri   = Color(0xFF1E293B)
private val WdTextMuted = Color(0xFF64748B)
private val WdGray      = Color(0xFFF1F5F9)
private val WdPurple    = Color(0xFF8B5CF6)
private val WdAmber     = Color(0xFFF59E0B)
private val WdGreen     = Color(0xFF10B981)
private val WdRed       = Color(0xFFEF4444)
private val WdSlate     = Color(0xFF94A3B8)

// ── Kanban column definitions ─────────────────────────────────────────────────
private data class WdCol(val status: String, val label: String, val color: Color)

private val WD_COLUMNS = listOf(
    WdCol("TODO",        "TO DO",       WdSlate),
    WdCol("IN_PROGRESS", "IN PROGRESS", Color(0xFF3B82F6)),
    WdCol("REVIEW",      "REVIEW",      WdAmber),
    WdCol("DONE",        "DONE",        WdGreen),
)

private val WD_MEMBER_COLORS = listOf(
    Color(0xFF8B5CF6), Color(0xFFEC4899), Color(0xFFF59E0B),
    Color(0xFF10B981), Color(0xFF3B82F6), Color(0xFFEF4444),
)

private fun wdMemberColor(userId: Int) = WD_MEMBER_COLORS[userId % WD_MEMBER_COLORS.size]

private fun wdMemberInitials(username: String) =
    username.split(" ", "_", ".")
        .filter { it.isNotBlank() }
        .take(2)
        .joinToString("") { it[0].uppercaseChar().toString() }
        .ifEmpty { username.take(2).uppercase() }

private fun wdRelativeTime(epochSeconds: Long): String {
    val nowSec  = Clock.System.now().epochSeconds
    val diffSec = nowSec - epochSeconds
    return when {
        diffSec < 60L            -> "Just now"
        diffSec < 3600L          -> "${diffSec / 60L}m ago"
        diffSec < 86400L         -> "${diffSec / 3600L}h ago"
        diffSec < 86400L * 2L    -> "Yesterday"
        diffSec < 86400L * 7L    -> "${diffSec / 86400L}d ago"
        else                     -> "${diffSec / (86400L * 7L)}w ago"
    }
}

private data class WdActivityEntry(val text: String, val time: String)

private fun wdBuildActivity(
    tasks: List<TaskModel>,
    members: List<WorkspaceMemberModel>
): List<WdActivityEntry> {
    val memberMap = members.associateBy { it.userId }
    return tasks
        .sortedByDescending { maxOf(it.createdAt, it.updatedAt) }
        .take(5)
        .map { task ->
            val author = memberMap[task.authorId]?.username ?: "User #${task.authorId}"
            val verb = when {
                task.status.uppercase() == "DONE"                  -> "completed"
                task.updatedAt > task.createdAt                    -> "updated"
                else                                               -> "created"
            }
            val timeEpoch = maxOf(task.createdAt, task.updatedAt)
            WdActivityEntry("$author $verb '${task.title}'", wdRelativeTime(timeEpoch))
        }
}

private fun wdPriorityColor(priority: String) = when (priority.uppercase()) {
    "HIGH"   -> WdRed
    "MEDIUM" -> WdAmber
    else     -> WdGreen
}

private fun taskInitials(task: TaskModel): String {
    val words = task.title.split(" ").filter { it.isNotBlank() }
    return words.take(2).joinToString("") { w -> w[0].uppercaseChar().toString() }
}

// ── Screen ────────────────────────────────────────────────────────────────────
@Composable
fun WorkspaceDetailScreen(
    workspace: WorkspaceModel,
    token: String,
    currentUserId: Int = 0,
    onBack: () -> Unit,
    onNavigateToTask: (TaskModel) -> Unit = {},
    onCreateTask: () -> Unit = {},
    onEditTask: (TaskModel) -> Unit = {}
) {
    val taskRepo      = remember(token) { TaskRepository(token) }
    val workspaceRepo = remember(token) { WorkspaceRepository(token) }
    val scope         = rememberCoroutineScope()

    var tasks          by remember { mutableStateOf<List<TaskModel>>(emptyList()) }
    var stats          by remember { mutableStateOf<WorkspaceStats?>(null) }
    var members        by remember { mutableStateOf<List<WorkspaceMemberModel>>(emptyList()) }
    var isLoading      by remember { mutableStateOf(true) }
    var refreshKey     by remember { mutableStateOf(0) }
    var searchQuery    by remember { mutableStateOf("") }
    var priorityFilter by remember { mutableStateOf("All") }
    var sortBy         by remember { mutableStateOf("Priority") }
    var currentWorkspace by remember { mutableStateOf(workspace) }
    var showInviteDialog by remember { mutableStateOf(false) }
    var showEditDialog   by remember { mutableStateOf(false) }
    var showHeaderMenu   by remember { mutableStateOf(false) }

    val isOwner = currentUserId != 0 && currentUserId == currentWorkspace.ownerId

    LaunchedEffect(currentWorkspace.id, refreshKey) {
        isLoading = true
        tasks   = taskRepo.getTasksByWorkspace(currentWorkspace.id).getOrElse { emptyList() }
        stats   = taskRepo.getWorkspaceStats(currentWorkspace.id).getOrNull()
        members = workspaceRepo.getMembers(currentWorkspace.id).getOrElse { emptyList() }
        isLoading = false
    }

    val priorityOrder = mapOf("HIGH" to 0, "MEDIUM" to 1, "LOW" to 2)

    val filteredTasks = tasks
        .filter { it.title.contains(searchQuery, ignoreCase = true) }
        .filter { priorityFilter == "All" || it.priority.equals(priorityFilter, ignoreCase = true) }
        .let { list ->
            when (sortBy) {
                "Priority" -> list.sortedBy { priorityOrder[it.priority.uppercase()] ?: 3 }
                "Due Date" -> list.sortedByDescending { it.createdAt }
                "Assignee" -> list.sortedBy { it.authorId }
                else       -> list
            }
        }

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize().background(WdPageBg),
        contentAlignment = Alignment.TopCenter
    ) {
        val showSidePanel = maxWidth >= 700.dp
        Row(modifier = Modifier.widthIn(max = 1920.dp).fillMaxSize()) {
            // ── Main content ──────────────────────────────────────────────────
            Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                WdHeaderBar(
                    workspace       = currentWorkspace,
                    members         = members,
                    showMenu        = showHeaderMenu,
                    onMenuToggle    = { showHeaderMenu = !showHeaderMenu },
                    onMenuDismiss   = { showHeaderMenu = false },
                    onBack          = onBack,
                    onInvite        = { showInviteDialog = true },
                    onEditWorkspace = { if (isOwner) showEditDialog = true }
                )
                WdToolbar(
                    searchQuery      = searchQuery,
                    onSearchChange   = { searchQuery = it },
                    priorityFilter   = priorityFilter,
                    onPriorityChange = { priorityFilter = it },
                    sortBy           = sortBy,
                    onSortChange     = { sortBy = it },
                    isOwner          = isOwner,
                    onCreateTask     = onCreateTask
                )
                WdKanbanBoard(
                    tasks        = filteredTasks,
                    isLoading    = isLoading,
                    isOwner      = isOwner,
                    onTaskClick  = onNavigateToTask,
                    onEditTask   = onEditTask,
                    onDeleteTask = { task ->
                        scope.launch {
                            taskRepo.deleteTask(task.id)
                            refreshKey++
                        }
                    },
                    modifier     = Modifier.weight(1f).fillMaxWidth()
                )
            }
            if (showSidePanel) {
                // ── Divider ───────────────────────────────────────────────────────
                Box(modifier = Modifier.width(1.dp).fillMaxHeight().background(WdBorder))
                // ── Side panel ────────────────────────────────────────────────────
                WdSidePanel(
                    workspace     = currentWorkspace,
                    filteredTasks = filteredTasks,
                    stats         = stats,
                    members       = members,
                    isOwner       = isOwner,
                    currentUserId = currentUserId,
                    onRemoveMember = { userId ->
                        scope.launch {
                            workspaceRepo.removeMember(currentWorkspace.id, userId)
                            refreshKey++
                        }
                    },
                    modifier      = Modifier.width(340.dp).fillMaxHeight()
                )
            }
        }
    }

    if (showInviteDialog) {
        WdInviteCodeDialog(
            workspace      = currentWorkspace,
            isOwner        = isOwner,
            workspaceRepo  = workspaceRepo,
            onDismiss      = { showInviteDialog = false }
        )
    }
    if (showEditDialog && isOwner) {
        WdEditWorkspaceDialog(
            workspace = currentWorkspace,
            onDismiss = { showEditDialog = false },
            onSave    = { name, description ->
                scope.launch {
                    workspaceRepo.updateWorkspace(
                        currentWorkspace.id,
                        UpdateWorkspaceRequest(name, description)
                    ).onSuccess {
                        currentWorkspace = currentWorkspace.copy(name = name, description = description)
                        showEditDialog = false
                    }
                }
            }
        )
    }
}

// ── Header Bar ────────────────────────────────────────────────────────────────
@Composable
private fun WdHeaderBar(
    workspace: WorkspaceModel,
    members: List<WorkspaceMemberModel>,
    showMenu: Boolean,
    onMenuToggle: () -> Unit,
    onMenuDismiss: () -> Unit,
    onBack: () -> Unit,
    onInvite: () -> Unit = {},
    onEditWorkspace: () -> Unit = {}
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(horizontal = 40.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Back button
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(WdGray, CircleShape)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center
            ) {
                Text("←", fontSize = 16.sp, color = WdTextMuted)
            }
            Spacer(Modifier.width(12.dp))
            // Blue check-circle workspace indicator
            Box(
                modifier = Modifier.size(36.dp).background(WdBlue, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text("✓", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(12.dp))
            // Title + chip + description
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        workspace.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = WdTextPri,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.width(8.dp))
                    WdVisibilityChip(workspace.visibility)
                }
                Text(
                    workspace.description ?: "No description",
                    color = WdTextMuted,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(16.dp))
            // Right side controls
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                WdAvatarGroup(members)
                // Invite members button
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(WdGray, CircleShape)
                        .clickable(onClick = onInvite),
                    contentAlignment = Alignment.Center
                ) {
                    Text("+", fontSize = 20.sp, color = WdTextMuted, fontWeight = FontWeight.Bold)
                }
                // Three-dot menu
                Box {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(WdGray, CircleShape)
                            .clickable(onClick = onMenuToggle),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("⋮", fontSize = 18.sp, color = WdTextMuted)
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = onMenuDismiss) {
                        DropdownMenuItem(
                            text    = { Text("✏  Edit Workspace") },
                            onClick = { onMenuDismiss(); onEditWorkspace() }
                        )
                        DropdownMenuItem(
                            text    = { Text("↗  Share / Invite") },
                            onClick = { onMenuDismiss(); onInvite() }
                        )
                    }
                }
            }
        }
        HorizontalDivider(color = WdBorder)
    }
}

@Composable
private fun WdVisibilityChip(visibility: String) {
    val isPublic = visibility == "PUBLIC"
    Row(
        modifier = Modifier
            .background(if (isPublic) WdSkyBg else WdGray, RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(if (isPublic) "🌐 " else "🔒 ", fontSize = 11.sp)
        Text(
            if (isPublic) "Public" else "Private",
            color = if (isPublic) WdSkyFg else Color(0xFF475569),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun WdAvatarGroup(members: List<WorkspaceMemberModel>) {
    val visible = members.take(5)
    val extra   = members.size - visible.size
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        visible.forEach { m ->
            WdAvatar34(wdMemberInitials(m.username), wdMemberColor(m.userId))
        }
        if (extra > 0) WdAvatar34("+$extra", WdRed)
    }
}

@Composable
private fun WdAvatar34(initials: String, color: Color) {
    Box(
        modifier = Modifier.size(34.dp).background(color, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(initials, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

// ── Toolbar ───────────────────────────────────────────────────────────────────
@Composable
private fun WdToolbar(
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    priorityFilter: String,
    onPriorityChange: (String) -> Unit,
    sortBy: String,
    onSortChange: (String) -> Unit,
    isOwner: Boolean = false,
    onCreateTask: () -> Unit = {}
) {
    var priorityExpanded by remember { mutableStateOf(false) }
    var sortExpanded     by remember { mutableStateOf(false) }

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(horizontal = 40.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchChange,
                modifier = Modifier.width(320.dp),
                placeholder = { Text("Search tasks...") },
                singleLine = true,
                shape = RoundedCornerShape(8.dp),
                leadingIcon = { Text("🔍", fontSize = 16.sp) }
            )

            Box {
                OutlinedButton(
                    onClick  = { priorityExpanded = true },
                    modifier = Modifier.width(160.dp),
                    colors   = ButtonDefaults.outlinedButtonColors(contentColor = WdTextMuted),
                    shape    = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        if (priorityFilter == "All") "All Priorities" else priorityFilter,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                DropdownMenu(
                    expanded = priorityExpanded,
                    onDismissRequest = { priorityExpanded = false }
                ) {
                    listOf("All", "High", "Medium", "Low").forEach { p ->
                        DropdownMenuItem(
                            text    = { Text(if (p == "All") "All Priorities" else p) },
                            onClick = { onPriorityChange(p); priorityExpanded = false }
                        )
                    }
                }
            }

            Box {
                OutlinedButton(
                    onClick  = { sortExpanded = true },
                    modifier = Modifier.width(160.dp),
                    colors   = ButtonDefaults.outlinedButtonColors(contentColor = WdTextMuted),
                    shape    = RoundedCornerShape(8.dp)
                ) {
                    Text("Sort: $sortBy", fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                DropdownMenu(
                    expanded = sortExpanded,
                    onDismissRequest = { sortExpanded = false }
                ) {
                    listOf("Priority", "Due Date", "Assignee").forEach { s ->
                        DropdownMenuItem(
                            text    = { Text(s) },
                            onClick = { onSortChange(s); sortExpanded = false }
                        )
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            if (isOwner) {
                Button(
                    onClick        = onCreateTask,
                    colors         = ButtonDefaults.buttonColors(containerColor = WdBlue),
                    shape          = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp)
                ) {
                    Text("+ Add Task", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
        HorizontalDivider(color = WdBorder)
    }
}

// ── Kanban Board ──────────────────────────────────────────────────────────────
@Composable
private fun WdKanbanBoard(
    tasks: List<TaskModel>,
    isLoading: Boolean,
    isOwner: Boolean = false,
    onTaskClick: (TaskModel) -> Unit = {},
    onEditTask: (TaskModel) -> Unit = {},
    onDeleteTask: (TaskModel) -> Unit = {},
    modifier: Modifier = Modifier
) {
    if (isLoading) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = WdBlue)
        }
        return
    }

    Box(modifier = modifier.verticalScroll(rememberScrollState())) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 40.dp, vertical = 28.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            WD_COLUMNS.forEach { col ->
                WdKanbanColumn(
                    col          = col,
                    tasks        = tasks.filter { it.status.uppercase() == col.status },
                    isOwner      = isOwner,
                    onTaskClick  = onTaskClick,
                    onEditTask   = onEditTask,
                    onDeleteTask = onDeleteTask,
                    modifier     = Modifier.weight(1f)
                )
            }
        }
    }
}

// ── Kanban Column ─────────────────────────────────────────────────────────────
@Composable
private fun WdKanbanColumn(
    col: WdCol,
    tasks: List<TaskModel>,
    isOwner: Boolean = false,
    onTaskClick: (TaskModel) -> Unit = {},
    onEditTask: (TaskModel) -> Unit = {},
    onDeleteTask: (TaskModel) -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        // Header row
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(bottom = 12.dp)
        ) {
            Box(modifier = Modifier.size(10.dp).background(col.color, CircleShape))
            Text(
                col.label,
                color       = WdTextMuted,
                fontSize    = 13.sp,
                fontWeight  = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
            Box(
                modifier = Modifier
                    .background(WdGray, RoundedCornerShape(10.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    tasks.size.toString(),
                    fontSize   = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color      = WdTextMuted
                )
            }
        }
        // Cards
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            tasks.forEach { task -> WdTaskCard(task, isOwner, onTaskClick, onEditTask, onDeleteTask) }
        }
    }
}

// ── Task Card ─────────────────────────────────────────────────────────────────
@Composable
private fun WdTaskCard(
    task: TaskModel,
    isOwner: Boolean = false,
    onTaskClick: (TaskModel) -> Unit = {},
    onEditTask: (TaskModel) -> Unit = {},
    onDeleteTask: (TaskModel) -> Unit = {}
) {
    val hoverSource = remember { MutableInteractionSource() }
    val isHovered   by hoverSource.collectIsHoveredAsState()
    var showMenu    by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .hoverable(hoverSource)
            .clickable { onTaskClick(task) }
            .then(if (isHovered) Modifier.offset(y = (-2).dp) else Modifier),
        shape     = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isHovered) 6.dp else 2.dp
        ),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            // Row 1: title + three-dot menu
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    task.title,
                    fontWeight  = FontWeight.SemiBold,
                    fontSize    = 15.sp,
                    color       = WdTextPri,
                    modifier    = Modifier.weight(1f),
                    maxLines    = 2,
                    overflow    = TextOverflow.Ellipsis
                )
                Box {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clickable { showMenu = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("⋮", fontSize = 14.sp, color = WdTextMuted)
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text    = { Text("View details") },
                            onClick = { showMenu = false; onTaskClick(task) }
                        )
                        if (isOwner) {
                            DropdownMenuItem(
                                text    = { Text("Edit") },
                                onClick = { showMenu = false; onEditTask(task) }
                            )
                            DropdownMenuItem(
                                text    = { Text("Delete", color = WdRed) },
                                onClick = { showMenu = false; onDeleteTask(task) }
                            )
                        }
                    }
                }
            }
            // Row 2: description
            Spacer(Modifier.height(4.dp))
            Text(
                task.description ?: "No description",
                color      = WdTextMuted,
                fontSize   = 13.sp,
                lineHeight = 19.5.sp,
                maxLines   = 1,
                overflow   = TextOverflow.Ellipsis,
                modifier   = Modifier.padding(bottom = 12.dp)
            )
            // Row 3: priority chip
            WdPriorityChip(task.priority)
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = WdBorder)
            // Row 4: footer
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.size(28.dp).background(WdPurple, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        taskInitials(task).take(2),
                        color      = Color.White,
                        fontSize   = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun WdPriorityChip(priority: String) {
    val color = wdPriorityColor(priority)
    Box(
        modifier = Modifier
            .background(color.copy(alpha = 0.09f), RoundedCornerShape(11.dp))
            .padding(horizontal = 10.dp, vertical = 3.dp)
    ) {
        Text(
            priority.lowercase().replaceFirstChar { it.uppercaseChar() },
            color      = color,
            fontSize   = 11.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

// ── Side Panel ────────────────────────────────────────────────────────────────
@Composable
private fun WdSidePanel(
    workspace: WorkspaceModel,
    filteredTasks: List<TaskModel>,
    stats: WorkspaceStats?,
    members: List<WorkspaceMemberModel>,
    isOwner: Boolean = false,
    currentUserId: Int = 0,
    onRemoveMember: (Int) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val doneCount   = filteredTasks.count { it.status.uppercase() == "DONE" }
    val totalCount  = filteredTasks.size
    val progress    = if (totalCount > 0) doneCount.toFloat() / totalCount else 0f
    val memberCount = if (members.isNotEmpty()) members.size else stats?.totalMembers ?: 0

    Column(
        modifier = modifier
            .background(Color.White)
            .verticalScroll(rememberScrollState())
            .padding(28.dp)
    ) {
        // 1. Heading
        Text(
            "Workspace Details",
            fontWeight = FontWeight.Bold,
            fontSize   = 18.sp,
            color      = WdTextPri
        )
        Spacer(Modifier.height(20.dp))
        HorizontalDivider(color = WdBorder)
        Spacer(Modifier.height(16.dp))

        // 2. Visibility
        Text(
            "VISIBILITY",
            fontSize      = 11.sp,
            fontWeight    = FontWeight.Bold,
            color         = WdTextMuted,
            letterSpacing = 0.8.sp
        )
        Spacer(Modifier.height(8.dp))
        Row(
            verticalAlignment     = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("🌐", fontSize = 16.sp)
            Text(
                if (workspace.visibility == "PUBLIC")
                    "Public - Anyone can view this workspace."
                else
                    "Private - Only members can view this workspace.",
                fontSize   = 14.sp,
                color      = WdTextPri,
                lineHeight = 20.sp
            )
        }
        Spacer(Modifier.height(16.dp))
        HorizontalDivider(color = WdBorder)
        Spacer(Modifier.height(16.dp))

        // 3. Team Members
        Text(
            "TEAM MEMBERS ($memberCount)",
            fontSize      = 11.sp,
            fontWeight    = FontWeight.Bold,
            color         = WdTextMuted,
            letterSpacing = 0.8.sp
        )
        Spacer(Modifier.height(12.dp))
        if (members.isEmpty()) {
            Text("Loading members...", fontSize = 14.sp, color = WdTextMuted)
        } else {
            members.forEach { m ->
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier              = Modifier.padding(vertical = 4.dp)
                ) {
                    Box(
                        modifier = Modifier.size(34.dp).background(wdMemberColor(m.userId), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            wdMemberInitials(m.username),
                            color      = Color.White,
                            fontSize   = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(m.username, fontSize = 14.sp, color = WdTextPri)
                        if (m.role == "OWNER") {
                            Text("Owner", fontSize = 11.sp, color = WdBlue)
                        }
                    }
                    if (isOwner && m.userId != currentUserId) {
                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .background(WdRed.copy(alpha = 0.08f), RoundedCornerShape(6.dp))
                                .clickable { onRemoveMember(m.userId) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("✕", fontSize = 12.sp, color = WdRed, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        HorizontalDivider(color = WdBorder)
        Spacer(Modifier.height(16.dp))

        // 4. Progress
        Text(
            "PROGRESS",
            fontSize      = 11.sp,
            fontWeight    = FontWeight.Bold,
            color         = WdTextMuted,
            letterSpacing = 0.8.sp
        )
        Spacer(Modifier.height(12.dp))
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Text("Tasks Completed", fontSize = 14.sp, color = WdTextPri)
            Text(
                "$doneCount / $totalCount",
                fontSize   = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color      = WdTextPri
            )
        }
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .background(Color(0xFFE2E8F0), RoundedCornerShape(5.dp))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction = progress.coerceIn(0f, 1f))
                    .background(WdGreen, RoundedCornerShape(5.dp))
            )
        }
        Spacer(Modifier.height(16.dp))
        HorizontalDivider(color = WdBorder)
        Spacer(Modifier.height(16.dp))

        // 5. Recent Activity
        Text(
            "RECENT ACTIVITY",
            fontSize      = 11.sp,
            fontWeight    = FontWeight.Bold,
            color         = WdTextMuted,
            letterSpacing = 0.8.sp
        )
        Spacer(Modifier.height(12.dp))
        val activity = wdBuildActivity(filteredTasks, members)
        if (activity.isEmpty()) {
            Text("No recent activity.", fontSize = 14.sp, color = WdTextMuted)
        } else {
            activity.forEach { entry ->
                Column(modifier = Modifier.padding(vertical = 6.dp)) {
                    Text(entry.text, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = WdTextPri, lineHeight = 20.sp)
                    Text(entry.time, fontSize = 12.sp, color = WdTextMuted)
                }
            }
        }
    }
}

// ── Invite Code Dialog ────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WdInviteCodeDialog(
    workspace: WorkspaceModel,
    isOwner: Boolean,
    workspaceRepo: WorkspaceRepository,
    onDismiss: () -> Unit
) {
    var inviteCode  by remember { mutableStateOf<String?>(null) }
    var expiresAt   by remember { mutableStateOf<Long?>(null) }
    var isLoading   by remember { mutableStateOf(false) }
    var error       by remember { mutableStateOf<String?>(null) }
    val scope       = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }

    fun loadCode(force: Boolean = false) {
        scope.launch {
            isLoading = true
            error = null
            val result = workspaceRepo.generateInviteCode(workspace.id, force)
            result.onSuccess {
                inviteCode = it.code
                expiresAt  = it.expiresAt
            }.onFailure {
                error = it.message ?: "Failed to generate invite code"
            }
            isLoading = false
        }
    }

    LaunchedEffect(workspace.id) {
        if (isOwner && workspace.visibility != "PUBLIC") loadCode()
    }

    fun minutesLeft(): Long {
        val exp = expiresAt ?: return 0L
        val nowSec = Clock.System.now().epochSeconds
        return maxOf(0L, (exp - nowSec) / 60L)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Invite Members", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when {
                    workspace.visibility == "PUBLIC" ->
                        Text(
                            "This workspace is public — anyone can find and join it from the workspace list.",
                            fontSize = 14.sp,
                            color    = WdTextMuted
                        )
                    isOwner && isLoading ->
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(Modifier.size(32.dp))
                        }
                    isOwner && error != null ->
                        Text(error!!, fontSize = 14.sp, color = MaterialTheme.colorScheme.error)
                    isOwner && inviteCode != null -> {
                        Text(
                            "Share this invite code to add members:",
                            fontSize = 14.sp,
                            color    = WdTextMuted
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(WdGray, RoundedCornerShape(8.dp))
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                inviteCode!!,
                                fontWeight = FontWeight.Bold,
                                fontSize   = 24.sp,
                                color      = WdTextPri
                            )
                        }
                        if (copied) {
                            Text("Copied!", fontSize = 12.sp, color = WdGreen)
                        }
                        Text(
                            "Expires in ${minutesLeft()} min",
                            fontSize = 12.sp,
                            color    = WdTextMuted
                        )
                    }
                    else ->
                        Text(
                            "Contact the workspace owner to receive an invite code.",
                            fontSize = 14.sp,
                            color    = WdTextMuted
                        )
                }
            }
        },
        confirmButton = {
            if (isOwner && inviteCode != null) {
                Button(
                    onClick = {
                        inviteCode?.let {
                            clipboardManager.setText(AnnotatedString(it))
                            copied = true
                            scope.launch {
                                kotlinx.coroutines.delay(2000L)
                                copied = false
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = WdBlue),
                    shape  = RoundedCornerShape(8.dp)
                ) {
                    Text("Copy")
                }
            } else {
                OutlinedButton(onClick = onDismiss, shape = RoundedCornerShape(8.dp)) {
                    Text("Close")
                }
            }
        },
        dismissButton = {
            Row {
                if (isOwner && workspace.visibility != "PUBLIC" && !isLoading) {
                    OutlinedButton(
                        onClick = { loadCode(force = true) },
                        shape   = RoundedCornerShape(8.dp),
                        modifier = Modifier.padding(end = 8.dp)
                    ) { Text("New Code") }
                }
                OutlinedButton(onClick = onDismiss, shape = RoundedCornerShape(8.dp)) {
                    Text("Close")
                }
            }
        }
    )
}

// ── Edit Workspace Dialog ─────────────────────────────────────────────────────
@Composable
private fun WdEditWorkspaceDialog(
    workspace: WorkspaceModel,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    var name by remember { mutableStateOf(workspace.name) }
    var description by remember { mutableStateOf(workspace.description ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Workspace", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Workspace Name") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    singleLine = true
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 80.dp),
                    shape = RoundedCornerShape(8.dp),
                    maxLines = 3
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(name, description) },
                colors = ButtonDefaults.buttonColors(containerColor = WdBlue),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Cancel")
            }
        }
    )
}
