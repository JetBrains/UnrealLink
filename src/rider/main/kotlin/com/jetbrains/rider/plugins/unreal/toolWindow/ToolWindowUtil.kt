package com.jetbrains.rider.plugins.unreal.toolWindow

internal const val TIME_WIDTH = 29
internal const val VERBOSITY_WIDTH = 12
internal const val CATEGORY_WIDTH = 20

fun CharSequence.messageStartPosition() = TIME_WIDTH + VERBOSITY_WIDTH + CATEGORY_WIDTH + 3 /* spaces */