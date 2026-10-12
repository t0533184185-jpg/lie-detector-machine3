package com.example.demo.analyzer;

import com.example.demo.model.FrameData;
import com.example.demo.model.Point3D;
import org.springframework.stereotype.Component;

@Component
public class SwallowingAnalyzer implements LieDetectorAnalyzer {

    private static final double ALPHA = 0.25;
    private double smoothedThroatY = -1.0;

    private int state = 0; // 0=מנוחה, 1=גרון בעלייה (בליעה)
    private int frameCounter = 0;

    @Override
    public boolean analyze(FrameData frame) {
        if (frame.getPoseLandmarks() == null || frame.getFaceLandmarks() == null) return false;

        // נקודת תחתית הסנטר (עוגן פנים) ונקודות הכתפיים (עוגן גוף)
        Point3D chin = frame.getFaceLandmarks().get(152);
        Point3D shoulderL = frame.getPoseLandmarks().get(11);
        Point3D shoulderR = frame.getPoseLandmarks().get(12);

        if (chin == null || shoulderL == null || shoulderR == null) return false;

        // נורמליזציה: נחשב את מיקום הגרון היחסי בתוך הטווח שבין הסנטר לקו הכתפיים
        double shouldersMidY = (shoulderL.getY() + shoulderR.getY()) / 2.0;
        double throatGap = shouldersMidY - chin.getY();

        if (throatGap <= 0) return false;

        // נמדוד את המיקום האנכי הדינמי של מרכז הגרון (מנורמל)
        double currentThroatY = (chin.getY() / shouldersMidY);

        if (smoothedThroatY == -1.0) {
            smoothedThroatY = currentThroatY;
            return false;
        }

        smoothedThroatY = (ALPHA * currentThroatY) + ((1.0 - ALPHA) * smoothedThroatY);

        // מכונת מצבים לזיהוי קפיצה אנכית מהירה (בליעה לוקחת כ-0.3 עד 0.8 שניות)
        return evaluateSwallowingFSM(smoothedThroatY);
    }

    private boolean evaluateSwallowingFSM(double throatY) {
        // ערכים נסיוניים לשינוי מהיר בגרון
        final double SWALLOW_THRESHOLD = 0.03;
        final int MIN_FRAMES = 8;
        final int MAX_FRAMES = 25;

        switch (this.state) {
            case 0:
                // אם מזוהה תזוזה פתאומית כלפי מעלה בציר ה-Y של הגרון
                if (throatY < (smoothedThroatY - SWALLOW_THRESHOLD)) {
                    this.state = 1;
                    this.frameCounter = 1;
                }
                break;

            case 1:
                this.frameCounter++;
                // הגרון חוזר למקומו
                if (throatY >= smoothedThroatY) {
                    int duration = this.frameCounter;
                    this.state = 0;
                    this.frameCounter = 0;

                    if (duration >= MIN_FRAMES && duration <= MAX_FRAMES) {
                        return true; // 🚨 זוהתה בליעת רוק חריגה/בולטת! 🚨
                    }
                }
                break;
        }
        return false;
    }

    @Override
    public String getGestureName() {
        return "Frequent / Heavy Swallowing";
    }
}