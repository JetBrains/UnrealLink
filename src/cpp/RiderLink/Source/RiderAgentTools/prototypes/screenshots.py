# Dev-time prototype for the screenshot MCP tool path.
#
# Iterated via Rider's MCP `ue_execute_python` against a connected Unreal
# Editor. Three capture surfaces are explored here so we can map each one
# onto its production seam in Phase 2 (C++ in RiderAgentTools + RD model
# extension + generic Game Tools MCP endpoint):
#
#   1. request_editor_window()  — the whole UE Editor application window
#   2. request_viewport()        — a specific viewport (level / PIE / etc.)
#   3. request_asset_preview()   — preview pane in BP / Material / Anim / Niagara
#                                  editors; tries the asset thumbnail index
#                                  cache first, falls back to live capture
#
# IMPORTANT — threading model:
#   `ue_execute_python` runs scripts on UE's game thread. Most UE screenshot
#   APIs are async: they enqueue a request that the *next* tick consumes and
#   writes to disk. Blocking the game thread from Python (sleeping or polling
#   from inside the script) deadlocks the request — the tick never runs.
#
#   So this prototype intentionally splits "trigger" from "wait":
#     * The `request_*` functions enqueue a capture and return immediately
#       with the file path UE will write to, and any extra metadata.
#     * The MCP caller (e.g. mcp_test_client.py) polls the path off the game
#       thread until the file appears, or calls `await_file` in a *separate*
#       ue_execute_python call once enough ticks have elapsed.
#
# Output strategy for the prototype: each function returns a metadata dict
# (path + which API path was used). The RD transport question (file path vs
# base64 blob) is deferred to Phase 2 — the prototype's job is to discover
# which surfaces are reachable from Python at all, and which will need a C++
# Slate helper.
#
# Quick run from Rider MCP:
#   ue_execute_python(script=<contents of this file>)
# or, with parameters:
#   ue_execute_python(script="exec(open(r'…/screenshots.py').read()); "
#                            "print(json.dumps(capture_viewport()))")
#
# Not packaged with the plugin — checked in for reproducible iteration only.
#
# ----------------------------------------------------------------------------
# Phase 2 hand-off notes (validated against UE 5.7 source under
# D:/EpicGames/UE_5.7/Engine/Source — file paths quoted are relative to there).
#
# Single primitive that unifies surfaces 2 and 4:
#
#   SLATE_API bool FSlateApplication::TakeScreenshot(
#       const TSharedRef<SWidget>& Widget,
#       TArray<FColor>& OutColorData,
#       FIntVector& OutSize);
#   // also overload with an FIntRect InnerWidgetArea for sub-rect capture.
#   //   Runtime/Slate/Public/Framework/Application/SlateApplication.h:1038/1054
#
# Pass an SWindow → whole window screenshot. Pass any SWidget → that widget
# only. Output is raw FColor (BGRA8). Encode to PNG via IImageWrapperModule
# (Runtime/ImageWrapper). Engine itself uses this path
# (Runtime/Engine/Private/GameViewportClient.cpp:2296).
#
# [x] request_viewport        — Python OK today.
#       `unreal.AutomationLibrary.take_high_res_screenshot(filename=<abs>)`
#       honours absolute paths and writes a valid PNG within a few game-thread
#       ticks. C++ replacement when we move off the Python detour:
#         FSlateApplication::Get().TakeScreenshot(
#             ActiveLevelViewportWidget, OutColorData, OutSize);
#       — get the viewport widget via LevelEditor module's
#       ILevelEditor::GetActiveViewportInterface()->GetViewportWidget(), or
#       fall through to the simpler whole-window path for the active editor
#       window.
#
# [ ] request_editor_window   — Phase 2 path validated.
#         TSharedPtr<SWindow> W = FSlateApplication::Get().GetActiveTopLevelWindow();
#           // Runtime/Slate/.../SlateApplication.h:1598
#         FSlateApplication::Get().TakeScreenshot(W.ToSharedRef(), Colors, Size);
#       Captures chrome + viewport + docked panels of the focused top-level
#       window. For "the main Unreal Editor frame" specifically, use
#       GetActiveTopLevelRegularWindow() (line 1600) to skip transient popups.
#
# [ ] request_asset_preview   — thumbnail-cache path validated.
#       Editor-only APIs in
#       Editor/UnrealEd/Public/ObjectTools.h:743 namespace ThumbnailTools:
#         * FindCachedThumbnail(InFullName)                     // line 795
#         * GetThumbnailForObject(UObject*)                     // line 798
#         * LoadThumbnailFromPackage(FAssetData, FObjectThumbnail&)  // 801
#         * ConditionallyLoadThumbnailsForObjects(FullNames, OutMap) // 813
#         * RenderThumbnail(Obj, W, H, FlushMode, RT, OutThumbnail)  // 770
#           — for forcing a live re-render of a stale thumbnail.
#       FObjectThumbnail (Runtime/Core/Public/Misc/ObjectThumbnail.h):
#         GetImage() returns FImageView (BGRA8-sRGB) → encode via ImageWrapper.
#
# [ ] request_asset_preview   — live pane path validated.
#       Two-step:
#         1. IAssetEditorInstance* E =
#                UAssetEditorSubsystem::FindEditorForAsset(asset, /*focus*/true);
#            // Editor/UnrealEd/Public/Subsystems/AssetEditorSubsystem.h:153
#         2. After focus, capture the asset editor's window:
#            TakeScreenshot(GetActiveTopLevelWindow().ToSharedRef(), ...)
#       For preview-pane-only crop instead of whole-editor-window:
#         walk the SWidget tree down from that SWindow and pick the first
#         descendant whose class is derived from SEditorViewport, then
#         TakeScreenshot(thatWidget, ...). One walker covers BP / Material /
#         Anim / Niagara because they all host an SEditorViewport-derived
#         preview pane.
#
# Module deps to add in RiderAgentTools.Build.cs (under WITH_EDITOR guard
# in code; AddRangeForTarget on PrivateDependencyModuleNames):
#   Slate, SlateCore, UnrealEd, EditorFramework (for AssetEditor context),
#   LevelEditor (for the active level-viewport widget),
#   ImageWrapper (for FColor → PNG encoding).
#
# Storage location & MCP return contract (locked):
#   * Screenshots are written to <Project>/Saved/Screenshots/<Platform>/RiderMCP/
#     — same root as `FPaths::ScreenShotDir()` so we follow UE's own
#     convention; `RiderMCP/` subfolder namespaces our outputs.
#   * Filenames are timestamp-prefixed (YYYYMMDD-HHMMSS_<kind>.png).
#   * Path handling is split:
#       - The path handed to UE for saving is **project-relative**
#         (`Saved/Screenshots/RiderMCP/<file>.png`). This lets the engine
#         keep its canonical path semantics and remains portable if the
#         project relocates.
#       - The path returned in the MCP response is **absolute** so the
#         MCP client can open the file without knowing the project layout.
#         C++ Phase 2 uses `FPaths::ConvertRelativePathToFull` for this.
#   * The MCP tool returns the absolute path as a string in its JSON result —
#     NO binary blob over RD. Keeps the RD model small (one FString path +
#     dimensions) and avoids size-limit concerns; the MCP client reads the
#     file off disk after the call returns.
# ----------------------------------------------------------------------------

