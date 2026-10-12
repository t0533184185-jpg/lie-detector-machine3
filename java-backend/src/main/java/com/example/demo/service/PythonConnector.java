package com.example.demo.service;

import com.example.demo.analyzer.BaselineData;
import com.example.demo.analyzer.DynamicGestureEngine;
import com.example.demo.model.FrameData;
import com.example.demo.model.FrameReceivedEvent;
import com.example.demo.model.Point3D;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.io.*;
import java.net.*;
import java.util.*;

@Component
public class PythonConnector
//        implements CommandLineRunner
{
    private final ApplicationEventPublisher eventPublisher;
    private final ConversationManagerService conversationManagerService;
    private final GraphPersistenceService graphPersistenceService;
    private final SpeechToTextService speechToTextService; // הזרקת ה-STT
    private final FusionService fusionService;
    private final BaselineData baselineData;

    private static int frameCounter = 0;
//    private static final int CALIBRATION_LIMIT = 10;

    private static double baseShoulderY = 0;
    private static double baseBrowDist = 0;
    private static double baseLipDist = 0;
    private static double baseEyeDist = 0;
    private static double baseFearDist = 0;
    private static double baseShoulderWidth = 0;

    //משתנים לסטיית תקן
    private double shoulderYStd;
    private double browStd;
    private double lipStd;
    private double eyeStd;
    private double fearStd;
    private double shoulderWidthStd;

    private int calibrationFrames;

    //רשימות לשמירת ערכי הכיול
    private final List<Double> shoulderYValues = new ArrayList<>();
    private final List<Double> browValues = new ArrayList<>();
    private final List<Double> lipValues = new ArrayList<>();
    private final List<Double> eyeValues = new ArrayList<>();
    private final List<Double> fearValues = new ArrayList<>();
    private final List<Double> shoulderWidthValues = new ArrayList<>();
    private final OllamaChatModel chatModel;
    private final DynamicGestureEngine dynamicGestureEngine;
    @Autowired
    public PythonConnector(GraphPersistenceService graphPersistenceService,
                           @Lazy ConversationManagerService conversationManagerService,
                           ApplicationEventPublisher eventPublisher,
                           SpeechToTextService speechToTextService, FusionService fusionService, BaselineData baselineData,
                           OllamaChatModel chatModel, DynamicGestureEngine dynamicGestureEngine){
        this.graphPersistenceService = graphPersistenceService;
        this.conversationManagerService = conversationManagerService;
        this.eventPublisher = eventPublisher;
        this.speechToTextService = speechToTextService;
        this.fusionService = fusionService;
        this.baselineData = baselineData;
        this.chatModel = chatModel;
        this.dynamicGestureEngine = dynamicGestureEngine;
    }
    public void setFps(double newFps) {
        this.fps = newFps;
    }

//    @Override
//    public void startAnalysis(String folderPath) throws Exception {
////        String folderPath = "C:\\Users\\user\\my_uploads\\6a193adddf0f3d13d3d3506a\\WIN_20260529_03_39_57_Pro.mp4_frames_1780039098287";
////        int totalFrames = 1000;
////
////        //ניקוי הגרף
////        graphPersistenceService.prepareGraphForNewVideo();
////        System.out.println(" Starting PythonConnector Video Processing Engine...");
////
////        // הגדרת ה-Thread ושמירתו במשתנה nlpThread
////        Thread nlpThread = new Thread(() -> {
////            try {
////                String mediaFilePath = folderPath + "\\original_audio.wav";
////                System.out.println("⏳ [NLP Async] מפעיל מנוע תמלול וניהול גרף ידע ברקע...");
////
////                File audioFile = new File(mediaFilePath);
////                if (audioFile.exists() && audioFile.length() > 0) {
////                    // קריאה למנהל השיחה - הוא יבצע את התמלול, הבדיקות ובניית הגרף משפט אחר משפט
////                    conversationManagerService.processAudioSegment(mediaFilePath, "הדובר");
////                } else {
//                    System.err.println(" [NLP Async] קובץ האודיו לא נמצא בנתיב: " + mediaFilePath);
////                }
////            } catch (Exception e) {
////                System.out.println(" שגיאה בעיבוד ה-NLP באשכול הרקע: " + e.getMessage());
////            }
////        });
////
////        // הזנקת ה-Thread ברקע
////        nlpThread.start();
////
////        // שלב הכיול והפריימים (רץ במקביל)
////        System.out.println("Starting Calibration Process...");
////        for (int i = 0; i < totalFrames && frameCounter < CALIBRATION_LIMIT; i++) {
////            String response = sendToPython(folderPath + "\\frame_" + i + ".png");
////            if (response != null && response.contains("|")) {
////                String[] points = response.split("\\|")[0].split(";");
////                if (points.length >= 501) {
////                    performCalibration(points);
////                    frameCounter++;
////                }
////            }
////        }
////
////        // עיבוד הפריימים של הוידאו (כאן הלולאה תעצור בפריים 192 בדינמיות)
////        processVideoFrames(folderPath, totalFrames);
////
////        // נקודת ההמתנה הסופית - מונע מהאפליקציה להיסגר בטרם עת
////        System.out.println(" הוידאו הסתיים. ממתין לסיום ניתוח ה-NLP והתרגום...");
////        nlpThread.join(); // גורם למתודה הראשית לחכות כאן עד ש-nlpThread יסיים לחלוטין!
////
////        System.out.println(" הכל הושלם בהצלחה! התוכנית נסגרת באופן מסודר.");
//    }


    // 1. הוסף את משתנה המופע והמתודה ל-PythonConnector (אם עדיין לא קיימים שם)
    private double fps = 30.0;

@Async
    public void processVideo(String folderPath, int totalFrames) throws Exception {
        frameCounter = 0;

        baseShoulderY = 0;
        baseBrowDist = 0;
        baseLipDist = 0;
        baseEyeDist = 0;
        baseFearDist = 0;
        baseShoulderWidth = 0;

        //ניקוי של רשימות
        shoulderYValues.clear();
        browValues.clear();
        lipValues.clear();
        eyeValues.clear();
        fearValues.clear();
        shoulderWidthValues.clear();

        graphPersistenceService.prepareGraphForNewVideo();
        System.out.println("Starting PythonConnector Video Processing Engine...");

        Thread nlpThread = new Thread(() -> {
            try {
                String mediaFilePath = folderPath + "\\original_audio.wav";
                File audioFile = new File(mediaFilePath);
                if (audioFile.exists() && audioFile.length() > 0) {
                    conversationManagerService.processAudioSegment(mediaFilePath, "הדובר");
                } else {
                    System.err.println("[NLP Async] קובץ אודיו לא נמצא: " + mediaFilePath);
                }
            } catch (Exception e) {
                System.out.println("שגיאה ב-NLP: " + e.getMessage());
            }
        });

        nlpThread.start();

        // מספר פריימי הכיול = 5 שניות
        this.calibrationFrames = (int) Math.round(fps * 5);

        // כיול
        for (int i = 0; i < totalFrames && i < calibrationFrames; i++) {
            String response = sendToPython(folderPath + "\\frame_" + i + ".png");
            if (response != null && response.contains("|")) {
                String[] points = response.split("\\|")[0].split(";");
                if (points.length >= 501) {
                    performCalibration(points);
                    frameCounter++;
                }
            }
        }

        processVideoFrames(folderPath, totalFrames, calibrationFrames);

        nlpThread.join();
        System.out.println("הכל הושלם בהצלחה!");
    }


    // מתודת העיבוד לצורך חישוב ציר הזמן
    public void processVideoFrames(String folderPath, int totalFrames,int calibrationFrames) {
        System.out.println("🎬 Starting Real-time Analysis on video frames...");
        int consecutiveErrors = 0;

        for (int i =  calibrationFrames; i < totalFrames; i++) {
            File f = new File(folderPath + "\\frame_" + i + ".png");

            System.out.println("JAVA PATH = " + f.getAbsolutePath());
            System.out.println("JAVA EXISTS = " + f.exists());
            String response = sendToPython(folderPath + "\\frame_" + i + ".png");

            if (response != null && response.contains("|")) {
                consecutiveErrors = 0;
                String[] points = response.split("\\|")[0].split(";");
                FrameData frameData = convertToFrameData(i, points);

                // חישוב הזמן המדויק בשניות על פי ה-FPS הדינמי שהוגדר לסרטון
                double currentTimeInSeconds = (double) i / this.fps;

                // בדיקה אם תגובת הפייתון מכילה חריגה (Gesture Detected) בשנייה הזו
                if (response.contains("Abnormal")) {
                    System.out.println("🎯 אנומליה ויזואלית זוהתה בשנייה: " + currentTimeInSeconds);
                }

                eventPublisher.publishEvent(new FrameReceivedEvent(this, frameData));
            } else {
                consecutiveErrors++;
                if (consecutiveErrors >= 5) {
                    System.out.println(" Video end detected dynamically at frame " + (i - consecutiveErrors + 1) + ". Stopping frame analysis.");
                    break;
                }
            }
            try { Thread.sleep(10); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
        System.out.println("🏁 Video analysis complete. All anomalies saved to Database!");
        fusionService.bodyFinished();
    }

    private FrameData convertToFrameData(int frameNumber, String[] points) {
        Map<Integer, Point3D> poseLandmarks = new HashMap<>();
        Map<Integer, Point3D> faceLandmarks = new HashMap<>();
        Map<Integer, Point3D> handLandmarks = new HashMap<>();

        for (int i = 0; i < points.length; i++) {
            String pointStr = points[i];
            if (pointStr.contains(":")) pointStr = pointStr.split(":")[1];

            String[] coords = pointStr.split(",");
            if (coords.length >= 3) {
                double x = Double.parseDouble(coords[0]);
                double y = Double.parseDouble(coords[1]);
                double z = Double.parseDouble(coords[2]);
                Point3D point = new Point3D(x, y, z);

                if (i <= 32) poseLandmarks.put(i, point);
                else if (i >= 33 && i <= 500) faceLandmarks.put(i, point);
                else handLandmarks.put(i, point);
            }
        }
        return FrameData.builder()
                .frameNumber(frameNumber)
                .poseLandmarks(poseLandmarks)
                .faceLandmarks(faceLandmarks)
                .handLandmarks(handLandmarks)
                .build();
    }

    private void performCalibration(String[] points) {

        //חישוב גובה כתפיים
        double leftDist  = Math.abs(getCoordY(points[11]) - getCoordY(points[7]));
        double rightDist = Math.abs(getCoordY(points[12]) - getCoordY(points[8]));
        double shoulderY = (leftDist + rightDist) / 2;

        baseShoulderY += shoulderY;
        shoulderYValues.add(shoulderY);

        //מרחק בין גבות
        double browLeft  = getCoordX(points[33 + 70]);
        double browRight = getCoordX(points[33 + 300]);
        double browDist = Math.abs(browRight - browLeft);

        baseBrowDist += browDist;
        browValues.add(browDist);

        //פתיחת שפתיים
        double lipTop    = getCoordY(points[33 + 13]);
        double lipBottom = getCoordY(points[33 + 14]);
        double lipDist = Math.abs(lipTop - lipBottom);

        baseLipDist += lipDist;
        lipValues.add(lipDist);

        //פתיחת העין
        double eyeTop    = getCoordY(points[33 + 159]);
        double eyeBottom = getCoordY(points[33 + 145]);
        double eyeDist = Math.abs(eyeTop - eyeBottom);

        baseEyeDist += eyeDist;
        eyeValues.add(eyeDist);

        //מדד הפחד
        double p1x = getCoordX(points[33 + 43]);
        double p1y = getCoordY(points[33 + 43]);
        double p2x = getCoordX(points[33 + 94]);
        double p2y = getCoordY(points[33 + 94]);
        double fearDist = Math.sqrt(Math.pow(p1x - p2x, 2) + Math.pow(p1y - p2y, 2));

        baseFearDist += fearDist;
        fearValues.add(fearDist);

        //מרחק הכתפיים
        double leftShoulderX  = getCoordX(points[11]);
        double rightShoulderX = getCoordX(points[12]);
        double shoulderWidth = Math.abs(leftShoulderX - rightShoulderX);

        baseShoulderWidth += shoulderWidth;
        shoulderWidthValues.add(shoulderWidth);

        if (frameCounter == calibrationFrames  - 1) {
            //חישוב ממוצעים
            baseShoulderY     /= calibrationFrames;
            baseBrowDist      /= calibrationFrames;
            baseLipDist       /= calibrationFrames;
            baseEyeDist       /= calibrationFrames;
            baseFearDist      /= calibrationFrames;
            baseShoulderWidth /= calibrationFrames;

            //בדיקת סטיית תקן
            lipStd = calculateStd(lipValues, baseLipDist);
            eyeStd = calculateStd(eyeValues, baseEyeDist);
            browStd = calculateStd(browValues, baseBrowDist);
            fearStd = calculateStd(fearValues, baseFearDist);
            shoulderYStd = calculateStd(shoulderYValues, baseShoulderY);
            shoulderWidthStd = calculateStd(shoulderWidthValues, baseShoulderWidth);

            //אובייקט לקביעת baseline
            BaselineData baseline = new BaselineData();

            baseline.setShoulderMean(baseShoulderY);
            baseline.setShoulderStd(shoulderYStd);

            baseline.setBrowMean(baseBrowDist);
            baseline.setBrowStd(browStd);

            baseline.setLipMean(baseLipDist);
            baseline.setLipStd(lipStd);

            baseline.setEyeMean(baseEyeDist);
            baseline.setEyeStd(eyeStd);

            baseline.setFearMean(baseFearDist);
            baseline.setFearStd(fearStd);

            baseline.setShoulderWidthMean(baseShoulderWidth);
            baseline.setShoulderWidthStd(shoulderWidthStd);

            dynamicGestureEngine.setBaseline(baseline);
        }
    }

    private static double getCoordY(String pointStr) {
        if (pointStr.contains(":")) pointStr = pointStr.split(":")[1];
        String[] coords = pointStr.split(",");
        return Double.parseDouble(coords[1]);
    }

    private static double getCoordX(String pointStr) {
        if (pointStr.contains(":")) pointStr = pointStr.split(":")[1];
        String[] coords = pointStr.split(",");
        return Double.parseDouble(coords[0]);
    }

    public static String sendToPython(String message) {
        try (Socket socket = new Socket("localhost", 5050);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

            System.out.println(">>> Sending:");
            System.out.println(message);

            out.println(message);

            String response = in.readLine();

            System.out.println("<<< Received:");
            System.out.println(response);

            return response;

        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    //פונקציה לסטיית תקן
    private double calculateStd(List<Double> values, double mean) {

        if (values.isEmpty()) {
            return 0;
        }

        double variance = 0;

        for (double value : values) {
            variance += Math.pow(value - mean, 2);
        }

        variance /= values.size();

        return Math.sqrt(variance);
    }
}