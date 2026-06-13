package ru.storozhenko.taskmanager

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withContext
import javax.swing.JFileChooser

actual suspend fun pickFile(): PickedFile? {
    val selected = withContext(Dispatchers.Swing) {
        val chooser = JFileChooser()
        if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) chooser.selectedFile else null
    } ?: return null
    return withContext(Dispatchers.IO) {
        PickedFile(selected.name, selected.readBytes(), null)
    }
}

actual suspend fun platformUpload(url: String, token: String, file: PickedFile): Result<Unit>? = null

actual suspend fun saveFile(fileName: String, bytes: ByteArray) {
    val file = withContext(Dispatchers.Swing) {
        val chooser = JFileChooser()
        chooser.selectedFile = java.io.File(fileName)
        if (chooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) chooser.selectedFile else null
    } ?: return
    withContext(Dispatchers.IO) {
        file.writeBytes(bytes)
    }
}
