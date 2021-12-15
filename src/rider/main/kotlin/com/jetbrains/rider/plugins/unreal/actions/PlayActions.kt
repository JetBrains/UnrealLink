package com.jetbrains.rider.plugins.unreal.actions

import com.intellij.icons.AllIcons
import com.intellij.notification.Notification
import com.intellij.notification.NotificationGroup
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.jetbrains.rd.platform.util.idea.LifetimedService
import com.jetbrains.rd.util.reactive.fire
import com.jetbrains.rider.UnrealLinkBundle
import com.jetbrains.rider.plugins.unreal.UnrealHost
import com.jetbrains.rider.plugins.unreal.model.PlayState
import com.jetbrains.rider.plugins.unreal.model.RequestFailed
import com.jetbrains.rider.plugins.unreal.model.RequestSucceed
import com.jetbrains.rider.plugins.unreal.model.frontendBackend.rdRiderModel
import com.jetbrains.rider.projectView.solution
import icons.UnrealIcons
import javax.swing.Icon
import com.jetbrains.rider.plugins.unreal.model.NotificationType as ReplyNotificationType

@Service(Service.Level.PROJECT)
class PlayStateActionStateService(val project: Project) : LifetimedService() {
    companion object {
        fun getInstance(project: Project): PlayStateActionStateService = project.service()
        private const val NOTIFICATION_GROUP_DISPLAY_ID = "RiderLink"
        private val notificationGroupId =
                NotificationGroup.createIdWithTitle(
                        NOTIFICATION_GROUP_DISPLAY_ID,
                        UnrealLinkBundle.message("notification.RiderLink.ReplyFromEditor.groupId")
                )
    }

    private var disabledUntilModelChange: Boolean = false
    private var currentRequestID: Int = 0

    init {
        val host = UnrealHost.getInstance(project)
        host.performModelAction { model ->
            model.isConnectedToUnrealEditor.change.advise(serviceLifetime) {
                invalidate()
            }
            model.notificationReplyFromEditor.advise(serviceLifetime) {
                if (it.requestID != currentRequestID) return@advise
                when (it) {
                    is RequestSucceed -> invalidate()
                    is RequestFailed -> {
                        invalidate()

                        val title = UnrealLinkBundle.message("notification.RiderLink.ReplyFromEditor.title")
                        val message = it.message.data
                        val type = when (it.type){
                            ReplyNotificationType.Message -> NotificationType.INFORMATION
                            ReplyNotificationType.Error -> NotificationType.ERROR
                        }
                        val notification = Notification(notificationGroupId, title, message, type)
                        Notifications.Bus.notify(notification, project)
                    }
                }
            }
        }
        host.playStateModel.change.advise(serviceLifetime) {
            invalidate()
        }
    }

    fun invalidate() {
        disabledUntilModelChange = false
        forceTriggerUIUpdate()
    }

    fun disableUntilStateChange() {
        disabledUntilModelChange = true
    }

    fun isDisabledUntilStateChange() = disabledUntilModelChange

    fun nextRequestID() : Int {
        currentRequestID++
        return currentRequestID
    }
}

abstract class PlayStateAction(text: String?, description: String?, icon: Icon?) : DumbAwareAction(text, description, icon) {
    override fun update(e: AnActionEvent) {
        val project = e.project
        if (project == null) {
            e.presentation.isEnabledAndVisible = false
            return
        }
        val host = UnrealHost.getInstance(project)

        e.presentation.isVisible = host.isUnrealEngineSolution
        e.presentation.isEnabled = host.isConnectedToUnrealEditor &&
                host.model.isGameControlModuleInitialized.value

        if (e.presentation.isEnabled) {
            val state = PlayStateActionStateService.getInstance(project)
            e.presentation.isEnabled = !state.isDisabledUntilStateChange()
        }
    }
}

class PlayInUnrealAction : PlayStateAction(
    UnrealLinkBundle.message("action.RiderLink.PlayInUnrealAction.text"),
    UnrealLinkBundle.message("action.RiderLink.PlayInUnrealAction.description"),
    UnrealIcons.Status.Play
) {
    override fun update(e: AnActionEvent) {
        super.update(e)
        val host = e.getHost() ?: return
        e.presentation.isEnabled = e.presentation.isEnabled && host.playState == PlayState.Idle
        e.presentation.isVisible = e.presentation.isVisible && host.playState == PlayState.Idle
    }

    override fun actionPerformed(e: AnActionEvent) {
        val host = e.getHost() ?: return
        val state = PlayStateActionStateService.getInstance(host.project)
        state.disableUntilStateChange()
        host.model.requestPlayFromRider.fire(state.nextRequestID())
    }
}

