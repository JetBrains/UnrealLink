package com.jetbrains.rider.plugins.unreal.filters

object LogParser {

    private val CPP_METHOD_REGEXP = Regex("[0-9a-z_A-Z]+::[0-9a-z_A-Z]+")
    private val LINK_REGEXP = Regex("[^\\s]*/[^\\s]+")

    fun parseMethodReferences(s: CharSequence) = CPP_METHOD_REGEXP.findAll(s)

    fun parseLinkCandidates(s: CharSequence): Sequence<MatchResult> {
        return LINK_REGEXP.findAll(s)
    }
}
