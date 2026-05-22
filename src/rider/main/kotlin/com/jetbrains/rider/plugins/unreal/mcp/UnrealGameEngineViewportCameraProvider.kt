package com.jetbrains.rider.plugins.unreal.mcp

import com.intellij.mcpserver.mcpFail
import com.intellij.openapi.project.Project
import com.jetbrains.rider.gameEngine.mcp.GameEngineRotator3
import com.jetbrains.rider.gameEngine.mcp.GameEngineVector3
import com.jetbrains.rider.gameEngine.mcp.GameEngineViewportCameraAction
import com.jetbrains.rider.gameEngine.mcp.GameEngineViewportCameraResult
import com.jetbrains.rider.gameEngine.mcp.IGameEngineViewportCameraProvider
import com.jetbrains.rider.plugins.unreal.UnrealHost
import com.jetbrains.rider.plugins.unreal.model.frontendBackend.UnrealRotator3
import com.jetbrains.rider.plugins.unreal.model.frontendBackend.UnrealVector3
import com.jetbrains.rider.plugins.unreal.model.frontendBackend.UnrealViewportCameraRequest

class UnrealGameEngineViewportCameraProvider : IGameEngineViewportCameraProvider {

    override val engineName: String = "Unreal Engine"

    // Camera control needs a live editor (no cache fallback like assets have).
    override fun isAvailable(project: Project): Boolean =
        UnrealHost.getInstance(project).isUnrealEngineSolution

    override suspend fun viewportCamera(
        project: Project,
        action: GameEngineViewportCameraAction,
        location: GameEngineVector3?,
        rotation: GameEngineRotator3?,
        delta: GameEngineVector3?,
        relative: Boolean,
        rotationDelta: GameEngineRotator3?,
        target: GameEngineVector3?,
        actorName: String?,
        minDistance: Double,
    ): GameEngineViewportCameraResult {
        val host = UnrealHost.getInstance(project)
        if (!host.isConnectedToUnrealEditor) {
            mcpFail("Unreal Editor is not connected. Start the editor with RiderLink installed and try again.")
        }
        val response = host.model.viewportCamera.startSuspending(
            UnrealViewportCameraRequest(
                action = action.name,
                location = location?.let { UnrealVector3(it.x, it.y, it.z) },
                rotation = rotation?.let { UnrealRotator3(it.pitch, it.yaw, it.roll) },
                delta = delta?.let { UnrealVector3(it.x, it.y, it.z) },
                relative = relative,
                rotationDelta = rotationDelta?.let { UnrealRotator3(it.pitch, it.yaw, it.roll) },
                target = target?.let { UnrealVector3(it.x, it.y, it.z) },
                actorName = actorName,
                minDistance = minDistance,
            )
        )
        if (!response.success) {
            mcpFail(response.error.ifBlank { "Viewport camera operation failed (no diagnostic returned)" })
        }
        return GameEngineViewportCameraResult(
            location = GameEngineVector3(response.location.x, response.location.y, response.location.z),
            rotation = GameEngineRotator3(response.rotation.pitch, response.rotation.yaw, response.rotation.roll),
            actorResolved = response.actorResolved,
        )
    }
}
