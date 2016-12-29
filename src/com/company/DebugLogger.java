package com.company;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

/**
 * Created by David Schweizer on 29-12-2016.
 */
public class DebugLogger  {
    private BufferedWriter debugLog;
    private static boolean __debug = false;
    private static DebugLogger instance;

    private DebugLogger() throws IOException {
        debugLog = new BufferedWriter(new FileWriter("debug.log"));
    }

    public static void Log(String format, Object... params) throws IOException {
        if (!__debug)
            return;
        if (instance == null)
            instance = new DebugLogger();
        instance.debugLog.write(String.format(format, params));
        instance.debugLog.flush();
    }

    private static void debugIntList(String msg, List<Integer> list) throws IOException {
        Log(msg);
        for (int value : list)
            Log("%d ", value);
        Log("%n");
    }

    private static void debugStringList(String msg, List<String> list) throws IOException {
        Log(msg);
        for (String value : list)
            Log("%s ", value);
        Log("%n");
    }
}
