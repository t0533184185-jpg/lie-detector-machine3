package com.example.demo.analyzer;

import com.example.demo.model.FrameData;
import com.example.demo.model.Point3D;
import org.springframework.stereotype.Component;

@Component
public class FaceTouchAnalyzer implements LieDetectorAnalyzer {

    // משתנה EMA מוחלק כדי לוודא שהיד נשארת על הפנים ולא סתם חלפה ברקע
    private static final double ALPHA = 0.3;
    private double smoothedTouchScore = 0.0;

    @Override
    public boolean analyze(FrameData frame) {
        // אם אין ידיים בפריים, בוודאות אין נגיעה בפנים
        if (frame.getHandLandmarks() == null || frame.getHandLandmarks().isEmpty()) {
            smoothedTouchScore = (ALPHA * 0.0) + ((1.0 - ALPHA) * smoothedTouchScore);
            return false;
        }

        // 1. שליפת קצות האצבעות של היד (למשל, אצבע מורה ואגודל)
        Point3D indexFingerTip = frame.getHandLandmarks().get(8);
        Point3D thumbTip = frame.getHandLandmarks().get(4);

        if (indexFingerTip == null) return false;

        // 2. שליפת נקודות עוגן בפנים ובגוף (סנטר ואף)
        Point3D chin = (frame.getFaceLandmarks() != null) ? frame.getFaceLandmarks().get(152) : null;
        Point3D nose = (frame.getPoseLandmarks() != null) ? frame.getPoseLandmarks().get(0) : null;

        if (chin == null || nose == null) return false;

        // 3. חישוב המרחק המינימלי בין האצבע לאזורי הפנים
        double distToChin = calculateDistance(indexFingerTip, chin);
        double distToNose = calculateDistance(indexFingerTip, nose);

        // נורמליזציה: נמדוד את המרחק יחסית לגודל הראש (המרחק בין האף לסנטר)
        double headSize = calculateDistance(nose, chin);
        if (headSize == 0) return false;

        double normalizedDist = Math.min(distToChin, distToNose) / headSize;

        // 4. קביעת ציון נגיעה נוכחי (1 = יש נגיעה קרובה, 0 = היד רחוקה)
        // אם המרחק קטן מ-25% מגודל הראש, היד נוגעת או ממש קרובה לפנים
        double currentTouchScore = (normalizedDist < 0.25) ? 1.0 : 0.0;

        // 5. החלקת הציון לאורך זמן (EMA)
        smoothedTouchScore = (ALPHA * currentTouchScore) + ((1.0 - ALPHA) * smoothedTouchScore);

        // חוק חכם: אם ציון הנגיעה המוחלק גבוה מ-0.7, זה אומר שהיד שוהה על הפנים
        // לאורך מספר פריימים רצופים (ולא סתם עברה במהירות מול המצלמה)
        if (smoothedTouchScore > 0.7) {
            return true; // 🚨 זוהתה נגיעה ממושכת בפנים/צוואר! 🚨
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
        return "Face / Neck Touching";
    }
}