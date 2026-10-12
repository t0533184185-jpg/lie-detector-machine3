package com.example.demo.model;

import lombok.AllArgsConstructor;
import java.time.LocalDateTime;
// האימפורטים החדשים של ה-Data של Spring עבור NoSQL
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@AllArgsConstructor
@Document(collection = "lie_detection_results") // מגדיר את האוסף ב-MongoDB
public class LieDetectionResult {

    @Id // האנוטציה מגיעה מ-org.springframework.data.annotation.Id
    private String id; // שינוי ל-String עבור מפתח אוטומטי של MongoDB

    private int frameNumber;

    private String gestureName; // שם התנועה (למשל: "Shoulder Shrug")

    private LocalDateTime detectedAt;
    private String videoName;
    // קונסטרקטורים
    public LieDetectionResult() {}

    public LieDetectionResult(String videoName, int frameNumber, String gestureName) {
        this.videoName = videoName;
        this.frameNumber = frameNumber;
        this.gestureName = gestureName;
        this.detectedAt = LocalDateTime.now();
    }

    // Getters & Setters
    public String getId() { return id; } // עודכן ל-String
    public void setId(String id) { this.id = id; } // כדאי להוסיף Setter ל-id

    public int getFrameNumber() { return frameNumber; }
    public void setFrameNumber(int frameNumber) { this.frameNumber = frameNumber; }

    public String getGestureName() { return gestureName; }
    public void setGestureName(String gestureName) { this.gestureName = gestureName; }

    public LocalDateTime getDetectedAt() { return detectedAt; }
    public void setDetectedAt(LocalDateTime detectedAt) { this.detectedAt = detectedAt; }
    public String getVideoName() {
        return videoName;
    }

    public void setVideoName(String videoName) {
        this.videoName = videoName;
    }
}