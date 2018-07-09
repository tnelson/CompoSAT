package edu.mit.csail.sdg.alloy4whole;

import java.io.File;
import java.io.IOException;
import java.util.Random;

import edu.mit.csail.sdg.alloy4.Err;
import edu.mit.csail.sdg.alloy4.XMLNode;
import edu.mit.csail.sdg.alloy4compiler.translator.A4Solution;
import edu.mit.csail.sdg.alloy4compiler.translator.A4SolutionReader;
import edu.mit.csail.sdg.alloy4compiler.translator.TranslateAlloyToKodkod;

abstract public class Enumerator {
    A4Solution ans;
    
    abstract public A4Solution init() throws Err;
    public boolean hasNext() {
        return ans.satisfiable();
    }
    public A4Solution next() throws Err, IOException {
        Random r = new Random();
        String filenameXML = "/tmp/amalgam-coverage" + r.nextInt();
        ans.writeXML(filenameXML);
        A4Solution ret = A4SolutionReader.read(ans.getAllReachableSigs(), new XMLNode(new File(filenameXML)));
        (new File(filenameXML)).delete();
        ans = ans.next("diff");
        return ret;
    }
    public boolean isFinished() {
        return !ans.satisfiable();
    }
    public void start() throws Err {
        ans = init();
    }
}
