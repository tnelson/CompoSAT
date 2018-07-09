package edu.mit.csail.sdg.alloy4whole;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Random;
import java.util.Set;
import java.util.regex.Pattern;

import javax.swing.JScrollPane;

import amalgam.examples.alloy.Lists;
import edu.mit.csail.sdg.alloy4.A4Reporter;
import edu.mit.csail.sdg.alloy4.Err;
import edu.mit.csail.sdg.alloy4.ErrorFatal;
import edu.mit.csail.sdg.alloy4.ErrorWarning;
import edu.mit.csail.sdg.alloy4.OurUtil;
import edu.mit.csail.sdg.alloy4.Pair;
import edu.mit.csail.sdg.alloy4.Pos;
import edu.mit.csail.sdg.alloy4.Util;
import edu.mit.csail.sdg.alloy4.XMLNode;
import edu.mit.csail.sdg.alloy4compiler.ast.Command;
import edu.mit.csail.sdg.alloy4compiler.ast.Expr;
import edu.mit.csail.sdg.alloy4compiler.ast.ExprVar;
import edu.mit.csail.sdg.alloy4compiler.ast.Module;
import edu.mit.csail.sdg.alloy4compiler.ast.Sig;
import edu.mit.csail.sdg.alloy4compiler.parser.CompUtil;
import edu.mit.csail.sdg.alloy4compiler.translator.A4Options;
import edu.mit.csail.sdg.alloy4compiler.translator.A4Solution;
import edu.mit.csail.sdg.alloy4compiler.translator.A4SolutionReader;
import edu.mit.csail.sdg.alloy4compiler.translator.ProvenanceTrace;
import edu.mit.csail.sdg.alloy4compiler.translator.ProvenanceTraceWrapper;
import edu.mit.csail.sdg.alloy4compiler.translator.ProvenanceTree;
import edu.mit.csail.sdg.alloy4compiler.translator.TranslateAlloyToKodkod;
import edu.mit.csail.sdg.alloy4viz.VizGUI;

public class EvaluationCLI extends CLI {
    public static void main(String[] args) throws Err, IOException {
        if (args.length == 0) {
            System.err.println("Usage: ... <file.als>\n");
            return;
        }
        
        
        String filename = args[0];
        Map<String, List<String>> params = parse(args);
        
        final String dirname = params.getOrDefault("out", Util.asList(".")).get(0);
        int timeLimit = Integer.parseInt(params.getOrDefault("time-limit", Util.asList("60")).get(0));
        String label = params.getOrDefault("label", Util.asList("")).get(0);
        String sub = params.getOrDefault("sub", Util.asList("")).get(0);
        String bound = params.getOrDefault("bound", Util.asList("")).get(0);
        
        CoverageOptions cOpt = new CoverageOptions();
        cOpt.timeLimit = 60000 * timeLimit;
        cOpt.modelLimit = -1;

        final A4Reporter rep = new A4Reporter();
        System.out.println("=========== Parsing+Typechecking "+filename+" =============");
        final Module root = CompUtil.parseEverything_fromFile(rep, null, filename);
        
        final Command command = root.getAllCommands().get(Integer.parseInt(params.getOrDefault("command", Util.asList("0")).get(0)));
        System.out.println("============ Command "+command+": ============");
        
        final A4Options options = new A4Options();
        options.solver = A4Options.SatSolver.MiniSatProverJNI;
        options.noOverflow = true;
        options.inferPartialInstance = false;
        options.skolemDepth = -1; // set to -1 to disable skolemization
        options.symmetry = 2000;
        
        Coverage cEngineB = new Coverage(root, null, new TextLog(), null);
        Coverage cEngineM = new Coverage(root, null, new TextLog(), null);
        
        final A4Solution initAnswer = TranslateAlloyToKodkod.execute_command(rep, root.getAllReachableSigs(), command, options);
        
        CoverageStruct csB = cEngineB.fromEnumerator(
                new Enumerator() {

                    @Override
                    public A4Solution init() throws Err {
                        return initAnswer;
                    }
                    
                },
                cOpt);
        
        CoverageStruct csM = cEngineM.fromEnumerator(
                new Enumerator() {

                    private int i;
                    private File f;
                    
                    @Override
                    public A4Solution init() throws Err {
                        i = 1;
                        f = new File(dirname + "/minimal-model-" + i);
                        return null;
                    }
                    
                    @Override
                    public boolean hasNext() {
                        return f.exists();
                    }
                    
                    @Override
                    public A4Solution next() throws Err, IOException {
                        A4Solution ret = A4SolutionReader.read(
                                initAnswer.getAllReachableSigs(), 
                                new XMLNode(f));
                        i++;
                        f = new File(dirname + "/minimal-model-" + i);
                        return ret;
                    }
                    
                    @Override
                    public boolean isFinished() {
                        return true;
                    }
                },
                cOpt);
        
        List<ProvenanceTraceWrapper> tB = new ArrayList<>(csB.accumTraces);
        Collections.reverse(tB);
        List<ProvenanceTraceWrapper> tM = new ArrayList<>(csM.accumTraces);
        Collections.reverse(tM);
        
        //csN.enhance(tB);
        //csB.enhance(tN);
        
        //assert csN.accumTracesSet.size() == csB.accumTracesSet.size();
        StringBuffer sb = new StringBuffer();
        PrintWriter writerB = new PrintWriter(dirname + "/blind.txt", "UTF-8");
        sb.append(label + " ");
        if ("{}".equals(sub)) {
            sb.append("& \\at{\\{\\} 3} ");
        } else {
            sb.append("& \\at{" + sub + " " + bound + "} ");
        }
        strategyPrint(csB, writerB, tM, sb, true);
        sb.append("\\rowcolor{gray!20}\n");
        sb.append("& ");
        PrintWriter writerM = new PrintWriter(dirname + "/minimal.txt", "UTF-8");
        strategyPrint(csM, writerM, tB, sb, false);
        PrintWriter writerMeta = new PrintWriter(dirname + "/meta.txt", "UTF-8");
        writerMeta.println(filename);
        writerMeta.println(command);
        writerMeta.println();
        writerMeta.print(sb.toString());
        writerMeta.close();
        writerB.close();
        writerM.close();
    }

