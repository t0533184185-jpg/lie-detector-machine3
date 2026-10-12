package com.example.demo.analyzer;

import com.example.demo.model.FrameData;
import com.example.demo.model.Point3D;
import org.springframework.stereotype.Component;
import jakarta.annotation.PostConstruct;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.*;

@Component
public class DynamicGestureEngine implements LieDetectorAnalyzer {

    private final List<GestureRule> rules = new ArrayList<>();
    private double fps = 30.0;
    private String lastDetectedGestureName = "Dynamic Gesture";
    private final List<String> detectedGestures = new ArrayList<>();
    //  מחסני זיכרון דינמיים המופרדים פר חוק/תנועה (לפי ה-Gesture Name)
    private final Map<String, Integer> fsmStates = new HashMap<>();
    private final Map<String, Integer> frameCounters = new HashMap<>();
    private final Map<String, Double> smoothedValues = new HashMap<>();
    private final Map<String, Queue<Double>> numericQueues = new HashMap<>();
    private final Map<String, Queue<Long>> timeQueues = new HashMap<>();

    // זיכרון ייעודי לחישובי נגזרות (מהירות ראש, תנועות ידיים, וערכי בסיס)
    private final Map<String, Double> lastAngles = new HashMap<>();
    private final Map<String, Point3D> lastPointsL = new HashMap<>();
    private final Map<String, Point3D> lastPointsR = new HashMap<>();
    private final Map<String, Double> lastVelocitiesL = new HashMap<>();
    private final Map<String, Double> lastVelocitiesR = new HashMap<>();
    private final Map<String, Double> baseRatios = new HashMap<>();
    private BaselineData baseline;

    private long engineStartTimeMs = -1;

    @PostConstruct
    public void init() {
        loadRulesFromCsv();
        System.out.println("Loaded rules: " + rules.size());
    }

    public void setBaseline(BaselineData baseline) {
        this.baseline = baseline;
    }

    @Override
    public void setFps(double fps) {
        this.fps = fps;
    }

    @Override
    public String getGestureName() {
        return this.lastDetectedGestureName;
    }

