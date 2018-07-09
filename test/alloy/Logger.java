package alloy;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;

@SuppressWarnings("unused")
public final class Logger {
    private static final boolean log = false;
    private static final String hs = "\t";
    private static String file; // latest file opened/saved
    private static PrintWriter out; // file io
    private static Long start; // start of logging


    private static void init(String alsfile) {
        if(log && file==null) {
            try {
                file = alsfile.substring(0,alsfile.lastIndexOf("/"))+"/AMALGAM.log";
                start = System.currentTimeMillis();
                // Check for old log
                String oldlog = "";
                if(new File(file).exists()) {
                    oldlog = new String(Files.readAllBytes(new File(file).toPath()));
                }
                out = new PrintWriter(file, "UTF-8");
                out.println(oldlog);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    // TWO WAYS TO INIT LOG: opening an old file or saving a new one
    public static void open(String alsfile) throws IOException  {
        init(alsfile);
        log(Event.OPEN, alsfile);
    }
    public static void save(String alsfile) {
        init(alsfile);
        log(Event.SAVE, alsfile);
    }

    // Event Logging
    public static enum Event {
        OPEN,
        SAVE,
        CLOSE,
        EXECUTE,
        SHOW,
        NEXT,
        EVAL
    }
    public static void log(Event e, String args) {
        if(log && file!=null) {
            System.err.println(start);
            String logline = (System.currentTimeMillis()-start)+hs+e.name()+hs+args;
            out.println(logline);
            out.flush();
        }
    }
}
