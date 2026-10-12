package com.example.demo.model;

public class TranscribedSentence {

    private final String text;
    private final String startTime;
    private final String endTime;

    public TranscribedSentence(
            String text,
            String startTime,
            String endTime) {

        this.text = text;
        this.startTime = startTime;
        this.endTime = endTime;
    }

    public String getText() {
        return text;
    }

    public String getStartTime() {
        return startTime;
    }

    public String getEndTime() {
        return endTime;
    }
}