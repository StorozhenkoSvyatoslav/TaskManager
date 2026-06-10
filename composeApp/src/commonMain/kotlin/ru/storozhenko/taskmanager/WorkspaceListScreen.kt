package ru.storozhenko.taskmanager

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
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
import ru.storozhenko.taskmanager.models.CreateWorkspaceRequest
import ru.storozhenko.taskmanager.models.WorkspaceModel

private val WlPageBg = Color(0xFFF8FAFC)
private val WlBorder = Color(0xFFE2E8F0)
private val WlBlue = Color(0xFF1976D2)
private val WlTextPrimary = Color(0xFF1E293B)
private val WlTextMuted = Color(0xFF64748B)
private val WlPublicBg = Color(0xFFE0F2FE)
private val WlPublicFg = Color(0xFF0369A1)
private val WlPrivateBg = Color(0xFFF1F5F9)
private val WlPrivateFg = Color(0xFF475569)

private val WlAvatarPalette = listOf(
    Color(0xFF8B5CF6), Color(0xFFEC4899), Color(0xFF3B82F6),
    Color(0xFF10B981), Color(0xFFF59E0B), Color(0xFFEF4444)
)

private fun wsAvatarColor(name: String) =
    WlAvatarPalette[name.hashCode().and(0x7FFFFFFF) % WlAvatarPalette.size]

@Composable
fun WorkspaceListScreen(
    token: String,
    onNavigateToWorkspace: (WorkspaceModel) -> Unit,
    onLogout: () -> Unit
) {
    val repo = remember(token) { WorkspaceRepository(token) }
    val scope = rememberCoroutineScope()

    var myWorkspaces by remember { mutableStateOf<List<WorkspaceModel>>(emptyList()) }
    var publicWorkspaces by remember { mutableStateOf<List<WorkspaceModel>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedTab by remember { mutableIntStateOf(0) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var showJoinDialog by remember { mutableStateOf(false) }
    var refreshKey by remember { mutableIntStateOf(0) }

    LaunchedEffect(refreshKey) {
        isLoading = true
        error = null
        val myResult = repo.getMyWorkspaces()
        val publicResult = repo.getPublicWorkspaces()
        myWorkspaces = myResult.getOrElse { emptyList() }
        publicWorkspaces = publicResult.getOrElse { emptyList() }
        if (myResult.isFailure) error = myResult.exceptionOrNull()?.message
        isLoading = false
    }

    val displayedWorkspaces = if (selectedTab == 0) myWorkspaces else publicWorkspaces
    val filteredWorkspaces = if (searchQuery.isBlank()) displayedWorkspaces
    else displayedWorkspaces.filter { it.name.contains(searchQuery, ignoreCase = true) }

    Box(
        modifier = Modifier.fillMaxSize().background(WlPageBg),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 1920.dp)
                .fillMaxSize()
        ) {
            WlHeaderBar(onLogout = onLogout)

            Surface(color = Color.White, modifier = Modifier.fillMaxWidth()) {
                Column {
                    PrimaryTabRow(
                        selectedTabIndex = selectedTab,
                        containerColor = Color.White,
                        contentColor = WlBlue
                    ) {
                        listOf(
                            "My Workspaces (${myWorkspaces.size})",
                            "Public (${publicWorkspaces.size})"
                        ).forEachIndexed { index, title ->
                            Tab(
                                selected = selectedTab == index,
                                onClick = { selectedTab = index },
                                text = { Text(title) }
                            )
                        }
                    }
                    HorizontalDivider(color = WlBorder)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 40.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier = Modifier.width(280.dp),
                            placeholder = { Text("Search workspaces...") },
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp)
                        )
                        Spacer(Modifier.weight(1f))
                        if (selectedTab == 1) {
                            OutlinedButton(
                                onClick = { showJoinDialog = true },
                                modifier = Modifier,
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = WlBlue),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Join Private")
                            }
                        }
                        Button(
                            onClick = { showCreateDialog = true },
                            modifier = Modifier,
                            colors = ButtonDefaults.buttonColors(containerColor = WlBlue),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp)
                        ) {
                            Text("+ Create Workspace")
                        }
                    }
                }
            }

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when {
                    isLoading -> Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = WlBlue)
                    }
                    error != null -> Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(error!!, color = Color(0xFFEF4444), fontSize = 15.sp)
                            Button(
                                onClick = { refreshKey++ },
                                colors = ButtonDefaults.buttonColors(containerColor = WlBlue),
                                shape = RoundedCornerShape(8.dp)
                            ) { Text("Retry") }
                        }
                    }
                    filteredWorkspaces.isEmpty() -> Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .background(Color(0xFFE2E8F0), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("?", fontSize = 32.sp, color = Color(0xFF94A3B8), fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                if (searchQuery.isBlank()) "No workspaces yet" else "No workspaces found",
                                color = WlTextMuted,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium
                            )
                            if (searchQuery.isBlank() && selectedTab == 0) {
                                Text(
                                    "Create your first workspace to get started",
                                    color = WlTextMuted,
                                    fontSize = 13.sp
                                )
                                Spacer(Modifier.height(8.dp))
                                Button(
                                    onClick = { showCreateDialog = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = WlBlue),
                                    shape = RoundedCornerShape(8.dp)
                                ) { Text("Create Workspace") }
                            }
                        }
                    }
                    else -> LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 320.dp),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 40.dp, vertical = 28.dp),
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                        verticalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        items(filteredWorkspaces, key = { it.id }) { workspace ->
                            WsCard(
                                workspace = workspace,
                                onClick = { onNavigateToWorkspace(workspace) }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        WlCreateDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { req ->
                scope.launch {
                    showCreateDialog = false
                    repo.createWorkspace(req).onSuccess { refreshKey++ }
                }
            }
        )
    }

    if (showJoinDialog) {
        WlJoinDialog(
            onDismiss = { showJoinDialog = false },
            onJoin = { code ->
                scope.launch {
                    showJoinDialog = false
                    repo.joinWorkspace(code).onSuccess {
                        selectedTab = 0
                        refreshKey++
                    }
                }
            }
        )
    }
}

