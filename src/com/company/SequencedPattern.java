package com.company;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.lang.String.format;

/**
 * Created by David Schweizer on 25-12-2016.
 *
 */
public class SequencedPattern implements Comparable<SequencedPattern>
{
    static private String matchGroups = "\\?\\<\\w+\\>";
    static private Pattern pGroups = Pattern.compile(matchGroups);

    public Pattern pattern;
    public List<String> groups;
    public List<Integer> columns; // used to speed up finding the proper column for output
    int sequence; // a file may xontain one or more sequences that may be distinguished in parsing, eg. movies and series
    int index;    // ordering within the sequence

    private void addGroupNames(String patternString)
    {
        // make sure group names are added once
        Matcher matcher = pGroups.matcher(patternString);
        while (matcher.find()) {
            String group = patternString.substring(matcher.start() + 2, matcher.end() - 1);
            if (!groups.contains(group))
                groups.add(group);
        }
        columns.clear();
        for (int i = 0; i < groups.size(); i++)
            columns.add(i); // note: columns will have to be set from ParserEngine
    }

    SequencedPattern(String patternString, int aIndex, int aSequence)
    {
        pattern = Pattern.compile(patternString);
        sequence = aIndex;
        index = aSequence;
        groups = new ArrayList<String>();
        columns= new ArrayList<Integer>();
        addGroupNames(patternString);
    }

    @Override
    public String toString()
    {
        StringBuilder  sb = new StringBuilder(format("|%s| (%d)", pattern.toString(), index));
        for (String s: groups)
            sb.append(format("-%s ", s));
        return sb.toString();
    }

    @Override
    public int compareTo(SequencedPattern o)
    {
        if (this.sequence > o.sequence)
            return 1;
        else
        if (this.sequence < o.sequence)
            return -1;
        else
        {
            if (this.index > o.index)
                return 1;
            else if (this.index < o.index)
                return -1;
            else
                return 0;
        }
    }
}
