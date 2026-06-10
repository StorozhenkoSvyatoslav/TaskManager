package ru.storozhenko.taskmanager

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import ru.storozhenko.taskmanager.models.AttachmentModel
import ru.storozhenko.taskmanager.models.CommentModel
import ru.storozhenko.taskmanager.models.TaskDetailModel
import ru.storozhenko.taskmanager.models.TaskModel
import ru.storozhenko.taskmanager.models.WorkspaceModel

// ── Colors ────────────────────────────────────────────────────────────────────
private val TdPageBg    = Color(0xFFF8FAFC)
private val TdBorder    = Color(0xFFE2E8F0)
private val TdBlue      = Color(0xFF1976D2)
private val TdTextPri   = Color(0xFF1E293B)
private val TdTextMuted = Color(0xFF64748B)
private val TdGray      = Color(0xFFF1F5F9)
private val TdPurple    = Color(0xFF8B5CF6)
private val TdAmber     = Color(0xFFF59E0B)
private val TdGreen     = Color(0xFF10B981)
private val TdRed       = Color(0xFFEF4444)
private val TdBlueStatus = Color(0xFF3B82F6)

private val TdCommentColors = listOf(
    Color(0xFF8B5CF6), Color(0xFFEC4899), Color(0xFFF59E0B),
    Color(0xFF10B981), Color(0xFF3B82F6), Color(0xFFEF4444),
)

// ── Helpers ───────────────────────────────────────────────────────────────────
private fun formatEpoch(epochSeconds: Long): String {
    val days = epochSeconds / 86400L
    val z = days + 719468L
    val era = (if (z >= 0) z else z - 146096L) / 146097L
    val doe = (z - era * 146097L).toInt()
    val yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365
    val y = yoe.toLong() + era * 400L
    val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
    val mp = (5 * doy + 2) / 153
    val d = doy - (153 * mp + 2) / 5 + 1
    val m = if (mp < 10) mp + 3 else mp - 9
    val year = if (m <= 2) y + 1L else y
    val months = arrayOf("Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec")
    return "${months[m - 1]} $d, $year"
}

private fun formatFileSize(bytes: Long): String = when {
    bytes < 1024L        -> "${bytes}B"
    bytes < 1024L * 1024 -> "${bytes / 1024L}KB"
    else                 -> "${bytes / (1024L * 1024L)}MB"
}

private fun tdStatusColor(status: String) = when (status.uppercase()) {
    "TODO"        -> TdTextMuted
    "IN_PROGRESS" -> TdBlueStatus
    "REVIEW"      -> TdAmber
    "DONE"        -> TdGreen
    else          -> TdTextMuted
}

private fun tdStatusLabel(status: String) = when (status.uppercase()) {
    "TODO"        -> "To Do"
    "IN_PROGRESS" -> "In Progress"
    "REVIEW"      -> "Review"
    "DONE"        -> "Done"
    else          -> status
}

private fun tdPriorityColor(priority: String) = when (priority.uppercase()) {
    "HIGH"   -> TdRed
    "MEDIUM" -> TdAmber
    else     -> TdGreen
}

private fun commentInitials(username: String): String =
    username.split(" ", "_", ".")
        .filter { it.isNotBlank() }
        .take(2)
        .joinToString("") { it[0].uppercaseChar().toString() }
        .ifEmpty { username.take(1).uppercase() }

private fun authorColor(authorId: Int): Color =
    TdCommentColors[authorId % TdCommentColors.size]

private fun isImageMime(mimeType: String?): Boolean =
    mimeType?.startsWith("image/") == true

