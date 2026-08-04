package com.dezxxx.minios3.exception;

public class FileNotFoundException extends RuntimeException {
    public FileNotFoundException(Integer id) {
        super("File not found: " + id);
    }
}
