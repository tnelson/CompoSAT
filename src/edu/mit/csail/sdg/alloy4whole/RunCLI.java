package edu.mit.csail.sdg.alloy4whole;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Scanner;

import edu.mit.csail.sdg.alloy4.A4Reporter;
import edu.mit.csail.sdg.alloy4.ConstList;
import edu.mit.csail.sdg.alloy4.Err;
import edu.mit.csail.sdg.alloy4.Util;
import edu.mit.csail.sdg.alloy4.XMLNode;
import edu.mit.csail.sdg.alloy4compiler.ast.Command;
import edu.mit.csail.sdg.alloy4compiler.ast.Module;
import edu.mit.csail.sdg.alloy4compiler.ast.Sig;
import edu.mit.csail.sdg.alloy4compiler.parser.CompUtil;
import edu.mit.csail.sdg.alloy4compiler.translator.A4Options;
import edu.mit.csail.sdg.alloy4compiler.translator.A4Solution;
import edu.mit.csail.sdg.alloy4compiler.translator.A4SolutionReader;
import edu.mit.csail.sdg.alloy4compiler.translator.TranslateAlloyToKodkod;

public class RunCLI {
    public static void main(String[] args) throws Err, IOException {
        final A4Reporter rep = new A4Reporter();
        File paperDir = new File("/home/sorawee/workspace/AmalgamAlloy/testing/PAPER");
        Scanner reader = new Scanner(System.in);
        for (File f : paperDir.listFiles()) {
            System.out.println(f);
            String dirname = "/home/sorawee/workspace/Aluminum/models/" + f.getName();

            if (!new File(dirname).exists()) {
                System.out.println("Skipping " + dirname);
                continue;
            }

            Module root = CompUtil.parseEverything_fromFile(rep, null, f.getAbsolutePath());
            ConstList<Command> commands = root.getAllCommands();
            Command cmd;
            if (commands.size() == 1) {
                cmd = commands.get(0);
            } else {
                for (int i = 0; i < commands.size(); i++) {
                    System.out.println("" + i + ": " + commands.get(i));
                }
                System.out.print("Enter a command number: ");
                cmd = commands.get(reader.nextInt());
            }

            final A4Options options = new A4Options();
            options.solver = A4Options.SatSolver.MiniSatProverJNI;
            options.noOverflow = true;
            options.inferPartialInstance = false;
            options.skolemDepth = -1; // set to -1 to disable skolemization
            options.symmetry = 2000;

            A4Solution initAnswer = TranslateAlloyToKodkod.execute_command(rep, root.getAllReachableSigs(), cmd,
                    options);

            int i = 1;
            File modelFile = new File(dirname + "/minimal-model-" + i);

            List<Integer> numLst = new ArrayList<>();

            while (modelFile.exists()) {
                A4Solution ret = A4SolutionReader.read(initAnswer.getAllReachableSigs(), new XMLNode(modelFile));
                i++;
                modelFile = new File(dirname + "/minimal-model-" + i);
                numLst.add(ret.eval(Sig.UNIV).size());
            }
            Collections.sort(numLst);

            int sumSize = 0;

            for (Integer x : numLst) {
                sumSize += x;
            }
            
            double avg = ((double) sumSize) / numLst.size();
            
            double median;
            if (numLst.size() % 2 == 0) {
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
            
            System.out.println(
                    f.getName() + "\t" + 
                    numLst.size() + "\t" + 
                    String.format("%.2f", avg) + "\t" + 
                    (sd == dummySD ? "-" : String.format("%.2f", sd)) + "\t" +
                    String.format("%.2f", median) + "\n");
        }

    }
}
