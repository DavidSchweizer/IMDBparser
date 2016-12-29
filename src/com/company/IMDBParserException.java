package com.company;

/**
 * Created by David Schweizer on 29-12-2016.
 */
public class IMDBParserException extends Exception {
    public IMDBParserException(String message) {
        super(message);
    }

    public IMDBParserException(String message, Throwable throwable)
    {
        super(message, throwable);
    }
}
