package com.jetbrains.rider.plugins.unreal.filters

object BlueprintParser {
    private val regexp = Regex("(/[^ ]*:[^ ]+)")

    fun parse(s: CharSequence): Sequence<MatchResult> = regexp.findAll(s, 0)
}