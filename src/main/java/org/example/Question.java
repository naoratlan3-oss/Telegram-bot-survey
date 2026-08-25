package org.example;

import java.util.List;

public class Question {
    private final String text;
    private final List<String> options;

    public Question(String text, List<String> options) {
        this.text = text;
        this.options = options;
    }

    public String getText() { return text; }
    public List<String> getOptions() { return options; }
}