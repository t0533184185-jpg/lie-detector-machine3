package com.example.demo.model;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.*;

import com.opencsv.CSVReader;


public class MovementRule {
    String pose;
    int[] landmarks;
    String action;
    double threshold;
    double timeDuration;

    // מפה לא סטטית - מנקה את עצמה לכל ריצה חדשה
    private Map<String, Long> startTimeMap = new HashMap<>();
    public double baseLipDist = 0;

    public MovementRule(String pose, String landmarksStr, String action, double threshold, double duration) {
        this.pose = pose;
        this.action = action;
        this.threshold = threshold;
        this.timeDuration = duration;

        if (landmarksStr.equals("0-467")) {
            this.landmarks = new int[468];
            for (int i = 0; i < 468; i++) this.landmarks[i] = i;
        } else {
            String[] parts = landmarksStr.split(",");
            this.landmarks = new int[parts.length];
            for (int i = 0; i < parts.length; i++) {
                try {
                    this.landmarks[i] = Integer.parseInt(parts[i].trim());
                } catch (NumberFormatException e) {
                    this.landmarks[i] = -1;
                }
            }
        }
    }

    // שדות חדשים בראש המחלקה
    private Map<String, Double> previousYMap = new HashMap<>();
    private Map<String, Double[]> previousPosMap = new HashMap<>();
    private Map<String, Long> countStartTimeMap = new HashMap<>();
    private Map<String, Integer> eventCountMap = new HashMap<>();
    public double baseEyeDist = 0;
    public
    double baseFearDist = 0;
    public double baseShoulderWidth = 0;

