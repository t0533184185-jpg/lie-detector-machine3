package com.example.demo.analyzer;

import com.example.demo.model.FrameData;

// LieDetectorAnalyzer.java
public interface LieDetectorAnalyzer {
    // מקבל את הפריים הנוכחי ומחזיר האם התנועה זוהתה בפריים זה (או טווח ערכים)
    boolean analyze(FrameData frame);
    String getGestureName();
    // 🔥 שורת הצינור החדשה: מאפשרת להזרים FPS לכל מי שרוצה להקשיב
    default void setFps(double fps) {}
}
