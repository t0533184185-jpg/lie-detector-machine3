package com.example.demo.analyzer;

import com.example.demo.model.FrameData;
import com.example.demo.model.Point3D;
import org.springframework.stereotype.Component;

@Component
public class LipPurseAnalyzer implements LieDetectorAnalyzer {

    private static final double ALPHA = 0.25; // מקדם החלקה מעט מהיר יותר לשפתיים
    private double smoothedLpr = -1.0;

    // משתני מכונת מצבים (FSM)
    private int state = 0; // 0 = מנוחה/דיבור, 1 = כיווץ שפתיים
    private int durationCounter = 0;

    @Override
    public boolean analyze(FrameData frame) {
        if (frame.getFaceLandmarks() == null) return false;

        // שליפת נקודות השפתיים מתוך ה-Face Mesh
        Point3D topLip = frame.getFaceLandmarks().get(0);
        Point3D bottomLip = frame.getFaceLandmarks().get(17);
        Point3D leftCorner = frame.getFaceLandmarks().get(61);
        Point3D rightCorner = frame.getFaceLandmarks().get(291);

        if (topLip == null || bottomLip == null || leftCorner == null || rightCorner == null) return false;

        //  חישוב מרחקים ונורמליזציה (LPR)
        double lipHeight = calculateDistance(topLip, bottomLip);
        double lipWidth = calculateDistance(leftCorner, rightCorner);

        if (lipWidth == 0) return false;
        double currentLpr = lipHeight / lipWidth;

        //  החלקת EMA
        if (smoothedLpr == -1.0) {
            smoothedLpr = currentLpr;
        } else {
            smoothedLpr = (ALPHA * currentLpr) + ((1.0 - ALPHA) * smoothedLpr);
        }

        //  מכונת המצבים (FSM) - זיהוי כיווץ קו ישר
        return evaluateLipFSM(smoothedLpr);
    }

    private boolean evaluateLipFSM(double lpr) {
        // ספים מבוססי ניתוח אנטומי (כיווץ חזק מוריד את היחס בד"כ מתחת ל-0.15)
        final double THRESHOLD_PURSED = 0.12;
        final double THRESHOLD_RELEASED = 0.22;

        // כיווץ שפתיים של שקר נמשך בדרך כלל בין חצי שנייה לשנייה וחצי (15 עד 45 פריימים)
        final int MIN_FRAMES = 12;

        switch (this.state) {
            case 0: // מצב רגיל / דיבור
                if (lpr < THRESHOLD_PURSED) {
                    // השפתיים נסגרו חזק לקו ישר
                    this.state = 1;
                    this.durationCounter = 1;
                }
                break;

            case 1: // השפתיים מכווצות
                if (lpr < THRESHOLD_PURSED) {
                    this.durationCounter++;
                } else if (lpr >= THRESHOLD_RELEASED) {
                    // השפתיים שוחררו וחזרו למצב טבעי
                    int finalDuration = this.durationCounter;
                    resetAnalyzer();

                    //בדיקה האם התנועה לקח יותר מ12 שניות כדי לא להחשיב את ההברות של השפה
                   if (finalDuration >= MIN_FRAMES) {
                        return true; //  זוהה כיווץ שפתיים לקו ישר
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
        this.durationCounter = 0;
    }

    @Override
    public String getGestureName() {
        return "Lip Pursing (Straight Line)";
    }
}
