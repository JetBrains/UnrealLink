package com.jetbrains.rider.plugins.unreal.spellchecker

import com.intellij.spellchecker.BundledDictionaryProvider

class UnrealBundledDictionaryProvider : BundledDictionaryProvider {
    override fun getBundledDictionaries(): Array<String> = arrayOf("unreal.dic")
}