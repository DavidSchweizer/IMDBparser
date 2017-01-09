package com.company;

import java.io.IOException;

public class Main {

    public static void main(String[] args)
    {
        try {
            //new ParserEngine("movies.list.cfg").process();
            new ParserEngine("soundtracks.list.cfg").process("sndtrks.list");
//            new ParserEngine("actors.list.cfg").process();
//            new ParserEngine("actors.list.cfg").process("actresses.list");
        } catch (IOException e) {
            e.printStackTrace();
        } catch (IMDBParserException e) {
            e.printStackTrace();
        }
    }
}
