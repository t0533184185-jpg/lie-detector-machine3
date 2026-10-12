package com.example.demo.analyzer;

import com.example.demo.model.FrameData;
import com.example.demo.model.Point3D;
import org.springframework.stereotype.Component;

@Component
public class HandMovementDynamicsAnalyzer implements LieDetectorAnalyzer {

    // שמירת מצב הפריים הקודם לצורך חישוב נגזרות (מהירות ותאוצה)
    private Point3D lastWristL = null;//מיקום אחרון של פירקי הידיים
    private Point3D lastWristR = null;
    private double lastVelocityL = 0.0;//מהירות
    private double lastVelocityR = 0.0;

    // מונים לצורך בדיקת רצף של תנועות חריגות
    private int jerkyFrameCounter = 0;
    private int wideFrameCounter = 0;

    @Override
    public boolean analyze(FrameData frame) {
        if (frame.getPoseLandmarks() == null) return false;

        // שליפת נקודות פרקי הידיים והכתפיים (לנורמליזציה)
        Point3D wristL = frame.getPoseLandmarks().get(15);
        Point3D wristR = frame.getPoseLandmarks().get(16);
        Point3D shoulderL = frame.getPoseLandmarks().get(11);
        Point3D shoulderR = frame.getPoseLandmarks().get(12);

        if (wristL == null || wristR == null || shoulderL == null || shoulderR == null) return false;

        // סרגל נורמליזציה: רוחב הכתפיים
        double shoulderWidth = calculateDistance(shoulderL, shoulderR);
        if (shoulderWidth == 0) return false;

        // אם זה הפריים הראשון, רק נשמור את המיקום ונמתין לפריים הבא
        if (lastWristL == null || lastWristR == null) {
            storeCurrentState(wristL, wristR, 0.0, 0.0);
            return false;
        }

        // חישוב מהירות נוכחית (המרחק שהיד עברה מהפריים הקודם, מנורמל ברוחב הכתפיים)
        double currentVelocityL = calculateDistance(wristL, lastWristL) / shoulderWidth;
        double currentVelocityR = calculateDistance(wristR, lastWristR) / shoulderWidth;

        // חישוב תאוצה (השינוי במהירות מהפריים הקודם)
        double accelerationL = Math.abs(currentVelocityL - lastVelocityL);
        double accelerationR = Math.abs(currentVelocityR - lastVelocityR);

        // חישוב מרחק הידיים ממרכז הגוף (בשביל תנועות רחבות)
        Point3D bodyCenter = new Point3D(
                (shoulderL.getX() + shoulderR.getX()) / 2.0,
                (shoulderL.getY() + shoulderR.getY()) / 2.0,
                (shoulderL.getZ() + shoulderR.getZ()) / 2.0
        );
        double distLFromCenter = calculateDistance(wristL, bodyCenter) / shoulderWidth;
        double distRFromCenter = calculateDistance(wristR, bodyCenter) / shoulderWidth;

        // שמירת הנתונים לפריים הבא
        storeCurrentState(wristL, wristR, currentVelocityL, currentVelocityR);

        //  חוקים ולוגיקת קבלת החלטות
        return evaluateMovementRules(currentVelocityL, currentVelocityR, accelerationL, accelerationR, distLFromCenter, distRFromCenter);
    }

    private boolean evaluateMovementRules(double vL, double vR, double aL, double aR, double distL, double distR) {
        // תנועות קטועות (Jerky)
        // תאוצה גבוהה מאוד בפרק זמן קצר מעידה על שינויי כיוון חדים ורובוטיים
        final double JERK_ACCELERATION_THRESHOLD = 0.15;
        final int JERK_MIN_FRAMES = 6; // רצף של תנועות חדות לאורך כחמישית שנייה

        if (aL > JERK_ACCELERATION_THRESHOLD || aR > JERK_ACCELERATION_THRESHOLD) {
            this.jerkyFrameCounter++;
            if (this.jerkyFrameCounter >= JERK_MIN_FRAMES) {
                this.jerkyFrameCounter = 0;
                this.wideFrameCounter = 0;
                return true; // זוהתה תנועת ידיים קטועה/רובוטית
            }
        } else {
            if (this.jerkyFrameCounter > 0) this.jerkyFrameCounter--;
        }

        // מהירות גבוהה כשהיד נמצאת רחוק ממרכז הגוף (מרחק הגדול מרוחב הכתפיים)
        final double WIDE_DISTANCE_THRESHOLD = 1.2; // היד רחוקה מחוץ לכתפיים
        final double WIDE_VELOCITY_THRESHOLD = 0.08; // היד זזה מהר
        final int WIDE_MIN_FRAMES = 12; // מחוות יד רחבה שנמשכת לפחות חצי שנייה

        if ((distL > WIDE_DISTANCE_THRESHOLD && vL > WIDE_VELOCITY_THRESHOLD) ||
                (distR > WIDE_DISTANCE_THRESHOLD && vR > WIDE_VELOCITY_THRESHOLD)) {
            this.wideFrameCounter++;
            if (this.wideFrameCounter >= WIDE_MIN_FRAMES) {
                this.wideFrameCounter = 0;
                this.jerkyFrameCounter = 0;
                return true; // זוהתה תנועת יד רחבה ומראוותנית!
            }
        } else {
            if (this.wideFrameCounter > 0) this.wideFrameCounter--;
        }

        return false;
    }

    private void storeCurrentState(Point3D wL, Point3D wR, double vL, double vR) {
        this.lastWristL = wL;
        this.lastWristR = wR;
        this.lastVelocityL = vL;
        this.lastVelocityR = vR;
    }

    private double calculateDistance(Point3D p1, Point3D p2) {
        return Math.sqrt(Math.pow(p1.getX() - p2.getX(), 2) +
                Math.pow(p1.getY() - p2.getY(), 2) +
                Math.pow(p1.getZ() - p2.getZ(), 2));
    }

    @Override
    public String getGestureName() {
        return "Abnormal Hand Dynamics (Jerky/Wide)";
    }

}