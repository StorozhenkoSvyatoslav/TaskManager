package ru.storozhenko.taskmanager

import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

actual fun ByteArray.decodeImageOrNull(): ImageBitmap? =
    BitmapFactory.decodeByteArray(this, 0, this.size)?.asImageBitmap()
