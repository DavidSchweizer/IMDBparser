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

import static com.company.DebugLogger.Log;
import static org.apache.commons.lang3.math.NumberUtils.toInt;

/**
 * Created by David Schweizer on 25-12-2016.
 */
public class ParserEngine {

    static final int FEEDBACK = 10000, FEEDBACK2 = 500000;
    private List<PatternSequence> sequences;

    private FileWriter file;
    private BufferedWriter errorLog;
    private CSVWriter writer;
    public String[] currentRecord = null;
    List<String> columnNames;
    private Map<String, Integer> columnIndex;

    private int curSequence = -1; // the sequence currently being parsed




    public String fileName;

    public ParserEngine() throws IOException
    {
        sequences = new ArrayList<PatternSequence>();
        sequences.add(new PatternSequence(0)); // make sure there is at least one sequence in the list
        fileName = "";
        columnNames = new ArrayList<>();
        columnIndex = new HashMap<>();
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

    SequencedPattern addPattern(String newPattern, int curSequence, int curIndex)
    {
        // add all sequences up to and including this one, if not yet done
        for (int i = sequences.get(sequences.size()-1).sequence+1; sequences.size() <= curSequence; i++)
           sequences.add(new PatternSequence(i));
        return sequences.get(curSequence).addPattern(newPattern, curIndex);
    }

    private void readConfigFile(String configName, Map <String,String> elements) throws IOException {

        int curSequence = 0, curIndex = 0;
        boolean isRepeatable;
        SequencedPattern pattern;
        BufferedReader configFile = new BufferedReader(new FileReader(configName));
        try {
            String s = configFile.readLine().trim();
            String newPattern;
            while (s != null) {
                s = s.trim();
                isRepeatable = false;
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
                        case 'R': // new repeatable pattern
                            newPattern = substituteQuotes(elements, s.substring(2).trim());
                            isRepeatable = true;
                            break;
                        default:
                            throw new IOException(String.format("Illegal input %s in configfile %s", s, configName));
                    }
                if (!newPattern.isEmpty())
                {
                    pattern = addPattern(newPattern, curSequence, curIndex);
                    if (isRepeatable)
                        pattern.isRepeatable = true;
                }
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

/*
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

    private void getGroupNames() throws IMDBParserException {
        Integer indexInSequence[] = new Integer[sequences.size()];
        SequencedPattern pattern;

        //  cycle through all groups in order of index/sequence and add to columns/indices
        for (int i = 0; i < sequences.size(); i++)
        {
            indexInSequence[i] = 0;
        }
        boolean ready = false;
        while (!ready)
        {
            int iMinSeq = 0;
            int minI = 123456; // whatever high value

            // find next lowest index
            for (int i = 0; i < indexInSequence.length; i++)
            {
                if (indexInSequence[i] < sequences.get(i).size() &&
                        sequences.get(i).get(indexInSequence[i]).index < minI) {
                    iMinSeq = i;
                    minI = sequences.get(iMinSeq).get(indexInSequence[iMinSeq]).index;
                }
            }
            if (iMinSeq >= sequences.size() || minI >= sequences.get(iMinSeq).size())
               ready = true;
            else
            {   // add the groups if not yet inserted
                pattern = sequences.get(iMinSeq).get(indexInSequence[iMinSeq]);
                for (String group : pattern.groups)
                {
                    if (!columnNames.contains(group))
                        columnNames.add(group);
                }
                indexInSequence[iMinSeq]++;
            }
        }
        // now build the hash map to translate group names to column index
        columnIndex.clear();
        for(String name: columnNames)
            columnIndex.put(name, columnNames.indexOf(name));
        // and insert in all sequences
        for (PatternSequence sequence: sequences)
            sequence.setColumns(columnIndex);
    }

    private void clearRecord()
    {
        if (currentRecord == null || currentRecord.length != columnNames.size())
            currentRecord = new String[columnNames.size()];
        for (int i = 0; i < currentRecord.length; i++)
            currentRecord[i] = "";
    }

    private void initCSVfile(String fileCSV) throws IOException
    {

        file = new FileWriter(fileCSV);
        writer = new CSVWriter(file);
        writer.writeNext(columnNames.toArray(new String[0]));
        writer.flush();
        errorLog = new BufferedWriter(new FileWriter(String.format("%s.error", fileCSV)));
    }


    private void closeCSVfile() throws IOException
    {
        writer.close();
        errorLog.close();
    }

    public void process(String aFileName) throws IOException, IMDBParserException {
        this.fileName = aFileName;
        process();
     }

    static final int ESTIMATELINES = 20000;
    public void process() throws IOException, IMDBParserException {
        int nLines = 0;
        String fileNameCSV = String.format("%s.csv",  this.fileName);
        StopWatch sw = new StopWatch();
        PatternSequence sequence;
        int sequenceIndex = 0;
        long fileSize = -1;
        boolean written;

        getGroupNames();

        // get file size estimate
        File temp = new File(fileName);
        fileSize = temp.length();

        try
        {
            initCSVfile(fileNameCSV);
            try (BufferedReader reader = new BufferedReader(new FileReader(fileName)))
            {
                int SumLengthFirstLines = 0; // sums the first 1000 lines to estimate how long it will take
                float estimateTotalLines = -1;
                clearRecord();
                sw.start();
                String s = reader.readLine(), s2;
                while (s != null) {
                    if (nLines < ESTIMATELINES)
                        SumLengthFirstLines += s.length();
                    else if (nLines == ESTIMATELINES) {
                        estimateTotalLines = fileSize / (SumLengthFirstLines / ESTIMATELINES);
                    }
                    DebugLogger.Log("*** START PROCESS LINE *** %s %n", s);
                    sequence = sequences.get(sequenceIndex);
                    s2 = s;
                    written = false;
                    while (!s2.isEmpty() && sequence != null)
                    {
                        s2 = sequence.processLine(s2, currentRecord);
                        if (s2.isEmpty() && sequence.recordedMatch())
                        {
                            Log("writing%n");
                            writer.writeNext(currentRecord);
                            written = true;
                        } else if (++sequenceIndex < sequences.size())
                            sequence = sequences.get(sequenceIndex);
                        else
                            sequence = null;
                    }
                    DebugLogger.Log("*** END PROCESS LINE *** !! %b !! ***%n", s2.isEmpty());
                    if (!s2.isEmpty() ||!written) {
                        errorLog.write(s);
                        errorLog.newLine();
                    }
                    s = reader.readLine();
                    sequenceIndex = 0;
                    clearRecord();
                    if (nLines % FEEDBACK2 == 0)
                    {
                        if (estimateTotalLines > 0)
                            System.out.format("%n%d (%2.1f %%)%n", nLines, 100.0 * nLines / estimateTotalLines);
                        else
                            System.out.format("%n%d%n", nLines);
                    }

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
