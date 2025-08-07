# CompoSAT

## Running CompoSAT in 2025

Amalgam and CompoSAT are built on **Alloy version 4.2**. This means that there is no support for temporal logic, but also that they don't benefit from the various improvements and cleanup work in the Alloy repository since. 

As of 2018, supported platforms were: amd64-linux, amd64-windows, and x86-mac. 

**Running on MacOS** relies on Mac-specific UI functionality that was deprecated at some point after Java 8. I haven't tested on all major versions, but the functionality is gone in Java 11. This applies to the base Amalgam release as well.

**Running on Windows** also needs an outdated version of Java to load the SAT-solver libraries. Java 11 works. This applies to the base Amalgam release as well.

**Running on "Apple Silicon"** (arm64) is not supported in the base Amalgam release, since the software was built before Apple's arm64 chips existed. This repository has been modified with a universal `.dylib` for core-extracting Minisat and Z3 (used only for coverage). 

**Running on Linux** This has not yet been updated. 

The instructions below clarify the build process as of [our 2018 FM paper](https://cs.brown.edu/~tbn/publications/pnk-fm18-coverage.pdf) to resolve problems with arm64. 

## Setup

CompoSAT does not use a standard build system. Instead, it was meant to be built using Eclipse. To get started:

**Step 1:** Clone both of these repositories:

- https://github.com/tnelson/CompoSAT
- https://github.com/tnelson/AmalgamKodkod

These are updated forks of the original.

**Step 2:** Add both to your workspace in Eclipse. You should see both projects at the root; this is important since `CompoSAT` refers to the `AmalgamKodkod` repository. Make sure that Eclipse uses an older version of Java as described above; Java 8 is suggested. 

**Do not** run the Alloy build script `mkdist.sh`; this will not properly build CompoSAT.

Since Alloy 4.2 only copies libraries to its working temporary folder _if they do not already exist_, you may need to clear out the binaries folder. When running, CompoSAT will print this to console along with the system architecture. E.g.: `platformBinary=C:\Users\Tim\AppData\Local\Temp\AMALGAMALPHA-Tim\binary; arch=amd64-windows`. 

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

The average user should not need to rebuild these libraries.

### Building Minisat Prover Universal Library (MacOS)

Copy the `build_universal_dylib.sh` script to AmalgamKodkod's `jni/minisatp` folder if it is not already present. Running it should generate a universal `.dylib` file for this solver. 

This should be placed in the `extra/x86-mac` and `bin/x86-mac` folders.

**Disclosure:** The `build_universal_dylib.sh` script was generated with Github Copilot.

### Building Z3 Universal Library (MacOS)

Rather than using the Python build system, build the universal libraries for Mac directly. Right after cloning the Z3 repository:

* `mkdir build && cd build`
* `cmake -DCMAKE_OSX_ARCHITECTURES="x86_64;arm64" -DZ3_BUILD_JAVA_BINDINGS=ON -DZ3_BUILD_LIBZ3_SHARED=ON  -DCMAKE_BUILD_TYPE=Release ../`
* `make -j8`

If you run the Python build script before this, you will get an error.

The `.dylib` files should be placed in the `extra/x86-mac` and `bin/x86-mac` folders.

The `com.microsoft.z3.jar` file should be placed in the `lib` folder of both `AmalgamKodkod` and `CompoSAT`.  

### Building Z3 .dll for Windows

**(This is not yet complete.)**

Install [Build Tools for Visual Studio](https://visualstudio.microsoft.com/downloads/#build-tools-for-visual-studio-2022). Then select the "Desktop Development with C++" install. This will let you run `nmake` from the developer command line. Make sure that your `javac` is version 8, or the Java bindings may cause problems in CompoSAT.

~~Open the `Developer Command Prompt`.~~ Don't open this one, it will try to build a 32-bit DLL but run into linker issues.

Open `x64 Native Tools Command Prompt`.  

Build the 64-bit version with Java bindings. The correct flag is `--64`, even if the readme file for Z3 says it is just `-x`. 

* `python scripts/mk_make.py --x64 --java`
* `cd build`
* `nmake`

Rename the `lib...` DLLs to `z3.dll` and `z3java.dll`. 

Note: the DLLs being produced must be the 64-bit version to work properly with a 64-bit JVM. E.g., if you have `git bash`, this would be bad on a 64-bit JVM:
```
$ file z3.dll
z3.dll: PE32 executable (DLL) (GUI) Intel 80386, for MS Windows
```




