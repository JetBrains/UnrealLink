package com.jetbrains.rider.plugins.unreal.test.cases.actions

import com.jetbrains.rider.plugins.unreal.actions.PlaySettings
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class PlaySettingsActionsTest {
    @Test
    fun `play settings toolbar button has a tooltip`() {
        assertEquals(
            "Unreal Editor connection and play settings",
            PlaySettings().templatePresentation.description,
        )
    }
}
