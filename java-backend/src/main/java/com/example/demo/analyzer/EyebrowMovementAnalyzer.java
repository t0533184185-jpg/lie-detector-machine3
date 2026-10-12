package com.example.demo.analyzer;

import com.example.demo.model.FrameData;
import com.example.demo.model.Point3D;
import org.springframework.stereotype.Component;

@Component
public class EyebrowMovementAnalyzer implements LieDetectorAnalyzer {

    private static final double ALPHA = 0.2; // מקדם EMA לסינון רעידות קטנות
    private double smoothedDistanceBetween = -1.0;
    private double smoothedDistanceToEye = -1.0;

    // משתני מצב לאירועים פתאומיים (FSM)
    private int frownState = 0;
    private int raiseState = 0;
    private int frameCounterFrown = 0;
    private int frameCounterRaise = 0;

    @Override
    public boolean analyze(FrameData frame) {
        if (frame.getFaceLandmarks() == null) return false;

        // 1. שליפת נקודות גבות ועיניים
        Point3D leftBrowInner = frame.getFaceLandmarks().get(107);
        Point3D rightBrowInner = frame.getFaceLandmarks().get(336);
        Point3D leftEyeTop = frame.getFaceLandmarks().get(159);

        // נקודות עוגן למרחק העיניים (לצורך נורמליזציה של קנה מידה)
        Point3D eyeLeftCorner = frame.getFaceLandmarks().get(33);
        Point3D eyeRightCorner = frame.getFaceLandmarks().get(133);

        if (leftBrowInner == null || rightBrowInner == null || leftEyeTop == null ||
                eyeLeftCorner == null || eyeRightCorner == null) {
            return false;
        }

        // 2. חישוב מרחקים בסיסיים
        double distBetweenBrows = calculateDistance(leftBrowInner, rightBrowInner);
        double distBrowToEye = calculateDistance(leftBrowInner, leftEyeTop);
        double eyeScale = calculateDistance(eyeLeftCorner, eyeRightCorner); // הסרגל שלנו

        if (eyeScale == 0) return false;

        // נורמליזציה (הפיכת המרחקים ליחסיים לגודל הפנים על המסך)
        double currentRatioBetween = distBetweenBrows / eyeScale;
        double currentRatioToEye = distBrowToEye / eyeScale;

        // 3. החלקת EMA
        if (smoothedDistanceBetween == -1.0) {
            smoothedDistanceBetween = currentRatioBetween;
            smoothedDistanceToEye = currentRatioToEye;
            return false;
        }

        smoothedDistanceBetween = (ALPHA * currentRatioBetween) + ((1.0 - ALPHA) * smoothedDistanceBetween);
        smoothedDistanceToEye = (ALPHA * currentRatioToEye) + ((1.0 - ALPHA) * smoothedDistanceToEye);

        // 4. הפעלת חוקי הזיהוי
        boolean isFrownDetected = evaluateFrown(smoothedDistanceBetween);
        boolean isRaiseDetected = evaluateRaise(smoothedDistanceToEye);

        // המערכת תחזיר true אם אחת מהתנועות החריגות בגבות זוהתה בפריים זה
        return isFrownDetected || isRaiseDetected;
    }

    // --- לוגיקת זיהוי כיווץ גבות (Frown) ---
    private boolean evaluateFrown(double ratio) {
        // סף אנטומי קלאסי לכיווץ גבות
        final double THRESHOLD_FROWN = 0.42;
        final int MIN_FRAMES = 10;

        if (ratio < THRESHOLD_FROWN) {
            this.frameCounterFrown++;
            if (this.frameCounterFrown == MIN_FRAMES) {
                // הגבות מכווצות כבר מספיק זמן (מעל שליש שנייה) - זהו לחץ/כעס
                this.frameCounterFrown = 0; // איפוס זמני למניעת התרעות כפולות רצופות
                return true;
            }
        } else {
            this.frameCounterFrown = 0;
        }
        return false;
    }

    // --- לוגיקת זיהוי הרמת גבות פתאומית (Raise) ---
    private boolean evaluateRaise(double ratio) {
        // סף אנטומי לגבות מורמות גבוה מהרגיל
        final double THRESHOLD_RAISE = 0.65;
        final int MAX_FRAMES_SPONTANEOUS = 30; // הרמת גבות של שקר/פחד היא מהירה וחולפת (פחות משנייה)

        switch (this.raiseState) {
            case 0: // מנוחה
                if (ratio > THRESHOLD_RAISE) {
                    this.raiseState = 1;
                    this.frameCounterRaise = 1;
                }
                break;

            case 1: // מורמות
                this.frameCounterRaise++;
                if (ratio <= THRESHOLD_RAISE) {
                    // הגבות חזרו למטה
                    int duration = this.frameCounterRaise;
                    this.raiseState = 0;
                    this.frameCounterRaise = 0;

                    // חוק חכם: אם זו הייתה הרמה מהירה ופתאומית (מיקרו-הבעה של בהלה/פחד) ולא החזקה ממושכת של הבעה
                    if (duration > 3 && duration < MAX_FRAMES_SPONTANEOUS) {
                        return true; // 🚨 זוהתה הרמת גבות פתאומית (Micro-raise)! 🚨
                    }
                }
                if (this.frameCounterRaise > MAX_FRAMES_SPONTANEOUS) {
                    // החזקה ממושכת מדי (למשל סתם אדם שמדבר עם הבעות ידיים מוגזמות), מאפסים
                    this.raiseState = 0;
                    this.frameCounterRaise = 0;
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

    @Override
    public String getGestureName() {
        return "Abnormal Eyebrow Movement (Frown/Raise)";
    }
}
