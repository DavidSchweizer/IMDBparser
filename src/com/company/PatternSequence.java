package com.company;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by David Schweizer on 29-12-2016.
 */
public class PatternSequence extends ArrayList<SequencedPattern> {
    public List<Integer> columns; // used to speed up finding the proper column for output
    public List<String> groups;
    public int sequence;
    public boolean needsEndOfLine = true;
    private boolean __skip;

    public PatternSequence(int aSequence)
    {
        columns = new ArrayList<Integer>();
        groups = new ArrayList<String>();
        sequence = aSequence;
    }

    public void addPattern(String patternString, int index)
    {
        add(new SequencedPattern(patternString, sequence, index));
        this.sort(SequencedPattern::compareTo);
        addGroupNames();
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

    public int groupCount()
    {
        return groups.size();
    }

    public String processMatch(SequencedPattern p, Matcher m, String l, String[]record, Map<String,Integer> columns)
    {
/*
        String value = "";
        String column = "";
        for (int i  = 0; i < groups.size(); i++)
        {
            try
            {
                column = groups.get(i);
                value = m.group(column);
            }
            catch (IllegalArgumentException ignored) {
                value = null;
            }
            if (value != null)
            {
                record[columns.get(column)] = value;
                __skip = false;
            }
        }
        */
        for (int i  = 0; i < groups.size(); i++)
        {
            try
            {
                record[columns.get(groups.get(i))] = m.group(groups.get(i));
            }
            catch (IllegalArgumentException ignored) {
            }
        }// remove matched part from string and extra whitespaces and return
        return l.substring(m.end()).trim();
    }

    public String processLine(String line, String[]record, Map<String,Integer> columns) throws IOException
    {
        String l = line;
        boolean result = false;
        boolean first = false;

        int curIndex = -1;
        if (line.trim().isEmpty())
            return "";
        __skip = true; // set to false if changes made
        DebugLogger.Log("* start sequence *%d%n", sequence);
        for (SequencedPattern p: this)
        {
            Matcher m = p.pattern.matcher(l);
            DebugLogger.Log("line:|%s| Pattern (%d): %s%n", l, p.index, m.pattern());
            if (m.find() && m.start() == 0)
            {
                DebugLogger.Log("found %d  %d  %s%n", m.start(), m.end(), m.group());
                l = processMatch(p, m, l, record, columns);
                first = true;
                if (l.isEmpty()) // whole line parsed
                    break;
            }
            else if (p.index > 1 && !first) // needs to have first index
                break;
        }
        DebugLogger.Log("* end sequence %d *%n", sequence);
        return l;
    }

}

