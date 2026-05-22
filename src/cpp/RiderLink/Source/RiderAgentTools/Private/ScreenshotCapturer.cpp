#include "ScreenshotCapturer.hpp"
#include "RiderAgentTools.hpp"
#include "RdEditorModel/RdEditorModel.Pregenerated.h"

#include "Async/Async.h"
#include "Framework/Application/SlateApplication.h"
#include "HAL/FileManager.h"
#include "IImageWrapper.h"
#include "IImageWrapperModule.h"
#include "ILevelEditor.h"
#include "LevelEditor.h"
#include "Misc/DateTime.h"
#include "Misc/FileHelper.h"
#include "Misc/Paths.h"
#include "Modules/ModuleManager.h"
#include "RenderingThread.h"
#include "SLevelViewport.h"
#include "UObject/SoftObjectPath.h"
#include "Widgets/SWindow.h"

#if WITH_EDITOR
#include "AssetRegistry/AssetData.h"
#include "AssetRegistry/IAssetRegistry.h"
#include "Editor.h"
#include "Misc/ObjectThumbnail.h"
#include "ObjectTools.h"
#include "Subsystems/AssetEditorSubsystem.h"
#endif

namespace
{
    using namespace JetBrains::EditorPlugin;

    // Build a failure ScreenshotResult — collapses the positional-constructor
    // boilerplate so the per-kind handlers stay readable.
    ScreenshotResult Fail(const FString& Error, const FString& SourceApi = FString())
    {
        return ScreenshotResult(/*success=*/false, /*path=*/FString(), /*w=*/0, /*h=*/0,
                                SourceApi, Error);
    }

    ScreenshotResult Ok(const FString& Path, int32 W, int32 H, const FString& SourceApi)
    {
        return ScreenshotResult(/*success=*/true, Path, W, H, SourceApi, /*error=*/FString());
    }

    // ── Filesystem -----------------------------------------------------------

    FString TimestampedFilename(const TCHAR* KindLabel)
    {
        const FDateTime Now = FDateTime::UtcNow();
        return FString::Printf(
            TEXT("%04d%02d%02d-%02d%02d%02d_%s.png"),
            Now.GetYear(), Now.GetMonth(), Now.GetDay(),
            Now.GetHour(), Now.GetMinute(), Now.GetSecond(),
            KindLabel);
    }

    FString EnsureOutputDir()
    {
        // FPaths::ScreenShotDir() is `<Project>/Saved/Screenshots/<Platform>/`.
        // We namespace ours under `RiderMCP/`.
        const FString Dir = FPaths::ConvertRelativePathToFull(
            FPaths::Combine(FPaths::ScreenShotDir(), TEXT("RiderMCP")));
        IFileManager::Get().MakeDirectory(*Dir, /*Tree=*/true);
        return Dir;
    }

    bool EncodeAndWritePng(
        const void* Bytes, int64 NumBytes,
        int32 Width, int32 Height,
        const FString& OutAbsPath)
    {
        if (!Bytes || NumBytes <= 0 || Width <= 0 || Height <= 0)
        {
            return false;
        }
        IImageWrapperModule& WrapperModule = FModuleManager::LoadModuleChecked<IImageWrapperModule>(TEXT("ImageWrapper"));
        const TSharedPtr<IImageWrapper> Png = WrapperModule.CreateImageWrapper(EImageFormat::PNG);
        if (!Png.IsValid() || !Png->SetRaw(Bytes, NumBytes, Width, Height, ERGBFormat::BGRA, /*BitDepth=*/8))
        {
            return false;
        }
        const TArray64<uint8>& Compressed = Png->GetCompressed(100);
        return FFileHelper::SaveArrayToFile(Compressed, *OutAbsPath);
    }

    // ── Slate capture --------------------------------------------------------

