package com.example.demo.analyzer;

import com.example.demo.model.FrameData;
import com.example.demo.model.Point3D;
import org.springframework.stereotype.Component;

@Component
public class HeadJerkAnalyzer implements LieDetectorAnalyzer {

    // שמירת הזווית של הפריים הקודם כדי לחשב מהירות תנועה
    private Double lastAngle = null;

    // מונה פריימים לזיהוי תנועה חדה וקצרה
    private int jerkFrameCounter = 0;

    @Override
    public boolean analyze(FrameData frame) {
        if (frame.getFaceLandmarks() == null) return false;

        // 1. שליפת נקודות העיניים (לחישוב זווית הראש)
        Point3D eyeLeft = frame.getFaceLandmarks().get(33);
        Point3D eyeRight = frame.getFaceLandmarks().get(133);

        if (eyeLeft == null || eyeRight == null) return false;

        // 2. חישוב זווית הראש הנוכחית ברדיאנים (באמצעות atan2 של הפרשי ה-Y וה-X)
        double deltaY = eyeRight.getY() - eyeLeft.getY();
        double deltaX = eyeRight.getX() - eyeLeft.getX();

        if (deltaX == 0) return false;
        double currentAngleRad = Math.atan2(deltaY, deltaX);

        // המרה למעלות לנוחות העבודה והגדרת הספים
        double currentAngleDeg = Math.toDegrees(currentAngleRad);

        // אם זה הפריים הראשון, נשמור את הזווית ונמתין לפריים הבא
        if (lastAngle == null) {
            lastAngle = currentAngleDeg;
            return false;
        }

        // 3. חישוב מהירות שינוי הזווית (הפרש המעלות מהפריים הקודם)
        double angleVelocity = Math.abs(currentAngleDeg - lastAngle);

        // שמירת הזווית הנוכחית עבור הפריים הבא
        lastAngle = currentAngleDeg;

        // 4. מכונת מצבים / חוק זיהוי לנפה פתאומית (Head Jerk)
        // שינוי של מעל 4 מעלות בפריים בודד נחשב לתנועה מהירה וחריגה מאוד עבור ראש
        final double JERK_VELOCITY_THRESHOLD = 4.0;
        // נפת ראש של שקר/לחץ היא מהירה ונמשכת לרוב בין 3 ל-12 פריימים (תנועה חולפת)
        final int MIN_FRAMES = 3;
        final int MAX_FRAMES = 15;

        if (angleVelocity > JERK_VELOCITY_THRESHOLD) {
            this.jerkFrameCounter++;

            // אם התנועה המהירה נמשכת בטווח הזמן הנכון
            if (this.jerkFrameCounter >= MIN_FRAMES && this.jerkFrameCounter <= MAX_FRAMES) {
                // המערכת מזהה את הטווח הדינמי ומחזירה true
                return true; // 🚨 זוהתה נפת ראש פתאומית! 🚨
            }
        } else {
            // ברגע שהתנועה נעצרת או מאיטה, מאפסים את המונה
            this.jerkFrameCounter = 0;
        }

        return false;
    }

    @Override
    public String getGestureName() {
        return "Sudden Head Jerk / Tilt";
    }
}
