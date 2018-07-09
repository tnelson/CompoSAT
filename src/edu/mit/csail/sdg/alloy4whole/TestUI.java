package edu.mit.csail.sdg.alloy4whole;
import edu.mit.csail.sdg.alloy4.A4Reporter;
import edu.mit.csail.sdg.alloy4.Err;
import edu.mit.csail.sdg.alloy4.ErrorWarning;
import edu.mit.csail.sdg.alloy4.Listener;
import edu.mit.csail.sdg.alloy4.OurUtil;
import edu.mit.csail.sdg.alloy4.XMLNode;
import edu.mit.csail.sdg.alloy4compiler.ast.Command;
import edu.mit.csail.sdg.alloy4compiler.ast.Expr;
import edu.mit.csail.sdg.alloy4compiler.ast.Func;
import edu.mit.csail.sdg.alloy4compiler.ast.Module;
import edu.mit.csail.sdg.alloy4compiler.parser.CompUtil;
import edu.mit.csail.sdg.alloy4compiler.translator.A4Options;
import edu.mit.csail.sdg.alloy4compiler.translator.A4Solution;
import edu.mit.csail.sdg.alloy4compiler.translator.A4SolutionReader;
import edu.mit.csail.sdg.alloy4compiler.translator.AmalgamNaiveEvaluator;
import edu.mit.csail.sdg.alloy4compiler.translator.Provenance;
import edu.mit.csail.sdg.alloy4compiler.translator.ProvenanceTree;
import edu.mit.csail.sdg.alloy4compiler.translator.TranslateAlloyToKodkod;

import static edu.mit.csail.sdg.alloy4.A4Preferences.FontName;
import static edu.mit.csail.sdg.alloy4.A4Preferences.FontSize;
import static edu.mit.csail.sdg.alloy4whole.AmalgamUI.*;

import java.awt.Color;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.swing.JScrollPane;

/**
 * Exists as a substitute for SimpleGUI in Evaluation framework
 */
public class TestUI {
    private A4Reporter rep;
    private A4Options options;
    private Module world;
    private JScrollPane logpane;
    private SwingLogPanel log;
    private final String xmlfilename;
    private Command command;

    public A4Solution currentSolution;
    
    public TestUI(String filename) throws Err {
        final String binary = SimpleGUI.alloyHome() + SimpleGUI.fs + "binary";
        // TN XXX this is a copy+paste from SimpleGUI
        //    Add the new JNI location to the java.library.path
        try {
            System.setProperty("java.library.path", binary);
            // The above line is actually useless on Sun JDK/JRE (see Sun's bug ID 4280189)
            // The following 4 lines should work for Sun's JDK/JRE (though they probably won't work for others)
            String[] newarray = new String[]{binary};
            java.lang.reflect.Field old = ClassLoader.class.getDeclaredField("usr_paths");
            old.setAccessible(true);
            old.set(null,newarray);
        } catch (Throwable ex) { }
        // NOTE: If you get an exception saying that the library can't be found, run SimpleGUI first. 
        // That will create the alloyHome() directory and unpack the minisatprover library.
        PreferencesDialog.loadLibrary("minisatprover");
        rep = new A4Reporter() {
            // For example, here we choose to display each "warning" by printing it to System.out
            @Override public void warning(ErrorWarning msg) {
                System.out.print("Relevance Warning:\n"+(msg.toString().trim())+"\n\n");
                System.out.flush();
            }
        };
        options = new A4Options();
        options.solver = A4Options.SatSolver.MiniSatProverJNI;
        // no prefetched map (first null), historical resolution mode (1), no log (null)
        world = CompUtil.parseEverything_fromFile(rep, null, filename, 2, null);
        logpane = OurUtil.scrollpane(null);
        log = new SwingLogPanel(logpane, FontName.get(), FontSize.get(), new Color(0.9f, 0.9f, 0.9f), Color.BLACK, new Color(.7f,.2f,.2f), null);
        xmlfilename = filename+".temp.xml";
    }

    public boolean hasCommands() {
        return world.getAllCommands().size() > 0;
    }
    
    public static TestUI init(String filename) throws Err {
        return new TestUI(filename);
    }

    public TestUI run(int commandIndex) throws Err {
        command = world.getAllCommands().get(commandIndex);
        A4Solution ans = TranslateAlloyToKodkod.execute_commandFromBook(rep, world.getAllReachableSigs(), command, options);
        currentSolution = ans;
        //ans.writeXML(rep, xmlfilename, null, null);
        return this;
    }

    /** Pass in "diff", "diffCone", "diffAntiCone", "shrink", "minimize", "grow", or "maximize" 
     * @throws IOException */
    public TestUI next(String type) throws Err, IOException {
        //A4Solution ans = getAns(); // read in XML for current model
        currentSolution = currentSolution.next(type);
        //ans = ans.next(type);      // advance to next model
        //ans.writeXML(rep, xmlfilename, null, null); // write out next-model XML
        return this;
    }

    /*public A4Solution getAns() throws Err, IOException {
        return A4SolutionReader.read(world.getAllReachableSigs(), new XMLNode(new File(xmlfilename)));                
    }*/

    public Set<TupleInExpr> getTestableTuples(boolean pos) throws Err, IOException {
        //return AmalgamUI.getTestableTuples(world, getAns(), pos);
        return AmalgamUI.getTestableTuples(world, currentSolution, pos);
    }

    public List<ProvenanceTree> why(boolean pos, TupleInExpr test, boolean literal) throws Err, IOException {
        String prefix = pos ? "+" : "-";
        whyLN(log, world, currentSolution, prefix, test);
        finalizeProvenances();    
        if(literal) return AmalgamUI.literalProvenanceTrees;
        else return AmalgamUI.provenanceTrees;
    }

    public void close() {
        new File(xmlfilename).delete();
    }
}
