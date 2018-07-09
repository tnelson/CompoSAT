package edu.mit.csail.sdg.alloy4whole;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Queue;
import java.util.Random;
import java.util.Set;
import java.util.regex.Pattern;

import javax.swing.JScrollPane;

import amalgam.examples.alloy.Lists;
import edu.mit.csail.sdg.alloy4.A4Reporter;
import edu.mit.csail.sdg.alloy4.Err;
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
import edu.mit.csail.sdg.alloy4compiler.translator.AmalgamNaiveEvaluator;
import edu.mit.csail.sdg.alloy4compiler.translator.AmalgamVisitorHelper;
import edu.mit.csail.sdg.alloy4compiler.translator.ProvenanceTrace;
import edu.mit.csail.sdg.alloy4compiler.translator.ProvenanceTraceWrapper;
import edu.mit.csail.sdg.alloy4compiler.translator.ProvenanceTree;
import edu.mit.csail.sdg.alloy4compiler.translator.TranslateAlloyToKodkod;
import edu.mit.csail.sdg.alloy4viz.VizGUI;

class Trimmer {
    int trimSize;
    
    Trimmer(int trimSize) {
        this.trimSize = trimSize;
    }
    
    String trim(Object s) {
        if (trimSize >= 0) {
            return s.toString().substring(0, trimSize);
        } else {
            return s.toString();
        }
    }
}

public class CoverageCLI extends CLI {
    public static void main(String[] args) throws Err, IOException {
        if (args.length == 0) {
            System.err.println("Usage: ... <file.als>\n"
                    + "[--model-limit <number>]\n"
                    + "[--time-limit <number>]\n"
                    + "[--command <number>]\n"
                    + "[--modelSubsumption <boolean>]\n");
            return;
        }
        
        CoverageOptions coptions = new CoverageOptions(); 
        
        String filename = args[0];
        Map<String, List<String>> params = parse(args);
        int commandNumber = Integer.parseInt(params.getOrDefault("command", Util.asList("0")).get(0));
        int symmetry = Integer.parseInt(params.getOrDefault("symmetry", Util.asList("0")).get(0));
        boolean showProv = !"false".equals(params.getOrDefault("show-prov", Util.asList("true")).get(0));
        boolean showLit = !"false".equals(params.getOrDefault("show-lit", Util.asList("false")).get(0));
        
        coptions.timeLimit = Integer.parseInt(params.getOrDefault("time-limit", Util.asList("-1")).get(0));
        coptions.modelLimit = Integer.parseInt(params.getOrDefault("model-limit", Util.asList("-1")).get(0));
        
        final A4Reporter rep = new A4Reporter() {
            @Override public void warning(ErrorWarning msg) {
                System.out.print("Relevance Warning:\n"+(msg.toString().trim())+"\n\n");
                System.out.flush();
            }
            
            @Override public void resultCNF(String filename) {
                try {
                    String content = new String(Files.readAllBytes(Paths.get(filename)));
                    System.out.println(content);
                } catch (IOException e) {
                    
                }
            }
            
            @Override public void reportCurrentLit(int lit) {
                System.out.println("Operating on " + lit);
            }
        };

        System.out.println("=========== Parsing+Typechecking "+filename+" =============");
        final Module root = CompUtil.parseEverything_fromFile(rep, null, filename);
        
        final Command command = root.getAllCommands().get(commandNumber);
        System.out.println("============ Command "+command+": ============");
        
        final A4Options options = new A4Options();
        options.solver = A4Options.SatSolver.MiniSatProverJNI;
        options.noOverflow = true;
        options.inferPartialInstance = false;
        options.skolemDepth = -1; // set to -1 to disable skolemization
        options.symmetry = symmetry;
        
        Coverage coverageEngine = new Coverage(root, null, new TextLog(), null);
        
        CoverageStruct cs = coverageEngine.fromEnumerator(
                new Enumerator() {

                    public A4Solution init() throws Err {
                        return TranslateAlloyToKodkod.execute_command(rep, root.getAllReachableSigs(), command, options);
                    }
                    
                },
                coptions);
        
        /*
        if (showProv) {
            for (ProvenanceTraceWrapper t : cs.accumTraces) {
                System.out.println("===================");
                System.out.println(t.getTrace());
                System.out.println("-------------------");
                System.out.println(SkeletonPrettifier.prettify(t));
                System.out.println("===================");
            }
            System.out.println("\n\nnumber of solutions: " + cs.accumTraces.size() + "\n\n");
        }
        */
        
        Queue<CoverageModel> queue = cs.getQueue();
        System.out.println("=========================================");
        CoverageModel lastModel = null;
        
        List<CoverageModel> qList = new ArrayList<>(queue);
        qList.sort(new Comparator<CoverageModel>() {
            @Override
            public int compare(CoverageModel o1, CoverageModel o2) {
                return o1.id - o2.id;
            }
        });
        
        for (CoverageModel m : qList) {
            System.out.println("i = " + m.id + " model-size: " + m.size);
            System.out.println(m.ans);
            System.out.println(m.getBitvec());
            lastModel = m;
        }
        
        System.out.println("Ensemble size: " + queue.size() + ", " + cs.accumTraces.size() + " prov traces, " + cs.numAllModels + " models generated");
    }
}

/*
// 
for (Pair<Integer, ProvenanceTrace> t : globalTracesOrig) {
    if (t.b.toString().contains("neighbors . univ in this/Node []")) {
        //what.add(t.b);
        
        System.out.println("===================================");
        System.out.print(t.a + ": ");
        System.out.println("-------------");
        System.out.println(t.b);
        System.out.println("-------------");
        System.out.println(t.b.getAlphasSkeleton());
        //System.out.println(t.b.toString() /*.substring(0, Math.min(1000, t.b.toString().length())) *-/);
        //System.out.println(t.b.getAlphas());
        //System.out.println(t.b.getAlphas().get(3).getContents().getSubnodes().get(0).getClass());
    }
}
*/