    // Slate's TakeScreenshot enqueues a copy on the render thread; we flush
    // so the caller can read OutPixels synchronously from the game thread.
    // Safe for editor-time use; expensive but bounded.
    bool CaptureWidget(
        const TSharedRef<SWidget>& Widget,
        TArray<FColor>& OutPixels,
        FIntVector& OutSize)
    {
        if (!FSlateApplication::IsInitialized())
        {
            return false;
        }
        OutPixels.Reset();
        if (!FSlateApplication::Get().TakeScreenshot(Widget, OutPixels, OutSize))
        {
            return false;
        }
        FlushRenderingCommands();
        return OutPixels.Num() > 0 && OutSize.X > 0 && OutSize.Y > 0;
    }

    // ── Per-kind handlers ---------------------------------------------------

    ScreenshotResult CaptureEditorWindow()
    {
        const FString Api(TEXT("SlateApplication.TakeScreenshot(SWindow)"));
        if (!FSlateApplication::IsInitialized())
        {
            return Fail(TEXT("Slate not initialised"), Api);
        }
        // GetActiveTopLevelRegularWindow() requires UE to currently own focus.
        // When the MCP call lands while Rider (or any other app) is foreground,
        // it returns null. Fall back to the first visible non-tooltip top-level
        // window — that's the editor frame the user actually wants captured.
        TSharedPtr<SWindow> Window = FSlateApplication::Get().GetActiveTopLevelRegularWindow();
        if (!Window.IsValid())
        {
            for (const TSharedRef<SWindow>& Top : FSlateApplication::Get().GetInteractiveTopLevelWindows())
            {
                if (Top->IsVisible() && !Top->IsWindowMinimized())
                {
                    Window = Top;
                    break;
                }
            }
        }
        if (!Window.IsValid())
        {
            return Fail(TEXT("No active or interactive top-level window"), Api);
        }

        TArray<FColor> Pixels;
        FIntVector Size;
        if (!CaptureWidget(Window.ToSharedRef(), Pixels, Size))
        {
            return Fail(TEXT("FSlateApplication::TakeScreenshot failed for window"), Api);
        }

        const FString OutPath = FPaths::Combine(EnsureOutputDir(), TimestampedFilename(TEXT("editor_window")));
        if (!EncodeAndWritePng(Pixels.GetData(), Pixels.Num() * sizeof(FColor), Size.X, Size.Y, OutPath))
        {
            return Fail(TEXT("PNG encode/write failed"), Api);
        }
        return Ok(OutPath, Size.X, Size.Y, Api);
    }

    ScreenshotResult CaptureViewport()
    {
        const FString Api(TEXT("SlateApplication.TakeScreenshot(SLevelViewport)"));
        if (!FSlateApplication::IsInitialized())
        {
            return Fail(TEXT("Slate not initialised"), Api);
        }
        FLevelEditorModule* LevelEditor = FModuleManager::GetModulePtr<FLevelEditorModule>(TEXT("LevelEditor"));
        if (!LevelEditor)
        {
            return Fail(TEXT("LevelEditor module not loaded"), Api);
        }
        TSharedPtr<ILevelEditor> LE = LevelEditor->GetLevelEditorInstance().Pin();
        if (!LE.IsValid())
        {
            return Fail(TEXT("No active level editor instance"), Api);
        }
        TSharedPtr<SLevelViewport> ViewportWidget = LE->GetActiveViewportInterface();
        if (!ViewportWidget.IsValid())
        {
            return Fail(TEXT("Level editor has no active viewport"), Api);
        }

        TArray<FColor> Pixels;
        FIntVector Size;
        if (!CaptureWidget(ViewportWidget.ToSharedRef(), Pixels, Size))
        {
            return Fail(TEXT("FSlateApplication::TakeScreenshot failed for viewport"), Api);
        }

        const FString OutPath = FPaths::Combine(EnsureOutputDir(), TimestampedFilename(TEXT("viewport")));
        if (!EncodeAndWritePng(Pixels.GetData(), Pixels.Num() * sizeof(FColor), Size.X, Size.Y, OutPath))
        {
            return Fail(TEXT("PNG encode/write failed"), Api);
        }
        return Ok(OutPath, Size.X, Size.Y, Api);
    }

#if WITH_EDITOR
    // Resolve a long package path like "/Game/Foo/BP_Hero" to a loaded UObject.
    UObject* ResolveAsset(const FString& AssetPath)
    {
        if (AssetPath.IsEmpty()) return nullptr;
        if (UObject* Loaded = FSoftObjectPath(AssetPath).TryLoad()) return Loaded;
        // BP package paths often need the inner-object suffix added.
        const FString WithSuffix = AssetPath + TEXT(".") + FPaths::GetBaseFilename(AssetPath);
        return FSoftObjectPath(WithSuffix).TryLoad();
    }

