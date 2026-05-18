package com.jetbrains.rider.plugins.unreal.mcp

import com.intellij.openapi.project.Project
import com.jetbrains.rider.gameEngine.mcp.GameEngineAssetInfo
import com.jetbrains.rider.gameEngine.mcp.GameEngineAssetPropertiesInfo
import com.jetbrains.rider.gameEngine.mcp.GameEngineClassInfo
import com.jetbrains.rider.gameEngine.mcp.GameEngineDefaultOverrideInfo
import com.jetbrains.rider.gameEngine.mcp.GameEnginePropertyInfo
import com.jetbrains.rider.gameEngine.mcp.GameEngineTagInfo
import com.jetbrains.rider.gameEngine.mcp.IGameEngineAssetIndexProvider
import com.jetbrains.rider.plugins.unreal.UnrealHost
import com.jetbrains.rider.plugins.unreal.model.frontendBackend.UnrealAssetPropertiesRequest
import com.jetbrains.rider.plugins.unreal.model.frontendBackend.UnrealAssetSearchRequest
import com.jetbrains.rider.plugins.unreal.model.frontendBackend.UnrealBlueprintHierarchyRequest
import com.jetbrains.rider.plugins.unreal.model.frontendBackend.UnrealDefaultOverridesRequest
import com.jetbrains.rider.plugins.unreal.model.frontendBackend.UnrealGameplayTagsRequest

class UnrealGameEngineAssetIndexProvider : IGameEngineAssetIndexProvider {

    override val engineName: String = "Unreal Engine"

    override fun isAvailable(project: Project): Boolean =
        UnrealHost.getInstance(project).isUnrealEngineSolution

    override suspend fun searchAssets(project: Project, query: String?, baseClass: String?, limit: Int): List<GameEngineAssetInfo> {
        val model = UnrealHost.getInstance(project).model
        val response = model.searchUnrealAssets.startSuspending(
            UnrealAssetSearchRequest(query = query, baseClass = baseClass, limit = limit)
        )
        return response.assets.map { GameEngineAssetInfo(it.assetPath, it.assetName, it.baseClass) }
    }

    override suspend fun getClassHierarchy(project: Project, baseClass: String, limit: Int): List<GameEngineClassInfo> {
        val model = UnrealHost.getInstance(project).model
        val response = model.getBlueprintHierarchy.startSuspending(
            UnrealBlueprintHierarchyRequest(baseClass = baseClass, limit = limit)
        )
        return response.blueprints.map { GameEngineClassInfo(it.name, it.assetPath) }
    }

    override suspend fun searchTags(project: Project, prefix: String?, limit: Int): List<GameEngineTagInfo> {
        val model = UnrealHost.getInstance(project).model
        val response = model.searchGameplayTags.startSuspending(
            UnrealGameplayTagsRequest(prefix = prefix, limit = limit)
        )
        return response.tags.map { GameEngineTagInfo(it.tagName, it.assetPath) }
    }

    override suspend fun getAssetProperties(project: Project, assetPath: String): GameEngineAssetPropertiesInfo {
        val model = UnrealHost.getInstance(project).model
        val response = model.getAssetProperties.startSuspending(
            UnrealAssetPropertiesRequest(assetPath = assetPath)
        )
        val properties = response.properties.map { GameEnginePropertyInfo(it.name, it.typeName, it.value) }
        return GameEngineAssetPropertiesInfo(objectName = response.objectName, properties = properties)
    }

    override suspend fun findDefaultOverrides(
        project: Project,
        className: String,
        fieldName: String,
        limit: Int,
    ): List<GameEngineDefaultOverrideInfo> {
        val model = UnrealHost.getInstance(project).model
        val response = model.findDefaultOverrides.startSuspending(
            UnrealDefaultOverridesRequest(className = className, fieldName = fieldName, limit = limit)
        )
        return response.overrides.map {
            GameEngineDefaultOverrideInfo(it.assetPath, it.instanceName, it.typeName, it.value)
        }
    }
}
