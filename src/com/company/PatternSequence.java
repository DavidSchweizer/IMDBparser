package com.company;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;

/**
 * Created by David Schweizer on 29-12-2016.
 */
public class PatternSequence extends ArrayList<SequencedPattern> {
    private List<Integer> columns; // used to speed up finding the proper column for output
    private List<String> groups; // all groups in the sequence
    public int sequence;

    public boolean needsEndOfLine = true;
    private boolean __skip;

    public PatternSequence(int aSequence) {
        columns = new ArrayList<>();
        groups = new ArrayList<>();
        sequence = aSequence;
    }

    public SequencedPattern addPattern(String patternString, int index) {
        SequencedPattern result = new SequencedPattern(patternString, sequence, index);
        add(result);
        this.sort(SequencedPattern::compareTo);
        addGroupNames();
        return result;
    }

    public boolean recordedMatch()
    {
        return !__skip;
    }

    private void addGroupNames()
    {
        // add names of groups in index->sequence order and get natural order of columns
        groups.clear();
        columns.clear();
        List<Integer> sequences = new ArrayList<>();
        List<Integer> indices = new ArrayList<>();
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
    }

    public void setColumns(Map<String, Integer> mapping) throws IMDBParserException
    {
        for (String group:groups)
        {
            if (mapping.containsKey(group))
               columns.set(groups.indexOf(group), mapping.get(group));
            else
                throw new IMDBParserException("invalid call to SetColumns");
        }
    }


    private String processMatch(SequencedPattern p, Matcher m, String l, String[]record)
    {
        for (String match: p.groups)
        {
            try
            {
                int i = columns.get(this.groups.indexOf(match));
                if (!p.isRepeatable || record[i].isEmpty())
                    record[i] = m.group(match);
                else
                    record[i] = String.format("%s,%s", record[i], m.group(match));
                __skip = false; // if we get here, there was an actual match recorded
            }
            catch (IllegalArgumentException ignored) { // not all groups in pattern may be present in the string
            }
        }// remove matched part from string and extra whitespaces and return
        return l.substring(m.end() - m.start()).trim();
    }

    public String processLine(String line, String[]record) throws IOException
    {
        String l = line;
        boolean result = false;
        boolean first = false;

        int curIndex = -1;
        if (line.trim().isEmpty())
            return "";
        __skip = true; // set to false if changes made
        DebugLogger.Log("* start sequence %d *%n", sequence);
        for (SequencedPattern p: this)
        {
            if (p.index == curIndex) // already one found with same index
                continue;
            curIndex = -1;
            Matcher m = p.pattern.matcher(l);
            DebugLogger.Log("line:|%s| Pattern (%d): %s%n", l, p.index, m.pattern());
            int start = 0;
            while (m.find() && m.start() == start)
            {
                DebugLogger.Log("found %d  %d  %s%n", m.start(), m.end(), m.group());
                l = processMatch(p, m, l, record);
                first = true;
                if (l.isEmpty()// whole line parsed
                     &&
                     !p.isRepeatable)
                    break;
                curIndex = p.index;
                if (p.isRepeatable)
                    start = m.end();
            }
            if ((p.index > 1 && !first) // needs to have first index
                || l.isEmpty())
                break;
        }
        DebugLogger.Log("* end sequence %d *%n", sequence);
        return l;
    }

}

