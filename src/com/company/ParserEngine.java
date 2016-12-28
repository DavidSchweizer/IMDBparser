package com.company;

import com.opencsv.CSVWriter;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;

import org.apache.commons.lang3.time.StopWatch;

import static org.apache.commons.lang3.math.NumberUtils.toInt;

/**
 * Created by David Schweizer on 25-12-2016.
 */
public class ParserEngine {

    static final int FEEDBACK = 10000, FEEDBACK2 = 500000;
    private List<SequencedPattern> patterns;
    private FileWriter file;
    private BufferedWriter errorLog;
    private CSVWriter writer;
    private BufferedWriter debugLog;
    private boolean __debug = false;
    private boolean __skip = true; // to skip special lines such as {{SUSPENDED}}
    private boolean __skipRest = false; // to accept all lines, use only during building config file

    public String fileName;
    private List<String> groups;
    private String[] currentRecord = null;

    public ParserEngine() throws IOException {
        patterns = new ArrayList<SequencedPattern>();
        groups = new ArrayList<String>();
        fileName = "";
        debugLog = new BufferedWriter(new FileWriter("debug.log"));
    }

    public ParserEngine(String configName) throws IOException {
        this();

        int curSequence = 0, curIndex = 0;

        BufferedReader configFile = new BufferedReader(new FileReader(configName));
        try {
            String s = configFile.readLine().trim();
            String newPattern;
            while (s != null) {
                s = s.trim();
                newPattern = "";
                if (!s.isEmpty())
                    switch (s.charAt(0)) {
                        case '#': // comment
                            break;
                        case 'F': // filename
                            this.fileName = s.substring(2).trim();
                            break;
                        case 'S': // read new sequence
                            curSequence = toInt(s.substring(2).trim(), curSequence);
                            break;
                        case 'I': // read new index
                            curIndex = toInt(s.substring(2).trim(), curIndex);
                            break;
                        case 'P': // read new pattern
                            newPattern = s.substring(2).trim();
                            break;
                        default:
                            throw new IOException(String.format("Illegal input %s in configfile", s));
                    }
                if (!newPattern.isEmpty())
                    this.addPattern(newPattern, curSequence, curIndex);
                s = configFile.readLine();
            }
        } finally {
            configFile.close();
        }
    }

    public void dump() {
        for (int i = 0; i < patterns.size(); i++)
            System.out.format("pattern %d: %s%n", i + 1, patterns.get(i).toString());
        System.out.format("groups: ");
        for (String s : groups)
            System.out.format("%s ", s);
        System.out.format("%n");
    }


    public void dumpRecord(String msg) {
        System.out.format("%s%n\t", msg);
        for (int i = 0; i < currentRecord.length; i++) {
            System.out.format("%s", currentRecord[i]);
            if (i < currentRecord.length - 1)
                System.out.format(",");
            else
                System.out.format("%n");
        }
    }

    private void clearRecord() {
        for (int i = 0; i < currentRecord.length; i++)
            currentRecord[i] = "";
    }

    private void Log(String format, Object... params) throws IOException {
        if (!__debug)
            return;
        String s = String.format(format, params);
        debugLog.write(s);
        debugLog.flush();
    }

    private void debugIntList(String msg, List<Integer> list) throws IOException
    {
        Log(msg);
        for (int value: list)
            Log("%d ", value);
        Log("%n");
    }

    private void debugStringList(String msg, List<String> list) throws IOException
    {
        Log(msg);
        for (String value: list)
            Log("%s ", value);
        Log("%n");
    }

    public void readyForProcessing() throws IOException {
        patterns.sort(SequencedPattern::compareTo);
        addGroupNames();
        currentRecord = new String[groups.size()];
    }