    private void loadRulesFromCsv() {
        String csvFile = "/try_1.csv"; // ודאי ששם הקובץ בתיקיית src/main/resources תואם

        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(getClass().getResourceAsStream(csvFile)))) {

            String line;
            boolean isHeader = true;

            while ((line = br.readLine()) != null) {
                // דילוג על שורת הכותרת (Header) של ה-CSV
                if (isHeader) {
                    isHeader = false;
                    continue;
                }

                // סינון שורות ריקות בטעות
                if (line.trim().isEmpty()) continue;

                String[] tokens = line.split(",");
                if (tokens.length < 9) {
                    System.err.println("שורה לא תקינה ב-CSV (פחות מ-9 עמודות): " + line);
                    continue;
                }

                try {
                    // חילוץ הפרמטרים מתוך ה-CSV
                    String gestureName = tokens[0].trim();
                    int p1 = Integer.parseInt(tokens[1].trim());
                    int p2 = Integer.parseInt(tokens[2].trim());
                    int p3 = Integer.parseInt(tokens[3].trim());
                    int p4 = Integer.parseInt(tokens[4].trim());
                    double threshold = Double.parseDouble(tokens[5].trim());
                    int minFrames = Integer.parseInt(tokens[6].trim());
                    int maxFrames = Integer.parseInt(tokens[7].trim());
                    String algorithmType = tokens[8].trim();

                    // יצירת החוק והוספתו לרשימה במנוע
                    GestureRule rule = new GestureRule(
                            gestureName, p1, p2, p3, p4, threshold, minFrames, maxFrames, algorithmType
                    );
                    rules.add(rule);

                    // 🧠 אתחול מחסני הזיכרון הדינמיים עבור החוק הספציפי הזה
                    fsmStates.put(gestureName, 0);
                    frameCounters.put(gestureName, 0);
                    smoothedValues.put(gestureName, -1.0); // ערך התחלתי מיוחד לצורך החלקה (smooth)
                    numericQueues.put(gestureName, new LinkedList<>());
                    timeQueues.put(gestureName, new LinkedList<>());

                    System.out.println("✅ Loaded gesture rule successfully: " + gestureName + " [" + algorithmType + "]");

                } catch (NumberFormatException e) {
                    System.err.println("❌ שגיאה בהמרת מספרים בשורה: " + line + " -> " + e.getMessage());
                }
            }

            System.out.println("📊 סך הכל נטענו בהצלחה " + rules.size() + " חוקי תנועה למנוע הדינמי.");

        } catch (Exception e) {
            System.err.println("❌ שגיאה קריטית בטעינת קובץ ה-CSV של החוקים: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public boolean analyze(FrameData frame) {
        if (engineStartTimeMs == -1) {
            engineStartTimeMs = System.currentTimeMillis();
        }

        boolean anyGestureDetected = false;

        for (GestureRule rule : rules) {
            String name = rule.getGestureName();

            // אתחול זיכרונות ראשוניים עבור התנועה הנוכחית אם אינם קיימים
            fsmStates.putIfAbsent(name, 0);
            frameCounters.putIfAbsent(name, 0);
            smoothedValues.putIfAbsent(name, -1.0);
            numericQueues.putIfAbsent(name, new LinkedList<>());
            timeQueues.putIfAbsent(name, new LinkedList<>());

            boolean detected = evalRule(rule, frame);
            if (detected) {
                this.lastDetectedGestureName = name;
                this.detectedGestures.add(name);

                System.out.println("🚨🚨 [RULE FIRED] Gesture detected: " + name +
                        " on frame " + frame.getFrameNumber());

                anyGestureDetected = true;
            }
        }

        return anyGestureDetected;
    }

    private boolean evalRule(GestureRule rule, FrameData frame) {
        String name = rule.getGestureName();
        String type = rule.getAlgorithmType();

        // שליפת משתני המצב והמונים הספציפיים לתנועה זו
        int state = fsmStates.get(name);
        int counter = frameCounters.get(name);

        switch (type) {

            // 1. חיוך א-סימטרי (AsymmetricSmileAnalyzer)
            case "ASYMMETRY_RATIO_FSM": {
                Point3D leftCorner = getLandmark(frame, rule.getP1(), true);
                Point3D rightCorner = getLandmark(frame, rule.getP2(), true);
                Point3D topLipCenter = getLandmark(frame, rule.getP3(), true);

                if (leftCorner == null || rightCorner == null || topLipCenter == null) {
                    // הדפסת דיאגנוסטיקה קלה
                    System.out.println("⚠️ [" + name + "] Missing landmarks! p1=" + leftCorner + ", p2=" + rightCorner + ", p3=" + topLipCenter);
                    return false;
                }
                double distLeftToCenter = dist(leftCorner, topLipCenter);
                double distRightToCenter = dist(rightCorner, topLipCenter);
                double totalWidth = dist(leftCorner, rightCorner);
                if (totalWidth == 0) return false;

                double asymmetryScore = Math.abs(distLeftToCenter - distRightToCenter) / totalWidth;
                double smoothed = smooth(name, asymmetryScore, 0.25);

                if (state == 0) {
                    if (smoothed > rule.getThreshold()) {
                        fsmStates.put(name, 1);
                        frameCounters.put(name, 1);
                    }
                } else if (state == 1) {
                    if (smoothed > rule.getThreshold()) {
                        counter++;
                        if (counter > rule.getMaxFrames()) { // מניעת עיוות טבעי ממושך
                            fsmStates.put(name, 0);
                            frameCounters.put(name, 0);
                        } else {
                            frameCounters.put(name, counter);
                        }
                    } else {
                        fsmStates.put(name, 0);
                        frameCounters.put(name, 0);
                        if (counter >= rule.getMinFrames()) {
                            return true; // 🚨 זוהה חיוך א-סימטרי דינמי!
                        }
                    }
                }
                return false;
            }

            // 2. נגיעה בפנים (FaceTouchAnalyzer)
            case "FACE_TOUCH_EMA": {

                Point3D fingerTip = getLandmark(frame, rule.getP1(), false);
                Point3D chin = getLandmark(frame, rule.getP3(), true);
                Point3D nose = getLandmark(frame, rule.getP4(), true);

                if (fingerTip == null || chin == null || nose == null) {
                    smooth(name, 0.0, 0.3);
                    return false;
                }

                double distToChin = dist(fingerTip, chin);
                double distToNose = dist(fingerTip, nose);
                double headSize = dist(nose, chin);

                if (headSize == 0) return false;

                double normalizedDist = Math.min(distToChin, distToNose) / headSize;
                double currentTouchScore = normalizedDist < 0.25 ? 1.0 : 0.0;
                double smoothedTouch = smooth(name, currentTouchScore, 0.3);

                if (state == 0) {
                    if (smoothedTouch > rule.getThreshold()) {
                        fsmStates.put(name, 1);
                    }
                } else if (state == 1) {
                    if (smoothedTouch <= rule.getThreshold()) {
                        fsmStates.put(name, 0);
                        return true; // המחווה הסתיימה - מדווחים פעם אחת בלבד
                    }
                }

                return false;
            }

            // 3. תזזיתיות גוף (BodyFidgetingAnalyzer)
            case "BODY_FIDGETING_WINDOW": {
                Point3D shoulderL = getLandmark(frame, rule.getP1(), false);
                Point3D shoulderR = getLandmark(frame, rule.getP2(), false);
                if (shoulderL == null || shoulderR == null) return false;

                double shoulderWidth = Math.abs(shoulderL.getX() - shoulderR.getX());
                if (shoulderWidth == 0) return false;

                double currentCenterX = (shoulderL.getX() + shoulderR.getX()) / 2.0;
                Queue<Double> q = numericQueues.get(name);
                q.add(currentCenterX);

                int windowSize = (int) Math.round(rule.getMinFrames()); // קצב החלון בפריימים (למשל 5 שניות * FPS)
                if (q.size() > windowSize) q.poll();

                if (q.size() == windowSize) {
                    double min = Collections.min(q);
                    double max = Collections.max(q);
                    if ((max - min) / shoulderWidth > rule.getThreshold()) {
                        q.clear();
                        return true; // 🚨 זוהתה תנועת תזזיתיות ימינה/שמאלה!
                    }
                }
                return false;
            }

            // 4. התנועעות גוף קדימה/אחורה (BodyRockingAnalyzer)
            case "BODY_ROCKING_WINDOW": {
                Point3D shoulderL = getLandmark(frame, rule.getP1(), false);
                Point3D shoulderR = getLandmark(frame, rule.getP2(), false);
                if (shoulderL == null || shoulderR == null) return false;

                double currentWidth = Math.sqrt(Math.pow(shoulderL.getX() - shoulderR.getX(), 2) + Math.pow(shoulderL.getY() - shoulderR.getY(), 2));
                Queue<Double> q = numericQueues.get(name);
                q.add(currentWidth);

                int windowSize = (int) Math.round(rule.getMinFrames()); // חלון ייעודי (למשל 4 שניות * FPS)
                if (q.size() > windowSize) q.poll();

                if (q.size() == windowSize) {
                    double min = Collections.min(q);
                    double max = Collections.max(q);
                    if (min == 0) return false;

                    double changeRatio = (max - min) / min;
                    if (changeRatio > rule.getThreshold()) {
                        q.clear();
                        return true; // 🚨 זוהתה התנועעות מחזורית קדימה/אחורה!
                    }
                }
                return false;
            }

            // 5. הרמת/כיווץ גבות פתאומי (EyebrowMovementAnalyzer)
            case "EYEBROW_MOVEMENT_FSM": {
                Point3D browInnerL = getLandmark(frame, rule.getP1(), true);
                Point3D browInnerR = getLandmark(frame, rule.getP2(), true);
                Point3D eyeTopL = getLandmark(frame, rule.getP3(), true);
                Point3D anchorL = getLandmark(frame, rule.getP4(), true); // עוגן נורמליזציה

                if (browInnerL == null || browInnerR == null || eyeTopL == null || anchorL == null) return false;

                double browDist = dist(browInnerL, browInnerR);
                double eyeToBrowDist = dist(browInnerL, eyeTopL);
                if (eyeToBrowDist == 0) return false;

                double ratio = browDist / eyeToBrowDist;
                double smoothedRatio = smooth(name, ratio, 0.2);

                if (state == 0) {
                    if (smoothedRatio > rule.getThreshold()) { // תנאי פריצה להרמה/כיווץ
                        fsmStates.put(name, 1);
                        frameCounters.put(name, 1);
                    }
                } else if (state == 1) {
                    counter++;
                    if (smoothedRatio <= rule.getThreshold()) {
                        fsmStates.put(name, 0);
                        frameCounters.put(name, 0);
                        // מיקרו-הבעה פתאומית: נמשכת מעט מאוד זמן (בטווח המוגדר)
                        if (counter > rule.getMinFrames() && counter < rule.getMaxFrames()) {
                            return true; // 🚨 זוהתה מיקרו-הבעת גבות חולפת!
                        }
                    }
                    if (counter > rule.getMaxFrames()) { // החזקה ממושכת מידי מאופסת
                        fsmStates.put(name, 0);
                        frameCounters.put(name, 0);
                    } else {
                        frameCounters.put(name, counter);
                    }
                }
                return false;
            }

            // 6. קצב מצמוצים חריג (EyeBlinkRateAnalyzer)
            case "EYE_BLINK_RATE_WINDOW": {
                Point3D eyeTop = getLandmark(frame, rule.getP1(), true);
                Point3D eyeBottom = getLandmark(frame, rule.getP2(), true);
                Point3D eyeLeft = getLandmark(frame, rule.getP3(), true);
                Point3D eyeRight = getLandmark(frame, rule.getP4(), true);

                if (eyeTop == null || eyeBottom == null || eyeLeft == null || eyeRight == null) return false;

                double height = dist(eyeTop, eyeBottom);
                double width = dist(eyeLeft, eyeRight);
                if (width == 0) return false;

                double ear = height / width;
                double currentTimestampMs = System.currentTimeMillis() - engineStartTimeMs;

                // שימוש במונה הקיים (state) כדי לדמות את משתנה ה-isEyeClosedBefore
                if (state == 0) {
                    if (belowStd(
                            ear,
                            baseline.getEyeMean(),
                            baseline.getEyeStd(),
                            rule.getThreshold())) { // סף סגירת עין (למשל 0.2)
                        fsmStates.put(name, 1); // עין נסגרה
                    }
                } else if (state == 1) {
                    if (ear >= rule.getThreshold() + 0.03) { // עין נפתחה חזרה
                        fsmStates.put(name, 0);
                        Queue<Long> bTimestamps = timeQueues.get(name);
                        bTimestamps.add((long) currentTimestampMs);
                    }
                }

                // ניקוי חלון זז של דקה (60,000 מילישניות)
                Queue<Long> bTimestamps = timeQueues.get(name);
                while (!bTimestamps.isEmpty() && (currentTimestampMs - bTimestamps.peek() > 60000)) {
                    bTimestamps.poll();
                }

                // בדיקת חוקים רק לאחר איסוף נתונים ראשוני (למשל 15 שניות מתחילת הריצה)
                if (currentTimestampMs > 15000) {
                    int blinkRate = bTimestamps.size();

                    if (name.equals("Eye Blink Rate Low")) {
                        return blinkRate <= rule.getMinFrames();
                    }

                    if (name.equals("Eye Blink Rate High")) {
                        return blinkRate >= rule.getMinFrames();
                    }
                }
                return false;
            }

            // 7. הנפת ראש חדה (HeadJerkAnalyzer)
            case "HEAD_JERK_VELOCITY": {
                Point3D eyeL = getLandmark(frame, rule.getP1(), true);
                Point3D eyeR = getLandmark(frame, rule.getP2(), true);
                if (eyeL == null || eyeR == null) return false;

                double deltaY = eyeR.getY() - eyeL.getY();
                double deltaX = eyeR.getX() - eyeL.getX();
                if (deltaX == 0) return false;

                double currentAngleDeg = Math.toDegrees(Math.atan2(deltaY, deltaX));
                Double lastAngle = lastAngles.get(name);

                if (lastAngle == null) {
                    lastAngles.put(name, currentAngleDeg);
                    return false;
                }

                double angleVelocity = Math.abs(currentAngleDeg - lastAngle);
                lastAngles.put(name, currentAngleDeg);

                if (angleVelocity > rule.getThreshold()) { // שינוי מעלות חריג בפריים בודד (למשל 4.0 מעלות)
                    counter++;
                    frameCounters.put(name, counter);
                    if (counter >= rule.getMinFrames() && counter <= rule.getMaxFrames()) {
                        fsmStates.put(name, 0); // תנועה חולפת מוצלחת
                        frameCounters.put(name, 0);
                        return true; //  זוהתה נפת ראש מהירה (Head Jerk)
                    }
                } else {
                    frameCounters.put(name, 0);
                }
                return false;
            }

            // 8. כיווץ שפתיים לקו ישר (LipPurseAnalyzer)
            case "LIP_PURSE_FSM": {
                Point3D topLip = getLandmark(frame, rule.getP1(), true);
                Point3D bottomLip = getLandmark(frame, rule.getP2(), true);
                Point3D leftCorner = getLandmark(frame, rule.getP3(), true);
                Point3D rightCorner = getLandmark(frame, rule.getP4(), true);

                if (topLip == null || bottomLip == null || leftCorner == null || rightCorner == null) return false;

                double lipHeight = dist(topLip, bottomLip);
                double lipWidth = dist(leftCorner, rightCorner);
                if (lipWidth == 0) return false;

                double lpr = lipHeight / lipWidth;
                double smoothedLpr = smooth(name, lpr, 0.25);
                //בדיקת ערכי סף
                System.out.println("[Lip Purse Check] " + name + " -> Current LPR: " + smoothedLpr + " | Threshold: " + rule.getThreshold() + " | State: " + state);

                if (state == 0) {

                    if (belowStd(smoothedLpr,
                            baseline.getLipMean(),
                            baseline.getLipStd(),
                            rule.getThreshold())) {

                        fsmStates.put(name, 1);
                        frameCounters.put(name, 1);
                    }

                } else if (state == 1) {

                    if (belowStd(smoothedLpr,
                            baseline.getLipMean(),
                            baseline.getLipStd(),
                            rule.getThreshold())) {

                        counter++;
                        frameCounters.put(name, counter);

                    } else {

                        fsmStates.put(name, 0);
                        frameCounters.put(name, 0);

                        if (counter >= rule.getMinFrames()) {
                            return true;
                        }
                    }
                }
                return false;
            }

            // 9. משיכת כתפיים (ShoulderShrugAnalyzer)
            case "SHOULDER_SHRUG_FSM": {
                Point3D shoulderL = getLandmark(frame, rule.getP1(), false);
                Point3D shoulderR = getLandmark(frame, rule.getP2(), false);
                Point3D earL = getLandmark(frame, rule.getP3(), false);
                Point3D earR = getLandmark(frame, rule.getP4(), false);

                if (shoulderL == null || shoulderR == null || earL == null || earR == null) return false;

                double distL = dist(earL, shoulderL);
                double distR = dist(earR, shoulderR);
                double shoulderWidth = dist(shoulderL, shoulderR);
                if (shoulderWidth == 0) return false;

                double ratio = (distL + distR) / shoulderWidth;
                double smoothedRatio = smooth(name, ratio, 0.2);

                baseRatios.putIfAbsent(name, smoothedRatio);
                double base = baseRatios.get(name);

                // עדכון דינמי של בסיס הכתפיים במצב מנוחה
                if (state == 0 && smoothedRatio > base) {
                    base = smoothedRatio;
                    baseRatios.put(name, base);
                }

                double thresholdUp = base * rule.getThreshold(); // למשל גובה יורד ב-15% (ערך Threshold סביב 0.85)
                double thresholdRest = base * 0.98;

                if (state == 0) {
                    if (smoothedRatio < thresholdUp) { // הכתפיים עלו למעלה לעבר האוזניים והמרחק קטן
                        fsmStates.put(name, 1);
                        frameCounters.put(name, 1);
                    }
                } else if (state == 1) {
                    counter++;
                    if (smoothedRatio < thresholdUp) {
                        if (counter > rule.getMaxFrames()) {
                            fsmStates.put(name, 0);
                            frameCounters.put(name, 0);
                        } else {
                            frameCounters.put(name, counter);
                        }
                    } else if (smoothedRatio > thresholdRest) { // חזרה למנוחה
                        fsmStates.put(name, 0);
                        frameCounters.put(name, 0);
                        if (counter >= rule.getMinFrames()) {
                            return true; //  זוהתה משיכת כתפיים (Shoulder Shrug)
                        }
                    }
                }
                return false;
            }

            // 10. הטיית ראש סטטית (StaticHeadTiltAnalyzer)
            case "STATIC_HEAD_TILT": {
                Point3D eyeL = getLandmark(frame, rule.getP1(), true);
                Point3D eyeR = getLandmark(frame, rule.getP2(), true);
                if (eyeL == null || eyeR == null) return false;

                double deltaY = eyeR.getY() - eyeL.getY();
                double deltaX = eyeR.getX() - eyeL.getX();
                if (deltaX == 0) return false;

                double currentAngleDeg = Math.toDegrees(Math.atan2(deltaY, deltaX));
                double smoothedAngle = smooth(name, currentAngleDeg, 0.15); // החלקה כבדה

                if (Math.abs(smoothedAngle) > rule.getThreshold()) { // חריגה מסף מעלות ממושך (למשל 8 מעלות)
                    counter++;
                    frameCounters.put(name, counter);
                    if (counter >= rule.getMinFrames()) { // החזקה סטטית לאורך הטווח הנדרש (למשל 2 שניות * FPS)
                        return true; //  זוהתה הטיית ראש סטטית ממושכ
                    }
                } else {
                    frameCounters.put(name, 0);
                }
                return false;
            }
            //בליעה
            case "VERTICAL_DROP_FSM": {
                // מימוש עתידי למעקב אנכי אחרי הלרינקס (גרגרת) - נקודה 152
                return false;
            }

            // 11. דינמיקת תנועות ידיים חריגות / רחבות (HandMovementDynamicsAnalyzer)
            case "HAND_MOVEMENT_DYNAMICS": {
                Point3D wristL = getLandmark(frame, rule.getP1(), false);
                Point3D wristR = getLandmark(frame, rule.getP2(), false);
                Point3D shoulderL = getLandmark(frame, rule.getP3(), false);
                Point3D shoulderR = getLandmark(frame, rule.getP4(), false);

                if (wristL == null || wristR == null || shoulderL == null || shoulderR == null) return false;

                double shoulderWidth = dist(shoulderL, shoulderR);
                if (shoulderWidth == 0) return false;

                // חישוב מיקום נוכחי ומהירות יחסית
                Point3D prevWristL = lastPointsL.get(name);
                Point3D prevWristR = lastPointsR.get(name);

                double vL = (prevWristL == null) ? 0 : dist(wristL, prevWristL);
                double vR = (prevWristR == null) ? 0 : dist(wristR, prevWristR);

                lastPointsL.put(name, wristL);
                lastPointsR.put(name, wristR);

                Point3D spineAnchor = new Point3D((shoulderL.getX() + shoulderR.getX()) / 2.0, (shoulderL.getY() + shoulderR.getY()) / 2.0, 0);
                double distL = dist(wristL, spineAnchor) / shoulderWidth;
                double distR = dist(wristR, spineAnchor) / shoulderWidth;

                // חוק מחוות יד רחבה (מחוץ לטווח הגוף ובמהירות גבוהה)
                if ((distL > 1.2 && vL > rule.getThreshold()) || (distR > 1.2 && vR > rule.getThreshold())) {
                    counter++;
                    frameCounters.put(name, counter);
                    if (counter >= rule.getMinFrames()) { // החזקה של לפחות חצי שנייה
                        frameCounters.put(name, 0);
                        return true; //  זוהתה תנועת יד רחבה
                    }
                } else {
                    if (counter > 0) frameCounters.put(name, counter - 1);
                }
                return false;
            }
        }
        return false;
    }

    private double dist(Point3D p1, Point3D p2) {
        return Math.sqrt(Math.pow(p1.getX() - p2.getX(), 2) + Math.pow(p1.getY() - p2.getY(), 2));
    }

    private double smooth(String name, double current, double alpha) {
        double prev = smoothedValues.get(name);
        if (prev == -1.0) {
            smoothedValues.put(name, current);
            return current;
        }
        double newVal = (alpha * current) + ((1.0 - alpha) * prev);
        smoothedValues.put(name, newVal);
        return newVal;
    }

    private Point3D getLandmark(FrameData frame, int index, boolean isFace) {
        if (index == -1) return null;
        try {
            if (isFace && frame.getFaceLandmarks() != null && index < frame.getFaceLandmarks().size()) {
                return frame.getFaceLandmarks().get(index);
            } else if (!isFace && frame.getPoseLandmarks() != null && index < frame.getPoseLandmarks().size()) {
                return frame.getPoseLandmarks().get(index);
            }
        } catch (Exception e) {
            // התעלמות משגיאות חריגה של אינדקסים לא תקינים בפריים
        }
        return null;
    }
    private boolean aboveStd(double value, double mean, double std, double factor) {
        return value > mean + factor * std;
    }

    private boolean belowStd(double value, double mean, double std, double factor) {
        return value < mean - factor * std;
    }

    private boolean outsideStd(double value, double mean, double std, double factor) {
        return Math.abs(value - mean) > factor * std;
    }
    //לקבלת רשימת תנועות חריגות
    public List<String> consumeDetectedGestures() {
        List<String> gestures = new ArrayList<>(detectedGestures);
        detectedGestures.clear();
        return gestures;
    }
}