    public static List<MovementRule> loadRulesFromCSV(String filePath) {
        List<MovementRule> rules = new ArrayList<>();
        try (CSVReader br = new CSVReader(new FileReader(filePath))) {
            String[] line;
            br.readNext(); // דילוג על כותרות
            while ((line = br.readNext()) != null) {
                try {
                    String pose       = line[0].trim();
                    String landmarkVal = line[1].trim();
                    String action     = line[2].trim();
                    double threshold  = Double.parseDouble(line[3].trim());
                    double duration   = Double.parseDouble(line[4].trim());

                    // ✅ "All Landmarks" → מעביר "0-467" לקונסטרקטור שיודע לטפל בזה
                    if (landmarkVal.equalsIgnoreCase("All Landmarks")) {
                        landmarkVal = "0-467";
                    }

                    // ✅ "Face Mesh" → כל 468 נקודות הפנים (offset +33)
                    if (landmarkVal.equalsIgnoreCase("Face Mesh")) {
                        landmarkVal = "0-467"; // יטופל עם offset בזיהוי
                    }

                    // ✅ "ROI" → נקודות מרכז הפנים לחישוב rPPG
                    if (landmarkVal.equalsIgnoreCase("ROI")) {
                        landmarkVal = "0-467"; // יטופל בפייתון
                    }

                    MovementRule newRule = new MovementRule(pose, landmarkVal, action, threshold, duration);
                    rules.add(newRule);
                    System.out.println("Loaded: " + pose + " | " + action + " | landmarks=" + landmarkVal);

                } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
                    System.err.println("Skipping invalid line: " + Arrays.toString(line));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return rules;
    }

    public double avgDistance = 0;
    public double baseBrowDistance = 0;

    private boolean evaluateCondition(MovementRule rule, String[] points) {
        try {
            switch (rule.action) {
                case "y_avg":
                    if (rule.pose.equals("shurg")) {
                        double leftDist  = Math.abs(getCoordY(points[11]) - getCoordY(points[7]));
                        double rightDist = Math.abs(getCoordY(points[12]) - getCoordY(points[8]));
                        double currentAvg = (leftDist + rightDist) / 2;
                        return currentAvg < (avgDistance - rule.threshold);
                    }
                    // שאר הלוגיקה הרגילה...
                case "dist_horiz":
                    double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE;
                    for (int id : rule.landmarks) {
                        // ✅ נקודות > 32 הן face mesh, צריך offset +33
                        int realIndex = (id > 32) ? (33 + id) : id;
                        if (realIndex != -1 && realIndex < points.length) {
                            double x = getCoordX(points[realIndex]);
                            if (x < minX) minX = x;
                            if (x > maxX) maxX = x;
                        }
                    }
                    return (maxX - minX) < (baseBrowDistance - rule.threshold);

                case "y_diff":
                    if (rule.landmarks.length < 2) return false;
                    // ✅ נקודות face mesh — צריך offset +33
                    int id1 = rule.landmarks[0]; // 324
                    int id2 = rule.landmarks[1]; // 94
                    int realId1 = (id1 > 32) ? (33 + id1) : id1;
                    int realId2 = (id2 > 32) ? (33 + id2) : id2;
                    if (realId1 >= points.length || realId2 >= points.length) return false;
                    double yA = getCoordY(points[realId1]);
                    double yB = getCoordY(points[realId2]);
                    double diff = Math.abs(yA - yB);
                    return diff > rule.threshold;

                case "angle_deg":
                    if (rule.landmarks.length < 2) return false;
                    double sx1 = getCoordX(points[rule.landmarks[0]]);
                    double sy1 = getCoordY(points[rule.landmarks[0]]);
                    double sx2 = getCoordX(points[rule.landmarks[1]]);
                    double sy2 = getCoordY(points[rule.landmarks[1]]);
                    double angleRad = Math.atan2(Math.abs(sy2 - sy1), Math.abs(sx2 - sx1));
                    double angleDeg = Math.toDegrees(angleRad);
                    return angleDeg > rule.threshold;

                case "x_var":
                    // שונות X — בודק נדנוד צידי
                    double sumX = 0;
                    int countX = 0;
                    for (int id : rule.landmarks) {
                        if (id != -1 && id < points.length) {
                            sumX += getCoordX(points[id]);
                            countX++;
                        }
                    }
                    if (countX == 0) return false;
                    double meanX = sumX / countX;
                    double varX = 0;
                    for (int id : rule.landmarks) {
                        if (id != -1 && id < points.length) {
                            double diff2 = getCoordX(points[id]) - meanX;
                            varX += diff2 * diff2;
                        }
                    }
                    varX /= countX;
                    return varX > rule.threshold;

                case "std_dev":
                    // סטיית תקן — קיבוע מבט (נקודות עיניים)
                    double sumSD = 0;
                    int countSD = 0;
                    for (int id : rule.landmarks) {
                        int realId = (id > 32) ? (33 + id) : id;
                        if (realId != -1 && realId < points.length) {
                            sumSD += getCoordY(points[realId]);
                            countSD++;
                        }
                    }
                    if (countSD == 0) return false;
                    double meanSD = sumSD / countSD;
                    double stdDev = 0;
                    for (int id : rule.landmarks) {
                        int realId = (id > 32) ? (33 + id) : id;
                        if (realId != -1 && realId < points.length) {
                            double d = getCoordY(points[realId]) - meanSD;
                            stdDev += d * d;
                        }
                    }
                    stdDev = Math.sqrt(stdDev / countSD);
                    return stdDev < rule.threshold;

                case "std_dev_pos":
                    // סטיית תקן מיקום — תנועות מדורגות
                    double sumPos = 0;
                    int countPos = 0;
                    for (int id : rule.landmarks) {
                        int realId = (id > 32) ? (33 + id) : id;
                        if (realId < points.length) {
                            sumPos += getCoordX(points[realId]);
                            countPos++;
                        }
                    }
                    if (countPos == 0) return false;
                    double meanPos = sumPos / countPos;
                    double stdPos = 0;
                    for (int id : rule.landmarks) {
                        int realId = (id > 32) ? (33 + id) : id;
                        if (realId < points.length) {
                            double d = getCoordX(points[realId]) - meanPos;
                            stdPos += d * d;
                        }
                    }
                    stdPos = Math.sqrt(stdPos / countPos);
                    return stdPos > rule.threshold;

                case "multi_point_dist":
                    // מרחק ממוצע בין כל הנקודות — הבעת פחד
                    if (rule.landmarks.length < 2) return false;
                    double totalDist = 0;
                    int pairCount = 0;
                    for (int a = 0; a < rule.landmarks.length; a++) {
                        for (int b = a + 1; b < rule.landmarks.length; b++) {
                            int idA = rule.landmarks[a];
                            int idB = rule.landmarks[b];
                            int rA = (idA > 32) ? (33 + idA) : idA;
                            int rB = (idB > 32) ? (33 + idB) : idB;
                            if (rA < points.length && rB < points.length) {
                                double dx = getCoordX(points[rA]) - getCoordX(points[rB]);
                                double dy = getCoordY(points[rA]) - getCoordY(points[rB]);
                                totalDist += Math.sqrt(dx * dx + dy * dy);
                                pairCount++;
                            }
                        }
                    }
                    if (pairCount == 0) return false;
                    return (totalDist / pairCount) > rule.threshold;

                case "z_axis_delta":
                    if (rule.landmarks.length < 2) return false;
                    String p1str = points[rule.landmarks[0]].split(":")[1];
                    String p2str = points[rule.landmarks[1]].split(":")[1];
                    String[] coords1 = p1str.split(",");
                    String[] coords2 = p2str.split(",");

                    // בדוק visibility אם קיים
                    if (coords1.length >= 4 && coords2.length >= 4) {
                        double vis1 = Double.parseDouble(coords1[3]);
                        double vis2 = Double.parseDouble(coords2[3]);
                        if (vis1 < 0.5 || vis2 < 0.5) return false;
                    }

                    double z1 = Double.parseDouble(coords1[2]);
                    double z2 = Double.parseDouble(coords2[2]);
                    return Math.abs(z1 - z2) > rule.threshold;

                case "asymmetry_ratio":
                    // יחס א-סימטריה — חיוך מוסתר
                    if (rule.landmarks.length < 2) return false;
                    int rL = (rule.landmarks[0] > 32) ? (33 + rule.landmarks[0]) : rule.landmarks[0];
                    int rR = (rule.landmarks[1] > 32) ? (33 + rule.landmarks[1]) : rule.landmarks[1];
                    if (rL >= points.length || rR >= points.length) return false;
                    double yL = getCoordY(points[rL]);
                    double yR = getCoordY(points[rR]);
                    double ratio = Math.abs(yL - yR) / ((yL + yR) / 2.0);
                    return ratio > rule.threshold;

                case "y_oscillation":
                    // תנודות Y — בליעה
                    if (rule.landmarks.length < 1) return false;
                    int oscId = rule.landmarks[0];
                    int realOscId = (oscId > 32) ? (33 + oscId) : oscId;
                    if (realOscId >= points.length) return false;
                    double currentY = getCoordY(points[realOscId]);
                    Double prevY = previousYMap.get(rule.pose);
                    previousYMap.put(rule.pose, currentY);
                    if (prevY == null) return false;
                    return Math.abs(currentY - prevY) > rule.threshold;

                case "simultaneous_v":
                    // תנועה בו-זמנית של שתי ידיים
                    if (rule.landmarks.length < 2) return false;

                    // בדוק visibility
                    String sv1 = points[rule.landmarks[0]].split(":")[1];
                    String sv2 = points[rule.landmarks[1]].split(":")[1];
                    String[] svCoords1 = sv1.split(",");
                    String[] svCoords2 = sv2.split(",");

                    if (svCoords1.length >= 4 && svCoords2.length >= 4) {
                        double vis1 = Double.parseDouble(svCoords1[3]);
                        double vis2 = Double.parseDouble(svCoords2[3]);
                        if (vis1 < 0.5 || vis2 < 0.5) return false;
                    }                    double vx1 = getCoordX(points[rule.landmarks[0]]);
                    double vy1 = getCoordY(points[rule.landmarks[0]]);
                    double vx2 = getCoordX(points[rule.landmarks[1]]);
                    double vy2 = getCoordY(points[rule.landmarks[1]]);
                    Double[] prev1 = previousPosMap.get(rule.pose + "_0");
                    Double[] prev2 = previousPosMap.get(rule.pose + "_1");
                    previousPosMap.put(rule.pose + "_0", new Double[]{vx1, vy1});
                    previousPosMap.put(rule.pose + "_1", new Double[]{vx2, vy2});
                    if (prev1 == null || prev2 == null) return false;
                    double vel1 = Math.sqrt(Math.pow(vx1-prev1[0],2) + Math.pow(vy1-prev1[1],2));
                    double vel2 = Math.sqrt(Math.pow(vx2-prev2[0],2) + Math.pow(vy2-prev2[1],2));
                    return vel1 > rule.threshold && vel2 > rule.threshold;

                case "velocity_peaks":
                    // שיאי מהירות — קפיצות עיניים
                    if (rule.landmarks.length < 2) return false;
                    int vpId1 = (rule.landmarks[0] > 32) ? (33 + rule.landmarks[0]) : rule.landmarks[0];
                    int vpId2 = (rule.landmarks[1] > 32) ? (33 + rule.landmarks[1]) : rule.landmarks[1];
                    if (vpId1 >= points.length || vpId2 >= points.length) return false;
                    double vpx1 = getCoordX(points[vpId1]), vpy1 = getCoordY(points[vpId1]);
                    double vpx2 = getCoordX(points[vpId2]), vpy2 = getCoordY(points[vpId2]);
                    Double[] prevVP1 = previousPosMap.get(rule.pose + "_0");
                    Double[] prevVP2 = previousPosMap.get(rule.pose + "_1");
                    previousPosMap.put(rule.pose + "_0", new Double[]{vpx1, vpy1});
                    previousPosMap.put(rule.pose + "_1", new Double[]{vpx2, vpy2});
                    if (prevVP1 == null || prevVP2 == null) return false;
                    double speed1 = Math.sqrt(Math.pow(vpx1-prevVP1[0],2) + Math.pow(vpy1-prevVP1[1],2));
                    double speed2 = Math.sqrt(Math.pow(vpx2-prevVP2[0],2) + Math.pow(vpy2-prevVP2[1],2));
                    return speed1 > rule.threshold && speed2 > rule.threshold;

                case "count_per_min":
                    // ספירת אירועים לדקה — מחוות ידיים מוגברות
                    if (rule.landmarks.length < 2) return false;
                    double cpx1 = getCoordX(points[rule.landmarks[0]]);
                    double cpy1 = getCoordY(points[rule.landmarks[0]]);
                    Double[] prevCP = previousPosMap.get(rule.pose);
                    previousPosMap.put(rule.pose, new Double[]{cpx1, cpy1});
                    if (prevCP == null) return false;
                    double movement = Math.sqrt(Math.pow(cpx1-prevCP[0],2) + Math.pow(cpy1-prevCP[1],2));
                    Long firstSeen = countStartTimeMap.get(rule.pose);
                    if (firstSeen == null) {
                        countStartTimeMap.put(rule.pose, System.currentTimeMillis());
                        eventCountMap.put(rule.pose, 0);
                    }
                    if (movement > 0.02) {
                        eventCountMap.merge(rule.pose, 1, Integer::sum);
                    }
                    long elapsed = System.currentTimeMillis() - countStartTimeMap.getOrDefault(rule.pose, System.currentTimeMillis());
                    double perMin = eventCountMap.getOrDefault(rule.pose, 0) / (elapsed / 60000.0);
                    return perMin > rule.threshold;

                case "duration":
                    Integer framesSeen = eventCountMap.getOrDefault(rule.pose + "_dur", 0);
                    framesSeen++;
                    eventCountMap.put(rule.pose + "_dur", framesSeen);
                    // threshold = מספר פריימים נדרש (למשל 30 = שניה בסרטון 30fps)
                    return framesSeen > (int) rule.threshold;

                case "dist_vert":
                    if (rule.landmarks.length < 2) return false;
                    double y1 = getCoordY(points[rule.landmarks[0]]);
                    double y2 = getCoordY(points[rule.landmarks[1]]);
                    // בדוק מרחק מוחלט קטן, לא יחסי לבסיס
                    return Math.abs(y1 - y2) < rule.threshold;

                case "dist_euclidean":
                    // בדוק visibility של הידיים
                    String deLstr = points[15].split(":")[1];
                    String deRstr = points[16].split(":")[1];
                    String[] deCoords1 = deLstr.split(",");
                    String[] deCoords2 = deRstr.split(",");

                    if (deCoords1.length >= 4 && deCoords2.length >= 4) {
                        double visL = Double.parseDouble(deCoords1[3]);
                        double visR = Double.parseDouble(deCoords2[3]);
                        if (visL < 0.5 && visR < 0.5) return false; // שתיהן לא נראות
                    }

                    double hLx = getCoordX(points[15]);
                    double hLy = getCoordY(points[15]);
                    double hRx = getCoordX(points[16]);
                    double hRy = getCoordY(points[16]);

                    for (int i = 33; i <= 501 && i < points.length; i++) {
                        double fX = getCoordX(points[i]);
                        double fY = getCoordY(points[i]);
                        if (Math.sqrt(Math.pow(hLx-fX,2)+Math.pow(hLy-fY,2)) < rule.threshold ||
                                Math.sqrt(Math.pow(hRx-fX,2)+Math.pow(hRy-fY,2)) < rule.threshold)
                            return true;
                    }
                    return false;

                case "hair_groom":
                    if (rule.landmarks.length < 2) return false;

                    // בדוק visibility
                    String hg1 = points[rule.landmarks[0]].split(":")[1];
                    String hg2 = points[rule.landmarks[1]].split(":")[1];
                    String[] hgC1 = hg1.split(",");
                    String[] hgC2 = hg2.split(",");

                    if (hgC1.length >= 4 && hgC2.length >= 4) {
                        double vis1 = Double.parseDouble(hgC1[3]);
                        double vis2 = Double.parseDouble(hgC2[3]);
                        if (vis1 < 0.5 && vis2 < 0.5) return false;
                    }

                    double handX = getCoordX(points[rule.landmarks[0]]);
                    double handY = getCoordY(points[rule.landmarks[0]]);
                    double hand2X = getCoordX(points[rule.landmarks[1]]);
                    double hand2Y = getCoordY(points[rule.landmarks[1]]);

                    // נקודות ראש/שיער ב-face mesh (offset +33)
                    // קדקוד ראש: 10, צדדים: 234, 454, עורף: 152
                    int[] hairPoints = {33+10, 33+234, 33+454, 33+152, 33+127, 33+356};

                    boolean handNearHair = false;
                    for (int hp : hairPoints) {
                        if (hp >= points.length) continue;
                        double hpX = getCoordX(points[hp]);
                        double hpY = getCoordY(points[hp]);
                        double dist1 = Math.sqrt(Math.pow(handX-hpX,2) + Math.pow(handY-hpY,2));
                        double dist2 = Math.sqrt(Math.pow(hand2X-hpX,2) + Math.pow(hand2Y-hpY,2));
                        if (dist1 < rule.threshold || dist2 < rule.threshold) {
                            handNearHair = true;
                            break;
                        }
                    }

                    if (!handNearHair) return false;

                    // בדוק תנועה
                    Double[] prevHG = previousPosMap.get(rule.pose);
                    previousPosMap.put(rule.pose, new Double[]{handX, handY});
                    if (prevHG == null) return false;
                    movement = Math.sqrt(Math.pow(handX-prevHG[0],2) +
                            Math.pow(handY-prevHG[1],2));
                    return movement > 0.01;

                case "blink_fast":
                    if (rule.landmarks.length < 2) return false;
                    int blinkId1 = (rule.landmarks[0] > 32) ? (33 + rule.landmarks[0]) : rule.landmarks[0];
                    int blinkId2 = (rule.landmarks[1] > 32) ? (33 + rule.landmarks[1]) : rule.landmarks[1];
                    if (blinkId1 >= points.length || blinkId2 >= points.length) return false;

                    double eyeOpen = Math.abs(getCoordY(points[blinkId1]) - getCoordY(points[blinkId2]));
                    Double prevEye = previousYMap.get(rule.pose);
                    previousYMap.put(rule.pose, eyeOpen);


                    if (prevEye == null) return false;
                    return prevEye > rule.threshold && eyeOpen < (rule.threshold / 2);


                default: return false;
            }
        } catch (Exception e) { return false; }
    }

    private static double getCoordY(String p) { return Double.parseDouble(p.split(":")[1].split(",")[1]); }
    private static double getCoordX(String p) { return Double.parseDouble(p.split(":")[1].split(",")[0]); }
    private Set<String> activeAnomalies = new HashSet<>();
    private Map<String, Integer> cooldownMap = new HashMap<>();
    private static final int COOLDOWN_FRAMES = 50; // לא ידווח שוב תוך 15 פריימים

    public List<String> detectAnomalies(String[] points, List<MovementRule> allRules) {
        List<String> newAnomalies = new ArrayList<>();
        long now = System.currentTimeMillis();
        Set<String> currentlyActive = new HashSet<>();

        for (MovementRule rule : allRules) {
            if (evaluateCondition(rule, points)) {
                startTimeMap.putIfAbsent(rule.pose, now);
                if ((now - startTimeMap.get(rule.pose)) / 1000.0 >= rule.timeDuration) {
                    currentlyActive.add(rule.pose);

                    int cooldown = cooldownMap.getOrDefault(rule.pose, 0);
                    if (!activeAnomalies.contains(rule.pose) && cooldown == 0) {
                        newAnomalies.add(rule.pose);
                        cooldownMap.put(rule.pose, COOLDOWN_FRAMES);
                    }
                }
            } else {
                startTimeMap.remove(rule.pose);
            }
        }

        // הורד cooldown בכל פריים
        cooldownMap.replaceAll((k, v) -> Math.max(0, v - 1));
        activeAnomalies = currentlyActive;
        return newAnomalies;
    }
    private static double getCoordZ(String p) {
        return Double.parseDouble(p.split(":")[1].split(",")[2]);
    }
}