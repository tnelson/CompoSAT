package edu.mit.csail.sdg.alloy4whole;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import edu.mit.csail.sdg.alloy4.A4Reporter;
import edu.mit.csail.sdg.alloy4.Err;
import edu.mit.csail.sdg.alloy4.ErrorWarning;
import edu.mit.csail.sdg.alloy4.Util;
import edu.mit.csail.sdg.alloy4compiler.ast.Command;
import edu.mit.csail.sdg.alloy4compiler.ast.Module;
import edu.mit.csail.sdg.alloy4compiler.ast.Sig;
import edu.mit.csail.sdg.alloy4compiler.parser.CompUtil;
import edu.mit.csail.sdg.alloy4compiler.translator.A4Options;
import edu.mit.csail.sdg.alloy4compiler.translator.A4Solution;
import edu.mit.csail.sdg.alloy4compiler.translator.TranslateAlloyToKodkod;

public class BatchCLI extends CLI {

    public static void main(String[] args) throws IOException {
        System.setProperty("java.awt.headless", "true");

        Map<String, List<String>> params = parse(args);

        String mode = params.getOrDefault("mode", Util.asList("coverage")).get(0);
        String outDir = params.getOrDefault("out", Util.asList("composat-output")).get(0);
        int modelLimit = Integer.parseInt(params.getOrDefault("model-limit", Util.asList("-1")).get(0));
        int timeLimit = Integer.parseInt(params.getOrDefault("time-limit", Util.asList("-1")).get(0));
        // -1 means "run all commands" (the default)
        int commandNumber = Integer.parseInt(params.getOrDefault("command", Util.asList("-1")).get(0));
        int symmetry = Integer.parseInt(params.getOrDefault("symmetry", Util.asList("2000")).get(0));

        // Collect .als files
        List<String> alsFiles = new ArrayList<>();
        if (params.containsKey("files")) {
            alsFiles.addAll(params.get("files"));
        }
        if (params.containsKey("dir")) {
            for (String dirPath : params.get("dir")) {
                File dir = new File(dirPath);
                if (!dir.isDirectory()) {
                    System.err.println("Warning: --dir path is not a directory: " + dirPath);
                    continue;
                }
                File[] files = dir.listFiles();
                if (files == null) continue;
                for (File f : files) {
                    if (f.getName().endsWith(".als")) {
                        alsFiles.add(f.getAbsolutePath());
                    }
                }
            }
        }

        if (alsFiles.isEmpty()) {
            System.err.println("Usage: batch --files a.als b.als [--dir models/] "
                    + "[--mode coverage|plain] [--out composat-output] "
                    + "[--model-limit N] [--time-limit N] [--command N] [--symmetry N]");
            return;
        }

        int totalInstances = 0;
        int fileCount = 0;
        int errorCount = 0;

        for (String filename : alsFiles) {
            fileCount++;
            System.out.println("\n=== Processing " + filename + " (" + fileCount + "/" + alsFiles.size() + ") ===");

            try {
                // Derive subdirectory name from file basename (without .als)
                String baseName = new File(filename).getName();
                if (baseName.endsWith(".als")) {
                    baseName = baseName.substring(0, baseName.length() - 4);
                }
                File fileOutDir = new File(outDir, baseName);

                A4Reporter rep = new A4Reporter() {
                    @Override public void warning(ErrorWarning msg) {
                        System.out.print("Relevance Warning:\n" + (msg.toString().trim()) + "\n\n");
                        System.out.flush();
                    }
                };

                System.out.println("Parsing+Typechecking " + filename + "...");
                final Module root = CompUtil.parseEverything_fromFile(rep, null, filename);

                List<Command> allCommands = root.getAllCommands();
                if (allCommands.isEmpty()) {
                    System.err.println("Skipping " + filename + ": no commands found");
                    errorCount++;
                    continue;
                }

                // Determine which commands to run
                List<Integer> commandIndices = new ArrayList<>();
                if (commandNumber >= 0) {
                    if (commandNumber >= allCommands.size()) {
                        System.err.println("Skipping " + filename + ": --command "
                                + commandNumber + " but file has only "
                                + allCommands.size() + " command(s)");
                        errorCount++;
                        continue;
                    }
                    commandIndices.add(commandNumber);
                } else {
                    for (int i = 0; i < allCommands.size(); i++) {
                        commandIndices.add(i);
                    }
                }

                final A4Options options = new A4Options();
                options.solver = A4Options.SatSolver.MiniSatProverJNI;
                options.noOverflow = true;
                options.inferPartialInstance = false;
                options.skolemDepth = -1;
                options.symmetry = symmetry;

                boolean multipleCommands = commandIndices.size() > 1;

                // Pre-compute directory names, appending index to disambiguate duplicates
                Set<String> usedDirNames = new HashSet<>();
                List<String> cmdDirNames = new ArrayList<>();
                for (int cmdIdx : commandIndices) {
                    String name = commandDirName(cmdIdx, allCommands.get(cmdIdx));
                    if (!usedDirNames.add(name)) {
                        name = name + "_" + cmdIdx;
                    }
                    cmdDirNames.add(name);
                }

                for (int ci = 0; ci < commandIndices.size(); ci++) {
                    int cmdIdx = commandIndices.get(ci);
                    final Command command = allCommands.get(cmdIdx);
                    System.out.println("Command [" + cmdIdx + "]: " + command);

                    // Build output dir: <out>/<basename>/<commandDirName>/
                    File cmdOutDir;
                    if (multipleCommands) {
                        cmdOutDir = new File(fileOutDir, cmdDirNames.get(ci));
                    } else {
                        cmdOutDir = fileOutDir;
                    }
                    cmdOutDir.mkdirs();

                    int instanceCount = 0;

                    if ("coverage".equals(mode)) {
                        instanceCount = runCoverageMode(root, command, options, rep,
                                cmdOutDir, modelLimit, timeLimit);
                    } else if ("plain".equals(mode)) {
                        instanceCount = runPlainMode(root, command, options, rep,
                                cmdOutDir, modelLimit);
                    } else {
                        System.err.println("Unknown mode: " + mode + " (expected 'coverage' or 'plain')");
                    }

                    totalInstances += instanceCount;
                    System.out.println("Wrote " + instanceCount + " instance(s) to " + cmdOutDir.getPath());
                }

            } catch (Exception e) {
                errorCount++;
                System.err.println("Error processing " + filename + ": " + e.getMessage());
                e.printStackTrace();
            }
        }

        System.out.println("\n=== Batch complete ===");
        System.out.println("Files processed: " + fileCount);
        System.out.println("Total instances: " + totalInstances);
        if (errorCount > 0) {
            System.out.println("Errors: " + errorCount);
        }
    }

