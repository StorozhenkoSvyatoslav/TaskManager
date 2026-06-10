package ru.storozhenko.taskmanager

import androidx.compose.ui.graphics.ImageBitmap

expect fun ByteArray.decodeImageOrNull(): ImageBitmap?
