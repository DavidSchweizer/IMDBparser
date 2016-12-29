package com.company;

import java.io.IOException;

public class Main {
    private static void test(ParserEngine patty, String s) throws IOException
    {
        patty.processLine(s);
        patty.dumpRecord(s);
    }

    public static void main(String[] args) throws IOException //throws IOException
    {
        //new ParserEngine("movies.list.cfg").process();
        new ParserEngine("actors.list.cfg").process("s_act2.txt");
    }
}
