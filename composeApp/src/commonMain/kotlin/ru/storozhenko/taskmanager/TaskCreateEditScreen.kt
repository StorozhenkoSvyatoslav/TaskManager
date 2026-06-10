package ru.storozhenko.taskmanager

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.*
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import ru.storozhenko.taskmanager.models.CreateTaskRequest
import ru.storozhenko.taskmanager.models.TaskModel
import ru.storozhenko.taskmanager.models.WorkspaceModel

// ── Colors ────────────────────────────────────────────────────────────────────
private val TcPageBg    = Color(0xFFF8FAFC)
private val TcBorder    = Color(0xFFE2E8F0)
private val TcBlue      = Color(0xFF1976D2)
private val TcTextPri   = Color(0xFF1E293B)
private val TcTextMuted = Color(0xFF64748B)
private val TcGray      = Color(0xFFF1F5F9)
private val TcGreen     = Color(0xFF10B981)
private val TcAmber     = Color(0xFFF59E0B)
private val TcRed       = Color(0xFFEF4444)

// ── Mock assignees ─────────────────────────────────────────────────────────────
private data class TcMember(val initials: String, val name: String, val color: Color)

private val TC_MEMBERS = listOf(
    TcMember("SC", "Sarah Chen",   Color(0xFF8B5CF6)),
    TcMember("MJ", "Mike Johnson", Color(0xFFEC4899)),
    TcMember("AK", "Alex Kim",     Color(0xFFF59E0B)),
    TcMember("JL", "Jordan Lee",   Color(0xFF10B981)),
    TcMember("TS", "Taylor Smith", Color(0xFF3B82F6)),
)

// ── Local data models ─────────────────────────────────────────────────────────
private data class TcChecklistItem(val id: Int, val text: String, val checked: Boolean)
private data class TcAttachment(val name: String, val sizeKb: Int)

// ── Status helpers ────────────────────────────────────────────────────────────
private val TC_STATUSES = listOf("TODO", "IN_PROGRESS", "REVIEW", "DONE")
private fun tcStatusLabel(s: String) = when (s) {
    "TODO"        -> "To Do"
    "IN_PROGRESS" -> "In Progress"
    "REVIEW"      -> "Review"
    "DONE"        -> "Done"
    else          -> s
}

private val MOCK_ATTACHMENTS = listOf(
    "document.pdf" to 245,
    "screenshot.png" to 89,
    "report.xlsx" to 512,
    "design.fig" to 1024,
)

