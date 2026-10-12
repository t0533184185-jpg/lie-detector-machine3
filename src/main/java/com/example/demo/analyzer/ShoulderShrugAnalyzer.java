package com.example.demo.analyzer;

import com.example.demo.model.FrameData;
import com.example.demo.model.Point3D;
import org.springframework.stereotype.Component;

@Component
public class ShoulderShrugAnalyzer implements LieDetectorAnalyzer {

    private static final double ALPHA = 0.2; // EMA
    private double smoothedRatio = -1.0;
    private double baseRatio = -1.0;

    // מכונת מצבים (FSM)
    private int state = 0;
    private int frameCounter = 0;

    @Override
    public boolean analyze(FrameData frame) {
        if (frame.getPoseLandmarks() == null) return false;

        Point3D shoulderL = frame.getPoseLandmarks().get(11);
        Point3D shoulderR = frame.getPoseLandmarks().get(12);
        Point3D earL = frame.getPoseLandmarks().get(7);
        Point3D earR = frame.getPoseLandmarks().get(8);

        if (shoulderL == null || shoulderR == null || earL == null || earR == null) return false;

        // 1. חישוב יחס נוכחי
        double distL = calculateDistance(earL, shoulderL);
        double distR = calculateDistance(earR, shoulderR);
        double shoulderWidth = calculateDistance(shoulderL, shoulderR);

        if (shoulderWidth == 0) return false;
        double currentRatio = (distL + distR) / (2.0 * shoulderWidth);

        // 2. החלקת EMA
        if (smoothedRatio == -1.0) {
            smoothedRatio = currentRatio;
        } else {
            smoothedRatio = (ALPHA * currentRatio) + ((1.0 - ALPHA) * smoothedRatio);
        }

        // 3. 🌟 קיבוע ה-Base Ratio על הפריים הראשון ממש (פריים 0) 🌟
        // במקום לחכות 25 פריימים ולפספס את התנועה, אנחנו לוקחים את פריים 0 כנקודת הייחוס.
        // אם בפריים 0 הכתפיים כבר קצת מורמות, נשתמש ב-0.98 כבסיס קבוע ומאובטח.
        if (baseRatio == -1.0) {
            baseRatio = Math.max(smoothedRatio, 0.98);
            System.out.println("🤷 [Shoulder Shrug] Immediate Base Locked at: " + baseRatio);
        }

        // 4. הרצת מכונת המצבים מיד מהפריים הראשון!
        return evaluateFSM(smoothedRatio);
    }

    private boolean evaluateFSM(double ratio) {
        // ספים דינמיים רגישים המותאמים לסרטון שלך
        double thresholdUp = baseRatio * 0.94;    // ירידה של 12% במרחק = עלייה של הכתפיים
        double thresholdRest = baseRatio * 0.98;  // חזרה ל-96% מהגובה המקורי = ירידה של הכתפיים

        final int MIN_FRAMES_AT_PEAK = 1;
        final int MAX_FRAMES_TOTAL = 60;

        switch (this.state) {
            case 0: // מנוחה
                if (ratio < thresholdUp) {
                    this.state = 1;
                    this.frameCounter = 1;
                }
                break;

            case 1: // בתנועה למעלה
                this.frameCounter++;

                if (ratio < thresholdUp) {
                    if (this.frameCounter > MAX_FRAMES_TOTAL) {
                        resetFSM();
                    }
                } else if (ratio > thresholdRest) {
                    if (this.frameCounter >= MIN_FRAMES_AT_PEAK) {
                        resetFSM();
                        return true; // 🚨 משיכת כתפיים זוהתה!
                    } else {
                        resetFSM();
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

    private void resetFSM() {
        this.state = 0;
        this.frameCounter = 0;
    }

    @Override
    public String getGestureName() {
        return "Shoulder Shrug";
    }
}