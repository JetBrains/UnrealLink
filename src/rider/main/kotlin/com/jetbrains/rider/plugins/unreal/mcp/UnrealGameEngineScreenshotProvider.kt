package com.jetbrains.rider.plugins.unreal.mcp

import com.intellij.mcpserver.mcpFail
import com.intellij.openapi.project.Project
import com.jetbrains.rider.gameEngine.mcp.GameEngineScreenshotKind
import com.jetbrains.rider.gameEngine.mcp.GameEngineScreenshotResult
import com.jetbrains.rider.gameEngine.mcp.IGameEngineScreenshotProvider
import com.jetbrains.rider.plugins.unreal.UnrealHost
import com.jetbrains.rider.plugins.unreal.model.frontendBackend.UnrealScreenshotRequest

class UnrealGameEngineScreenshotProvider : IGameEngineScreenshotProvider {

    override val engineName: String = "Unreal Engine"

    // Screenshots all require a live editor connection — the cache-based asset
    // search path doesn't apply here. The MCP tool surfaces this clearly.
    override fun isAvailable(project: Project): Boolean =
        UnrealHost.getInstance(project).isUnrealEngineSolution

    override suspend fun takeScreenshot(
        project: Project,
        kind: GameEngineScreenshotKind,
        assetPath: String?,
        width: Int,
        height: Int,
        forceLive: Boolean,
    ): GameEngineScreenshotResult {
        val host = UnrealHost.getInstance(project)
        if (!host.isConnectedToUnrealEditor) {
            mcpFail("Unreal Editor is not connected. Start the editor with RiderLink installed and try again.")
        }
        val response = host.model.takeScreenshot.startSuspending(
            UnrealScreenshotRequest(
                kind = kind.name,
                assetPath = assetPath,
                width = width,
                height = height,
                forceLive = forceLive,
            )
        )
        if (!response.success) {
            mcpFail(response.error.ifBlank { "Screenshot capture failed (no diagnostic returned)" })
        }
        return GameEngineScreenshotResult(
            path = response.path,
            width = response.width,
            height = response.height,
            sourceApi = response.sourceApi,
        )
    }
}