// ── Screen ────────────────────────────────────────────────────────────────────
@Composable
fun TaskCreateEditScreen(
    workspace: WorkspaceModel,
    token: String,
    onBack: () -> Unit,
    onTaskCreated: () -> Unit,
    existingTask: TaskModel? = null
) {
    val repo       = remember(token) { TaskRepository(token) }
    val scope      = rememberCoroutineScope()
    val isEditMode = existingTask != null

    // Form state — prefilled from existingTask when editing
    var title           by remember { mutableStateOf(existingTask?.title ?: "") }
    var description     by remember { mutableStateOf(existingTask?.description ?: "") }
    var status          by remember { mutableStateOf(existingTask?.status ?: "TODO") }
    var dueDate         by remember { mutableStateOf("") }
    var priority        by remember { mutableStateOf(existingTask?.priority ?: "MEDIUM") }
    var assigneeIdx     by remember { mutableStateOf<Int?>(null) }

    // Checklist state
    var checklistInput  by remember { mutableStateOf("") }
    var checklistItems  by remember { mutableStateOf<List<TcChecklistItem>>(emptyList()) }
    var nextItemId      by remember { mutableStateOf(0) }

    // Attachments (mock — real file pick is platform-specific)
    var attachments     by remember { mutableStateOf<List<TcAttachment>>(emptyList()) }
    var nextAttachIdx   by remember { mutableStateOf(0) }

    // Async state
    var isSaving        by remember { mutableStateOf(false) }
    var errorMsg        by remember { mutableStateOf<String?>(null) }

    fun reset() {
        title = ""; description = ""; status = "TODO"; dueDate = ""
        priority = "MEDIUM"; assigneeIdx = null
        checklistInput = ""; checklistItems = emptyList()
        attachments = emptyList(); errorMsg = null
    }

    fun addChecklistItem() {
        val t = checklistInput.trim()
        if (t.isNotEmpty()) {
            checklistItems = checklistItems + TcChecklistItem(nextItemId++, t, false)
            checklistInput = ""
        }
    }

    fun addMockAttachment() {
        val mock = MOCK_ATTACHMENTS[nextAttachIdx % MOCK_ATTACHMENTS.size]
        attachments = attachments + TcAttachment(mock.first, mock.second)
        nextAttachIdx++
    }

    fun saveTask() {
        if (title.isBlank()) { errorMsg = "Task title is required."; return }
        scope.launch {
            isSaving = true; errorMsg = null
            val req = CreateTaskRequest(
                title       = title.trim(),
                description = description.trim().ifBlank { null },
                status      = status,
                priority    = priority,
                workspaceId = workspace.id,
            )
            val result = if (existingTask != null) {
                repo.updateTask(existingTask.id, req)
            } else {
                repo.createTask(req)
            }
            result
                .onSuccess { onTaskCreated() }
                .onFailure { errorMsg = it.message ?: if (isEditMode) "Failed to update task." else "Failed to create task." }
            isSaving = false
        }
    }

    // ── Layout ────────────────────────────────────────────────────────────────
    Box(
        modifier = Modifier.fillMaxSize().background(TcPageBg),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(modifier = Modifier.widthIn(max = 1920.dp).fillMaxSize()) {
            TcTopBar(onBack = onBack, isEditMode = isEditMode)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Column(
                    modifier = Modifier.widthIn(max = 900.dp).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    // Page header
                    TcPageHeader(isEditMode = isEditMode)

                    // Form card
                    Card(
                        shape     = RoundedCornerShape(12.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                        colors    = CardDefaults.cardColors(containerColor = Color.White),
                        modifier  = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(32.dp),
                            verticalArrangement = Arrangement.spacedBy(20.dp)
                        ) {
                            // 1. Task Title
                            TcField("Task Title *") {
                                OutlinedTextField(
                                    value         = title,
                                    onValueChange = { title = it },
                                    modifier      = Modifier.fillMaxWidth(),
                                    placeholder   = { Text("Enter task title...") },
                                    singleLine    = true,
                                    shape         = RoundedCornerShape(8.dp),
                                    isError       = errorMsg != null && title.isBlank()
                                )
                            }

                            // 2. Description
                            TcField("Description") {
                                OutlinedTextField(
                                    value         = description,
                                    onValueChange = { description = it },
                                    modifier      = Modifier.fillMaxWidth(),
                                    placeholder   = { Text("Describe the task in detail...") },
                                    minLines      = 4,
                                    maxLines      = 8,
                                    shape         = RoundedCornerShape(8.dp)
                                )
                            }

                            // 3. Status & Due Date
                            Row(
                                modifier              = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                TcField("Status", modifier = Modifier.weight(1f)) {
                                    TcDropdown(
                                        value      = tcStatusLabel(status),
                                        options    = TC_STATUSES,
                                        onSelect   = { status = it },
                                        labelOf    = ::tcStatusLabel,
                                        modifier   = Modifier.fillMaxWidth()
                                    )
                                }
                                TcField("Due Date", modifier = Modifier.weight(1f)) {
                                    OutlinedTextField(
                                        value         = dueDate,
                                        onValueChange = { dueDate = it },
                                        modifier      = Modifier.fillMaxWidth(),
                                        placeholder   = { Text("MM/DD/YYYY") },
                                        singleLine    = true,
                                        shape         = RoundedCornerShape(8.dp),
                                        leadingIcon   = { Text("📅", fontSize = 16.sp) }
                                    )
                                }
                            }

                            // 4. Priority
                            TcField("Priority") {
                                TcPriorityGroup(selected = priority, onSelect = { priority = it })
                            }

                            // 5. Workspace & Assignee
                            Row(
                                modifier              = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                TcField("Workspace", modifier = Modifier.weight(1f)) {
                                    TcStaticField(workspace.name)
                                }
                                TcField("Assignee", modifier = Modifier.weight(1f)) {
                                    TcAssigneeDropdown(
                                        selectedIdx = assigneeIdx,
                                        onSelect    = { assigneeIdx = it },
                                        modifier    = Modifier.fillMaxWidth()
                                    )
                                }
                            }

                            HorizontalDivider(color = TcBorder)

                            // 6. Checklist
                            TcChecklistSection(
                                input       = checklistInput,
                                onInput     = { checklistInput = it },
                                items       = checklistItems,
                                onAdd       = ::addChecklistItem,
                                onToggle    = { id ->
                                    checklistItems = checklistItems.map {
                                        if (it.id == id) it.copy(checked = !it.checked) else it
                                    }
                                },
                                onDelete    = { id ->
                                    checklistItems = checklistItems.filter { it.id != id }
                                }
                            )

                            HorizontalDivider(color = TcBorder)

                            // 7. Attachments
                            TcAttachmentSection(
                                attachments   = attachments,
                                onUploadClick = ::addMockAttachment,
                                onRemove      = { idx ->
                                    attachments = attachments.toMutableList().also { it.removeAt(idx) }
                                }
                            )
                        }
                    }

                    // Error
                    if (errorMsg != null) {
                        Text(
                            errorMsg!!,
                            color    = TcRed,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }

                    // Action buttons
                    Row(
                        modifier              = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick         = { reset(); onBack() },
                            enabled         = !isSaving,
                            shape           = RoundedCornerShape(8.dp),
                            contentPadding  = PaddingValues(horizontal = 32.dp, vertical = 14.dp)
                        ) {
                            Text("Cancel", fontSize = 15.sp)
                        }
                        Spacer(Modifier.width(12.dp))
                        Button(
                            onClick        = ::saveTask,
                            enabled        = !isSaving,
                            shape          = RoundedCornerShape(8.dp),
                            colors         = ButtonDefaults.buttonColors(containerColor = TcBlue),
                            contentPadding = PaddingValues(horizontal = 32.dp, vertical = 14.dp)
                        ) {
                            if (isSaving) {
                                CircularProgressIndicator(
                                    modifier    = Modifier.size(18.dp),
                                    color       = Color.White,
                                    strokeWidth = 2.dp
                                )
                                Spacer(Modifier.width(8.dp))
                            } else {
                                Text("✓ ", fontSize = 15.sp)
                            }
                            Text(
                                if (isEditMode) "Update Task" else "Save Task",
                                fontSize = 15.sp, fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Top bar ───────────────────────────────────────────────────────────────────
@Composable
private fun TcTopBar(onBack: () -> Unit, isEditMode: Boolean = false) {
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
                    .size(32.dp)
                    .background(TcGray, CircleShape)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center
            ) {
                Text("←", fontSize = 16.sp, color = TcTextMuted)
            }
            Spacer(Modifier.width(12.dp))
            Text(
                if (isEditMode) "Edit Task" else "Create New Task",
                fontWeight = FontWeight.Bold,
                fontSize   = 20.sp,
                color      = TcTextPri
            )
        }
        HorizontalDivider(color = TcBorder)
    }
}

// ── Page header ───────────────────────────────────────────────────────────────
@Composable
private fun TcPageHeader(isEditMode: Boolean = false) {
    Row(
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(
            modifier         = Modifier.size(48.dp).background(TcBlue, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text("✎", color = Color.White, fontSize = 22.sp)
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                if (isEditMode) "Edit Task" else "Create New Task",
                fontSize   = 24.sp,
                fontWeight = FontWeight.Bold,
                color      = TcTextPri
            )
            Text(
                if (isEditMode) "Update the task details below."
                else "Fill in the details below to create a new task for your team.",
                fontSize = 14.sp,
                color    = TcTextMuted
            )
        }
    }
}

// ── Labeled field wrapper ─────────────────────────────────────────────────────
@Composable
private fun TcField(
    label: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TcTextPri)
        content()
    }
}

// ── Read-only field (e.g. pre-filled workspace) ───────────────────────────────
@Composable
private fun TcStaticField(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .border(1.dp, TcBorder, RoundedCornerShape(8.dp))
            .background(TcGray, RoundedCornerShape(8.dp))
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(text, fontSize = 15.sp, color = TcTextMuted)
    }
}

// ── Generic dropdown ──────────────────────────────────────────────────────────
@Composable
private fun TcDropdown(
    value: String,
    options: List<String>,
    onSelect: (String) -> Unit,
    labelOf: (String) -> String = { it },
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .border(1.dp, TcBorder, RoundedCornerShape(8.dp))
                .clickable { expanded = true }
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(
                modifier          = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(value, modifier = Modifier.weight(1f), fontSize = 15.sp, color = TcTextPri)
                Text("▼", fontSize = 11.sp, color = TcTextMuted)
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { opt ->
                DropdownMenuItem(
                    text    = { Text(labelOf(opt)) },
                    onClick = { onSelect(opt); expanded = false }
                )
            }
        }
    }
}