import json
import os
import time

import unreal


# Where screenshots land.
#
# Decision: use UE's own screenshot dir under the project — same place
# `FPaths::ScreenShotDir()` returns and where `HighResShot` / `shot` console
# commands write by default. We append a `RiderMCP/` subfolder so our files
# are clearly namespaced and easy to clean up. This is the same convention
# the production C++ tool will use, so paths returned by the prototype look
# exactly like the paths the MCP endpoint will return in Phase 2.
#
# Resolved lazily because `unreal.Paths` is only importable on the game
# thread once the editor is up.


_SAVE_DIR_REL = os.path.join("Saved", "Screenshots", "RiderMCP")


def _ensure_output_dir():
    """Resolve & create the screenshot output dir.

    Two-tier path handling — UE itself works with relative paths; the MCP
    response carries absolute. This function creates the dir on disk using
    the absolute path and returns the absolute form for response use.

    Returns the absolute path (resolved from UE's engine-relative
    `project_saved_dir()` via `convert_relative_path_to_full`).
    """
    saved_abs = unreal.Paths.convert_relative_path_to_full(unreal.Paths.project_saved_dir())
    root_abs = os.path.normpath(os.path.join(saved_abs, "Screenshots", "RiderMCP"))
    os.makedirs(root_abs, exist_ok=True)
    return root_abs


def _to_absolute(path):
    """Resolve any UE-style relative path (`../../...`) to absolute."""
    return os.path.normpath(unreal.Paths.convert_relative_path_to_full(path))


