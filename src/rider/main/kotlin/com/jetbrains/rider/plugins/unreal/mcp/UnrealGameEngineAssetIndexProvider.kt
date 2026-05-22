package com.jetbrains.rider.plugins.unreal.mcp

import com.intellij.openapi.project.Project
import com.intellij.mcpserver.mcpFail
import com.jetbrains.rider.gameEngine.mcp.AssetSearchSource
import com.jetbrains.rider.gameEngine.mcp.GameEngineAssetInfo
import com.jetbrains.rider.gameEngine.mcp.GameEngineAssetPropertiesInfo
import com.jetbrains.rider.gameEngine.mcp.GameEngineClassInfo
import com.jetbrains.rider.gameEngine.mcp.GameEngineDefaultOverrideInfo
import com.jetbrains.rider.gameEngine.mcp.GameEnginePropertyInfo
import com.jetbrains.rider.gameEngine.mcp.GameEngineTagInfo
import com.jetbrains.rider.gameEngine.mcp.IGameEngineAssetIndexProvider
import com.jetbrains.rider.plugins.unreal.UnrealHost
import com.jetbrains.rider.plugins.unreal.model.frontendBackend.UnrealAssetLiveSearchRequest
import com.jetbrains.rider.plugins.unreal.model.frontendBackend.UnrealAssetPropertiesRequest
import com.jetbrains.rider.plugins.unreal.model.frontendBackend.UnrealAssetSearchRequest
import com.jetbrains.rider.plugins.unreal.model.frontendBackend.UnrealBlueprintHierarchyRequest
import com.jetbrains.rider.plugins.unreal.model.frontendBackend.UnrealDefaultOverridesRequest
import com.jetbrains.rider.plugins.unreal.model.frontendBackend.UnrealGameplayTagsRequest

class UnrealGameEngineAssetIndexProvider : IGameEngineAssetIndexProvider {

    override val engineName: String = "Unreal Engine"

    override fun isAvailable(project: Project): Boolean =
        UnrealHost.getInstance(project).isUnrealEngineSolution

    override suspend fun searchAssets(
        project: Project,
        query: String?,
        baseClass: String?,
        packagePath: String?,
        source: AssetSearchSource,
        limit: Int,
    ): List<GameEngineAssetInfo> {
        val host = UnrealHost.getInstance(project)
        return when (source) {
            AssetSearchSource.Cache -> searchCache(host, query, baseClass, packagePath, limit)
            AssetSearchSource.Editor -> {
                if (!host.isConnectedToUnrealEditor) {
                    mcpFail("Editor not connected. Use source='cache' or start Unreal Editor with RiderLink installed.")
                }
                searchLive(host, query, baseClass, packagePath, limit)
            }
            AssetSearchSource.Auto -> {
                val cacheResult = searchCache(host, query, baseClass, packagePath, limit)
                if (cacheResult.isNotEmpty() || !host.isConnectedToUnrealEditor) cacheResult
                else searchLive(host, query, baseClass, packagePath, limit)
            }
        }
    }

    private suspend fun searchCache(
        host: UnrealHost,
        query: String?,
        baseClass: String?,
        packagePath: String?,
        limit: Int,
    ): List<GameEngineAssetInfo> {
        val response = host.model.searchUnrealAssets.startSuspending(
            UnrealAssetSearchRequest(query = query, baseClass = baseClass, packagePath = packagePath, limit = limit)
        )
        return response.assets.map { GameEngineAssetInfo(it.assetPath, it.assetName, it.baseClass) }
    }

    private suspend fun searchLive(
        host: UnrealHost,
        query: String?,
        baseClass: String?,
        packagePath: String?,
        limit: Int,
    ): List<GameEngineAssetInfo> {
        val response = host.model.searchUnrealAssetsLive.startSuspending(
            UnrealAssetLiveSearchRequest(query = query, baseClass = baseClass, packagePath = packagePath, limit = limit)
        )
        return response.assets.map { GameEngineAssetInfo(it.assetPath, it.assetName, it.baseClass, it.assetClass) }
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