    /** Build a filesystem-safe directory name for a command, e.g. "run_ownGrandpa" or "check_NoSelfGrandpa".
     *  Falls back to "cmd0", "cmd1", etc. if the label is empty. */
    private static String commandDirName(int index, Command command) {
        String prefix = command.check ? "check" : "run";
        String label = command.label;
        if (label != null && !label.isEmpty()) {
            // Replace characters that are unsafe in directory names
            label = label.replaceAll("[^a-zA-Z0-9_\\-]", "_");
            return prefix + "_" + label;
        }
        return "cmd" + index;
    }

    private static int runCoverageMode(Module root, Command command, A4Options options,
            A4Reporter rep, File outDir, int modelLimit, int timeLimit) throws Err, IOException {

        CoverageOptions coptions = new CoverageOptions();
        coptions.modelLimit = modelLimit;
        coptions.timeLimit = timeLimit == -1 ? -1 : timeLimit * 1000L;

        Coverage coverageEngine = new Coverage(root, null, new TextLog(), null);

        CoverageStruct cs = coverageEngine.fromEnumerator(
                new Enumerator() {
                    public A4Solution init() throws Err {
                        return TranslateAlloyToKodkod.execute_command(rep, root.getAllReachableSigs(), command, options);
                    }
                },
                coptions);

        Queue<CoverageModel> queue = cs.getQueue();

        List<CoverageModel> qList = new ArrayList<>(queue);
        qList.sort(new Comparator<CoverageModel>() {
            @Override
            public int compare(CoverageModel o1, CoverageModel o2) {
                return o1.id - o2.id;
            }
        });

        int instanceCount = 0;
        for (CoverageModel m : qList) {
            instanceCount++;
            String xmlPath = new File(outDir, "instance_" + instanceCount + ".xml").getAbsolutePath();
            m.ans.writeXML(xmlPath);
        }

        System.out.println("Ensemble size: " + queue.size()
                + ", " + cs.accumTraces.size() + " prov traces, "
                + cs.numAllModels + " models generated");

        return instanceCount;
    }

    private static int runPlainMode(Module root, Command command, A4Options options,
            A4Reporter rep, File outDir, int modelLimit) throws Err {

        int instanceCount = 0;
        for (A4Solution ans = TranslateAlloyToKodkod.execute_command(rep, root.getAllReachableSigs(), command, options);
             ans.satisfiable() && (instanceCount < modelLimit || modelLimit == -1);
             ans = ans.next()) {
            instanceCount++;
            String xmlPath = new File(outDir, "instance_" + instanceCount + ".xml").getAbsolutePath();
            ans.writeXML(xmlPath);
        }

        return instanceCount;
    }
}
