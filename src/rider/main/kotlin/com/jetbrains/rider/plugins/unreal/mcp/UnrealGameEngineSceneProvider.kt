package com.jetbrains.rider.plugins.unreal.mcp

import com.intellij.mcpserver.mcpFail
import com.intellij.openapi.project.Project
import com.jetbrains.rider.gameEngine.mcp.GameEngineRotator3
import com.jetbrains.rider.gameEngine.mcp.GameEngineSpawnActorResult
import com.jetbrains.rider.gameEngine.mcp.GameEngineVector3
import com.jetbrains.rider.gameEngine.mcp.IGameEngineSceneProvider
import com.jetbrains.rider.plugins.unreal.UnrealHost
import com.jetbrains.rider.plugins.unreal.model.frontendBackend.UnrealRotator3
import com.jetbrains.rider.plugins.unreal.model.frontendBackend.UnrealSpawnActorRequest
import com.jetbrains.rider.plugins.unreal.model.frontendBackend.UnrealVector3

class UnrealGameEngineSceneProvider : IGameEngineSceneProvider {

    override val engineName: String = "Unreal Engine"

    // Spawning needs a live editor (no cache fallback like assets have).
    override fun isAvailable(project: Project): Boolean =
        UnrealHost.getInstance(project).isUnrealEngineSolution

    override suspend fun spawnActor(
        project: Project,
        assetPath: String,
        location: GameEngineVector3,
        rotation: GameEngineRotator3,
        scale: GameEngineVector3,
        label: String?,
    ): GameEngineSpawnActorResult {
        val host = UnrealHost.getInstance(project)
        if (!host.isConnectedToUnrealEditor) {
            mcpFail("Unreal Editor is not connected. Start the editor with RiderLink installed and try again.")
        }
        val response = host.model.spawnActor.startSuspending(
            UnrealSpawnActorRequest(
                assetPath = assetPath,
                location = UnrealVector3(location.x, location.y, location.z),
                rotation = UnrealRotator3(rotation.pitch, rotation.yaw, rotation.roll),
                scale = UnrealVector3(scale.x, scale.y, scale.z),
                label = label,
            )
        )
        if (!response.success) {
            mcpFail(response.error.ifBlank { "Spawn actor operation failed (no diagnostic returned)" })
        }
        return GameEngineSpawnActorResult(
            spawned = response.success,
            actorLabel = response.actorLabel,
            actorName = response.actorName,
            location = GameEngineVector3(response.location.x, response.location.y, response.location.z),
        )
    }
}
