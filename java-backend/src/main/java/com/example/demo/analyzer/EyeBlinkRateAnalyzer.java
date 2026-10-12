package com.example.demo.analyzer;

import com.example.demo.model.FrameData;
import com.example.demo.model.Point3D;
import org.springframework.stereotype.Component;

import java.util.LinkedList;
import java.util.Queue;

@Component
public class EyeBlinkRateAnalyzer implements LieDetectorAnalyzer {

    // משתנים לזיהוי מצמוץ בודד (FSM)
    private boolean isEyeClosedBefore = false;

    // רשימה שתשמור את זמני המצמוצים (במילישניות) כדי לחשב קצב בדקה
    private final Queue<Long> blinkTimestamps = new LinkedList<>();

    // קצב פריימים משוער (למשל סרטון של 30 פריימים בשנייה)
    private double fps = 30.0;

    @Override
    //פונקציית עדכון הFPS
    public void setFps(double newFps) {
        this.fps = newFps;
    }

    @Override
    public boolean analyze(FrameData frame) {
        if (frame.getFaceLandmarks() == null) return false;

        // 1. שליפת נקודות עין שמאל (לפי המיפוי הרשמי של MediaPipe Face Mesh)
        Point3D p1 = frame.getFaceLandmarks().get(33);  // קצה שמאלי
        Point3D p2 = frame.getFaceLandmarks().get(160); // עפעף עליון 1
        Point3D p3 = frame.getFaceLandmarks().get(158); // עפעף עליון 2
        Point3D p4 = frame.getFaceLandmarks().get(133); // קצה ימני
        Point3D p5 = frame.getFaceLandmarks().get(153); // עפעף תחתון 1
        Point3D p6 = frame.getFaceLandmarks().get(144); // עפעף תחתון 2

        if (p1 == null || p2 == null || p3 == null || p4 == null || p5 == null || p6 == null) {
            return false;
        }

        // 2. חישוב מדד ה-EAR לפי הנוסחה המקובלת
        double distA = calculateDistance(p2, p6);
        double distB = calculateDistance(p3, p5);
        double distC = calculateDistance(p1, p4);

        if (distC == 0) return false;
        double ear = (distA + distB) / (2.0 * distC);

        // 3. המרת מספר הפריים לזמן וירטואלי בשניות/מילישניות
        long currentTimestampMs = (long) ((frame.getFrameNumber() / fps) * 1000);

        // 4. זיהוי מצמוץ (FSM)
        boolean isCurrentClosed = (ear < 0.18); // סף אנטומי קלאסי לעין עצומה
        boolean detectedBlinkEvent = false;

        if (isCurrentClosed && !isEyeClosedBefore) {
            // העין נסגרה בפריים הזה! נרשום את הזמן שלה
            isEyeClosedBefore = true;
            blinkTimestamps.add(currentTimestampMs);
            detectedBlinkEvent = true;
        } else if (!isCurrentClosed && isEyeClosedBefore) {
            // העין נפתחה חזרה
            isEyeClosedBefore = false;
        }

        // 5. ניקוי מצמוצים ישנים (חלון זמן זז של דקה אחת = 60,000 מילישניות)
        while (!blinkTimestamps.isEmpty() && (currentTimestampMs - blinkTimestamps.peek() > 60000)) {
            blinkTimestamps.poll(); // מסיר את המצמוץ הישן שיצא מטווח הדקה האחרונה
        }

        // 6. חוקי השקר: בדיקת חריגות בקצב המצמוצים
        int currentBlinkRatePerMinute = blinkTimestamps.size();

        // אדם נורמלי במצב רגיעה ממצמץ בין 12 ל-20 פעמים בדקה.
        // נתחיל לבדוק את החוק רק אחרי שחלפו לפחות 15 שניות מתחילת הסרטון כדי שייאסף מידע בסיסי
        if (currentTimestampMs > 15000) {
            if (currentBlinkRatePerMinute <= 5) {
                // 🚨 חוק 1: ירידה חדה במצמוצים (קפיאה/ריכוז קוגניטיבי מוגבר של שקר)
                return true;
            }
            if (currentBlinkRatePerMinute >= 35) {
                // 🚨 חוק 2: מצמוץ מוגבר פתאומי (שחרור לחץ / פוסט-שקר)
                return true;
            }
        }

        // אם זיהינו מצמוץ רגיל שלא חורג מהנורמה, נחזיר false (הכל תקין כרגע)
        return false;
    }

    private double calculateDistance(Point3D p1, Point3D p2) {
        return Math.sqrt(Math.pow(p1.getX() - p2.getX(), 2) +
                Math.pow(p1.getY() - p2.getY(), 2) +
                Math.pow(p1.getZ() - p2.getZ(), 2));
    }

    @Override
    public String getGestureName() {
        return "Abnormal Eye Blink Rate";
    }
}