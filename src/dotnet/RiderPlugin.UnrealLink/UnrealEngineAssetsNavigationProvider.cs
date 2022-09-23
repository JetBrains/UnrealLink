using JetBrains.ProjectModel;
using JetBrains.ReSharper.Feature.Services.Cpp.UE4.UEAsset.Reader;
using JetBrains.ReSharper.Feature.Services.Cpp.UE4.UEAsset.Search;
using JetBrains.Util;
using RiderPlugin.UnrealLink.Model;

namespace RiderPlugin.UnrealLink
{
	[SolutionComponent]
	public class UnrealEngineAssetsNavigationProvider : IUnrealEngineNavigationProvider
	{
		private readonly RiderBackendToUnrealEditor myBackendToUnrealEditor;

		public UnrealEngineAssetsNavigationProvider(RiderBackendToUnrealEditor backendToUnrealEditor)
		{
			myBackendToUnrealEditor = backendToUnrealEditor;
		}
		
		public bool Navigate(VirtualFileSystemPath assetPath, UEObjectExport objectExport, string guid)
		{
			var model = myBackendToUnrealEditor.EditorModel;
			if (model == null)
			{
				return false;
			}
			
			model.OpenBlueprint.Fire(new BlueprintReference(new FString(assetPath.NormalizeSeparators(FileSystemPathEx.SeparatorStyle.Unix)), new FString(guid ?? "")));
			return true;
		}
	}
}