// ── Priority radio group ──────────────────────────────────────────────────────
@Composable
private fun TcPriorityGroup(selected: String, onSelect: (String) -> Unit) {
    val options = listOf(
        Triple("LOW",    TcGreen, "Low"),
        Triple("MEDIUM", TcAmber, "Medium"),
        Triple("HIGH",   TcRed,   "High"),
    )
    Row(
        modifier              = Modifier.selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { (value, color, label) ->
            val isSelected = selected == value
            Row(
                modifier = Modifier
                    .selectable(
                        selected = isSelected,
                        onClick  = { onSelect(value) },
                        role     = Role.RadioButton
                    )
                    .border(
                        width  = 1.dp,
                        color  = if (isSelected) color else TcBorder,
                        shape  = RoundedCornerShape(8.dp)
                    )
                    .background(
                        color = if (isSelected) color.copy(alpha = 0.08f) else Color.Transparent,
                        shape = RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier.size(12.dp).background(color, CircleShape)
                )
                Text(
                    label,
                    fontSize   = 14.sp,
                    color      = if (isSelected) color else TcTextPri,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                )
            }
        }
    }
}

// ── Assignee dropdown with avatars ────────────────────────────────────────────
@Composable
private fun TcAssigneeDropdown(
    selectedIdx: Int?,
    onSelect: (Int?) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = selectedIdx?.let { TC_MEMBERS[it] }
    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .border(1.dp, TcBorder, RoundedCornerShape(8.dp))
                .clickable { expanded = true }
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(
                modifier              = Modifier.fillMaxWidth(),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (selected != null) {
                    Box(
                        modifier         = Modifier.size(28.dp).background(selected.color, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(selected.initials, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                    Text(selected.name, modifier = Modifier.weight(1f), fontSize = 15.sp, color = TcTextPri)
                } else {
                    Text(
                        "Select assignee...",
                        modifier = Modifier.weight(1f),
                        fontSize = 15.sp,
                        color    = TcTextMuted
                    )
                }
                Text("▼", fontSize = 11.sp, color = TcTextMuted)
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text    = { Text("None", color = TcTextMuted) },
                onClick = { onSelect(null); expanded = false }
            )
            TC_MEMBERS.forEachIndexed { idx, member ->
                DropdownMenuItem(
                    text = {
                        Row(
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier         = Modifier.size(28.dp).background(member.color, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(member.initials, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                            Text(member.name, fontSize = 14.sp)
                        }
                    },
                    onClick = { onSelect(idx); expanded = false }
                )
            }
        }
    }
}

// ── Checklist section ─────────────────────────────────────────────────────────
@Composable
private fun TcChecklistSection(
    input: String,
    onInput: (String) -> Unit,
    items: List<TcChecklistItem>,
    onAdd: () -> Unit,
    onToggle: (Int) -> Unit,
    onDelete: (Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Checklist", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TcTextPri)
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment     = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value         = input,
                onValueChange = onInput,
                modifier      = Modifier
                    .weight(1f)
                    .onKeyEvent { evt ->
                        if (evt.key == Key.Enter && evt.type == KeyEventType.KeyDown) {
                            onAdd(); true
                        } else false
                    },
                placeholder   = { Text("Add a checklist item...") },
                singleLine    = true,
                shape         = RoundedCornerShape(8.dp)
            )
            OutlinedButton(
                onClick        = onAdd,
                shape          = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Text("+ Add Item", fontSize = 14.sp)
            }
        }
        if (items.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, TcBorder, RoundedCornerShape(8.dp))
            ) {
                items.forEachIndexed { idx, item ->
                    if (idx > 0) HorizontalDivider(color = TcBorder)
                    Row(
                        modifier              = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Checkbox(
                            checked         = item.checked,
                            onCheckedChange = { onToggle(item.id) }
                        )
                        Text(
                            item.text,
                            modifier        = Modifier.weight(1f),
                            fontSize        = 14.sp,
                            color           = if (item.checked) TcTextMuted else TcTextPri,
                            textDecoration  = if (item.checked) TextDecoration.LineThrough else TextDecoration.None
                        )
                        Box(
                            modifier         = Modifier.size(28.dp).clickable { onDelete(item.id) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("🗑", fontSize = 14.sp)
                        }
                    }
                }
            }
        }
    }
}