    // Try the on-disk thumbnail cache for an asset that isn't loaded into the editor yet.
    // Returns true and fills OutThumb if a stored thumbnail is found in the .uasset's package.
    bool TryLoadThumbnailFromPackage(const FString& AssetPath, FObjectThumbnail& OutThumb)
    {
        // FAssetRegistryModule is the cheapest way to get the FAssetData
        // without forcing a UObject load.
        IAssetRegistry* AR = IAssetRegistry::Get();
        if (!AR) return false;
        const FAssetData Data = AR->GetAssetByObjectPath(FSoftObjectPath(AssetPath));
        if (!Data.IsValid())
        {
            const FString WithSuffix = AssetPath + TEXT(".") + FPaths::GetBaseFilename(AssetPath);
            const FAssetData Retry = AR->GetAssetByObjectPath(FSoftObjectPath(WithSuffix));
            if (!Retry.IsValid()) return false;
            return ThumbnailTools::LoadThumbnailFromPackage(Retry, OutThumb);
        }
        return ThumbnailTools::LoadThumbnailFromPackage(Data, OutThumb);
    }
#endif

    ScreenshotResult CaptureAssetPreview(const FString& AssetPath, int32 RequestedW, int32 RequestedH, bool bForceLive)
    {
#if !WITH_EDITOR
        return Fail(TEXT("Asset preview is editor-only"));
#else
        FString Api;
        FObjectThumbnail Buffer;
        const FObjectThumbnail* Source = nullptr;

        // Step 1 — in-memory cache. Works for any asset that the asset
        // registry has already touched.
        if (!bForceLive)
        {
            // FindCachedThumbnail needs the object full name; only available
            // if the asset is loaded. Try a non-forcing resolve first.
            if (UObject* AlreadyLoaded = FindObject<UObject>(nullptr, *AssetPath))
            {
                if (const FObjectThumbnail* MemCached = ThumbnailTools::FindCachedThumbnail(AlreadyLoaded->GetFullName());
                    MemCached && !MemCached->IsEmpty() && MemCached->HasValidImageData())
                {
                    Source = MemCached;
                    Api = TEXT("ThumbnailTools.CachedThumbnail(Memory)");
                }
            }

            // Step 2 — on-disk thumbnail in the .uasset package. No asset load required.
            if (!Source && TryLoadThumbnailFromPackage(AssetPath, Buffer)
                && !Buffer.IsEmpty() && Buffer.HasValidImageData())
            {
                Source = &Buffer;
                Api = TEXT("ThumbnailTools.LoadThumbnailFromPackage");
            }
        }

        // Step 3 — explicit live render. Skipped unless forceLive is set, because
        // RenderThumbnail can hang on assets that pull in long streaming chains
        // (skeletal mesh / animation BPs) when textures aren't ready. NeverFlush
        // keeps the call bounded: we get a possibly-lower-fidelity image instead
        // of blocking the game thread until streaming finishes.
        if (!Source && bForceLive)
        {
            UObject* Asset = ResolveAsset(AssetPath);
            if (!Asset)
            {
                return Fail(FString::Printf(TEXT("Asset not found or failed to load: %s"), *AssetPath));
            }
            const uint32 W = RequestedW > 0 ? (uint32)RequestedW : (uint32)ThumbnailTools::DefaultThumbnailSize;
            const uint32 H = RequestedH > 0 ? (uint32)RequestedH : (uint32)ThumbnailTools::DefaultThumbnailSize;
            ThumbnailTools::RenderThumbnail(
                Asset, W, H,
                ThumbnailTools::EThumbnailTextureFlushMode::NeverFlush,
                /*RenderTargetResource=*/nullptr,
                &Buffer);
            Source = &Buffer;
            Api = TEXT("ThumbnailTools.RenderThumbnail(NeverFlush)");
        }

        if (!Source || Source->IsEmpty() || !Source->HasValidImageData())
        {
            const TCHAR* Hint = bForceLive
                ? TEXT(": forceLive render produced no data")
                : TEXT(": no cached thumbnail. Open the asset in the editor once, or set forceLive=true.");
            return Fail(FString::Printf(TEXT("No thumbnail available for %s%s"), *AssetPath, Hint), Api);
        }

        const TArray<uint8>& Bytes = Source->GetUncompressedImageData();
        const int32 W = Source->GetImageWidth();
        const int32 H = Source->GetImageHeight();

        const FString OutPath = FPaths::Combine(EnsureOutputDir(),
            TimestampedFilename(*FString::Printf(TEXT("preview_%s"), *FPaths::GetBaseFilename(AssetPath))));
        if (!EncodeAndWritePng(Bytes.GetData(), Bytes.Num(), W, H, OutPath))
        {
            return Fail(TEXT("PNG encode/write failed"), Api);
        }
        return Ok(OutPath, W, H, Api);
#endif
    }

