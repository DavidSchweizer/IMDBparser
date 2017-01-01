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
import static java.lang.String.format;
import static org.apache.commons.lang3.math.NumberUtils.toInt;

/**
 * Created by David Schweizer on 25-12-2016.
 */
public class ParserEngine {

    private static final int FEEDBACK = 10000, FEEDBACK2 = 500000;
    private List<PatternSequence> sequences;

    private FileWriter file;
    private BufferedWriter errorLog;
    private CSVWriter writer;
    private String[] currentRecord = null;
    private List<String> columnNames;
    private Map<String, Integer> columnIndex;

    private int curSequence = -1; // the sequence currently being parsed

    public String fileName;

    public ParserEngine() throws IOException {
        sequences = new ArrayList<>();
        sequences.add(new PatternSequence(0)); // make sure there is at least one sequence in the list
        fileName = "";
        columnNames = new ArrayList<>();
        columnIndex = new HashMap<>();
    }

    private String substituteQuotes(Map<String, String> elements, String s) {
        Pattern p = Pattern.compile("\\<[A-Z][A-Z0-9]*\\>");
        String result = s;
        int changes = -1;
        while (changes != 0) // note: elements may be nested
        {
            changes = 0;
            Matcher m = p.matcher(result);
            while (m.find()) {
                if (elements.get(m.group()) != null) {
                    result = result.replace(m.group(), elements.get(m.group()));
                    changes++;
                }
            }
        }
        return result;
    }

    private void setPatternName(int curSequence, String curSequenceName) {
        // add all sequences up to and including this one, if not yet done
        for (int i = sequences.get(sequences.size() - 1).sequence + 1; sequences.size() <= curSequence; i++)
            sequences.add(new PatternSequence(i));
        if (!curSequenceName.isEmpty())
            sequences.get(curSequence).sequenceName = curSequenceName;
    }

    private SequencedPattern addPattern(String newPattern, int curSequence, int curIndex) {
        // add all sequences up to and including this one, if not yet done
        for (int i = sequences.get(sequences.size() - 1).sequence + 1; sequences.size() <= curSequence; i++)
            sequences.add(new PatternSequence(i));
        return sequences.get(curSequence).addPattern(newPattern, curIndex);
    }

    private void readConfigFile(String configName, Map<String, String> elements) throws IOException {

        int curSequence = 0, curIndex = 0;
        boolean isRepeatable;
        SequencedPattern pattern;
        BufferedReader configFile = new BufferedReader(new FileReader(configName));
        try {
            String s = configFile.readLine().trim();
            String newPattern;
            String curSequenceName;
            while (s != null) {
                s = s.trim();
                isRepeatable = false;
                newPattern = "";
                curSequenceName = "";
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
                            String[] s2 = s.split(":");
                            curSequence = toInt(s2[1].trim(), curSequence);
                            if (s2.length > 2)
                                curSequenceName = s2[2];
                            else
                                curSequenceName = "";
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
                            throw new IOException(format("Illegal input %s in configfile %s", s, configName));
                    }
                if (!curSequenceName.isEmpty())
                    setPatternName(curSequence, curSequenceName);
                if (!newPattern.isEmpty()) {
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

    public ParserEngine(String configName) throws IOException {
        this();
        Map<String, String> elements = new HashMap<>();
        try {
            readConfigFile(configName, elements);
        } catch (IOException E) {
            System.out.format("Error (%s) reading configfile %s", configName, E.getMessage());
        }
    }

    private void getGroupNames() throws IMDBParserException {
        Integer indexInSequence[] = new Integer[sequences.size()];
        SequencedPattern pattern;

        //  cycle through all groups in order of index/sequence and add to columns/indices
        for (int i = 0; i < sequences.size(); i++) {
            indexInSequence[i] = 0;
        }
        boolean ready = false;
        while (!ready) {
            int iMinSeq = 0;
            int minI = 123456; // whatever high value

            // find next lowest index
            for (int i = 0; i < indexInSequence.length; i++) {
                if (indexInSequence[i] < sequences.get(i).size() &&
                        sequences.get(i).get(indexInSequence[i]).index < minI) {
                    iMinSeq = i;
                    minI = sequences.get(iMinSeq).get(indexInSequence[iMinSeq]).index;
                }
            }
            if (iMinSeq >= sequences.size() || minI >= sequences.get(iMinSeq).size())
                ready = true;
            else {   // add the groups if not yet inserted
                pattern = sequences.get(iMinSeq).get(indexInSequence[iMinSeq]);
                for (String group : pattern.groups) {
                    if (!columnNames.contains(group))
                        columnNames.add(group);
                }
                indexInSequence[iMinSeq]++;
            }
        }
        // now build the hash map to translate group names to column index
        columnIndex.clear();
        for (String name : columnNames)
            columnIndex.put(name, columnNames.indexOf(name));
        // and insert in all sequences
        for (PatternSequence sequence : sequences)
            sequence.setColumns(columnIndex);
    }

    private void clearRecord() {
        if (currentRecord == null || currentRecord.length != columnNames.size())
            currentRecord = new String[columnNames.size()];
        for (int i = 0; i < currentRecord.length; i++)
            currentRecord[i] = "";
    }

    private void initCSVfile(String fileCSV) throws IOException {

        file = new FileWriter(fileCSV);
        writer = new CSVWriter(file);
        writer.writeNext(columnNames.toArray(new String[0]));
        writer.flush();
        errorLog = new BufferedWriter(new FileWriter(format("%s.error", fileCSV)));
    }


    private void closeCSVfile() throws IOException {
        writer.close();
        errorLog.close();
    }

    public void process(String aFileName) throws IOException, IMDBParserException {
        this.fileName = aFileName.trim();
        process();
    }

    private String makeString()
    {
        String result = "";
        for (int i = 0; i < currentRecord.length; i++)
            result = String.format("%s\"%s\" ", result, currentRecord[i]);
        return result;
    }

    private static final int ESTIMATELINES = 20000;
    public void process() throws IOException, IMDBParserException {
        int nLines = 0, nWritten = 0, nError = 0;
        String fileNameCSV = format("%s.csv",  this.fileName);
        StopWatch sw = new StopWatch();
        PatternSequence sequence;
        int sequenceIndex = 0;
        long fileSize;
        boolean written;

        getGroupNames();

        // get file size estimate
        File temp = new File(fileName);
        fileSize = temp.length();

        try
        {
            initCSVfile(fileNameCSV);

            DebugLogger.Log("IMDBparser  START PROCESSING %n\tInput file \t%s%n\tOutput file \t%s%n---------------------------------%n", fileName, fileNameCSV);
            try (InputStreamReader input = new InputStreamReader(new FileInputStream(fileName), "Windows-1252");
                    BufferedReader reader = new BufferedReader(input))
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
                            Log("writing %s%n", makeString());
                            writer.writeNext(currentRecord);
                            written = true;
                            nWritten++;
                        } else if (++sequenceIndex < sequences.size())
                            sequence = sequences.get(sequenceIndex);
                        else
                            sequence = null;
                    }
                    DebugLogger.Log("*** END PROCESS LINE *** !! %b !! ***%n", s2.isEmpty());
                    if (!s2.isEmpty() ||!written) {
                        errorLog.write(s);
                        errorLog.newLine();
                        nError++;
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
                System.out.format("%nReady after %s. %d lines processed. Successfully written: %d lines. Errors: %d lines.%n", sw.toString(),  nLines, nWritten, nError);
            }
        }
        finally
        {
            closeCSVfile();
        }
    }
}
