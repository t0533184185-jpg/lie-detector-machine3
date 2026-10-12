package com.example.demo.analyzer;

import com.example.demo.model.FrameData;
import com.example.demo.model.Point3D;
import org.springframework.stereotype.Component;
import java.util.LinkedList;
import java.util.Queue;

@Component
public class BodyRockingAnalyzer implements LieDetectorAnalyzer {

    // נשמור את רוחב הכתפיים הנתפס לאורך 4 שניות (חלון זמן של 120 פריימים)
    private final Queue<Double> shoulderWidths = new LinkedList<>();
//    private static final int WINDOW_SIZE = 120;

    private int windowSize = 120;

    @Override
    public void setFps(double fps) {
        // החלון מוגדר אצלך ל-4 שניות:
        this.windowSize = (int) Math.round(4 * fps);
    }

    @Override
    public boolean analyze(FrameData frame) {
        if (frame.getPoseLandmarks() == null) return false;

        Point3D shoulderL = frame.getPoseLandmarks().get(11);
        Point3D shoulderR = frame.getPoseLandmarks().get(12);

        if (shoulderL == null || shoulderR == null) return false;

        // כשאדם מתקרב למצלמה (רוכן קדימה), המרחק הדו-ממדי בין הכתפיים גדל.
        // כשהוא מתרחק (נשען אחורה), המרחק קטן.
        double currentWidth = Math.sqrt(Math.pow(shoulderL.getX() - shoulderR.getX(), 2) +
                Math.pow(shoulderL.getY() - shoulderR.getY(), 2));

        shoulderWidths.add(currentWidth);
        if (shoulderWidths.size() > windowSize) {
            shoulderWidths.poll();
        }

        if (shoulderWidths.size() == windowSize) {
            double minWidth = currentWidth;
            double maxWidth = currentWidth;

            for (double w : shoulderWidths) {
                if (w < minWidth) minWidth = w;
                if (w > maxWidth) maxWidth = w;
            }

            // חישוב אחוז השינוי ברוחב הכתפיים בחלון הזמן
            double changeRatio = (maxWidth - minWidth) / minWidth;

            // חוק: אם רוחב הכתפיים השתנה בלולאה ביותר מ-15% בתוך 4 שניות,
            // זה מעיד על נדנוד עקבי קדימה ואחורה (Rocking)
            if (changeRatio > 0.15) {
                return true; // 🚨 זוהה נדנוד גוף קדימה ואחורה! 🚨
            }
        }

        return false;
    }

    @Override
    public String getGestureName() {
        return "Body Rocking (Forward/Backward)";
    }
}