# Kept for compatibility with the existing __main__ driver and any external
# script that prints output paths. Resolved lazily so import-time evaluation
# in a non-editor context doesn't fail.
_OUTPUT_ROOT = "<resolved lazily; see _ensure_output_dir()>"


def _resolve_output_path(name_hint, output_path=None, ext=".png"):
    """Return (save_path, response_path) for a new screenshot.

      * `save_path` — the path we hand to UE. Project-relative
        (`Saved/Screenshots/RiderMCP/<stamp>_<name>.png`) so the engine
        treats it like its own outputs and the on-disk layout is portable
        if the project moves.
      * `response_path` — the absolute path returned to the MCP caller.

    `name_hint` is a short kind label ("viewport", "window",
    "preview_<asset>"). Caller-supplied `output_path` wins if absolute and
    is used verbatim for both.
    """
    if output_path and os.path.isabs(output_path):
        os.makedirs(os.path.dirname(output_path) or ".", exist_ok=True)
        return output_path, output_path

    # Ensure the absolute dir exists on disk; the save path itself is
    # expressed relative to the project so UE can keep its own canonical
    # path semantics, and the absolute form is computed for the response.
    abs_root = _ensure_output_dir()
    stamp = time.strftime("%Y%m%d-%H%M%S")
    safe = "".join(c if c.isalnum() or c in "-_." else "_" for c in name_hint)
    filename = f"{stamp}_{safe}{ext}"

    save_path = os.path.join(_SAVE_DIR_REL, filename)        # project-relative
    response_path = os.path.join(abs_root, filename)          # absolute
    return save_path, response_path


def _read_png_dimensions(path):
    """Return (width, height) from a PNG header without pulling Pillow.

    UE writes a PNG with the standard 8-byte signature followed by the IHDR
    chunk; bytes 16-23 are big-endian width and height.
    """
    try:
        with open(path, "rb") as f:
            head = f.read(24)
        if len(head) >= 24 and head[:8] == b"\x89PNG\r\n\x1a\n":
            width = int.from_bytes(head[16:20], "big")
            height = int.from_bytes(head[20:24], "big")
            return width, height
    except OSError:
        pass
    return None, None


def await_file(path, timeout_sec=10.0, poll_sec=0.1):
    """Off-game-thread helper: poll `path` until it exists and stops growing.

    DO NOT call this from inside `ue_execute_python` — blocking the game
    thread prevents the tick that writes the file. Call it from the MCP
    test client (or any thread that is NOT UE's game thread) AFTER calling
    one of the `request_*` functions.

    Returns (ok, width, height, sizeBytes).
    """
    deadline = time.monotonic() + timeout_sec
    last_size = -1
    stable_ticks = 0
    while time.monotonic() < deadline:
        if os.path.exists(path):
            size = os.path.getsize(path)
            if size > 0 and size == last_size:
                stable_ticks += 1
                if stable_ticks >= 2:
                    w, h = _read_png_dimensions(path)
                    return True, w, h, size
            else:
                stable_ticks = 0
            last_size = size
        time.sleep(poll_sec)
    if os.path.exists(path) and os.path.getsize(path) > 0:
        w, h = _read_png_dimensions(path)
        return True, w, h, os.path.getsize(path)
    return False, None, None, 0


# ---------------------------------------------------------------------------
# UE screenshot trigger primitives.
#
# All `request_*` functions are FIRE-AND-FORGET: they enqueue a capture and
# return IMMEDIATELY with the path(s) the next tick is expected to write to,
# plus a `triggeredAtUtc` timestamp. The caller is responsible for waiting
# OFF the game thread (see `await_file` and the test driver below).
# ---------------------------------------------------------------------------


def _project_screenshots_dir():
    """Default sink for `shot` / `HighResShot` console commands.

    UE writes to `<ProjectSaved>/Screenshots/<PlatformDir>/` with an
    auto-numbered filename. We list this directory after the trigger
    to discover what was produced.
    """
    try:
        saved = unreal.Paths.project_saved_dir()
    except Exception:
        return None
    # On Windows-host editors UE uses the "WindowsEditor" subfolder;
    # on others it's "Mac" / "Linux". Just point at the parent and
    # let the waiter scan recursively.
    return os.path.join(saved, "Screenshots")


# ---------------------------------------------------------------------------
# 1. Whole editor window
# ---------------------------------------------------------------------------

