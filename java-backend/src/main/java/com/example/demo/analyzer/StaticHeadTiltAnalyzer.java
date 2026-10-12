package com.example.demo.analyzer;

import com.example.demo.model.FrameData;
import com.example.demo.model.Point3D;
import org.springframework.stereotype.Component;

@Component
public class StaticHeadTiltAnalyzer implements LieDetectorAnalyzer {

    private static final double ALPHA = 0.15; // החלקה כבדה כדי למדוד מצב סטטי
    private double smoothedAngle = 0.0;
    private int tiltFrameCounter = 0;

    @Override
    public boolean analyze(FrameData frame) {
        if (frame.getFaceLandmarks() == null) return false;

        Point3D eyeLeft = frame.getFaceLandmarks().get(33);
        Point3D eyeRight = frame.getFaceLandmarks().get(133);

        if (eyeLeft == null || eyeRight == null) return false;

        // חישוב זווית הראש במעלות
        double deltaY = eyeRight.getY() - eyeLeft.getY();
        double deltaX = eyeRight.getX() - eyeLeft.getX();
        if (deltaX == 0) return false;

        double currentAngleDeg = Math.toDegrees(Math.atan2(deltaY, deltaX));

        // החלקת הזווית לאורך זמן
        smoothedAngle = (ALPHA * currentAngleDeg) + ((1.0 - ALPHA) * smoothedAngle);

        // חוק: אם הראש נטוי ביותר מ-8 מעלות (ימינה או שמאלה) למשך יותר מ-2 שניות (60 פריימים)
        final double TILT_THRESHOLD = 8.0;
        final int MIN_FRAMES = 60;

        if (Math.abs(smoothedAngle) > TILT_THRESHOLD) {
            this.tiltFrameCounter++;
            if (this.tiltFrameCounter >= MIN_FRAMES) {
                // המערכת מתריעה על החזקת ראש נטוי באופן חריג
                return true; // 🚨 זוהתה הטיית ראש סטטית ממושכת! 🚨
            }
        } else {
            this.tiltFrameCounter = 0;
        }

        return false;
    }

    @Override
    public String getGestureName() {
        return "Prolonged Head Tilt";
    }
}
