# Dev-time prototype for the live asset-search path.
#
# Iterated via Rider's MCP `ue_execute_python` tool against a connected Unreal
# Editor. Once the algorithm here matches expectations (results overlap with
# the on-disk index for known assets, and include newly-created/unindexed
# ones), the same logic is re-implemented in C++ in `AssetRegistrySearcher`
# next to this folder (see ../Private/AssetRegistrySearcher.cpp).
#
# Not packaged with the plugin — checked in for reproducible iteration only.
#
# Quick run from Rider MCP:
#   ue_execute_python(script=<contents of this file>)
# or, with parameters:
#   ue_execute_python(script="exec(open(r'…/asset_search.py').read()); "
#                            "print(json.dumps(search_assets_live(query='Hero', "
#                                                               "base_class='Character', "
#                                                               "package_path='/Game'), indent=2))")

import json
import unreal


def _class_short_name_variants(short_name):
    """Yield bare + U/A/F prefixed candidates so callers can pass either form.

    UE 5.x Python lacks a stable "find UClass by short name" API
    (`unreal.find_class` does not exist; `unreal.find_object` needs a full
    `/Script/<Module>.<ClassName>` path which we cannot synthesise without
    knowing the owning module). Instead we filter post-fetch by matching
    asset metadata against this candidate set.
    """
    if not short_name:
        return ()
    out = [short_name]
    if short_name[:1] not in {"U", "A", "F"}:
        out += ["U" + short_name, "A" + short_name, "F" + short_name]
    return tuple(out)


def _resolve_classes_by_short_name(class_candidates):
    """Walk every loaded UClass, return those whose short name is in the candidate set.

    UE Python lacks a direct "find UClass by short name" — `find_object` requires
    a full /Script/<Module>.<Name> path that we cannot synthesise. ClassIterator
    enumerates all loaded native + blueprint-generated UClasses, which is good
    enough for the live path (the editor has them all loaded once Python runs).
    """
    if not class_candidates:
        return []
    found = []
    # ClassIterator requires a root type; unreal.Object covers everything.
    for cls in unreal.ClassIterator(unreal.Object):
        try:
            if cls.get_name() in class_candidates:
                found.append(cls)
        except Exception:
            pass
    return found


def _short_name_from_class_path(class_path_str):
    """Reduce a class path to its short class name.

    Inputs we see in the wild:
      "/Script/Engine.Character"           -> "Character"
      "/Game/.../BP_Foo.BP_Foo_C"          -> "BP_Foo" (the _C suffix marks a class object)
      "Class'/Script/Engine.Character'"    -> "Character"
    """
    if not class_path_str:
        return ""
    s = class_path_str.strip().strip("'").strip('"')
    # Some tag values use the "ClassName'Path'" form — strip the prefix.
    if "'" in s:
        s = s.split("'")[-1]
    s = s.split(".")[-1]
    if s.endswith("_C"):
        s = s[:-2]
    return s


def _asset_matches_class(asset_data, class_candidates):
    """True if this AssetData represents — or derives from — one of the names."""
    if not class_candidates:
        return True
    # Direct match against the asset's own class (e.g. Blueprint, Texture2D).
    cls_path = asset_data.asset_class_path
    if cls_path and str(cls_path.asset_name) in class_candidates:
        return True
    # For Blueprint assets, the meaningful class is the parent — stored as a
    # tag "ParentClass" (Blueprint) or "NativeParentClass" (BP descendants).
    for tag in ("ParentClass", "NativeParentClass"):
        raw = asset_data.get_tag_value(tag)
        if not raw:
            continue
        short = _short_name_from_class_path(raw)
        if short in class_candidates:
            return True
    return False


def _to_disk_path(package_name):
    """Best-effort conversion of "/Game/Foo/Bar" -> absolute "<Proj>/Content/Foo/Bar.uasset".

    The C++ implementation will use FPackageName::LongPackageNameToFilename, which
    handles plugin-mounted roots correctly. Here we approximate using
    unreal.Paths so the prototype output is comparable to cache results
    for the /Game root; non-/Game roots fall back to the package name.
    """
    if package_name.startswith("/Game/") or package_name == "/Game":
        try:
            content_dir = unreal.Paths.project_content_dir()
            rel = package_name[len("/Game"):].lstrip("/")
            return unreal.Paths.convert_relative_path_to_full(
                content_dir + rel + ".uasset"
            )
        except Exception:
            pass
    return package_name


def search_assets_live(query=None, base_class=None, package_path=None, limit=200):
    ar = unreal.AssetRegistryHelpers.get_asset_registry()

    # ARFilter exposes its fields read-only on instances — pass values via the
    # constructor only. Mutating .package_paths post-construction raises
    # "cannot be edited on instances".
    paths = [package_path] if package_path else ["/Game"]

    # Resolve base_class -> set of TopLevelAssetPath via ClassIterator; with
    # recursive_classes=True AssetRegistry then returns every derived asset
    # natively (no manual ancestor walk required).
    class_paths = []
    class_short_match = set()
    if base_class:
        candidates = set(_class_short_name_variants(base_class))
        class_short_match = candidates
        for cls in _resolve_classes_by_short_name(candidates):
            try:
                class_paths.append(unreal.TopLevelAssetPath(cls.get_path_name()))
            except Exception:
                # cls.get_path_name() returns "/Script/<Module>.<Name>"; the
                # 2-arg form (package, asset) is the documented constructor.
                pn = cls.get_path_name()
                if "." in pn:
                    package, asset = pn.rsplit(".", 1)
                    class_paths.append(unreal.TopLevelAssetPath(package, asset))

    kwargs = dict(package_paths=paths, recursive_paths=True, recursive_classes=True)
    if class_paths:
        kwargs["class_paths"] = class_paths
    f = unreal.ARFilter(**kwargs)
    assets = ar.get_assets(f)

    # If base_class was given but no UClass resolved (class not loaded yet),
    # fall back to tag-based shallow match so the prototype still returns
    # something useful while we iterate.
    use_tag_fallback = bool(base_class) and not class_paths

    needle = (query or "").lower()
    out = []
    for asset_data in assets:
        name = str(asset_data.asset_name)
        if needle and needle not in name.lower():
            continue
        if use_tag_fallback and not _asset_matches_class(asset_data, class_short_match):
            continue
        out.append({
            "assetPath": _to_disk_path(str(asset_data.package_name)),
            "assetName": name,
            "assetClass": str(asset_data.asset_class_path.asset_name) if asset_data.asset_class_path else None,
            "baseClass": base_class,
        })
        if len(out) >= limit:
            break
    return out


# Dev driver — edit the literals and rerun via ue_execute_python.
if __name__ == "__main__":
    sample = search_assets_live(query="Hero", base_class="Character", package_path="/Game", limit=50)
    print(json.dumps({"count": len(sample), "assets": sample}, indent=2))
