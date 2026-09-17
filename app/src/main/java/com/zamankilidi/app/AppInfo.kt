package com.zamankilidi.app

import android.graphics.drawable.Drawable

data class AppInfo(
    val packageName: String,
    val label: String,
    val icon: Drawable?,
    var allowed: Boolean
)
