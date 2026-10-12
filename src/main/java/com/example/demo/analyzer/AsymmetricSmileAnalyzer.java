package com.example.demo.analyzer;

import com.example.demo.model.FrameData;
import com.example.demo.model.Point3D;
import org.springframework.stereotype.Component;

@Component
public class AsymmetricSmileAnalyzer implements LieDetectorAnalyzer {

    private static final double ALPHA = 0.25; // מסנן EMA להחלקת תנועות השפתיים
    private double smoothedAsymmetryScore = -1.0;

    // משתני מכונת מצבים (FSM)
    private int state = 0; // 0 = מנוחה, 1 = חיוך א-סימטרי פעיל
    private int frameCounter = 0;

    @Override
    public boolean analyze(FrameData frame) {
        if (frame.getFaceLandmarks() == null) return false;

        // 1. שליפת נקודות זוויות הפה ומרכז השפה
        Point3D leftCorner = frame.getFaceLandmarks().get(61);
        Point3D rightCorner = frame.getFaceLandmarks().get(291);
        Point3D topLipCenter = frame.getFaceLandmarks().get(0);

        if (leftCorner == null || rightCorner == null || topLipCenter == null) return false;

        // 2. חישוב המרחקים לכל צד והרוחב הכולל לנורמליזציה
        double distLeftToCenter = calculateDistance(leftCorner, topLipCenter);
        double distRightToCenter = calculateDistance(rightCorner, topLipCenter);
        double totalLipWidth = calculateDistance(leftCorner, rightCorner);

        if (totalLipWidth == 0) return false;

        // חישוב ההפרש המוחלט בין הצדדים חלקי הרוחב הכולל
        double currentAsymmetry = Math.abs(distLeftToCenter - distRightToCenter) / totalLipWidth;

        // 3. החלקת EMA לסינון קפיצות רגעיות בזמן דיבור
        if (smoothedAsymmetryScore == -1.0) {
            smoothedAsymmetryScore = currentAsymmetry;
        } else {
            smoothedAsymmetryScore = (ALPHA * currentAsymmetry) + ((1.0 - ALPHA) * smoothedAsymmetryScore);
        }

        // 4. מכונת מצבים (FSM) לזיהוי משך החיוך הא-סימטרי
        return evaluateSmileFSM(smoothedAsymmetryScore);
    }

    private boolean evaluateSmileFSM(double asymmetryScore) {
        // סף אנטומי: הפרש של מעל 15% בין צידי הפה נחשב א-סימטרי בצורה חריגה
        final double THRESHOLD_ASYMMETRIC = 0.15;

        // חיוך מאולץ או הבעת זלזול נמשכים בדרך כלל לפחות חצי שנייה (15 פריימים)
        final int MIN_FRAMES = 15;
        // מניעת התרעות על עווית קבועה או מבנה פנים לא סימטרי טבעי (מעל 3 שניות)
        final int MAX_FRAMES = 90;

        switch (this.state) {
            case 0: // מצב רגיל
                if (asymmetryScore > THRESHOLD_ASYMMETRIC) {
                    // הפה נכנס למצב א-סימטרי חריג
                    this.state = 1;
                    this.frameCounter = 1;
                }
                break;

            case 1: // מצב א-סימטרי פעיל
                if (asymmetryScore > THRESHOLD_ASYMMETRIC) {
                    this.frameCounter++;

                    if (this.frameCounter > MAX_FRAMES) {
                        // אם האסימטריה נמשכת יותר מדי זמן, כנראה שזה פשוט מבנה הפנים הטבעי של האדם.
                        // נשבור את המצב כדי למנוע התרעות שווא קבועות.
                        resetAnalyzer();
                    }
                } else {
                    // הפה חזר למצב סימטרי/רגיעה
                    int finalDuration = this.frameCounter;
                    resetAnalyzer();

                    // חוק חכם: אם האסימטריה החזיקה מספיק זמן (ולא קרתה רק בגלל הגיית מילה) - זוהי הבעה חריגה!
                    if (finalDuration >= MIN_FRAMES) {
                        return true; // 🚨 זוהה חיוך א-סימטרי / הבעת הסוואה! 🚨
                    }
                }
                break;
        }

        return false;
    }

    private double calculateDistance(Point3D p1, Point3D p2) {
        return Math.sqrt(Math.pow(p1.getX() - p2.getX(), 2) +
                Math.pow(p1.getY() - p2.getY(), 2) +
                Math.pow(p1.getZ() - p2.getZ(), 2));
    }

    private void resetAnalyzer() {
        this.state = 0;
        this.frameCounter = 0;
    }

    @Override
    public String getGestureName() {
        return "Asymmetric / Masked Smile";
    }
}