private fun isImageByName(name: String): Boolean =
    name.lowercase().let { n -> n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png") || n.endsWith(".gif") || n.endsWith(".webp") || n.endsWith(".bmp") }

// ── Screen ────────────────────────────────────────────────────────────────────
@Composable
fun TaskDetailScreen(
    task: TaskModel,
    workspace: WorkspaceModel,
    token: String,
    onBack: () -> Unit
) {
    val repo  = remember(token) { TaskRepository(token) }
    val scope = rememberCoroutineScope()

    var detail          by remember { mutableStateOf<TaskDetailModel?>(null) }
    var comments        by remember { mutableStateOf<List<CommentModel>>(emptyList()) }
    var isLoading       by remember { mutableStateOf(true) }
    var isUploading     by remember { mutableStateOf(false) }
    var detailRefreshKey by remember { mutableStateOf(0) }
    var commentRefreshKey by remember { mutableStateOf(0) }
    var commentText     by remember { mutableStateOf("") }
    var isSending       by remember { mutableStateOf(false) }
    var deletingIds     by remember { mutableStateOf<Set<Int>>(emptySet()) }

    // Load / reload task detail (attachments included)
    LaunchedEffect(task.id, detailRefreshKey) {
        if (detailRefreshKey == 0) isLoading = true
        detail    = repo.getTask(task.id).getOrNull()
        isLoading = false
    }

    LaunchedEffect(task.id, commentRefreshKey) {
        comments = repo.getComments(task.id).getOrElse { emptyList() }
    }

    val onSend: () -> Unit = {
        val text = commentText.trim()
        if (text.isNotEmpty() && !isSending) {
            scope.launch {
                isSending = true
                repo.postComment(task.id, text).onSuccess {
                    commentText = ""
                    commentRefreshKey++
                }
                isSending = false
            }
        }
    }

    val onDeleteComment: (Int) -> Unit = { commentId ->
        if (commentId !in deletingIds) {
            scope.launch {
                deletingIds = deletingIds + commentId
                repo.deleteComment(task.id, commentId).onSuccess { commentRefreshKey++ }
                deletingIds = deletingIds - commentId
            }
        }
    }

    val onUploadAttachment: () -> Unit = {
        scope.launch {
            val file = pickFile() ?: return@launch
            isUploading = true
            repo.uploadAttachment(task.id, file).onSuccess { detailRefreshKey++ }
            isUploading = false
        }
    }

    val displayTask = detail?.let {
        task.copy(
            title       = it.title,
            description = it.description,
            status      = it.status,
            priority    = it.priority,
            updatedAt   = it.updatedAt,
        )
    } ?: task

    Box(
        modifier = Modifier.fillMaxSize().background(TdPageBg),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(modifier = Modifier.widthIn(max = 1920.dp).fillMaxSize()) {
            TdHeaderBar(task = displayTask, onBack = onBack)
            if (isLoading) {
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = TdBlue)
                }
            } else {
                Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    TdMainPanel(
                        task            = displayTask,
                        detail          = detail,
                        workspace       = workspace,
                        repo            = repo,
                        isUploading     = isUploading,
                        onUploadClick   = onUploadAttachment,
                        modifier        = Modifier.weight(1f).fillMaxHeight()
                    )
                    Box(Modifier.width(1.dp).fillMaxHeight().background(TdBorder))
                    TdCommentPanel(
                        comments         = comments,
                        deletingIds      = deletingIds,
                        commentText      = commentText,
                        onTextChange     = { commentText = it },
                        isSending        = isSending,
                        onSend           = onSend,
                        onDeleteComment  = onDeleteComment,
                        modifier         = Modifier.width(380.dp).fillMaxHeight()
                    )
                }
            }
        }
    }
}

// ── Header ────────────────────────────────────────────────────────────────────
@Composable
private fun TdHeaderBar(task: TaskModel, onBack: () -> Unit) {
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
                    .background(Color(0xFFF1F5F9), CircleShape)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center
            ) {
                Text("←", fontSize = 16.sp, color = TdTextMuted)
            }
            Spacer(Modifier.width(12.dp))
            Box(
                modifier = Modifier.size(36.dp).background(TdPurple, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text("T", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(12.dp))
            Text(
                task.title,
                fontWeight = FontWeight.Bold,
                fontSize   = 20.sp,
                color      = TdTextPri,
                maxLines   = 2,
                overflow   = TextOverflow.Ellipsis,
                modifier   = Modifier.weight(1f)
            )
            Spacer(Modifier.width(16.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TdStatusChip(task.status)
                TdPriorityChip(task.priority)
            }
        }
        HorizontalDivider(color = TdBorder)
    }
}

