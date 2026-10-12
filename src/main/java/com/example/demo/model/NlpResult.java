package com.example.demo.model;

public class NlpResult {
    private String sentence;
    private double score;
    private double confidence;

    // 1. קונסטרקטור משולש (עבור הקוד החדש שמחשב ביטחון אדפטיבי)
    public NlpResult(String sentence, double score, double confidence) {
        this.sentence = sentence;
        this.score = score;
        this.confidence = confidence;
    }

    // 2. קונסטרקטור כפול (עבור תאימות לאחור - קוד ישן יקבל אוטומטית ביטחון 1.0)
    public NlpResult(String sentence, double score) {
        this.sentence = sentence;
        this.score = score;
        this.confidence = 1.0; // ברירת מחדל
    }

    // Getters ו-Setters
    public String getSentence() {
        return sentence;
    }

    public void setSentence(String sentence) {
        this.sentence = sentence;
    }

    public double getScore() {
        return score;
    }

    public void setScore(double score) {
        this.score = score;
    }

    public double getConfidence() {
        return confidence;
    }

    public void setConfidence(double confidence) {
        this.confidence = confidence;
    }
}