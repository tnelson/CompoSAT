# CompoSAT

## Running CompoSAT in 2025

Amalgam and CompoSAT are built on **Alloy version 4.2**. This means that there is no support for temporal logic, but also that they don't benefit from the various improvements and cleanup work in the Alloy repository since. 

As of 2018, supported platforms were: amd64-linux, amd64-windows, and x86-mac. 

**Running on MacOS** relies on Mac-specific UI functionality that was deprecated at some point after Java 8. I haven't tested on all major versions, but the functionality is gone in Java 11. This applies to the base Amalgam release as well.

**Running on Windows** also needs an outdated version of Java to load the SAT-solver libraries. Java 11 works. This applies to the base Amalgam release as well.

**Running on "Apple Silicon"** (arm64) is not supported in the base Amalgam release, since the software was built before Apple's arm64 chips existed. This repository has been modified with a universal `.dylib` for core-extracting Minisat and Z3 (used only for coverage). 

The instructions below reflect the build process as of [our 2018 FM paper](https://cs.brown.edu/~tbn/publications/pnk-fm18-coverage.pdf), which has not been updated in 2025. Some of the instructions have been clarified.

## Setup

CompoSAT does not use a standard build system. Instead, it was meant to be built using Eclipse. To get started:

**Step 1:** Clone both of these repositories:

- https://github.com/tnelson/CompoSAT
- https://github.com/tnelson/AmalgamKodkod

These are updated forks of the original.

**Step 2:** Add both to your workspace in Eclipse. You should see both projects at the root; this is important since `CompoSAT` refers to the `AmalgamKodkod` repository. Make sure that Eclipse uses an older version of Java as described above; Java 7 is suggested. 

**Do not** run the Alloy build script `mkdist.sh`; this will not properly build CompoSAT.

## Use

After building in Eclipse, run the `./gui` script at the root of the `CompoSAT` project. This will launch the modified Alloy GUI. 

CompoSAT exposes a number of instance-exploration features from prior work. When viewing instances, you should see buttons like "Minimize" and "Maximize" (from [our 2013 Aluminum paper](https://cs.brown.edu/~tbn/publications/nsdfk-icse13-aluminum.pdf)). To use CompoSAT's coverage feature, use the "CompoSAT Next" button. 

### Important caveats 

The first output model is not a part of CompoSAT's output. As such, it is possible that models output by CompoSAT will contain the first output model.

CompoSAT currently doesn't support interleaving between regular "Next" and "CompoSAT Next" (or any other options such as Shrink and Grow). That is, make sure that you only use "CompoSAT Next" if you want to use the coverage functionality.

The time limit for the internal enumeration is 20 seconds. This dictates the initial instance set from which coverage is computed. 

To see CompoSAT logs, set the Alloy's verbosity level to "debug"

## Contact 

Contact `tim_nelson@brown.edu` with questions.

## Build Notes 

### Building Minisat Prover Universal Library (MacOS)

Copy the `build_universal_dylib.sh` script to AmalgamKodkod's `jni/minisatp` folder if it is not already present. Running it should generate a universal `.dylib` file for this solver. 

This should be placed in the `extra/x86-mac` and `bin/x86-mac` folders.

### Building Z3 Universal Library (MacOS)

Rather than using the Python build system, build the universal libraries for Mac directly. Right after cloning the Z3 repository:

```
mkdir build && cd build
```

```
cmake -DCMAKE_OSX_ARCHITECTURES="x86_64;arm64" \
      -DZ3_BUILD_JAVA_BINDINGS=ON \
      -DZ3_BUILD_LIBZ3_SHARED=ON \
      -DCMAKE_BUILD_TYPE=Release \
      ../
```

```
make -j8
```

The `.dylib` files should be placed in the `extra/x86-mac` and `bin/x86-mac` folders.

The `com.microsoft.z3.jar` file should be placed in the `lib` folder of both `AmalgamKodkod` and `CompoSAT`.  
