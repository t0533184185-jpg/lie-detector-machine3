package com.example.demo.analyzer;

public class GestureRule {
    private String gestureName;
    private int p1;
    private int p2;
    private int p3;
    private int p4;
    private double threshold;
    private int minFrames;
    private int maxFrames;
    private String algorithmType;

    public GestureRule(String gestureName, int p1, int p2, int p3, int p4,
                       double threshold, int minFrames, int maxFrames, String algorithmType) {
        this.gestureName = gestureName;
        this.p1 = p1;
        this.p2 = p2;
        this.p3 = p3;
        this.p4 = p4;
        this.threshold = threshold;
        this.minFrames = minFrames;
        this.maxFrames = maxFrames;
        this.algorithmType = algorithmType;
    }

    // Getters
    public String getGestureName() { return gestureName; }
    public int getP1() { return p1; }
    public int getP2() { return p2; }
    public int getP3() { return p3; }
    public int getP4() { return p4; }
    public double getThreshold() { return threshold; }
    public int getMinFrames() { return minFrames; }
    public int getMaxFrames() { return maxFrames; }
    public String getAlgorithmType() { return algorithmType; }
}