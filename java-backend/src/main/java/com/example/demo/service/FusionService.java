package com.example.demo.service;

import com.example.demo.model.Videos;
import com.example.demo.model.WindowScores;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class FusionService {

    private volatile boolean bodyFinished = false;
    private volatile boolean audioFinished = false;
    private volatile boolean contentFinished = false;
    private final VideosService videosService;

    public FusionService(VideosService videosService) {
        this.videosService = videosService;
    }
    private String currentVideoId;
    private final Map<Integer, WindowScores> windows = new ConcurrentHashMap<>();
    public void setCurrentVideoId(String videoId) {
        this.currentVideoId = videoId;
    }
    @Value("${fusion.weight.base-content:0.5}")
    private double baseWContent;

    @Value("${fusion.weight.base-audio:0.3}")
    private double baseWAudio;

    @Value("${fusion.weight.base-body:0.2}")
    private double baseWBody;

    // הגנה הרמטית על חלונות הזמן מפני דריסת אחוז הביטחון והציון!
    //נעילת חלונות על בסיס חומרת השקר המקסימלית בלבד!
    public void setContentScore(int window, double score, double confidence) {
        double windowStart = window;
        double windowEnd = windowStart + 3.0;

        if (windowEnd <= 5.0) {
            return;
        }
        WindowScores ws = windows.computeIfAbsent(window, k -> {
            WindowScores w = new WindowScores();
            w.setWindowIndex(window);
            w.setContentScore(0.0);
            w.setContentConfidence(0.0); // מתחילים מאפס ביטחון כדי שכל משפט ראשוני ייכנס
            return w;
        });

        // 1. אם הציון החדש גבוה/חמור יותר מהקיים - מעדכנים תמיד! (לוכד את השקר)
        if (score > ws.getContentScore()) {
            ws.setContentScore(score);
            ws.setContentConfidence(confidence);
            System.out.printf("[Fusion Guard] Window %d updated with LIE score: %.4f (Conf: %.2f)%n", window, score, confidence);
        }
        // 2. אם החלון נקי (הציון הקיים הוא 0 וגם החדש הוא 0) - נעדכן רק אם הביטחון של האמת גבוה יותר
        else if (score == 0.0 && ws.getContentScore() == 0.0) {
            if (confidence > ws.getContentConfidence()) {
                ws.setContentConfidence(confidence);
            }
        }
        // כל ניסיון אחר (כמו להכניס ציון אמת 0.0 לחלון שכבר יש בו שקר 0.93) - נחסם אוטומטית!
    }

    public void setAudioScore(int window, double score, double confidence) {
        double windowStart = window;
        double windowEnd = windowStart + 3.0;

        if (windowEnd <= 5.0) {
            return;
        }
        WindowScores ws = windows.computeIfAbsent(window, k -> {
            WindowScores w = new WindowScores();
            w.setWindowIndex(window);
            w.setAudioScore(0.0);
            w.setAudioConfidence(0.0);
            return w;
        });

        if (score > ws.getAudioScore()) {
            ws.setAudioScore(score);
            ws.setAudioConfidence(confidence);
        } else if (score == 0.0 && ws.getAudioScore() == 0.0) {
            if (confidence > ws.getAudioConfidence()) {
                ws.setAudioConfidence(confidence);
            }
        }
    }

    public void setBodyScore(int window, double score, double confidence) {
        double windowStart = window;
        double windowEnd = windowStart + 3.0;

        if (windowEnd <= 5.0) {
            return;
        }
        WindowScores ws = windows.computeIfAbsent(window, k -> {
            WindowScores w = new WindowScores();
            w.setWindowIndex(window);
            w.setBodyScore(0.0);
            w.setBodyConfidence(0.0);
            return w;
        });

        if (score > ws.getBodyScore()) {
            ws.setBodyScore(score);
            ws.setBodyConfidence(confidence);
        } else if (score == 0.0 && ws.getBodyScore() == 0.0) {
            if (confidence > ws.getBodyConfidence()) {
                ws.setBodyConfidence(confidence);
            }
        }
    }

    public double getFinalScore(int window) {
        WindowScores ws = windows.get(window);
        if (ws == null) return 0.0;

        double dynamicWContent = baseWContent * ws.getContentConfidence();
        double dynamicWAudio = baseWAudio * ws.getAudioConfidence();
        double dynamicWBody = baseWBody * ws.getBodyConfidence();

        double totalWeight = dynamicWContent + dynamicWAudio + dynamicWBody;

        if (totalWeight == 0) {
            return baseWContent * ws.getContentScore()
                    + baseWAudio * ws.getAudioScore()
                    + baseWBody * ws.getBodyScore();
        }

        double normWContent = dynamicWContent / totalWeight;
        double normWAudio = dynamicWAudio / totalWeight;
        double normWBody = dynamicWBody / totalWeight;

        return normWContent * ws.getContentScore()
                + normWAudio * ws.getAudioScore()
                + normWBody * ws.getBodyScore();
    }

    public boolean isLie(int window) {
        return getFinalScore(window) > 0.5;
    }

    public Map<Integer, Double> getAllFinalScores() {
        Map<Integer, Double> result = new HashMap<>();
        for (Integer window : windows.keySet()) {
            result.put(window, getFinalScore(window));
        }
        return result;
    }

    public String[] getFinalIndependentSequence() {
        int maxWindow = windows.keySet().stream().max(Integer::compareTo).orElse(-1);
        if (maxWindow == -1) return new String[0];

        int totalWindows = maxWindow + 1;
        String[] finalSequence = new String[totalWindows];

        for (int t = 0; t < totalWindows; t++) {
            WindowScores ws = windows.get(t);

            if (ws == null) {
                finalSequence[t] = "אין נתונים";
                continue;
            }

            double combinedScore = getFinalScore(t);

            if (combinedScore > 0.5) {
                finalSequence[t] = "שקר";
            } else {
                finalSequence[t] = "אמת";
            }
        }

        return finalSequence;
    }

    public Map<Integer, WindowScores> getTimelineData() {
        return this.windows;
    }


    public void setContentScoreSpreadOverTime(
            double startTimeInSeconds,
            double endTimeInSeconds,
            double score,
            double confidence) {

        double WINDOW_SIZE = 3.0;
        double STRIDE = 1.0;

        int firstWindow = (int) Math.ceil((startTimeInSeconds - WINDOW_SIZE) / STRIDE);
        firstWindow = Math.max(0, firstWindow);

        int lastWindow = (int) Math.floor(endTimeInSeconds / STRIDE);

        if (score > 0.5) {
            lastWindow++;
        }

        System.out.printf(
                "[NLP Alignment] Spreading text score %.4f from Window %d to Window %d%n",
                score, firstWindow, lastWindow);

        for (int w = firstWindow; w <= lastWindow; w++) {
            setContentScore(w, score, confidence);
        }
    }

    public synchronized void contentFinished() {
        contentFinished = true;
        tryFinishAnalysis();
    }

    public synchronized void audioFinished() {
        audioFinished = true;
        tryFinishAnalysis();
    }

    public synchronized void bodyFinished() {
        bodyFinished = true;
        tryFinishAnalysis();
    }
    private void tryFinishAnalysis() {

        if (!(contentFinished && audioFinished && bodyFinished)) {
            return;
        }

        System.out.println("All channels finished.");

        saveAnalysis();
    }
    private void saveAnalysis() {

        System.out.println("Saving analysis for video: " + currentVideoId);

        System.out.println("Existing videos:");

        videosService.findAll().forEach(v ->
                System.out.println(v.getId())
        );

        Videos video = videosService.findById(currentVideoId)
                .orElseThrow();

        for (Map.Entry<Integer, WindowScores> entry : windows.entrySet()) {

            WindowScores ws = entry.getValue();
            double windowEnd = ws.getWindowIndex() + 3.0;

            if (windowEnd <= 5.0) {

                ws.setContentScore(0);
                ws.setAudioScore(0);
                ws.setBodyScore(0);

                ws.setContentConfidence(1);
                ws.setAudioConfidence(1);
                ws.setBodyConfidence(1);

                ws.setFinalScore(0);

                ws.getSuspicionReasons().clear();

                continue;
            }

            ws.setFinalScore(getFinalScore(entry.getKey()));
        }

        video.setTimeline(new ArrayList<>(windows.values()));
        video.setAnalysisFinished(true);

        videosService.addVideo(video);

        windows.clear();

        bodyFinished = false;
        audioFinished = false;
        contentFinished = false;

        System.out.println("Analysis saved.");
    }

    public void addSuspicionReason(int window, String reason) {
        double windowEnd = window + 3.0;

        if (windowEnd <= 5.0) {
            return;
        }
        System.out.println("Adding reason to window " + window + ": " + reason);
        WindowScores ws = windows.computeIfAbsent(window, k -> {
            WindowScores w = new WindowScores();
            w.setWindowIndex(window);
            w.setContentScore(0.0);
            w.setAudioScore(0.0);
            w.setBodyScore(0.0);
            w.setContentConfidence(0.0);
            w.setAudioConfidence(0.0);
            w.setBodyConfidence(0.0);
            return w;
        });

        ws.addSuspicionReason(reason);
    }

    //פונקציה שתגדיר מתי היתה הסתירה
    public void addSuspicionReasonSpreadOverTime(
            double startTimeInSeconds,
            double endTimeInSeconds,
            double score,
            String reason) {

        double WINDOW_SIZE = 3.0;
        double STRIDE = 1.0;

        int firstWindow = (int) Math.ceil((startTimeInSeconds - WINDOW_SIZE) / STRIDE);
        firstWindow = Math.max(0, firstWindow);

        int lastWindow = (int) Math.floor(endTimeInSeconds / STRIDE);

        if (score > 0.5) {
            lastWindow++;
        }

        for (int w = firstWindow; w <= lastWindow; w++) {
            addSuspicionReason(w, reason);
        }
    }
//מחשבת את החלונות הזמן של אודיו
    public void setAudioScoreSpreadOverTime(
            double startTimeInSeconds,
            double endTimeInSeconds,
            double score,
            double confidence) {

        double WINDOW_SIZE = 3.0;
        double STRIDE = 1.0;

        int firstWindow = (int) Math.ceil((startTimeInSeconds - WINDOW_SIZE) / STRIDE);
        firstWindow = Math.max(0, firstWindow);

        int lastWindow = (int) Math.floor(endTimeInSeconds / STRIDE);

        for (int w = firstWindow; w <= lastWindow; w++) {
            setAudioScore(w, score, confidence);
        }
    }


    //לחישוב חלונות זמן של שפת הגוף
    public void setBodyScoreSpreadOverTime(
            double startTimeInSeconds,
            double endTimeInSeconds,
            double score,
            double confidence) {

        double WINDOW_SIZE = 3.0;
        double STRIDE = 1.0;

        int firstWindow = (int) Math.ceil((startTimeInSeconds - WINDOW_SIZE) / STRIDE);
        firstWindow = Math.max(0, firstWindow);

        int lastWindow = (int) Math.floor(endTimeInSeconds / STRIDE);

        for (int w = firstWindow; w <= lastWindow; w++) {
            setBodyScore(w, score, confidence);
        }
    }



}