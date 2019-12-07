package com.jetbrains.rider.plugins.unreal.filters

import com.jetbrains.rider.model.BlueprintClass
import com.jetbrains.rider.model.FString

object BlueprintParser {
    private val regexp = Regex("(/[^ ]*:[^ ]+)")

    fun parse(s: CharSequence): Sequence<MatchResult> = regexp.findAll(s, 0)

    fun split(s: String) = BlueprintClass(FString(s))
}