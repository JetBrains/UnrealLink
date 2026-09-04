package com.jetbrains.rider.plugins.unreal.actions

import java.util.Properties
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlaySettingsActionsTest {
    @Test
    fun `RIDER-93805 play settings toolbar group has a tooltip`() {
        val properties = Properties().apply {
            PlaySettingsActionsTest::class.java.classLoader
                .getResourceAsStream("messages/UnrealLinkBundle.properties")!!
                .use(::load)
        }

        assertEquals(
            "Unreal Editor connection and play settings",
            properties.getProperty("group.RiderLink.UnrealPlaySettings.description"),
        )
    }

    @Test
    fun `RIDER-84545 attach is disabled while a debug session is active`() {
        assertFalse(isAttachToConnectedEditorEnabled(true, true, true))
    }

    @Test
    fun `attach is enabled only for an undebugged connected editor`() {
        assertTrue(isAttachToConnectedEditorEnabled(true, true, false))
        assertFalse(isAttachToConnectedEditorEnabled(false, true, false))
        assertFalse(isAttachToConnectedEditorEnabled(true, false, false))
    }
}
