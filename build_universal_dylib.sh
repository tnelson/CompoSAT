#!/bin/bash

# Build script for creating universal dylib for both x86_64 and arm64
# This replaces the waf build system
# Generated with the help of Claude in Copilot -- TN
# Run this in the AmalgamKodkod's jni/minisatp folder. 

set -e  # Exit on error

# Configuration
BUILD_DIR="build"
LIB_NAME="libminisatprover.dylib"

# Auto-detect JNI headers based on current Java version
detect_jni_headers() {
    local java_home
    java_home=$(java -XshowSettings:properties -version 2>&1 | grep 'java.home' | cut -d'=' -f2 | tr -d ' ')
    
    # Common JNI header locations to try
    local jni_candidates=(
        "${java_home}/include"
        "${java_home}/../include"
        "/opt/homebrew/opt/openjdk/libexec/openjdk.jdk/Contents/Home/include"
        "/Library/Java/JavaVirtualMachines/temurin-11.jdk/Contents/Home/include"
        "/System/Library/Frameworks/JavaVM.framework/Headers"
    )
    
    for candidate in "${jni_candidates[@]}"; do
        if [[ -f "${candidate}/jni.h" ]]; then
            echo "${candidate}"
            return 0
        fi
    done
    
    echo "ERROR: Could not find jni.h headers!" >&2
    echo "Current Java version: $(java -version 2>&1 | head -1)" >&2
    echo "JAVA_HOME: ${JAVA_HOME:-not set}" >&2
    exit 1
}

JNI_INCLUDE=$(detect_jni_headers)
echo "Using JNI headers from: $JNI_INCLUDE"

# Compiler flags from wscript with additional safety flags
DEFINES="-D__STDC_LIMIT_MACROS -D__STDC_FORMAT_MACROS"
CXXFLAGS="-w -O3 -fPIC -ffloat-store -std=c++11 -fno-common"
# Use relative includes to avoid hardcoded paths in the binary
INCLUDES="-I. -I./minisatp -I${JNI_INCLUDE} -I${JNI_INCLUDE}/darwin"

# Source files
MINISATP_SOURCES="minisatp/Solver.C minisatp/Proof.C minisatp/File.C"
MAIN_SOURCE="kodkod_engine_satlab_MiniSatProver.cpp"

echo "Building universal dylib for minisatprover..."

# Clean and create build directory
rm -rf "$BUILD_DIR"
mkdir -p "$BUILD_DIR"

# Function to build for a specific architecture
build_arch() {
    local arch=$1
    local arch_dir="$BUILD_DIR/$arch"
    
    echo "Building for $arch..."
    mkdir -p "$arch_dir"
    
    # Compile minisatp object files
    for source in $MINISATP_SOURCES; do
        obj_name=$(basename "$source" .C).o
        echo "  Compiling $source -> $arch_dir/$obj_name"
        clang++ -arch "$arch" $CXXFLAGS $DEFINES $INCLUDES -c "$source" -o "$arch_dir/$obj_name"
    done
    
    # Compile main JNI wrapper
    echo "  Compiling $MAIN_SOURCE -> $arch_dir/kodkod_engine_satlab_MiniSatProver.o"
    clang++ -arch "$arch" $CXXFLAGS $DEFINES $INCLUDES -c "$MAIN_SOURCE" -o "$arch_dir/kodkod_engine_satlab_MiniSatProver.o"
    
    # Link into architecture-specific dylib
    echo "  Linking $arch_dir/$LIB_NAME"
    clang++ -arch "$arch" -shared -o "$arch_dir/$LIB_NAME" \
        "$arch_dir"/*.o \
        -Wl,-install_name,@rpath/$(basename "$LIB_NAME") \
        -Wl,-rpath,@loader_path \
        -framework CoreFoundation
}

# Build for both architectures
build_arch "x86_64"
build_arch "arm64"

# Create universal binary
echo "Creating universal binary..."
lipo -create \
    "$BUILD_DIR/x86_64/$LIB_NAME" \
    "$BUILD_DIR/arm64/$LIB_NAME" \
    -output "$BUILD_DIR/$LIB_NAME"

# Verify the universal binary
echo "Verifying universal binary:"
file "$BUILD_DIR/$LIB_NAME"
lipo -info "$BUILD_DIR/$LIB_NAME"

# Check for hardcoded paths (excluding system frameworks)
echo ""
echo "Checking for potentially problematic absolute paths:"
otool -L "$BUILD_DIR/$LIB_NAME" | grep -v '/System/' | grep -v '/usr/lib/' || echo "  No concerning absolute paths found"

echo ""
echo "Build completed successfully!"
echo "Universal dylib created at: $BUILD_DIR/$LIB_NAME"
