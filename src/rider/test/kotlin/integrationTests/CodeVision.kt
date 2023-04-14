package integrationTests

import com.intellij.codeInsight.codeVision.CodeVisionEntry
import com.intellij.codeInsight.codeVision.ui.model.CodeVisionListData
import com.intellij.codeInsight.codeVision.ui.model.CounterCodeVisionEntry
import com.intellij.codeInsight.codeVision.ui.model.RichTextCodeVisionEntry
import com.intellij.codeInsight.codeVision.ui.model.TextCodeVisionEntry
import com.intellij.codeInsight.codeVision.ui.model.richText.RichText
import com.intellij.codeInsight.codeVision.ui.renderers.InlineCodeVisionListRenderer
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.util.TextRange
import com.intellij.ui.SimpleTextAttributes
import com.jetbrains.rd.ide.model.FontStyle
import com.jetbrains.rd.ide.model.RdTextRange
import com.jetbrains.rd.ide.model.UnrealEngine
import com.jetbrains.rd.util.string.printToString
import com.jetbrains.rdclient.util.idea.toRdTextRange
import com.jetbrains.rider.test.annotations.TestEnvironment
import com.jetbrains.rider.test.enums.PlatformType
import com.jetbrains.rider.test.env.enums.SdkVersion
import com.jetbrains.rider.test.framework.executeWithGold
import com.jetbrains.rider.test.scriptingApi.*
import io.qameta.allure.Epic
import io.qameta.allure.Feature
import org.testng.annotations.Test
import testFrameworkExtentions.EngineInfo
import testFrameworkExtentions.UnrealTestProject

@Epic("Blueprint")
@Feature("Code Vision")
@TestEnvironment(platform = [PlatformType.WINDOWS_X64], sdkVersion = SdkVersion.AUTODETECT)
class CodeVision : UnrealTestProject() {

    init {
        projectDirectoryName = "BlueprintCodeVision"
    }

    @Test(dataProvider = "ue51Only_uprojectOnly")
    fun bpCodeVision0(@Suppress("UNUSED_PARAMETER") caseName: String,
                      openWith: EngineInfo.UnrealOpenType,
                      engine: UnrealEngine) {
        waitForLensInfos(project)
        enableAllLensProviders()
        waitForAllAnalysisFinished(project)

        val editorActor = withOpenedEditor(project,
            activeSolutionDirectory.resolve("Source")
                .resolve(activeSolution).resolve("MyActor.h").absolutePath
        ) {
            executeWithGold(testGoldFile, "_actor") {
                it.println(this.virtualFile.name)
                it.print(dumpULenses())
            }
        }
        closeEditor(editorActor)

        val editorAnimInst = withOpenedEditor(project,
                                      activeSolutionDirectory.resolve("Source")
                                          .resolve(activeSolution).resolve("MyAnimInstance.h").absolutePath
        ) {
            executeWithGold(testGoldFile, "_animInst") {
                it.print(dumpULenses())
            }
        }
        closeEditor(editorAnimInst)
    }
}

fun Editor.dumpULenses(): String {
    val builder = StringBuilder()

    val inlines = inlayModel.getAfterLineEndElementsInRange(0, document.textLength)
    val blocks = inlayModel.getBlockElementsInRange(0, document.textLength)

    fun Int.toFontStyle(): FontStyle = when {
        this and SimpleTextAttributes.STYLE_BOLD != 0 -> FontStyle.Bold
        this and SimpleTextAttributes.STYLE_ITALIC != 0 -> FontStyle.Italic
        this and SimpleTextAttributes.STYLE_UNDERLINE != 0 -> FontStyle.Underline
        this and SimpleTextAttributes.STYLE_STRIKEOUT != 0 -> FontStyle.Strikeout
        else -> FontStyle.Regular
    }

    fun RichText.printSignificantPartsToString(): String {
        val richTextBuilder = StringBuilder()
        parts.forEach {
            richTextBuilder.append(it.text)
            it.attributes.bgColor?.let { bg -> richTextBuilder.append("|bg=" + bg.printToString()) }
            it.attributes.fgColor?.let { fg -> richTextBuilder.append("|fg=" + fg.printToString()) }
            it.attributes.waveColor?.let { ec -> richTextBuilder.append("|ec=" + ec.printToString()) }
            if (it.attributes.fontStyle.toFontStyle() != FontStyle.Regular) richTextBuilder.append("|fs=" + it.attributes.fontStyle.toFontStyle())
        }
        return richTextBuilder.toString()
    }

    fun CodeVisionEntry.lensToString(range: RdTextRange): String {
        return when (this) {
            is TextCodeVisionEntry -> "Text " + range + " " + this.text // todo
            is CounterCodeVisionEntry -> "Counter " + range + " " + this.count + " " + this.text
            is RichTextCodeVisionEntry -> "RichText " + range + " " + this.text.printSignificantPartsToString()
            else -> error("Unprintable lens of type ${this.javaClass.simpleName}, fix test")
        }
    }

    fun addInlay(inlay: Inlay<*>) {
        builder.appendLine("${if (inlay.renderer is InlineCodeVisionListRenderer) "Inline" else "Block"} ${inlay.offset} ${inlay.renderer.javaClass.simpleName}")
        val lensList = CodeVisionListData.KEY.get(inlay) ?: return // todo: don't append non-lens inlays?
        builder.appendLine(lensList.anchor)
        builder.appendLine("    Visible")

        val lineNum = document.getLineNumber(inlay.offset)
        val lineRange = TextRange.create(document.getLineStartOffset(lineNum), document.getLineEndOffset(lineNum))
        builder.appendLine("        Line $lineNum " + this.document.getText(lineRange))

        lensList.visibleLens.forEach { builder.appendLine("        " + it.lensToString(lensList.rangeCodeVisionModel.anchoringRange.toRdTextRange())) }
        builder.appendLine("    Hidden")
        lensList.anchoredLens.forEach { if(!lensList.visibleLens.contains(it)) builder.appendLine(
            "        " + it.lensToString(
                lensList.rangeCodeVisionModel.anchoringRange.toRdTextRange()
            )
        )
        }
    }

    val inlays = inlines + blocks
    
    inlays.sortedBy { it.offset }.forEach { addInlay(it) }

    return builder.toString()
}