    // Dispatch on the game thread using primitives only (no rd::Wrapper
    // captures across the AsyncTask boundary).
    ScreenshotResult Dispatch(
        ScreenshotKind Kind,
        const FString& AssetPath,
        int32 Width, int32 Height, bool bForceLive)
    {
        check(IsInGameThread());
        switch (Kind)
        {
            case ScreenshotKind::EditorWindow:
                return CaptureEditorWindow();
            case ScreenshotKind::Viewport:
                return CaptureViewport();
            case ScreenshotKind::AssetPreview:
                if (AssetPath.IsEmpty())
                {
                    return Fail(TEXT("assetPath is required for AssetPreview"));
                }
                return CaptureAssetPreview(AssetPath, Width, Height, bForceLive);
        }
        return Fail(TEXT("Unknown ScreenshotKind"));
    }
}

void ScreenshotCapturer::BindTo(rd::Lifetime ModelLifetime, JetBrains::EditorPlugin::RdEditorModel const& Model)
{
    using namespace JetBrains::EditorPlugin;

    Model.get_takeScreenshot().set(
        [](rd::Lifetime, ScreenshotRequest const& Request) -> rd::RdTask<ScreenshotResult>
        {
            rd::RdTask<ScreenshotResult> Task;
            const ScreenshotKind Kind = Request.get_kind();
            const FString AssetPath = Request.get_assetPath().has_value()
                ? Request.get_assetPath().value()
                : FString();
            const int32 Width = Request.get_width();
            const int32 Height = Request.get_height();
            const bool bForceLive = Request.get_forceLive();

            // Capture primitive values from the RD wrapper before the lambda
            // captures — copying the whole ScreenshotRequest across thread
            // boundaries triggers rd::Wrapper lifetime concerns we'd rather
            // avoid; primitives are trivially copyable.
            AsyncTask(ENamedThreads::GameThread,
                [Kind, AssetPath, Width, Height, bForceLive, Task]() mutable
                {
                    Task.set(Dispatch(Kind, AssetPath, Width, Height, bForceLive));
                });
            return Task;
        });
}
