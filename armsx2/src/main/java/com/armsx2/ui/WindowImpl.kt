package com.armsx2.ui

import androidx.compose.runtime.mutableStateOf

object WindowImpl {
    val toolbarVisible = mutableStateOf(true)
    val showLibrary = mutableStateOf(false)
    val overlayVisible = mutableStateOf(false)

    val frontendCovers: Boolean
        get() = overlayVisible.value || showLibrary.value
}
