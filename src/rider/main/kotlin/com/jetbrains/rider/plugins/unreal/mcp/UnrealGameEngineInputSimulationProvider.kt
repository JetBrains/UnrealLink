package com.jetbrains.rider.plugins.unreal.mcp

import com.intellij.mcpserver.mcpFail
import com.intellij.openapi.project.Project
import com.jetbrains.rider.gameEngine.mcp.GameEngineInputActionEntry
import com.jetbrains.rider.gameEngine.mcp.GameEngineInputSimulationMode
import com.jetbrains.rider.gameEngine.mcp.GameEngineInputSimulationResult
import com.jetbrains.rider.gameEngine.mcp.GameEngineVector3
import com.jetbrains.rider.gameEngine.mcp.IGameEngineInputSimulationProvider
import com.jetbrains.rider.plugins.unreal.UnrealHost
import com.jetbrains.rider.plugins.unreal.model.frontendBackend.UnrealInputActionEntry
import com.jetbrains.rider.plugins.unreal.model.frontendBackend.UnrealInputSimulationRequest
import com.jetbrains.rider.plugins.unreal.model.frontendBackend.UnrealVector3

class UnrealGameEngineInputSimulationProvider : IGameEngineInputSimulationProvider {

    override val engineName: String = "Unreal Engine"

    // Input simulation requires a live PIE world; the C++ side hard-fails if
    // none is present. Availability here mirrors the camera provider — we
    // gate on the project being an Unreal solution, not on PIE state, so the
    // tool is discoverable even when the user has yet to press Play.
    override fun isAvailable(project: Project): Boolean =
        UnrealHost.getInstance(project).isUnrealEngineSolution

    override suspend fun simulateInput(
        project: Project,
        mode: GameEngineInputSimulationMode,
        actions: List<GameEngineInputActionEntry>,
        primitiveCall: String?,
        primitiveDirection: String?,
        primitiveWorldVec: GameEngineVector3?,
        primitiveScale: Double,
        primitiveValue: Double,
        primitiveDuration: Double,
        enhancedAssetPath: String?,
        enhancedValueKind: String?,
        enhancedAxis2dX: Double,
        enhancedAxis2dY: Double,
        enhancedAxis1d: Double,
        enhancedBool: Boolean,
        enhancedClear: Boolean,
    ): GameEngineInputSimulationResult {
        val host = UnrealHost.getInstance(project)
        if (!host.isConnectedToUnrealEditor) {
            mcpFail("Unreal Editor is not connected. Start the editor with RiderLink installed and try again.")
        }
        val wireMode = when (mode) {
            GameEngineInputSimulationMode.Actions   -> "actions"
            GameEngineInputSimulationMode.Primitive -> "primitive"
            GameEngineInputSimulationMode.Enhanced  -> "enhanced"
        }
        val response = host.model.simulateInput.startSuspending(
            UnrealInputSimulationRequest(
                mode = wireMode,
                actions = actions.map {
                    UnrealInputActionEntry(it.type, it.direction, it.scale, it.yaw, it.pitch, it.duration)
                },
                primitiveCall = primitiveCall,
                primitiveDirection = primitiveDirection,
                primitiveWorldVec = primitiveWorldVec?.let { UnrealVector3(it.x, it.y, it.z) },
                primitiveScale = primitiveScale,
                primitiveValue = primitiveValue,
                primitiveDuration = primitiveDuration,
                enhancedAssetPath = enhancedAssetPath,
                enhancedValueKind = enhancedValueKind,
                enhancedAxis2dX = enhancedAxis2dX,
                enhancedAxis2dY = enhancedAxis2dY,
                enhancedAxis1d = enhancedAxis1d,
                enhancedBool = enhancedBool,
                enhancedClear = enhancedClear,
            )
        )
        if (!response.success) {
            mcpFail(response.error.ifBlank { "Input simulation failed (no diagnostic returned)" })
        }
        return GameEngineInputSimulationResult(
            armed = response.armed,
            startLocation = response.startLocation?.let { GameEngineVector3(it.x, it.y, it.z) },
            startVelocity = response.startVelocity?.let { GameEngineVector3(it.x, it.y, it.z) },
            nActions = response.nActions,
        )
    }
}
