package com.example.demo.service;

import com.example.demo.analyzer.DynamicGestureEngine;
import com.example.demo.model.FrameData;
import com.example.demo.model.FrameReceivedEvent;
import com.example.demo.model.LieDetectionResult;
import com.example.demo.repository.mongo.LieDetectionResultRepository;
import com.example.demo.repository.neo4j.EntityRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class LieDetectionService implements ApplicationListener<FrameReceivedEvent> {

    private final DynamicGestureEngine gestureEngine;
    private final LieDetectionResultRepository resultRepository;
    private final EntityRepository entityRepository;
    private final FusionService fusionService;
    @Autowired
    @Lazy
    private PythonConnector pythonConnector;

    private final Map<String, Integer> lastDetectedFrames = new HashMap<>();
    private final List<FrameData> currentWindowFrames = new java.util.ArrayList<>();
    private int currentWindowIndex = 0;

    // משתנה לשמירת ה-FPS המחושב עבור הסרטון הנוכחי (ברירת מחדל 30)
    private double currentVideoFps = 30.0;

    @Value("${video.score.one-anomaly:0.40}")
    private double scoreOneAnomaly;

    @Value("${video.score.two-anomalies:0.75}")
    private double scoreTwoAnomalies;

    @Value("${video.score.many-anomalies:1.00}")
    private double scoreManyAnomalies;

    @Autowired
    public LieDetectionService(DynamicGestureEngine gestureEngine,
                               LieDetectionResultRepository resultRepository,
                               EntityRepository entityRepository,
                               FusionService fusionService) {
        this.gestureEngine = gestureEngine;
        this.resultRepository = resultRepository;
        this.entityRepository = entityRepository;
        this.fusionService = fusionService;
    }


    String currentVideoName =
            "WIN_20260529_03_39_57_Pro";

    public void updateAnalyzersFps(double finalFps) {
        this.currentVideoFps = finalFps;
        System.out.println("🚀 מעדכן את ה-FPS של מנוע החוקים הדינמי ל: " + finalFps);
        gestureEngine.setFps(finalFps);
    }

    @Override
    public void onApplicationEvent(FrameReceivedEvent event) {
// 🔥 התיקון בתוך הלולאה או מתודת קליטת הפריים ב-LieDetectionService
        FrameData frame = event.getFrameData();

// 1. קריאה אחת בלבד ל-analyze (מניעת ריצה כפולה ובאגים של FSM)
        boolean isGestureDetected = gestureEngine.analyze(frame);
        int currentFrameNum = frame.getFrameNumber();


// הוספת הפריים לחלון הנוכחי לצורך חישוב ה-Confidence וה-Score בסוף החלון
        currentWindowFrames.add(frame);

        if (isGestureDetected) {
            // שליפת שם המחווה שזוהתה
            String gestureName = gestureEngine.getGestureName();

            System.out.println("🎯 [LieDetectionService] Processing detected gesture: " + gestureName + " on frame " + currentFrameNum);

            // 2. 🚨 התיקון הקריטי: עדכון מפת הזיכרון כדי שהחלון ידע לספור את החריגה!
            lastDetectedFrames.put(gestureName, currentFrameNum);

            // שמירה ל-MongoDB לצורך היסטוריה/דשבורד
            LieDetectionResult result = new LieDetectionResult(currentVideoName,currentFrameNum, gestureName);
            resultRepository.save(result);
        }


// בדיקה האם הגענו לסוף חלון הזמן (למשל חלון של 3 שניות לפי ה-FPS)
        int windowSizeInFrames = (int) Math.round(3 * currentVideoFps); // או גודל החלון שהגדרת במערכת
        int hopSizeInFrames = (int) Math.round(currentVideoFps); // שנייה אחת
        if (currentWindowFrames.size() >= windowSizeInFrames) {

            // חישוב הציון המשוקלל על בסיס האנומליות שהוכנסו למפה
            double calculatedBodyScore = calculateWindowAnomalyScore(currentWindowFrames);

            double startTime = currentWindowIndex * 1.0;   // stride = 1 שנייה
            double endTime = startTime + 3.0;

            List<String> gestures = new ArrayList<>(new LinkedHashSet<>(gestureEngine.consumeDetectedGestures()));
            if (!gestures.isEmpty()) {

                String reason = "שפת גוף: " + String.join(", ", gestures);

                fusionService.addSuspicionReasonSpreadOverTime(
                        startTime,
                        endTime,
                        calculatedBodyScore,
                        reason
                );
            }
            // הזרקה ל-Fusion Service
            submitWindowBodyScoreToFusion(startTime,endTime, currentWindowFrames, calculatedBodyScore);

            // מעבר לחלון הבא ואיפוס זמני של הפריימים בחלון
            currentWindowIndex++;

            // משאירים את שתי השניות האחרונות
            if (currentWindowFrames.size() > hopSizeInFrames) {
                currentWindowFrames.subList(0, hopSizeInFrames).clear();
                if (!currentWindowFrames.isEmpty()) {
                    int firstFrameInNextWindow = currentWindowFrames.get(0).getFrameNumber();

                    lastDetectedFrames.values()
                            .removeIf(frameNum -> frameNum < firstFrameInNextWindow);
                }
            } else {
                currentWindowFrames.clear();
            }

        }

    }

    /**
     * 🧠 פונקציית עזר לחישוב ציון החשד (Score) של חלון הוידאו
     */
    private double calculateWindowAnomalyScore(List<FrameData> windowFrames) {
        if (windowFrames.isEmpty()) return 0.0;

        int totalAnomaliesInWindow = 0;
        int firstFrame = windowFrames.get(0).getFrameNumber();
        int lastFrame = windowFrames.get(windowFrames.size() - 1).getFrameNumber();

        // ספירת אנומליות בטווח הפריימים של החלון
        for (int frameNum : lastDetectedFrames.values()) {
            if (frameNum >= firstFrame && frameNum <= lastFrame) {
                totalAnomaliesInWindow++;
            }
        }

        if (totalAnomaliesInWindow == 1) return scoreOneAnomaly;
        if (totalAnomaliesInWindow == 2) return scoreTwoAnomalies;
        if (totalAnomaliesInWindow >= 3) return scoreManyAnomalies;

        return 0.0;
    }

    // פונקציית אתחול ראשונית המקבלת נתוני וידאו ומעדכנת את מנוע הניתוח
    public void initializeVideoFps(double totalFrames, double durationSeconds) {
        double rawFps = totalFrames / durationSeconds;
        long finalFps = Math.round(rawFps);

        this.currentVideoFps = finalFps;
        System.out.println("🎯 [FPS Setup] Raw FPS: " + rawFps + " -> Rounded to: " + finalFps);

        // עדכון המנוע הדינמי החדש
        gestureEngine.setFps(finalFps);
    }

    // פונקציה המחשבת את רמת הביטחון ע"י אחוז הפריימים שבהם Mediapipe זיהתה נקודות
    public double calculateBodyConfidence(List<FrameData> windowFrames) {
        int totalFrames = windowFrames.size();
        if (totalFrames == 0) return 0.0;

        int successfullyTrackedFrames = 0;
        for (FrameData frame : windowFrames) {
            if (frame.getFaceLandmarks() != null && !frame.getFaceLandmarks().isEmpty() &&
                    frame.getPoseLandmarks() != null && !frame.getPoseLandmarks().isEmpty()) {
                successfullyTrackedFrames++;
            }
        }
        return (double) successfullyTrackedFrames / totalFrames;
    }

    // פונקציה שמזריקה את הציון ורמת הביטחון ל-FusionService
    public void submitWindowBodyScoreToFusion(double startTime, double endTime, List<FrameData> windowFrames, double calculatedBodyScore) {
        double confidence = calculateBodyConfidence(windowFrames);

        // הזרקה לתוך מנגנון ה-Fusion
        fusionService.setBodyScoreSpreadOverTime(startTime,endTime, calculatedBodyScore, confidence);

        System.out.printf("[Body Fusion Inject] %.1f-%.1fs-> Score: %.2f, Confidence: %.2f%n",
                startTime,endTime, calculatedBodyScore, confidence);
    }
}