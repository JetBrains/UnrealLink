using JetBrains.ProjectModel;
using JetBrains.ReSharper.Feature.Services.Cpp.UEAsset.Reader;
using JetBrains.ReSharper.Feature.Services.Cpp.UEAsset.Search;
using JetBrains.Unreal.Lib;
using JetBrains.Util;

namespace ReSharperPlugin.UnrealEditor
{
	[SolutionComponent]
	public class UnrealEngineAssetsNavigationProvider : IUnrealEngineNavigationProvider
	{
		private readonly RiderBackendToUnrealEditor myBackendToUnrealEditor;

		public UnrealEngineAssetsNavigationProvider(RiderBackendToUnrealEditor backendToUnrealEditor)
		{
			myBackendToUnrealEditor = backendToUnrealEditor;
		}
		
		public bool Navigate(FileSystemPath assetPath, UEObjectExport objectExport)
		{
			var model = myBackendToUnrealEditor.GetCurrentEditorModel();
			if (model == null)
			{
				return false;
			}
			
			model.Navigate.Fire(new BlueprintStruct(new FString(assetPath.NormalizeSeparators(FileSystemPathEx.SeparatorStyle.Unix)), new FString("")));
			return true;
		}
	}
}