@Composable
private fun TdStatusChip(status: String) {
    val color = tdStatusColor(status)
    Box(
        modifier = Modifier
            .background(color.copy(alpha = 0.1f), RoundedCornerShape(6.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(tdStatusLabel(status), color = color, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun TdPriorityChip(priority: String) {
    val color = tdPriorityColor(priority)
    Box(
        modifier = Modifier
            .background(color.copy(alpha = 0.09f), RoundedCornerShape(11.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            priority.lowercase().replaceFirstChar { it.uppercaseChar() },
            color      = color,
            fontSize   = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

// ── Main panel (left) ─────────────────────────────────────────────────────────
@Composable
private fun TdMainPanel(
    task: TaskModel,
    detail: TaskDetailModel?,
    workspace: WorkspaceModel,
    repo: TaskRepository,
    isUploading: Boolean,
    onUploadClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 40.dp, vertical = 32.dp)
    ) {
        TdSectionLabel("DESCRIPTION")
        Spacer(Modifier.height(10.dp))
        val desc = task.description
        if (desc.isNullOrBlank()) {
            Text("No description provided.", fontSize = 15.sp, color = TdTextMuted)
        } else {
            Text(desc, fontSize = 15.sp, color = TdTextPri, lineHeight = 24.sp)
        }

        Spacer(Modifier.height(28.dp))
        HorizontalDivider(color = TdBorder)
        Spacer(Modifier.height(20.dp))

        TdSectionLabel("DETAILS")
        Spacer(Modifier.height(12.dp))
        TdMetaRow("Status",    tdStatusLabel(task.status))
        TdMetaRow("Priority",  task.priority.lowercase().replaceFirstChar { it.uppercaseChar() })
        TdMetaRow("Workspace", workspace.name)
        TdMetaRow("Created",   formatEpoch(task.createdAt))
        TdMetaRow("Updated",   formatEpoch(task.updatedAt))

        Spacer(Modifier.height(28.dp))
        HorizontalDivider(color = TdBorder)
        Spacer(Modifier.height(20.dp))

        // Attachments header with upload button
        Row(
            modifier          = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TdSectionLabel("ATTACHMENTS (${detail?.attachments?.size ?: 0})")
            Spacer(Modifier.weight(1f))
            if (isUploading) {
                CircularProgressIndicator(
                    modifier    = Modifier.size(20.dp),
                    color       = TdBlue,
                    strokeWidth = 2.dp
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .background(TdBlue, CircleShape)
                        .clickable(onClick = onUploadClick),
                    contentAlignment = Alignment.Center
                ) {
                    Text("+", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        if (detail == null) {
            // still loading
        } else if (detail.attachments.isEmpty()) {
            Text("No attachments.", fontSize = 14.sp, color = TdTextMuted)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                detail.attachments.forEach { attachment ->
                    TdAttachmentItem(attachment = attachment, repo = repo)
                }
            }
        }
    }
}

@Composable
private fun TdSectionLabel(text: String) {
    Text(
        text,
        fontSize      = 11.sp,
        fontWeight    = FontWeight.Bold,
        color         = TdTextMuted,
        letterSpacing = 0.8.sp
    )
}

@Composable
private fun TdMetaRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 14.sp, color = TdTextMuted, modifier = Modifier.width(100.dp))
        Text(value, fontSize = 14.sp, color = TdTextPri, fontWeight = FontWeight.Medium)
    }
}

// ── Attachment item (with image preview) ─────────────────────────────────────
@Composable
private fun TdAttachmentItem(attachment: AttachmentModel, repo: TaskRepository) {
    val isImage = isImageMime(attachment.mimeType) || isImageByName(attachment.fileName)
    var imageBitmap  by remember(attachment.id) { mutableStateOf<ImageBitmap?>(null) }
    var imageLoading by remember(attachment.id) { mutableStateOf(false) }

    // Download and decode image bytes once per attachment
    LaunchedEffect(attachment.id) {
        if (!isImage) return@LaunchedEffect
        imageLoading = true
        repo.downloadAttachment(attachment.taskId, attachment.id)
            .getOrNull()
            ?.let { bytes -> imageBitmap = bytes.decodeImageOrNull() }
        imageLoading = false
    }

    Card(
        shape     = RoundedCornerShape(10.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors    = CardDefaults.cardColors(containerColor = Color.White),
        modifier  = Modifier.fillMaxWidth()
    ) {
        if (isImage) {
            Column {
                // Image preview area
                Box(
                    modifier         = Modifier.fillMaxWidth().height(200.dp).background(TdGray),
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        imageLoading -> CircularProgressIndicator(
                            modifier    = Modifier.size(28.dp),
                            color       = TdBlue,
                            strokeWidth = 2.dp
                        )
                        imageBitmap != null -> Image(
                            painter            = remember(imageBitmap) { BitmapPainter(imageBitmap!!) },
                            contentDescription = attachment.fileName,
                            modifier           = Modifier.fillMaxSize(),
                            contentScale       = ContentScale.Crop
                        )
                        else -> Text("🖼", fontSize = 36.sp)
                    }
                }
                // File info below image
                Row(
                    modifier              = Modifier.padding(horizontal = 14.dp, vertical = 10.dp).fillMaxWidth(),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            attachment.fileName,
                            fontSize   = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color      = TdTextPri,
                            maxLines   = 1,
                            overflow   = TextOverflow.Ellipsis
                        )
                        Text(
                            "${formatFileSize(attachment.fileSize)} · ${formatEpoch(attachment.uploadedAt)}",
                            fontSize = 11.sp,
                            color    = TdTextMuted
                        )
                    }
                }
            }
        } else {
            // Non-image file: compact row
            Row(
                modifier              = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier         = Modifier.size(40.dp).background(TdGray, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(fileTypeEmoji(attachment.fileName), fontSize = 20.sp)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        attachment.fileName,
                        fontSize   = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color      = TdTextPri,
                        maxLines   = 1,
                        overflow   = TextOverflow.Ellipsis
                    )
                    Text(
                        "${formatFileSize(attachment.fileSize)} · ${formatEpoch(attachment.uploadedAt)}",
                        fontSize = 12.sp,
                        color    = TdTextMuted
                    )
                }
            }
        }
    }
}

private fun fileTypeEmoji(fileName: String): String {
    val ext = fileName.substringAfterLast('.', "").lowercase()
    return when (ext) {
        "pdf"               -> "📄"
        "doc", "docx"       -> "📝"
        "xls", "xlsx"       -> "📊"
        "zip", "rar", "7z"  -> "🗜"
        "mp4", "mov", "avi" -> "🎬"
        "mp3", "wav", "ogg" -> "🎵"
        else                -> "📎"
    }
}

// ── Comment panel (right) ─────────────────────────────────────────────────────
@Composable
private fun TdCommentPanel(
    comments: List<CommentModel>,
    deletingIds: Set<Int>,
    commentText: String,
    onTextChange: (String) -> Unit,
    isSending: Boolean,
    onSend: () -> Unit,
    onDeleteComment: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.background(Color.White)) {
        Row(
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 28.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "COMMENTS",
                fontSize      = 11.sp,
                fontWeight    = FontWeight.Bold,
                color         = TdTextMuted,
                letterSpacing = 0.8.sp,
                modifier      = Modifier.weight(1f)
            )
            Box(
                modifier = Modifier
                    .background(TdGray, RoundedCornerShape(10.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(comments.size.toString(), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = TdTextMuted)
            }
        }
        HorizontalDivider(color = TdBorder)

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            if (comments.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No comments yet. Be the first!", fontSize = 14.sp, color = TdTextMuted)
                }
            } else {
                comments.forEach { c ->
                    TdCommentItem(comment = c, isDeleting = c.id in deletingIds, onDelete = { onDeleteComment(c.id) })
                }
            }
        }

        HorizontalDivider(color = TdBorder)
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value         = commentText,
                onValueChange = onTextChange,
                modifier      = Modifier.weight(1f),
                placeholder   = { Text("Write a comment...", fontSize = 13.sp) },
                singleLine    = false,
                maxLines      = 3,
                shape         = RoundedCornerShape(10.dp),
                enabled       = !isSending
            )
            if (isSending) {
                CircularProgressIndicator(modifier = Modifier.size(36.dp), color = TdBlue)
            } else {
                val canSend = commentText.isNotBlank()
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(if (canSend) TdBlue else TdGray, RoundedCornerShape(10.dp))
                        .clickable(enabled = canSend, onClick = onSend),
                    contentAlignment = Alignment.Center
                ) {
                    Text("→", fontSize = 18.sp, color = if (canSend) Color.White else TdTextMuted, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun TdCommentItem(comment: CommentModel, isDeleting: Boolean, onDelete: () -> Unit) {
    val color = authorColor(comment.authorId)
    Row(
        modifier              = Modifier.fillMaxWidth(),
        verticalAlignment     = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier         = Modifier.size(34.dp).background(color, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(commentInitials(comment.authorUsername), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier              = Modifier.fillMaxWidth(),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(comment.authorUsername, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TdTextPri, modifier = Modifier.weight(1f))
                Text(formatEpoch(comment.createdAt), fontSize = 12.sp, color = TdTextMuted)
                if (isDeleting) {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 1.5.dp, color = TdTextMuted)
                } else {
                    Box(modifier = Modifier.size(22.dp).clickable(onClick = onDelete), contentAlignment = Alignment.Center) {
                        Text("🗑", fontSize = 12.sp)
                    }
                }
            }
            Spacer(Modifier.height(3.dp))
            Text(comment.content, fontSize = 14.sp, color = TdTextPri, lineHeight = 21.sp)
        }
    }
}