// ── Attachments section ───────────────────────────────────────────────────────
@Composable
private fun TcAttachmentSection(
    attachments: List<TcAttachment>,
    onUploadClick: () -> Unit,
    onRemove: (Int) -> Unit
) {
    val dashColor = TcBorder
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Attachments", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TcTextPri)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
                .background(TcGray, RoundedCornerShape(8.dp))
                .drawBehind {
                    drawRoundRect(
                        color       = dashColor,
                        cornerRadius = CornerRadius(8.dp.toPx()),
                        style       = Stroke(
                            width      = 1.5.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx()))
                        )
                    )
                }
                .clickable { onUploadClick() },
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment   = Alignment.CenterHorizontally,
                verticalArrangement   = Arrangement.spacedBy(4.dp)
            ) {
                Text("☁", fontSize = 24.sp, color = TcTextMuted)
                Text("Click to upload files", fontSize = 14.sp, color = TcTextMuted)
            }
        }
        if (attachments.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, TcBorder, RoundedCornerShape(8.dp))
            ) {
                attachments.forEachIndexed { idx, file ->
                    if (idx > 0) HorizontalDivider(color = TcBorder)
                    Row(
                        modifier              = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("📎", fontSize = 18.sp)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(file.name, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TcTextPri)
                            Text("${file.sizeKb} KB", fontSize = 12.sp, color = TcTextMuted)
                        }
                        Box(
                            modifier         = Modifier.size(28.dp).clickable { onRemove(idx) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("✕", fontSize = 14.sp, color = TcTextMuted)
                        }
                    }
                }
            }
        }
    }
}
