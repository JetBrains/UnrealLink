package com.jetbrains.rider.plugins.unreal.test.cases.log

import com.jetbrains.rider.plugins.unreal.model.VerbosityType
import com.jetbrains.rider.plugins.unreal.toolWindow.log.VerbositySelection
import com.jetbrains.rider.plugins.unreal.toolWindow.log.isVerbosityEnabled
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UnrealLogVerbosityFilterTest {
    @Test
    fun `verbose levels can be filtered independently`() {
        val selection = VerbositySelection(
            fatal = true,
            error = true,
            warning = true,
            display = true,
            log = true,
            verbose = false,
            veryVerbose = true,
        )

        assertFalse(isVerbosityEnabled(VerbosityType.Verbose, selection))
        assertTrue(isVerbosityEnabled(VerbosityType.VeryVerbose, selection))
        assertTrue(isVerbosityEnabled(VerbosityType.Log, selection))
    }
}
