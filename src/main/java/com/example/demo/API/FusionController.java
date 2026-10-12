package com.example.demo.API;

import com.example.demo.service.FusionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/fusion")
public class FusionController {

    private final FusionService fusionService;

    public FusionController(FusionService fusionService) {
        this.fusionService = fusionService;
    }

    /**
     * נקודת הקצה אליה תוכנת האודיו העצמאית שולחת את הנתונים שלה
     */
    @PostMapping("/audio")
    public ResponseEntity<String> receiveAudioScore(
            @RequestParam String videoId,            @RequestParam int windowIndex,
            @RequestParam double score,
            @RequestParam double confidence) {

        fusionService.setAudioScore(windowIndex, score, confidence);
        System.out.printf("[API] Received External Audio Data -> Window: %d, Score: %.2f, Confidence: %.2f%n",
                windowIndex, score, confidence);

        return ResponseEntity.ok("Audio scores captured successfully.");
    }

    /**
     * נקודת הקצה אליה רכיב ניתוח הראייה הממוחשבת (שפת הגוף) ישלח את הנתונים שלו
     */
    @PostMapping("/video")
    public ResponseEntity<String> receiveVideoScore(
            @RequestParam int windowIndex,
            @RequestParam double score,
            @RequestParam double confidence) {

        // ב-WindowScores שלך קראת לזה BodyScore
        fusionService.setBodyScore(windowIndex, score, confidence);
        System.out.printf("[API] Received External Video Data -> Window: %d, Score: %.2f, Confidence: %.2f%n",
                windowIndex, score, confidence);

        return ResponseEntity.ok("Video scores captured successfully.");
    }

    /**
     * שליפת ציר הזמן המאוחד והסופי עבור תצוגת המשתמש (UI)
     */
    @GetMapping("/timeline")
    public ResponseEntity<Map<Integer, Double>> getFinalTimeline() {
        Map<Integer, Double> finalTimeline = fusionService.getAllFinalScores();
        return ResponseEntity.ok(finalTimeline);
    }

    @GetMapping("/report")
    public ResponseEntity<java.util.Map<String, Object>> getFinalReport() {
        java.util.Map<String, Object> report = new java.util.HashMap<>();

        // 1. שליפת נתוני ציר הזמן מחולקים לחלונות (הנתונים הגולמיים של שלושת הערוצים לגרף)
        report.put("timeline", fusionService.getTimelineData());

        // 2. שליפת רצף הסיווגים הסופי והעצמאי (לפי הנתונים הפיזיים והביטחון של כל חלון בנפרד)
        report.put("finalSequence", fusionService.getFinalIndependentSequence());

        return ResponseEntity.ok(report);
    }
}