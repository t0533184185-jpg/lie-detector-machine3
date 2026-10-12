package com.example.demo.model;

import java.util.ArrayList;
import java.util.List;

public class WindowScores {

    private int windowIndex;
    private double contentScore;
    private double audioScore;
    private double bodyScore;
    private double finalScore;
    private List<String> suspicionReasons = new ArrayList<>();

    //רמת הביטחון
    private double contentConfidence = 1.0;
    private double audioConfidence = 1.0;
    private double bodyConfidence = 1.0;

    public WindowScores() {
    }

    public WindowScores(int windowIndex,double contentScore,double audioScore,double bodyScore)
    {
        this.windowIndex=windowIndex;
        this.contentScore=contentScore;
        this.audioScore=audioScore;
        this.bodyScore=bodyScore;
    }

    public int getWindowIndex() {
        return windowIndex;
    }

    public double getAudioScore() {
        return audioScore;
    }

    public double getBodyScore() {
        return bodyScore;
    }

    public double getContentScore() {
        return contentScore;
    }

    public void setContentScore(double contentScore) {
        this.contentScore = contentScore;
    }

    public void setAudioScore(double audioScore) {
        this.audioScore = audioScore;
    }

    public void setBodyScore(double bodyScore) {
        this.bodyScore = bodyScore;
    }

    public void setWindowIndex(int windowIndex) {
        this.windowIndex = windowIndex;
    }

    public double getContentConfidence() {
        return contentConfidence;
    }

    public void setContentConfidence(double contentConfidence) {
        this.contentConfidence = contentConfidence;
    }

    public double getAudioConfidence() {
        return audioConfidence;
    }

    public void setAudioConfidence(double audioConfidence) {
        this.audioConfidence = audioConfidence;
    }

    public double getBodyConfidence() {
        return bodyConfidence;
    }

    public void setBodyConfidence(double bodyConfidence) {
        this.bodyConfidence = bodyConfidence;
    }

    public double getFinalScore(){return finalScore;}

    public void setFinalScore(double finalScore){this.finalScore=finalScore;}

    public List<String> getSuspicionReasons() {
        return suspicionReasons;
    }

    public void addSuspicionReason(String reason) {
        if (!suspicionReasons.contains(reason)) {
            suspicionReasons.add(reason);
        }
    }
}
