using JetBrains.Collections.Viewable;
using JetBrains.Lifetimes;
using JetBrains.Platform.Unreal.EditorPluginModel;
using JetBrains.ProjectModel;

namespace RiderPlugin.UnrealLink.Notifications
{
    [SolutionComponent]
    public class HotReloadWorking
    {
        private SolutionConfiguration _solutionConfiguration = null;
        public HotReloadWorking(Lifetime lifetime, RiderBackendToUnrealEditor backendToEditor)
        {
            backendToEditor.EditorModel.AdviseNotNull(lifetime, model =>
            {
                model.SolutionConfiguration.AdviseNotNull(lifetime,
                    configuration => _solutionConfiguration = configuration);
            });
        }
    }
}