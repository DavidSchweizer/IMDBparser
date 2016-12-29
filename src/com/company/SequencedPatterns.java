package com.company;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by David Schweizer on 29-12-2016.
 */
public class SequencedPatterns extends ArrayList<SequencedPattern> {
    public List<Integer> columns; // used to speed up finding the proper column for output
    public List<String> groups;
    private int curSequence = -1;
    public String[] currentRecord = null;
    private boolean __skip = true; // to skip special lines such as {{SUSPENDED}}

    public SequencedPatterns()
    {
        columns = new ArrayList<Integer>();
        groups = new ArrayList<String>();
    }

    public String processMatch(SequencedPattern p, Matcher m, String l)
    {
        String group = "";
        for (int i  = 0; i < groups.size(); i++)
        {
            try
            {
                group = m.group(groups.get(i));
            }
            catch (IllegalArgumentException ignored) {
            }
            if (group != null)
            {
                currentRecord[columns.get(i)] = group;
                __skip = false;
            }
        }
        // remove matched part from string and extra whitespaces and return
        return l.substring(m.end()).trim();
    }

    public boolean processLine(String line) throws IOException
    {
        String l = line;
        boolean result = false;

        int curIndex = -1;
        if (line.trim().isEmpty())
            return false;
        DebugLogger.Log("*** START PROCESS LINE ***%n%s%n", line);
        for (SequencedPattern p: this)
        {
            Matcher m = p.pattern.matcher(l);
            if (curSequence != -1 && curSequence != p.sequence) // different sequence: skip
                continue;
            else if (curSequence == -1)
            {
                if (p.index > 1) // can not use pattern before first of sequence!
                    continue;
            }
            if (curSequence == p.sequence && curIndex == p.index) // is set only if one found with this sequence and index
                continue;
            DebugLogger.Log("line:|%s| Pattern (%d,%d): %s  cur: %d%n", l, p.sequence, p.index, m.pattern(), curSequence);
            if (m.find() && m.start() == 0)
            {
                if (curSequence == -1 && p.sequence > 0)
                {
                    curSequence = p.sequence;
                }
                curIndex = p.index; // skip rest of patterns with same index
                DebugLogger.Log("found %d  %d  %s%n", m.start(), m.end(), m.group());
                l = processMatch(p, m, l);
                if (l.isEmpty()) // whole line parsed
                    result = true;
            }
        }
        DebugLogger.Log("*** END PROCESS LINE *** !!! %b !!!%n", result);
        return result;
    }

    public boolean parseLine(String line) throws IOException
    {
        clearRecord();
        __skip = true; // set to false if non-empty string parsed into currentRecord
        if (processLine(line))
        {
            return (!__skip);
        }
        else
        {
            return (line.trim().isEmpty()); // no point in showing
        }
    }


    private void addGroupNames()
    {
        // add names of groups in index->sequence order and get natural order of columns
        groups.clear();
        columns.clear();
        List<Integer> sequences = new ArrayList<Integer>();
        List<Integer> indices = new ArrayList<Integer>();
        int index;
        for (SequencedPattern p: this)
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
                    columns.add(0);
                }
        }
        // now set the columns for each pattern to speed up processing later
        for (SequencedPattern p: this) {
            for (int i = 0; i < p.groups.size(); i++)
                columns.set(i, groups.indexOf(p.groups.get(i)));
        }
        clearRecord();
    }


    private void clearRecord()
    {
        if (currentRecord == null || currentRecord.length != groups.size())
            currentRecord = new String[groups.size()];
        for (int i = 0; i < currentRecord.length; i++)
            currentRecord[i] = "";
    }

    public void addPattern(String patternString, int sequence, int index)
    {
        add(new SequencedPattern(patternString, sequence, index));
        this.sort(SequencedPattern::compareTo);
        addGroupNames();
    }
    public int groupCount()
    {
        return groups.size();
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


}

