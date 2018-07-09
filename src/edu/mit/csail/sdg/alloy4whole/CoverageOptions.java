package edu.mit.csail.sdg.alloy4whole;

public class CoverageOptions {
    public long timeLimit;
    public int modelLimit;
    public boolean showProv;
    
    public CoverageOptions() {
        timeLimit = -1;
        modelLimit = -1;
        showProv = true;
    }
    
    public CoverageOptions dup() {
        CoverageOptions ret = new CoverageOptions();
        ret.timeLimit = timeLimit;
        ret.modelLimit = modelLimit;
        ret.showProv = showProv;
        return ret;
    }
}
