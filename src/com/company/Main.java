package com.company;

import java.io.IOException;

public class Main {

    public static void main(String[] args) throws IOException //throws IOException
    {
        new ParserEngine("movies.list.cfg").process();
        //new ParserEngine("actors.list.cfg").process("act0.list");
    }
}
