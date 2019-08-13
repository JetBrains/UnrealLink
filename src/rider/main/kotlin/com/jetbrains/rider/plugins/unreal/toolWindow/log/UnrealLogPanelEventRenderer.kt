package com.jetbrains.rider.plugins.unreal.toolWindow.log

import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.speedSearch.SpeedSearchUtil
import javax.swing.JList

class UnrealLogPanelEventRenderer : ColoredListCellRenderer<LogPanelItem>() {
    override fun customizeCellRenderer(list: JList<out LogPanelItem>, event: LogPanelItem?, index: Int, selected: Boolean, hasFocus: Boolean) {
        if (event != null) {
//            icon = RowIcon(event.type.getIcon(), event.mode.getIcon())
//            var countPresentation = ""
//            if (event.count>1)
//                countPresentation = " ("+ event.count + ")"
//            append(event.message + countPresentation, SimpleTextAttributes.REGULAR_ATTRIBUTES)
            append(event.message)
            SpeedSearchUtil.applySpeedSearchHighlighting(list, this, true, selected)
        }
    }
}