package com.jetbrains.rider.plugins.unreal.test.cases.actions

import com.jetbrains.rider.plugins.unreal.actions.isAttachToConnectedEditorAvailable
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.ResourceBundle

class UnrealToolbarActionsTest {
    @Test
    fun `play settings toolbar group has a tooltip`() {
        val messages = ResourceBundle.getBundle("messages.UnrealLinkBundle")

        assertTrue(messages.getString("action.RiderLink.UnrealPlaySettings.description").isNotBlank())
    }

    @Test
    fun `attach action is unavailable while a C++ debugger is attached`() {
        assertFalse(isAttachToConnectedEditorAvailable(hasAttachedCppDebugger = true))
    }

    @Test
    fun `attach action is available without an attached C++ debugger`() {
        assertTrue(isAttachToConnectedEditorAvailable(hasAttachedCppDebugger = false))
    }
}
