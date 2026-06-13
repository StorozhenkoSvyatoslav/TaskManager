package ru.storozhenko.taskmanager

import kotlinx.browser.document
import kotlinx.coroutines.suspendCancellableCoroutine
import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Uint8Array
import org.khronos.webgl.get
import org.w3c.dom.HTMLAnchorElement
import org.w3c.dom.HTMLInputElement
import org.w3c.files.FileReader
import kotlin.coroutines.resume
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

actual suspend fun pickFile(): PickedFile? = suspendCancellableCoroutine { cont ->
    val input = document.createElement("input") as HTMLInputElement
    input.type = "file"
    input.style.setProperty("display", "none")
    document.body?.appendChild(input)

    input.addEventListener("change", { _ ->
        val file = input.files?.item(0)
        runCatching { document.body?.removeChild(input) }
        if (file == null) {
            cont.resume(null)
        } else {
            val reader = FileReader()
            reader.onload = { _ ->
                @Suppress("UNCHECKED_CAST")
                val buffer = reader.result as ArrayBuffer
                val u8 = Uint8Array(buffer)
                val bytes = ByteArray(u8.length) { i -> u8[i] }
                reader.onload = null
                reader.onerror = null
                cont.resume(PickedFile(file.name, bytes, file.type.ifEmpty { null }))
            }
            reader.onerror = { _ ->
                reader.onload = null
                reader.onerror = null
                cont.resume(null)
            }
            reader.readAsArrayBuffer(file)
        }
    })

    cont.invokeOnCancellation { runCatching { document.body?.removeChild(input) } }
    input.click()
}

actual suspend fun platformUpload(url: String, token: String, file: PickedFile): Result<Unit>? = null

@OptIn(ExperimentalEncodingApi::class)
actual suspend fun saveFile(fileName: String, bytes: ByteArray) {
    val base64 = Base64.encode(bytes)
    val a = document.createElement("a") as HTMLAnchorElement
    a.href = "data:application/octet-stream;base64,$base64"
    a.setAttribute("download", fileName)
    a.style.setProperty("display", "none")
    document.body?.appendChild(a)
    a.click()
    runCatching { document.body?.removeChild(a) }
}