def request_editor_window(output_path=None):
    """Trigger a whole-editor-window capture (fire-and-forget).

    Pure-Python coverage is limited: `FSlateApplication` and the SWindow
    tree are not exposed to the Python API surface in UE 5.x. The reachable
    paths are:

      a) Console command "shot" — captures the active viewport only, NOT the
         surrounding editor chrome. Does not satisfy the requirement but is
         the only Python-reachable option today.
      b) Future C++ helper that walks SWindow children and rasterises via
         FSlateRenderer::CopyWindowTo. Tracked as a Phase 2 task.

    Returns the *candidate sink directory* — UE picks the filename — plus a
    timestamp the waiter uses to filter freshly-created files.
    """
    triggered_at = time.time()
    sink_dir = _to_absolute(_project_screenshots_dir()) if _project_screenshots_dir() else None

    err = None
    try:
        unreal.SystemLibrary.execute_console_command(None, "shot")
        source_api = "console:shot"
    except Exception as exc:
        err = repr(exc)
        source_api = None

    return {
        "ok": err is None,
        "error": err,
        "sourceApi": source_api,
        "sinkDir": sink_dir,
        "triggeredAtUtc": triggered_at,
        "expectedPath": None,  # filename chosen by UE
        "phase2Note": (
            "Full-window capture needs a C++/Slate helper; Python lacks an SWindow accessor. "
            "'console:shot' here only captures the active viewport, not editor chrome."
        ),
    }


# ---------------------------------------------------------------------------
# 2. Specific viewport
# ---------------------------------------------------------------------------

def request_viewport(viewport="active_level", res_x=0, res_y=0, output_path=None):
    """Trigger a viewport capture (fire-and-forget).

    Strategy: try two engine paths so the prototype reports which one
    actually produces a file on this UE version. Both are async — neither
    blocks the game thread.

      * `AutomationLibrary.take_high_res_screenshot(filename=<abs>)` —
        nominally writes to the given absolute path. May silently no-op
        outside automation runs on some 5.x builds.
      * `SystemLibrary.execute_console_command(None, "shot")` —
        writes to <ProjectSaved>/Screenshots/<Platform>/ with an
        auto-numbered name.

    The caller polls BOTH `expectedPath` and `sinkDir` (newer than
    `triggeredAtUtc`) and accepts whichever appears first.

    `viewport`: logical selector for future expansion (level / pie /
    <editor>_preview). Prototype handles only "active_level".
    """
    triggered_at = time.time()
    if viewport != "active_level":
        return {
            "ok": False,
            "sourceApi": None,
            "viewport": viewport,
            "error": f"viewport selector {viewport!r} not implemented in prototype",
            "phase2Note": "Non-active viewport selectors require Slate-side targeting (C++).",
        }

    save_path, response_path = _resolve_output_path(f"viewport_{viewport}", output_path)
    sink_dir_raw = _project_screenshots_dir()
    sink_dir = _to_absolute(sink_dir_raw) if sink_dir_raw else None
    apis_tried = []
    errors = []

    # Hand UE the project-relative path; UE resolves it against its own
    # canonical root. We expose the absolute form on the response so the
    # MCP client doesn't have to know where the project lives.
    try:
        unreal.AutomationLibrary.take_high_res_screenshot(
            res_x=res_x,
            res_y=res_y,
            filename=save_path,
        )
        apis_tried.append("AutomationLibrary.take_high_res_screenshot")
    except Exception as exc:
        errors.append(f"take_high_res_screenshot: {exc!r}")

    try:
        unreal.SystemLibrary.execute_console_command(None, "shot")
        apis_tried.append("console:shot")
    except Exception as exc:
        errors.append(f"console:shot: {exc!r}")

    return {
        "ok": bool(apis_tried),
        "sourceApi": apis_tried,
        "viewport": viewport,
        "expectedPath": response_path,    # absolute, for the MCP client
        "savePath": save_path,            # project-relative, what UE writes
        "sinkDir": sink_dir,
        "triggeredAtUtc": triggered_at,
        "errors": errors,
    }


# ---------------------------------------------------------------------------
# 3. Asset preview (Blueprint / Material / Animation / Niagara / …)
# ---------------------------------------------------------------------------

