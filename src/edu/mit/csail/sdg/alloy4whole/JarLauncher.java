package edu.mit.csail.sdg.alloy4whole;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.Locale;

/**
 * Cross-platform launcher that detects the OS, extracts bundled native libraries
 * to a temp directory, loads them via System.load(...), and starts the GUI.
 *
 * This class is the Main-Class in the jar manifest so the jar can be run via
 * `java -jar ...` and still load the native libraries packaged under /native/
 * for each platform (osx, linux, windows).
 */
public class JarLauncher {

    // Platform detection
    private static final String OS = System.getProperty("os.name").toLowerCase(Locale.ENGLISH);
    private static final boolean IS_WINDOWS = OS.contains("win");
    private static final boolean IS_MAC = OS.contains("mac");
    private static final boolean IS_LINUX = OS.contains("nux");

    // Native library configurations per platform
    // macOS libraries (from OldCompSAT/extra/x86-mac)
    private static final String[] MAC_LIBS = new String[] {
        "libminisatprover.dylib",
        "libz3.4.15.3.0.dylib",
        "libz3.4.15.dylib",
        "libz3.dylib",
        "libz3java.dylib"
    };

    // Linux libraries (from OldCompSAT/extra/amd64-linux)
    private static final String[] LINUX_LIBS = new String[] {
        "libminisatprover.so",
        "libz3.so",
        "libz3java.so"
    };

    // Windows libraries (from OldCompSAT/extra/amd64-windows)
    private static final String[] WINDOWS_LIBS = new String[] {
        "minisatprover.dll",
        "z3.dll",
        "z3java.dll"
    };

    public static void main(String[] args) throws Exception {
        // Set headless mode early (before any AWT classes load) for batch mode
        if (args.length > 0 && "batch".equals(args[0])) {
            System.setProperty("java.awt.headless", "true");
        }

        // Determine platform and library list
        String platform;
        String[] libsToLoad;

        if (IS_MAC) {
            platform = "x86-mac";
            libsToLoad = MAC_LIBS;
        } else if (IS_LINUX) {
            platform = "amd64-linux";
            libsToLoad = LINUX_LIBS;
        } else if (IS_WINDOWS) {
            platform = "amd64-windows";
            libsToLoad = WINDOWS_LIBS;
        } else {
            System.err.println("Warning: Unknown platform '" + OS + "'. Native libraries may not load.");
            platform = "unknown";
            libsToLoad = new String[0];
        }

        System.out.println("Detected platform: " + platform);

        // Create temp directory for native libraries
        File tmpDir = Files.createTempDirectory("oldcompsat-native").toFile();
        tmpDir.deleteOnExit();

        // Extract and load native libraries
        for (String libName : libsToLoad) {
            String resourcePath = "/" + platform + "/" + libName;
            try (InputStream in = JarLauncher.class.getResourceAsStream(resourcePath)) {
                if (in == null) {
                    // Library not present in jar; skip (it may be optional)
                    System.out.println("Skipping (not found): " + libName);
                    continue;
                }
                File out = new File(tmpDir, libName);
                try (OutputStream os = new FileOutputStream(out)) {
                    byte[] buf = new byte[8192];
                    int r;
                    while ((r = in.read(buf)) != -1) os.write(buf, 0, r);
                }
                out.setReadable(true);
                out.setExecutable(true);
                out.setWritable(true);

                // Load the native library explicitly from the extracted path
                try {
                    System.load(out.getAbsolutePath());
                    System.out.println("Loaded: " + libName);
                } catch (UnsatisfiedLinkError e) {
                    System.err.println("Failed to load: " + libName);
                    // Print stack trace only if verbose debugging needed
                    // e.printStackTrace();
                }
            }
        }

        // Add the native lib directory to java.library.path so that
        // Kodkod's NativeSolver.loadLibrary (which uses System.loadLibrary)
        // can find the extracted libraries.
        try {
            System.setProperty("java.library.path", tmpDir.getAbsolutePath());
            java.lang.reflect.Field usrPaths = ClassLoader.class.getDeclaredField("usr_paths");
            usrPaths.setAccessible(true);
            usrPaths.set(null, new String[]{tmpDir.getAbsolutePath()});
        } catch (Throwable ex) {
            System.err.println("Warning: could not update java.library.path: " + ex.getMessage());
        }

        // Delegate to the original GUI main
        SimpleGUI.main(args);
    }
}