class ResumeInUnrealAction : PlayStateAction(
        UnrealLinkBundle.message("action.RiderLink.ResumeInUnrealAction.text"),
        UnrealLinkBundle.message("action.RiderLink.ResumeInUnrealAction.description"),
        UnrealIcons.Status.Play
) {
    override fun update(e: AnActionEvent) {
        super.update(e)
        val host = e.getHost() ?: return
        e.presentation.isEnabled = e.presentation.isEnabled && host.playState == PlayState.Pause
        e.presentation.isVisible = e.presentation.isVisible && host.playState != PlayState.Idle
    }

    override fun actionPerformed(e: AnActionEvent) {
        val host = e.getHost() ?: return
        val state = PlayStateActionStateService.getInstance(host.project)
        state.disableUntilStateChange()
        host.model.requestResumeFromRider.fire(state.nextRequestID())
    }
}

class StopInUnrealAction : PlayStateAction(
    UnrealLinkBundle.message("action.RiderLink.StopInUnrealAction.text"),
    UnrealLinkBundle.message("action.RiderLink.StopInUnrealAction.description"),
    UnrealIcons.Status.Stop
) {
    override fun update(e: AnActionEvent) {
        super.update(e)
        val host = e.getHost() ?: return
        e.presentation.isEnabled = e.presentation.isEnabled && host.playState != PlayState.Idle
    }

    override fun actionPerformed(e: AnActionEvent) {
        val host = e.getHost() ?: return
        val state = PlayStateActionStateService.getInstance(host.project)
        state.disableUntilStateChange()
        host.model.requestStopFromRider.fire(state.nextRequestID())
    }
}

class PauseInUnrealAction : PlayStateAction(
    UnrealLinkBundle.message("action.RiderLink.PauseInUnrealAction.text"),
    UnrealLinkBundle.message("action.RiderLink.PauseInUnrealAction.description"),
    UnrealIcons.Status.Pause
) {
    override fun update(e: AnActionEvent) {
        super.update(e)
        val host = e.getHost() ?: return
        e.presentation.isEnabled = e.presentation.isEnabled && host.playState == PlayState.Play
        e.presentation.isVisible = e.presentation.isVisible && host.playState != PlayState.Pause
    }

    override fun actionPerformed(e: AnActionEvent) {
        val host = e.getHost() ?: return
        val state = PlayStateActionStateService.getInstance(host.project)
        state.disableUntilStateChange()
        host.model.requestPauseFromRider.fire(state.nextRequestID())
    }
}

class SingleStepInUnrealAction : PlayStateAction(
        UnrealLinkBundle.message("action.RiderLink.SkipFrame.text"),
        UnrealLinkBundle.message("action.RiderLink.SkipFrame.description"),
        UnrealIcons.Status.FrameSkip
) {
    override fun update(e: AnActionEvent) {
        super.update(e)
        val host = e.getHost() ?: return
        e.presentation.isEnabled = e.presentation.isEnabled && host.playState == PlayState.Pause
        e.presentation.isVisible = e.presentation.isVisible && host.playState == PlayState.Pause
    }

    override fun actionPerformed(e: AnActionEvent) {
        val host = e.getHost() ?: return
        val state = PlayStateActionStateService.getInstance(host.project)
        state.disableUntilStateChange()
        host.model.requestFrameSkipFromRider.fire(state.nextRequestID())
    }
}

class RefreshProjects : DumbAwareAction(AllIcons.Actions.Refresh) {
    override fun update(e: AnActionEvent) {
        super.update(e)
        val host = e.getHost()
        e.presentation.isVisible = host?.isUnrealEngineSolution ?: false
        e.presentation.isEnabled = false
        if (host == null) return;

        e.presentation.isEnabled = !host.isUproject &&
                !host.isRefreshProjectsInProgress && !host.isRiderLinkInstallationInProgress
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        project.solution.rdRiderModel.refreshProjects.fire()
    }
}