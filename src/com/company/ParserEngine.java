package com.company;

import com.opencsv.CSVWriter;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.time.StopWatch;

import static org.apache.commons.lang3.math.NumberUtils.toInt;

/**
 * Created by David Schweizer on 25-12-2016.
 */
public class ParserEngine {

    static final int FEEDBACK = 10000, FEEDBACK2 = 500000;
    private SequencedPatterns patterns;
    private FileWriter file;
    private BufferedWriter errorLog;
    private CSVWriter writer;

    private int curSequence = -1; // the sequence currently being parsed


    public String fileName;

    public ParserEngine() throws IOException
    {
        patterns = new SequencedPatterns();
        fileName = "";
    }

    private String substituteQuotes(Map<String, String> elements, String s)
    {
        Pattern p = Pattern.compile("\\<[A-Z][A-Z0-9]*\\>");
        String result = s;
        int changes = -1;
        while (changes != 0) // note: elements may be nested
        {
            changes = 0;
            Matcher m = p.matcher(result);
            while (m.find())
            {
                if (elements.get(m.group()) != null)
                {
                    result = result.replace(m.group(), elements.get(m.group()));
                    changes++;
                }
            }
        }
        return result;
    }

    private void readConfigFile(String configName, Map <String,String> elements) throws IOException {

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
                        case 'N': // iNclude a file
                            readConfigFile(s.substring(2).trim(), elements);
                            break;
                        case 'E': // element
                            String[] els = s.split(":");
                            elements.put(els[0].substring(1), els[1]);
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
                            newPattern = substituteQuotes(elements, s.substring(2).trim());
                            break;
                        default:
                            throw new IOException(String.format("Illegal input %s in configfile %s", s, configName));
                    }
                if (!newPattern.isEmpty())
                    patterns.addPattern(newPattern, curSequence, curIndex);
                s = configFile.readLine();
            }
        } finally {
            configFile.close();
        }
    }

    public ParserEngine(String configName) throws IOException
    {
        this();
        Map<String,String> elements = new HashMap<>();
        try
        {
            readConfigFile(configName, elements);
        }
        catch(IOException E)
        {
            System.out.format("Error (%s) reading configfile %s", configName, E.getMessage());
        }
    }

    public void dump()
    {
        for (int i = 0; i < patterns.size(); i++)
            System.out.format("pattern %d: %s%n", i + 1, patterns.get(i).toString());
        System.out.format("groups: ");
        for (String s : patterns.groups)
            System.out.format("%s ", s);
        System.out.format("%n");
    }

/*
    private SequencedPattern findNextPattern(SequencedPattern curP)
    {

    }
*/

    private void initCSVfile(String fileCSV) throws IOException
    {
        file = new FileWriter(fileCSV);
        writer = new CSVWriter(file);
        writer.writeNext(patterns.groups.toArray(new String[0]));
        writer.flush();
        errorLog = new BufferedWriter(new FileWriter(String.format("%s.error", fileCSV)));

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
                    if (patterns.parseLine(s))
                    {
                        writer.writeNext(patterns.currentRecord);
                    }
                    else
                    {
                        errorLog.write(s);
                        errorLog.newLine();
                    }

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