    private static void strategyPrint(CoverageStruct cs, PrintWriter writer, List<ProvenanceTraceWrapper> rest, StringBuffer sb, boolean header) throws ErrorFatal {
        List<Integer> numLst = new ArrayList<>();
        for (CoverageModel m : cs.models) {
            writer.println("i = " + m.id + ", time: " + m.timeSoFar + ", prov-accum: " + m.accumNumTrace + ", size: " + m.size);
            numLst.add(m.size);
        }
        Collections.sort(numLst);
        
        int sumSize = 0;
        for (Integer x : numLst) {
            sumSize += x;
        }
        double avg = ((double) sumSize) / numLst.size();
        double median;
        if (cs.models.size() % 2 == 0) {
            median = (((double) numLst.get((numLst.size() - 1) / 2)) + ((double) numLst.get(numLst.size() / 2))) / 2;
        } else {
            median = numLst.get(numLst.size() / 2);
        }
        
        double sd;
        double dummySD = 999999999;
        
        if (numLst.size() == 1) {
            sd = dummySD;
        } else {
            double another_sum = 0;
            for (Integer x : numLst) {
                double v = avg - x;
                v *= v;
                another_sum += v;
            }   
            sd = Math.sqrt(another_sum / (numLst.size() - 1));
        }
         
        
        writer.println();
        writer.println("GC hit?: " + cs.gcHit);
        writer.println("Model all enumerated: " + cs.numAllModels);
        writer.println("Model enumeration time: " + cs.timeAll);
        writer.println("#Traces: " + cs.accumTracesSet.size());
        writer.println("Model all?: " + cs.isFinishedEnum);
        
        int allEnum = cs.numAllModels;
        int allTime = (int) cs.timeAll;
        int numTraceOrig = cs.accumTracesSet.size();
        boolean finished = cs.isFinishedEnum;
        
        
        int missing = cs.getMissing(rest);
        writer.println("Missing: " + missing);
        Queue<CoverageModel> queue = cs.getQueue();
        writer.println("#Trace-compacted: " + cs.accumTracesSet.size());
        
        int compacted = cs.accumTracesSet.size();
        sb.append("& " + compacted + " [" + missing + "] ");
        
        String msgFinished = "";
        if (!finished) {
            if (allTime < 3600) {
                msgFinished = "M";
            } else {
                msgFinished = "\\xm";
            }
        }
        
        sb.append("& " + msgFinished + " ");
        
        writer.println();
        writer.println("== Enumeration ==");
        
        printProvStat(cs.models, 50, numTraceOrig, writer, sb);
        printProvStat(cs.models, 70, numTraceOrig, writer, sb);
        printProvStat(cs.models, 90, numTraceOrig, writer, sb);
        printProvStat(cs.models, 100, numTraceOrig, writer, sb);
        
        writer.println();
        
        
        writer.println();
        writer.println("== Ensemble ==");
        printEnsemble(queue, 50, cs.accumTracesSet.size(), writer, sb);
        printEnsemble(queue, 70, cs.accumTracesSet.size(), writer, sb);
        printEnsemble(queue, 90, cs.accumTracesSet.size(), writer, sb);
        printEnsemble(queue, 100, cs.accumTracesSet.size(), writer, sb);
        
        sb.append("& " + allEnum + " ");
        if (allTime == 0) {
            sb.append("& <1 ");
        } else if (allTime > 3600) {
            sb.append("& >3600 ");
        } else {
            sb.append("& " + allTime + " ");
        }
        
        sb.append("& " + String.format("%.2f", avg) + " ");
        
        if (sd == dummySD) {
            sb.append("& - ");
        } else {
            sb.append("& " + String.format("%.2f", sd) + " ");
        }
        
        sb.append("& " + String.format("%.2f", median) + " ");
        
        sb.append("\\\\\n");
    }

    private static void printEnsemble(Queue<CoverageModel> queue, int percent, int allProv, PrintWriter writer, StringBuffer sb) {
        double numerator = allProv * percent;
        int target = (int) (numerator/100.0);
        List<CoverageModel> models = new ArrayList<>(queue);
        for (int i = 0; i < models.size(); i++) {
            CoverageModel m = models.get(i);
            if (m.queueProvCoverage >= target) {
                writer.println(percent + "%, id: " + (i + 1));
                sb.append("& " + (i + 1) + " ");
                return;
            }
        }
        if (models.size() == 0) {
            writer.println(percent + "%, id: -1");
            sb.append("& 0 ");
        }
    }

    private static void printProvStat(List<CoverageModel> models, int percent, int allProv, PrintWriter writer, StringBuffer sb) {
        double numerator = allProv * percent;
        int target = (int) (numerator/100.0);
        for (CoverageModel m : models) {
            if (m.accumNumTrace >= target) {
                writer.println(percent + "%, time: " + m.timeSoFar + ", id: " + m.id);
                if (m.timeSoFar == 0) {
                    sb.append("& <1 [" + m.id + "] ");                    
                } else {
                    sb.append("& " + m.timeSoFar + " [" + m.id + "] ");
                }
                
                return;
            }
        }
    }
}