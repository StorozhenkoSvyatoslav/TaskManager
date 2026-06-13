package ru.storozhenko.taskmanager

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.coroutines.resume
import kotlin.js.JsString

// JS function: calls back with JSON string {name, base64, mimeType} or null on cancel/error
@JsFun("""(onResult) => {
    const input = document.createElement('input');
    input.type = 'file';
    input.style.display = 'none';
    document.body.appendChild(input);
    let settled = false;
    function settle(value) {
        if (settled) return;
        settled = true;
        if (input.parentNode) document.body.removeChild(input);
        onResult(value);
    }
    input.addEventListener('change', function() {
        const file = input.files && input.files[0];
        if (!file) { settle(null); return; }
        const reader = new FileReader();
        reader.addEventListener('load', function() {
            const base64 = (reader.result.split(',')[1]) || '';
            settle(JSON.stringify({name: file.name, base64: base64, mimeType: file.type || ''}));
        });
        reader.addEventListener('error', function() { settle(null); });
        reader.readAsDataURL(file);
    }, {once: true});
    input.click();
}""")
private external fun nativePickFile(onResult: (JsString?) -> Unit)

@Serializable
private data class WasmFileInfo(val name: String, val base64: String, val mimeType: String)

// Keeps a strong Kotlin-side reference to the callback so WasmJS GC cannot collect it
// before the JS file-change event fires.
private var _activePickCallback: ((JsString?) -> Unit)? = null

@OptIn(ExperimentalEncodingApi::class)
actual suspend fun pickFile(): PickedFile? = suspendCancellableCoroutine { cont ->
    val callback: (JsString?) -> Unit = { jsResult ->
        _activePickCallback = null
        val json = jsResult?.toString()
        val result = if (json.isNullOrEmpty()) {
            null
        } else {
            try {
                val info = Json.decodeFromString<WasmFileInfo>(json)
                PickedFile(info.name, Base64.decode(info.base64), info.mimeType.ifEmpty { null })
            } catch (_: Exception) {
                null
            }
        }
        cont.resume(result)
    }
    _activePickCallback = callback
    nativePickFile(callback)
    cont.invokeOnCancellation { _activePickCallback = null }
}

// ── Native browser upload via FormData + fetch ─────────────────────────────────
// Avoids WasmJS→JS binary interop issues with Ktor's multipart serialization.
// Passes file as base64 string (string interop is reliable across WasmJS/JS boundary).

@JsFun("""(url, bearerToken, name, base64, mimeType, onSuccess, onError) => {
    try {
        const binary = atob(base64);
        const bytes = new Uint8Array(binary.length);
        for (let i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i);
        const blob = new Blob([bytes], {type: mimeType || 'application/octet-stream'});
        const form = new FormData();
        form.append('file', blob, name);
        fetch(url, {
            method: 'POST',
            headers: {'Authorization': bearerToken},
            body: form
        }).then(function(resp) {
            if (resp.ok) {
                try { onSuccess(); } catch(e) {}
            } else {
                resp.text().then(function(text) {
                    try { onError('HTTP ' + resp.status + ': ' + text); } catch(e) {}
                }).catch(function() {
                    try { onError('HTTP ' + resp.status); } catch(e) {}
                });
            }
        }).catch(function(err) {
            try { onError(String(err)); } catch(e) {}
        });
    } catch(err) {
        try { onError(String(err)); } catch(e) {}
    }
}""")
private external fun nativeUploadFile(
    url: JsString,
    bearerToken: JsString,
    name: JsString,
    base64: JsString,
    mimeType: JsString,
    onSuccess: () -> Unit,
    onError: (JsString) -> Unit
)

// ── Native browser file save (download) ───────────────────────────────────────
@JsFun("""(fileName, base64) => {
    const binary = atob(base64);
    const bytes = new Uint8Array(binary.length);
    for (let i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i);
    const blob = new Blob([bytes], {type: 'application/octet-stream'});
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = fileName;
    a.style.display = 'none';
    document.body.appendChild(a);
    a.click();
    setTimeout(function() {
        document.body.removeChild(a);
        URL.revokeObjectURL(url);
    }, 100);
}""")
private external fun nativeSaveFile(fileName: JsString, base64: JsString)

@OptIn(ExperimentalEncodingApi::class)
actual suspend fun saveFile(fileName: String, bytes: ByteArray) {
    nativeSaveFile(fileName.toJsString(), Base64.encode(bytes).toJsString())
}

private var _uploadSuccessCallback: (() -> Unit)? = null
private var _uploadErrorCallback: ((JsString) -> Unit)? = null

@OptIn(ExperimentalEncodingApi::class)
actual suspend fun platformUpload(url: String, token: String, file: PickedFile): Result<Unit>? =
    suspendCancellableCoroutine { cont ->
        val onSuccess: () -> Unit = {
            _uploadSuccessCallback = null
            _uploadErrorCallback = null
            if (cont.isActive) cont.resume(Result.success(Unit))
        }
        val onError: (JsString) -> Unit = { msg ->
            _uploadSuccessCallback = null
            _uploadErrorCallback = null
            if (cont.isActive) cont.resume(Result.failure(Exception(msg.toString())))
        }
        _uploadSuccessCallback = onSuccess
        _uploadErrorCallback = onError
        val base64 = Base64.encode(file.bytes)
        nativeUploadFile(
            url.toJsString(),
            "Bearer $token".toJsString(),
            file.name.toJsString(),
            base64.toJsString(),
            (file.mimeType ?: "application/octet-stream").toJsString(),
            onSuccess,
            onError
        )
        cont.invokeOnCancellation {
            _uploadSuccessCallback = null
            _uploadErrorCallback = null
        }
    }
