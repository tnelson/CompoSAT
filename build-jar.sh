#!/usr/bin/env bash
#
# build-jar.sh
# Builds a single runnable JAR for OldCompSAT (SimpleGUI) with AmalgamKodkod.
#
# Requirements:
#   - Java 8 JDK (javac and jar must be on PATH or JAVA_HOME set)
#   - This script compiles both OldCompSAT and AmalgamKodkod sources,
#     extracts dependency jars, bundles native dylibs, and produces dist/OldCompSAT.jar
#
# Usage:
#   ./build-jar.sh          (run from the OldCompSAT directory)
#
# Output:
#   dist/OldCompSAT.jar (runnable via: java -jar dist/OldCompSAT.jar)

set -e

REPO_ROOT="$(cd "$(dirname "$0")" && pwd)"
cd "$REPO_ROOT"

echo "=== Building OldCompSAT.jar ==="
echo "Repo root: $REPO_ROOT"

# Clean previous build artifacts
rm -rf build dist
mkdir -p build/classes
mkdir -p build/deps
mkdir -p dist

# Step 1: Compile AmalgamKodkod sources (needs sat4j on classpath)
echo "[1/7] Compiling AmalgamKodkod sources..."
find ../AmalgamKodkod/src -name "*.java" > build/amalgam-sources.txt
find ../AmalgamKodkod/examples -name "*.java" >> build/amalgam-sources.txt
javac -source 8 -target 8 -d build/classes -encoding UTF-8 \
  -cp "lib/sat4j.jar:lib/com.microsoft.z3.jar" \
  @build/amalgam-sources.txt

# Step 2: Compile OldCompSAT sources (depends on AmalgamKodkod classes + external jars)
echo "[2/7] Compiling OldCompSAT sources..."
CLASSPATH="build/classes"
CLASSPATH="$CLASSPATH:lib/apple-osx-ui.jar"
CLASSPATH="$CLASSPATH:lib/sat4j.jar"
CLASSPATH="$CLASSPATH:lib/trove-3.1a1.jar"
CLASSPATH="$CLASSPATH:lib/gs-core-1.3.jar"
CLASSPATH="$CLASSPATH:lib/com.microsoft.z3.jar"

find src -name "*.java" > build/oldcompsat-sources.txt
# Include only Logger.java from test (needed by main sources, no JUnit dependency)
echo "test/alloy/Logger.java" >> build/oldcompsat-sources.txt
javac -source 8 -target 8 -d build/classes -encoding UTF-8 -cp "$CLASSPATH" @build/oldcompsat-sources.txt

# Step 3: Extract dependency JARs into build/deps
echo "[3/7] Extracting dependency JARs..."
for jar in lib/*.jar; do
  echo "  Extracting $jar"
  unzip -q -o "$jar" -d build/deps || true
done

# Remove signature files from META-INF (can cause issues in uber-jar)
rm -rf build/deps/META-INF/*.SF build/deps/META-INF/*.DSA build/deps/META-INF/*.RSA

# Step 4: Copy native libraries to match application's expected resource paths
echo "[4/7] Copying native libraries..."

# macOS (x86-mac -> /x86-mac/)
# The application expects them at this path via Util.copy() in SimpleGUI
if [ -d "extra/x86-mac" ]; then
  echo "  Copying macOS libraries..."
  mkdir -p build/classes/x86-mac
  cp extra/x86-mac/*.dylib build/classes/x86-mac/ 2>/dev/null || true
fi

# Linux - support both amd64-linux and x86_64-linux naming
# (Java may report os.arch as either amd64 or x86_64)
if [ -d "extra/amd64-linux" ]; then
  echo "  Copying Linux libraries (amd64-linux)..."
  mkdir -p build/classes/amd64-linux
  cp extra/amd64-linux/*.so build/classes/amd64-linux/ 2>/dev/null || true
  # Also copy to x86_64-linux for compatibility
  mkdir -p build/classes/x86_64-linux
  cp extra/amd64-linux/*.so build/classes/x86_64-linux/ 2>/dev/null || true
fi

# Windows - support both amd64-windows and x86_64-windows naming
if [ -d "extra/amd64-windows" ]; then
  echo "  Copying Windows libraries (amd64-windows)..."
  mkdir -p build/classes/amd64-windows
  cp extra/amd64-windows/*.dll build/classes/amd64-windows/ 2>/dev/null || true
  # Also copy to x86_64-windows for compatibility
  mkdir -p build/classes/x86_64-windows
  cp extra/amd64-windows/*.dll build/classes/x86_64-windows/ 2>/dev/null || true
fi

# Step 5: Copy resources from extra/ (help, icons, images, models, etc.)
echo "[5/7] Copying extra resources (help, icons, images, models, etc.)..."
# The extra/ folder is a source folder in Eclipse classpath, so everything in it
# should be available as resources. We'll copy them into build/classes.
rsync -a --exclude='*.java' --exclude='dev/' --exclude='OSX/' --exclude='amd64-*' --exclude='x86-mac/' \
  extra/ build/classes/

# Step 6: Create MANIFEST.MF with Main-Class = JarLauncher
echo "[6/7] Creating MANIFEST.MF..."
cat > build/MANIFEST.MF <<EOF
Manifest-Version: 1.0
Main-Class: edu.mit.csail.sdg.alloy4whole.JarLauncher
Created-By: build-jar.sh

EOF

# Step 7: Build the JAR
echo "[7/7] Packaging dist/OldCompSAT.jar..."
cd build/classes
# Merge dependency classes
cp -R ../deps/* . 2>/dev/null || true

jar cfm "$REPO_ROOT/dist/OldCompSAT.jar" ../MANIFEST.MF .

cd "$REPO_ROOT"
echo ""
echo "=== Build complete ==="
echo "Output: dist/OldCompSAT.jar"
echo ""
echo "Run with: java -jar dist/OldCompSAT.jar"