    public boolean processLine(String line) throws IOException
    {
        String l = line;
        boolean result = false;
        int curSequence = -1;
        int curIndex = -1;

        Log("*** START PROCESS LINE ***%n%s%n", line);
        for (SequencedPattern p: patterns)
        {
            Matcher m = p.pattern.matcher(l);
            Log("line: %s. Pattern (%d,%d): %s  cur: %d%n", l, p.sequence, p.index, m.pattern(), curSequence);
            if (curSequence != -1 && curSequence != p.sequence) // different sequence: skip
                continue;
            else if (curSequence == -1)
            {
                if (p.index > 1) // can not use pattern before first of sequence!
                    continue;
            }
            if (curIndex == p.index) // is set only if one found with this index
                continue;
            if (m.find() && m.start() == 0)
            {
                if (curSequence == -1 && p.sequence > 0)
                {
                    curSequence = p.sequence;
                }
                curIndex = p.index; // skip rest of patterns with same index
                Log("found %d  %d  %s%n", m.start(), m.end(), m.group());
                l = processMatch(m, p, l);
                if (l.isEmpty()) // whole line parsed
                   result = true;
                if (__skipRest) // whole line parsed
                    result = true;
            }
        }
        Log("*** END PROCESS LINE *** !!! %b !!!%n", result);
        return result;
    }

    private String processMatch(Matcher m, SequencedPattern p, String l)
    {
        String group = "";
        for (int i  = 0; i < p.groups.size(); i++)
        {
            try
            {
                group = m.group(p.groups.get(i));
            }
            catch (IllegalArgumentException ignored) {
            }
            if (group != null)
            {
                currentRecord[p.columns.get(i)] = group;
                __skip = false;
            }
        }
        // remove matched part from string and extra whitespaces and return
        return l.substring(m.end()).trim();
    }

    private void addGroupNames() throws IOException {
        // add names of groups in index->sequence order to get natural order of columns
        groups.clear();

        List<Integer> sequences = new ArrayList<Integer>();
        List<Integer> indices = new ArrayList<Integer>();
        int index;

        for (SequencedPattern p: patterns)
        {
            for (String s : p.groups)
                if (!groups.contains(s))
                {
                    // find index to insert (first sequence 1, 1, then sequence 2, index 1, etc)
                    index = 0;
                    while (index < indices.size() && indices.get(index) < p.index)
                        index++;
                    while (index < sequences.size() && sequences.get(index) <= p.sequence && indices.get(index) <= p.index)
                        index++;
                    groups.add(index, s);
                    sequences.add(index, p.sequence);
                    indices.add(index, p.index);
                }
        }
        // now set the columns for each pattern to speed up processing later
        for (SequencedPattern p: patterns) {
            for (int i = 0; i < p.groups.size(); i++)
                p.columns.set(i, groups.indexOf(p.groups.get(i)));
        }
    }

    public void addPattern(String patternString, int sequence, int index)
    {
        patterns.add(new SequencedPattern(patternString, sequence, index));
    }

    private void initCSVfile(String fileCSV) throws IOException
    {
        file = new FileWriter(fileCSV);
        writer = new CSVWriter(file);
        writer.writeNext(groups.toArray(new String[0]));
        writer.flush();
        errorLog = new BufferedWriter(new FileWriter(String.format("%s.error", fileCSV)));

    }

    public void parseLine(String line) throws IOException
    {
    // no flushing because of performance
        clearRecord();
        __skip = true; // set to false if non-empty string parsed into currentRecord
        if (processLine(line))
        {
            if (__skip)
                return;
            writer.writeNext(currentRecord);
        }
        else
        {
            if (line.trim().isEmpty()) // no point in showing
                return;
            errorLog.write(line);
            errorLog.newLine();
        }
    }

    private void closeCSVfile() throws IOException
    {
        writer.close();
        errorLog.close();
    }

    public void process(String aFileName) throws IOException
    {
        this.fileName = aFileName;
        process();
    }

    public void process() throws IOException
    {
        int nLines = 0;
        readyForProcessing();
        String fileNameCSV = String.format("%s.csv",  this.fileName);
        StopWatch sw = new StopWatch();
        try
        {
            initCSVfile(fileNameCSV);
            try (BufferedReader reader = new BufferedReader(new FileReader(fileName)))
            {
                String s = reader.readLine();
                sw.start();
                while (s != null)
                {
                    parseLine(s);
                    s = reader.readLine();
                    if (nLines%FEEDBACK2 == 0)
                        System.out.format("%n%d%n", nLines);
                    if (nLines++ % FEEDBACK == 0)
                        System.out.print(".");
                }
                sw.stop();
                System.out.format("%nReady after %s. %d lines processed.%n", sw.toString(),  nLines);

            }
        }
        finally
        {
            closeCSVfile();
        }
    }
}
