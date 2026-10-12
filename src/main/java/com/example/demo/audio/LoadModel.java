package com.example.demo.audio;

import com.example.demo.service.FusionService;
import org.deeplearning4j.nn.modelimport.keras.KerasModelImport;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpClient;
@Service
public class LoadModel {
    private final FusionService fusionService;
    private final LieDetectorPipeline lieDetectorPipeline;

    public LoadModel(FusionService fusionService, LieDetectorPipeline lieDetectorPipeline) {
        this.fusionService = fusionService;
        this.lieDetectorPipeline = lieDetectorPipeline;
    }

    @Async
    public void analyzeAudio(String videoId, String audioPath) throws Exception {
        lieDetectorPipeline.analyze(videoId, audioPath);
        fusionService.audioFinished();
    }

    public static void sendAudioResultToFusion(String videoId, double startTime,double endTime, double score, double confidence) {
        try {
            HttpClient client = HttpClient.newHttpClient();

            // בניית ה-URL עם הפרמטרים שחושבו בחלון הזמן
            String url = String.format("http://localhost:8080/api/fusion/audio?windowIndex=%d&score=%.2f&confidence=%.2f",
                    startTime, endTime, score, confidence);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .POST(HttpRequest.BodyPublishers.noBody()) // הבקשה שולחת פרמטרים ב-URL ולכן ה-body ריק
                    .build();

            // שליחת הבקשה בצורה סינכרונית לשרת ה-Spring Boot
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            System.out.println("[Audio Sender] Server Response: " + response.body());
        } catch (Exception e) {
            System.err.println("[Audio Sender] Failed to send data to Fusion server: " + e.getMessage());

        }
    }

}