@Composable
private fun WlHeaderBar(onLogout: () -> Unit) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(horizontal = 40.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(WlBlue, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text("✓", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(10.dp))
            Text(
                "Task Manager",
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = WlTextPrimary
            )
            Spacer(Modifier.weight(1f))
            OutlinedButton(
                onClick = onLogout,
                modifier = Modifier,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = WlTextMuted),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Sign out", fontSize = 13.sp)
            }
        }
        HorizontalDivider(color = WlBorder)
    }
}

@Composable
private fun WsCard(workspace: WorkspaceModel, onClick: () -> Unit) {
    val hoverSource = remember { MutableInteractionSource() }
    val isHovered by hoverSource.collectIsHoveredAsState()
    val elevation = if (isHovered) 8.dp else 2.dp

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .hoverable(hoverSource)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = elevation),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(wsAvatarColor(workspace.name), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = workspace.name.take(2).uppercase(),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    text = workspace.name,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    color = WlTextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                WsVisibilityChip(workspace.visibility)
            }

            Spacer(Modifier.height(10.dp))

            Text(
                text = workspace.description ?: "No description",
                color = if (workspace.description != null) WlTextMuted else Color(0xFFCBD5E1),
                fontSize = 13.sp,
                lineHeight = 20.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = WlBorder)
            Spacer(Modifier.height(10.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Button(
                    onClick = onClick,
                    modifier = Modifier,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = WlBlue.copy(alpha = 0.08f),
                        contentColor = WlBlue
                    ),
                    elevation = ButtonDefaults.buttonElevation(0.dp),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text("Open workspace →", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
private fun WsVisibilityChip(visibility: String) {
    val isPublic = visibility == "PUBLIC"
    val bg = if (isPublic) WlPublicBg else WlPrivateBg
    val fg = if (isPublic) WlPublicFg else WlPrivateFg

    Row(
        modifier = Modifier
            .background(bg, RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (!isPublic) {
            Text("🔒", fontSize = 10.sp, modifier = Modifier.padding(end = 3.dp))
        }
        Text(
            if (isPublic) "Public" else "Private",
            color = fg,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun WlCreateDialog(
    onDismiss: () -> Unit,
    onCreate: (CreateWorkspaceRequest) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var isPublic by remember { mutableStateOf(true) }
    var inviteCode by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Workspace", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Visibility", fontWeight = FontWeight.Medium, fontSize = 14.sp, color = WlTextMuted)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Public" to true, "Private" to false).forEach { (label, value) ->
                        FilterChip(
                            selected = isPublic == value,
                            onClick = { isPublic = value },
                            label = { Text(label) },
                            modifier = Modifier
                        )
                    }
                }
                if (!isPublic) {
                    OutlinedTextField(
                        value = inviteCode,
                        onValueChange = { inviteCode = it },
                        label = { Text("Invite Code") },
                        placeholder = { Text("e.g. my-secret-code") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        onCreate(
                            CreateWorkspaceRequest(
                                name = name.trim(),
                                description = description.trim().ifBlank { null },
                                visibility = if (isPublic) "PUBLIC" else "PRIVATE",
                                inviteCode = if (!isPublic && inviteCode.isNotBlank()) inviteCode.trim() else null
                            )
                        )
                    }
                },
                enabled = name.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = WlBlue),
                shape = RoundedCornerShape(8.dp)
            ) { Text("Create") }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = WlTextMuted),
                shape = RoundedCornerShape(8.dp)
            ) { Text("Cancel") }
        }
    )
}

@Composable
private fun WlJoinDialog(
    onDismiss: () -> Unit,
    onJoin: (String) -> Unit
) {
    var inviteCode by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Join Private Workspace", fontWeight = FontWeight.Bold) },
        text = {
            OutlinedTextField(
                value = inviteCode,
                onValueChange = { inviteCode = it },
                label = { Text("Invite Code *") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = { if (inviteCode.isNotBlank()) onJoin(inviteCode.trim()) },
                enabled = inviteCode.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = WlBlue),
                shape = RoundedCornerShape(8.dp)
            ) { Text("Join") }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = WlTextMuted),
                shape = RoundedCornerShape(8.dp)
            ) { Text("Cancel") }
        }
    )
}