def _try_cached_thumbnail(asset_path, out_path):
    """Try to dump the AssetRegistry thumbnail cache to a PNG.

    Returns True on success. The prototype tries every reachable Python seam
    and records which one worked so Phase 2 can pick the cleanest C++ path.

    Notes on UE 5.x Python surface:
      * `unreal.EditorAssetLibrary` has no `get_thumbnail` accessor (the
        thumbnail subsystem is C++ only).
      * `unreal.ContentBrowserAssetExtraData` / `unreal.AssetThumbnail`
        are not exposed.
      * The `ContentBrowserExtraData` package thumbnail bytes can sometimes
        be read off `FAssetData` tags, but that's the in-package thumbnail
        only and is not present for transient assets.

    => The prototype currently returns False; we record this so Phase 2 wires
    the thumbnail readback via `ThumbnailTools::ConditionallyLoadThumbnailsForObjects`
    in C++.
    """
    return False


def _try_live_preview_capture(asset_path, out_path):
    """Try to open the asset editor and capture its preview viewport.

    Same problem as `capture_editor_window`: Python has no handle on the
    asset editor's preview SViewport. Best we can do from Python is open
    the editor (which becomes the focused window) and then call
    `take_high_res_screenshot`, which captures the active *level* viewport,
    not the editor's preview pane. So we record that the live path needs C++.

    Returns True on success, False otherwise. The prototype always returns
    False so callers see the gap.
    """
    try:
        asset = unreal.load_asset(asset_path)
    except Exception:
        return False
    if asset is None:
        return False

    try:
        # Opens the asset editor; useful as a side-effect for manual verification
        # but does not produce a preview-pane PNG on its own.
        ales = unreal.get_editor_subsystem(unreal.AssetEditorSubsystem)
        ales.open_editor_for_assets([asset])
    except Exception:
        pass
    return False


def request_asset_preview(asset_path, output_path=None, force_live=False):
    """Trigger an asset-preview capture (fire-and-forget).

    Strategy:
      1. Unless `force_live`, try the thumbnail-cache path
         (`_try_cached_thumbnail`). This is the "index cache" requirement —
         the AssetRegistry's pre-rendered thumbnails answer "what does this
         asset look like in the content browser" without opening any editor.
      2. If that fails, fall back to opening the asset editor and triggering
         a live preview capture (`_try_live_preview_capture`).
      3. Report which path actually worked, or surface the Phase 2 gap.

    `asset_path` is a long package path like `/Game/Foo/BP_Hero`.

    The cache path is synchronous (we already have bytes in memory) so it
    can complete inside this call and return `ok=True` immediately. The
    live path is async; if taken, the caller still has to poll.
    """
    triggered_at = time.time()
    save_path, response_path = _resolve_output_path(
        f"preview_{asset_path.replace('/', '_').strip('_')}", output_path
    )

    used = None
    immediate_ok = False
    sink_dir = None
    if not force_live and _try_cached_thumbnail(asset_path, response_path):
        used = "thumbnail_cache"
        immediate_ok = True

    if not immediate_ok:
        if _try_live_preview_capture(asset_path, save_path):
            used = "live_editor_pane"
            sink_dir_raw = _project_screenshots_dir()
            sink_dir = _to_absolute(sink_dir_raw) if sink_dir_raw else None

    width, height = _read_png_dimensions(response_path) if immediate_ok else (None, None)
    return {
        "ok": immediate_ok or used == "live_editor_pane",
        "completedSynchronously": immediate_ok,
        "sourceApi": used,
        "assetPath": asset_path,
        "expectedPath": response_path if (immediate_ok or used == "live_editor_pane") else None,
        "savePath": save_path if used == "live_editor_pane" else None,
        "sinkDir": sink_dir,
        "triggeredAtUtc": triggered_at,
        "width": width,
        "height": height,
        "phase2Note": (
            "Neither thumbnail-cache nor live-pane capture is reachable from Python in UE 5.x. "
            "Phase 2 must add a C++ helper: ThumbnailTools::ConditionallyLoadThumbnailsForObjects "
            "for the cached path, and SWidget-tree walk to the asset editor's SViewport for the live path."
        ),
    }


# ---------------------------------------------------------------------------
# Dev driver — invoked when the module is run as the top-level script.
# Triggers every surface so a single ue_execute_python call enqueues all
# three captures; the MCP client then waits on the returned paths/dirs.
# ---------------------------------------------------------------------------

if __name__ == "__main__":
    report = {
        "outputRoot": _ensure_output_dir(),
        "viewport": request_viewport(),
        "editorWindow": request_editor_window(),
        "assetPreview": request_asset_preview(
            "/Game/StarterContent/Blueprints/Blueprint_CeilingLight"
        ),
    }
    print(json.dumps(report, indent=2))
