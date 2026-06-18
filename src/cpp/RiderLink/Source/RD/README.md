This is an integration of the C++ version of [RD](https://github.com/JetBrains/rd), an IPC framework from JetBrains.

# How the RD-CPP Build Differs from the UE RD Build

The architecture of RD-CPP prefers to divide the code into multiple static libraries:

* `rd_gen_cpp`
* `thirdparty` (everything from the `thirdparty` folder except `clsocket`)
  * `optional`
  * `mpark_variant`
  * `tsl::ordered_map`
  * `string_view-lite`
  * `ctpl`
  * `countdownlatch`
  * `spdlog`
  * `utf-cpp`
* `rd_core`
  * `thirdparty`
* `rd_framework`
  * `rd_gen_cpp`
  * `rd_core`
  * `rd_framework_util`
  * `clsocket` - technically and structurally part of `thirdparty`, but only used by and linked to `rd_framework`

The UE build system doesn't really support building static libraries separately. We either have one dynamic library per module, or we build the whole application (e.g. the game) into one huge static binary.

So that's one of the reasons we squashed all those libraries into a single RD module that builds into a dynamic library.

What are the drawbacks:

* Separate libraries don't propagate their include directories to the libraries that depend on them. `PublicIncludePaths` are hand-picked using a "try to build → it fails → add the required include path" method. So if you're modifying the `RiderLink` plugin and want to use something new from `RD`, there's a chance you'll need to modify the `RD.Build.cs` file;
* Unity builds are disabled to preserve at least some isolation between compilation units;
* RD-CPP uses multiple PCH files that are scattered across the codebase, but we can't use multiple PCH files inside a UE module, so RD uses none.
  * While writing these lines, I realized that:
    * we could at least use the `rd_framework_cpp` PCH; or
    * we could artificially generate an RD PCH that combines all the other PCH files into a single one.
* `export_api_helper.h` contains definition of macros that silent MSVC compiler warnings that are enabled by default in UE, but are present across RD-CPP codebase. The way of force including files into code in UE has changed quite a few times, so instead of using CMake way of including `export_api_helper.h`, I've created required macros in the `RD.Build.cs` file.
    * `export_api_helper.h` from RD-CPP and `disabledWarnings` variable in `RD.Build.cs` should be kept in sync.

Theoretically, it would be more UE-like to split the RD module into `RDCore` and `RDFramework`, enable Unity builds, and combine them inside the `RiderRD` module, which would serve as a facade for `RD`. However, that would require reorganizing the code layout of RD-CPP, and ain't nobody got time for that.

# Pulling Changes from RD-CPP into UE RD

## tl;dr

```shell
export UNREAL_LINK_ROOT=<UnrealLink/root>
export RD_ROOT=<RD/root>

rm -rf $UNREAL_LINK_ROOT/src/cpp/RiderLink/Source/RD/src
rm -rf $UNREAL_LINK_ROOT/src/cpp/RiderLink/Source/RD/thirdparty

cp -R $RD_ROOT/rd-cpp/src $UNREAL_LINK_ROOT/src/cpp/RiderLink/Source/RD
cp -R $RD_ROOT/rd-cpp/thirdparty $UNREAL_LINK_ROOT/src/cpp/RiderLink/Source/RD

cd $UNREAL_LINK_ROOT

git add -A
git clean -x -f -d src/cpp/RiderLink/Source/RD
```

# Explanation

We only need the source code of the C++ part of RD. The RD code generation part, which is based on a Kotlin DSL, is synchronized separately.

RD-CPP contains a bunch of auxiliary and miscellaneous files, such as `CMakeLists.txt`, test files, CI/CD configuration, etc., that are not needed in UnrealLink/RiderLink. Some files can even break the build, for example duplicate `pch.cpp` files across the codebase (the Unreal build system doesn't really like translation units with the same name).