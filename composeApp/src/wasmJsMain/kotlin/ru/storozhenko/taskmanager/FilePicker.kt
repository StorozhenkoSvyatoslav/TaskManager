package ru.storozhenko.taskmanager

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.coroutines.resume
import kotlin.js.JsString

// JS function: calls back with JSON string {name, base64, mimeType} or null on cancel
@JsFun("""(onResult) => {
    const input = document.createElement('input');
    input.type = 'file';
    document.body.appendChild(input);
    input.addEventListener('change', () => {
        const file = input.files && input.files[0];
        document.body.removeChild(input);
        if (!file) { onResult(null); return; }
        const reader = new FileReader();
        reader.addEventListener('load', () => {
            const base64 = reader.result.split(',')[1] || '';
            onResult(JSON.stringify({name: file.name, base64: base64, mimeType: file.type || ''}));
        });
        reader.readAsDataURL(file);
    }, {once: true});
    input.click();
}""")
private external fun nativePickFile(onResult: (JsString?) -> Unit)

@Serializable
private data class WasmFileInfo(val name: String, val base64: String, val mimeType: String)

@OptIn(ExperimentalEncodingApi::class)
actual suspend fun pickFile(): PickedFile? = suspendCancellableCoroutine { cont ->
    nativePickFile { jsResult ->
        val json = jsResult?.toString()
        if (json.isNullOrEmpty()) {
            cont.resume(null)
            return@nativePickFile
        }
        try {
            val info = Json.decodeFromString<WasmFileInfo>(json)
            val bytes = Base64.decode(info.base64)
            cont.resume(PickedFile(info.name, bytes, info.mimeType.ifEmpty { null }))
        } catch (_: Exception) {
            cont.resume(null)
        }
    }
}
