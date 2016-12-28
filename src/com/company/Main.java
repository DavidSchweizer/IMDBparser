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
	// write your code here
//        ParserEngine patty = new ParserEngine("actors.list.cfg");
//        patty.process();
       ParserEngine patty = new ParserEngine("actors.list.cfg");
       patty.process("smallact.txt");
    }
}
