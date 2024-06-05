package com.jetbrains.rider.plugins.unreal

import com.intellij.internal.statistic.StructuredIdeActivity
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector

import com.intellij.openapi.project.Project


object UnrealPluginUsagesCollector : CounterUsagesCollector() {
  override fun getGroup(): EventLogGroup = GROUP

  private val GROUP = EventLogGroup("rider.unreal.debugger", 1)

  private val IS_PLATFORM_SUPPORTED_ARG = EventFields.Boolean("platform_supported")
  private val IS_RIDER_DEBUGGING_SUPPORT_MODULE_AVAILABLE_ARG = EventFields.Boolean("rider_debugging_support_module_available")
  private val IS_SHOW_BLUEPRINT_FRAMES_ENABLED_ARG = EventFields.Enum<OptionalBoolean>("show_blueprint_frames_enabled")
  private val IS_SHOW_UNREAL_FRAMES_ENABLED_ARG = EventFields.Enum<OptionalBoolean>("show_unreal_frames_enabled")

  private val BLUEPRINT_STACK_TRANSFORM_ACTIVITY = GROUP.registerIdeActivity("blueprint_stack_transform",
                                                                             startEventAdditionalFields = arrayOf(
                                                                               IS_PLATFORM_SUPPORTED_ARG,
                                                                               IS_RIDER_DEBUGGING_SUPPORT_MODULE_AVAILABLE_ARG,
                                                                               IS_SHOW_BLUEPRINT_FRAMES_ENABLED_ARG,
                                                                               IS_SHOW_UNREAL_FRAMES_ENABLED_ARG,
                                                                             ))

  private val BATCH_NUMBER_ARG = EventFields.Int("batch_number")
  private val BATCH_SIZE_ARG = EventFields.Int("batch_size")

  private val BLUEPRINT_STACK_TRANSFORM_BATCH_ACTIVITY = GROUP.registerIdeActivity("blueprint_stack_transform_batch",
                                                                                   startEventAdditionalFields = arrayOf(BATCH_NUMBER_ARG,
                                                                                                                        BATCH_SIZE_ARG),
                                                                                   parentActivity = BLUEPRINT_STACK_TRANSFORM_ACTIVITY)
  private val BLUEPRINT_STACK_GETTING_DATA_FROM_DBG_DRIVER_ACTIVITY = GROUP.registerIdeActivity(
    "blueprint_stack_getting_data_from_dbg_driver", parentActivity = BLUEPRINT_STACK_TRANSFORM_BATCH_ACTIVITY)

  enum class OptionalBoolean {
    True, False, Undefined
  }

  private fun Boolean?.toEnum() = when (this) {
    true -> OptionalBoolean.True
    false -> OptionalBoolean.False
    null -> OptionalBoolean.Undefined
  }

  @JvmStatic
  fun startBlueprintStackTransformActivity(project: Project?,
                                           isPlatformSupported: Boolean,
                                           isRiderDebuggingSupportModuleAvailable: Boolean,
                                           isShowBlueprintFramesEnabled: Boolean?,
                                           isShowUnrealFramesEnabled: Boolean?): StructuredIdeActivity? {
    if (project?.isDisposed == true) return null

    return BLUEPRINT_STACK_TRANSFORM_ACTIVITY.started(project) {
      listOf(IS_PLATFORM_SUPPORTED_ARG.with(isPlatformSupported),
             IS_RIDER_DEBUGGING_SUPPORT_MODULE_AVAILABLE_ARG.with(isRiderDebuggingSupportModuleAvailable),
             IS_SHOW_BLUEPRINT_FRAMES_ENABLED_ARG.with(isShowBlueprintFramesEnabled.toEnum()),
             IS_SHOW_UNREAL_FRAMES_ENABLED_ARG.with(isShowUnrealFramesEnabled.toEnum()))
    }
  }

  @JvmStatic
  fun startBlueprintStackTransformBatchActivity(project: Project?,
                                                activity: StructuredIdeActivity?,
                                                batchNumber: Int,
                                                batchSize: Int): StructuredIdeActivity? {
    if (project?.isDisposed == true) return null

    if (activity == null) return null

    return BLUEPRINT_STACK_TRANSFORM_BATCH_ACTIVITY.startedWithParent(project, activity) {
      listOf(BATCH_NUMBER_ARG.with(batchNumber), BATCH_SIZE_ARG.with(batchSize))
    }
  }

  @JvmStatic
  fun startBlueprintStackGettingDataActivity(project: Project?, activity: StructuredIdeActivity?): StructuredIdeActivity? {
    if (project?.isDisposed == true) return null

    if (activity == null) return null

    return BLUEPRINT_STACK_GETTING_DATA_FROM_DBG_DRIVER_ACTIVITY.startedWithParent(project, activity)
  }
}