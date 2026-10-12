package com.example.demo.analyzer;

import com.example.demo.model.FrameData;
import com.example.demo.model.Point3D;
import org.springframework.stereotype.Component;
import java.util.LinkedList;
import java.util.Queue;

@Component
public class BodyFidgetingAnalyzer implements LieDetectorAnalyzer {

    // נשמור את מיקומי מרכז הגוף ב-5 השניות האחרונות (חלון זמן של 150 פריימים)
    private final Queue<Double> xPositions = new LinkedList<>();
//    private static final int WINDOW_SIZE = 150;

    private int windowSize = 150;

    @Override
    public void setFps(double fps) {
        // החלון מוגדר אצלך ל-5 שניות, אז נחשב דינמית:
        this.windowSize = (int) Math.round(5 * fps);
        System.out.println("🔄 BodyFidgeting חלון עודכן ל: " + this.windowSize + " פריימים");
    }

    @Override
    public boolean analyze(FrameData frame) {
        if (frame.getPoseLandmarks() == null) return false;

        Point3D shoulderL = frame.getPoseLandmarks().get(11);
        Point3D shoulderR = frame.getPoseLandmarks().get(12);

        if (shoulderL == null || shoulderR == null) return false;

        // חישוב רוחב הכתפיים לצורך נורמליזציה
        double shoulderWidth = Math.abs(shoulderL.getX() - shoulderR.getX());
        if (shoulderWidth == 0) return false;

        // חישוב נקודת האמצע של ה-X של הגוף
        double currentCenterX = (shoulderL.getX() + shoulderR.getX()) / 2.0;

        // הוספה לתור וניהול גודל החלון
        xPositions.add(currentCenterX);
        if (xPositions.size() > windowSize) {
            xPositions.poll();
        }

        // נתחיל לנתח רק כשהחלון מלא במידע
        if (xPositions.size() == windowSize) {
            // חישוב השונות (Variance) או טווח התנועה בחלון הזמן
            double minX = currentCenterX;
            double maxX = currentCenterX;

            for (double pos : xPositions) {
                if (pos < minX) minX = pos;
                if (pos > maxX) maxX = pos;
            }

            // טווח התנועה מנורמל ברוחב הכתפיים
            double movementRange = (maxX - minX) / shoulderWidth;

            // חוק: אם האדם זז מצד לצד בטווח שחורג מ-20% מרוחב הכתפיים שלו בתוך 5 שניות,
            // זה מעיד על חוסר שקט תנועתי חריג (Fidgeting)
            if (movementRange > 0.20) {
                return true; // 🚨 זוהה חוסר שקט ותזוזות גוף מוגברות! 🚨
            }
        }

        return false;
    }

    @Override
    public String getGestureName() {
        return "Body Fidgeting / Shifting";
    }
}