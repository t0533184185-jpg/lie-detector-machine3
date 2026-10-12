package com.example.demo.API;

import com.example.demo.audio.LoadModel;
import com.example.demo.model.TranscribedSentence;
import com.example.demo.service.FusionService;
import com.example.demo.service.PythonConnector;
import com.example.demo.service.SpeechToTextService;
import com.example.demo.model.Videos;
import com.example.demo.service.VideosService;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;
import org.bytedeco.javacv.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.springframework.web.bind.annotation.CrossOrigin;

@RestController
@RequestMapping("/api/videos")
@CrossOrigin(origins = "http://localhost:3000")
public class VideoController {

    private final SpeechToTextService speechToTextService;
    private final VideosService videosService;
    private final PythonConnector pythonConnector;
    private final LoadModel loadModel;
    private final FusionService fusionService;
    // 💡 הזרקה מסודרת של כל השירותים דרך Constructor אחד
    public VideoController(SpeechToTextService speechToTextService,
                           VideosService videosService,
                           PythonConnector pythonConnector,
                           LoadModel loadModel, FusionService fusionService) {            // ✅
        this.speechToTextService = speechToTextService;
        this.videosService = videosService;
        this.pythonConnector = pythonConnector;
        this.loadModel = loadModel;
        this.fusionService = fusionService;
    }

    // 💡 חילוץ פריימים חכם - מדלג על פריימים כדי לא להעמיס על הדיסק
    private void extractFrames(String videoPath, String imageOutputPath) throws Exception {
        try (FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(videoPath)) {
            grabber.start();
            Java2DFrameConverter converter = new Java2DFrameConverter();

            Frame frame;
            int frameNumber = 0;

            while ((frame = grabber.grabImage()) != null) {

                BufferedImage bi = converter.convert(frame);

                if (bi != null) {
                    String fileName = "frame_" + frameNumber + ".png";
                    File outputFile = new File(imageOutputPath, fileName);
                    ImageIO.write(bi, "png", outputFile);
                }

                frameNumber++;
            }

            grabber.stop();
        }
    }

    @GetMapping("/user/{personId}")
    public List<Videos> getVideosByPerson(@PathVariable String personId) {
        return videosService.findByPersonId(personId);
    }

    //    @PostMapping("/add/{userId}")
//    public ResponseEntity<?> add(@RequestParam("file") MultipartFile file, @PathVariable String userId) {
//        try {
//            // יצירת התיקייה בשרת
//            String path = System.getProperty("user.home") + "/my_uploads/" + userId + "/";
//            java.io.File uploadDir = new java.io.File(path);
//
//            if (!uploadDir.exists()) {
//                uploadDir.mkdirs();
//            }
//            System.out.println("Uploading to: " + uploadDir.getAbsolutePath());
//
//            // שמירת קובץ הוידאו
//            String fileName = file.getOriginalFilename();
//            java.io.File destFile = new java.io.File(uploadDir, fileName);
//            file.transferTo(destFile);
//
//            // יצירת תיקייה נפרדת לפריימים
//            String framesFolderName = fileName + "_frames_" + System.currentTimeMillis();
//            java.io.File framesDir = new java.io.File(uploadDir, framesFolderName);
//            framesDir.mkdirs();
//
//            // חילוץ תמונות ואודיו
//            extractFrames(destFile.getAbsolutePath(), framesDir.getAbsolutePath());
//            String audioPath = framesDir.getAbsolutePath() + "/original_audio.wav";
//            extractAudio(destFile.getAbsolutePath(), audioPath);
//
//            // תמלול האודיו
//            List<TranscribedSentence> transcriptResult = speechToTextService.transcribe(audioPath);
//            System.out.println("📝 [DEBUG] Transcript received.");
//
//            // חיבור כל חלקי הטקסט מהתמלול למחרוזת אחת רציפה עבור הפייתון והגרף
//            String combinedText = "";
//            if (transcriptResult != null && !transcriptResult.isEmpty()) {
//                combinedText = transcriptResult.stream()
//                        .map(TranscribedSentence::getText)
//                        .collect(Collectors.joining(". "));
//            }
//
//            String pythonResponse = "";
//            // שולחים לפייתון לבדיקת סתירות פנימיות רק אם חולץ טקסט
//            if (!combinedText.isEmpty()) {
//                try (java.net.Socket socket = new java.net.Socket("localhost", 5050);
//                     java.io.PrintWriter out = new java.io.PrintWriter(socket.getOutputStream(), true);
//                     java.io.BufferedReader in = new java.io.BufferedReader(new java.io.InputStreamReader(socket.getInputStream()))) {
//
//                    out.println("NLP:|" + combinedText);
//                    pythonResponse = in.readLine();
//                    System.out.println("🤖 [NLP Response from Python]: " + pythonResponse);
//
//                } catch (Exception e) {
//                    System.out.println("❌ [DEBUG] Failed to connect to Python NLP server: " + e.getMessage());
//                }
//            }
//
//            // 💡 שמירה ל-Database - מתוקן ל-String כדי לפתור את השגיאה בשורה 128
//            Videos newVideo = new Videos();
//            newVideo.setName(file.getOriginalFilename());
//            newVideo.setPersonId(userId);
//            newVideo.setTranscript(combinedText); // שמירת ה-String הנקי
//            videosService.addVideo(newVideo);
//
//            // בניית ה-Response חזרה לסינכרון ה-Front
//            Map<String, Object> responseBody = new HashMap<>();
//            responseBody.put("message", "File uploaded and processed successfully");
//            responseBody.put("transcript", combinedText);
//
//            if (pythonResponse != null && pythonResponse.contains("CONTRADICTION_FOUND")) {
//                responseBody.put("hasContradiction", true);
//                responseBody.put("contradictionDetails", pythonResponse);
//            } else {
//                responseBody.put("hasContradiction", false);
//            }
//
//            return ResponseEntity.ok(responseBody);
//
//        } catch (Exception e) {
//            e.printStackTrace();
//            return ResponseEntity.status(500).body("Error: " + e.getMessage());
//        }
//    }
    @PostMapping("/add/{userId}")
    public ResponseEntity<?> add(@RequestParam("file") MultipartFile file, @PathVariable String userId) {
        try {
            String path = System.getProperty("user.home") + "/my_uploads/" + userId + "/";
            java.io.File uploadDir = new java.io.File(path);
            if (!uploadDir.exists()) uploadDir.mkdirs();

            String fileName = file.getOriginalFilename();
            java.io.File destFile = new java.io.File(uploadDir, fileName);
            System.out.println("Destination: " + destFile.getAbsolutePath());
            System.out.println("Exists upload dir: " + uploadDir.exists());
            System.out.println("Can write: " + uploadDir.canWrite());
            System.out.println("File size: " + file.getSize());
            System.out.println("Original name: " + file.getOriginalFilename());
            file.transferTo(destFile);

            String framesFolderName = fileName + "_frames_" + System.currentTimeMillis();
            java.io.File framesDir = new java.io.File(uploadDir, framesFolderName);
            framesDir.mkdirs();

            extractFrames(destFile.getAbsolutePath(), framesDir.getAbsolutePath());
            String audioPath = framesDir.getAbsolutePath() + "/original_audio.wav";
            extractAudio(destFile.getAbsolutePath(), audioPath);

            String folderPath = framesDir.getAbsolutePath();
            // תמלול
            List<TranscribedSentence> transcriptResult = speechToTextService.transcribe(audioPath);
            String combinedText = "";
            if (transcriptResult != null && !transcriptResult.isEmpty()) {
                combinedText = transcriptResult.stream()
                        .map(TranscribedSentence::getText)
                        .collect(Collectors.joining(". "));
            }

            // NLP
            String pythonResponse = "";
            if (!combinedText.isEmpty()) {
                try (java.net.Socket socket = new java.net.Socket("localhost", 5050);
                     java.io.PrintWriter out = new java.io.PrintWriter(socket.getOutputStream(), true);
                     java.io.BufferedReader in = new java.io.BufferedReader(
                             new java.io.InputStreamReader(socket.getInputStream()))) {
                    out.println("NLP:|" + combinedText);
                    pythonResponse = in.readLine();
                } catch (Exception e) {
                    System.out.println("Failed to connect to Python NLP: " + e.getMessage());
                }
            }

            // שמירה ל-DB
            Videos newVideo = new Videos();
            newVideo.setName(fileName);
            newVideo.setPersonId(userId);
            newVideo.setTranscript(combinedText);
            Videos savedVideo = videosService.addVideo(newVideo);
            fusionService.setCurrentVideoId(savedVideo.getId());

            // חישוב מספר הפריימים וה-FPS
            FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(destFile);
            grabber.start();

            double fps = grabber.getFrameRate();
            int totalFrames = grabber.getLengthInFrames();

            grabber.stop();

            pythonConnector.setFps(fps);
            pythonConnector.processVideo(folderPath, totalFrames);
            loadModel.analyzeAudio(savedVideo.getId(),audioPath);
            Map<String, Object> response = new HashMap<>();
            response.put("id", savedVideo.getId());

//        int estimatedFrames = 1000; // או תחשב לפי אורך הסרטון
//
//        Thread analysisThread = new Thread(() -> {
//
//                try {
//                    Thread pcThread = new Thread(() -> {
//                        // מריץ את שניהם במקביל
//                try {
//                        pythonConnector.processVideo(folderPath, totalFrames);
//                    } catch (Exception e) {
//
//                        System.err.println("PythonConnector error: " + e.getMessage());
//                    }
//                });
//
//                Thread lmThread = new Thread(() -> {
//
//                 try {
//                        loadModel.analyzeAudio(folderPath + "/original_audio.wav");
//                    } catch (Exception e) {
//                        System.err.println("LoadModel error: " + e.getMessage());
//                    }
//                });
//
//                pcThread.start();
//                lmThread.start();
//                pcThread.join();
//                lmThread.join();
//
//            } catch (Exception e) {
//                System.err.println("Analysis thread error: " + e.getMessage());
//            }
//        });
//        analysisThread.setDaemon(true);
//        analysisThread.start(); // ✅ רץ ברקע, לא חוסם את ה-Response

            // Response מיידי לפרונט
            Map<String, Object> responseBody = new HashMap<>();
            responseBody.put("message", "File uploaded and processed successfully");
            responseBody.put("transcript", combinedText);
            responseBody.put("hasContradiction",
                    pythonResponse != null && pythonResponse.contains("CONTRADICTION_FOUND"));
            if (pythonResponse != null && pythonResponse.contains("CONTRADICTION_FOUND")) {
                responseBody.put("contradictionDetails", pythonResponse);
            }
            System.out.println(System.getProperty("user.home"));
            responseBody.put("id", savedVideo.getId());
            return ResponseEntity.ok(responseBody);

        } catch (Exception e) {
            e.printStackTrace();
            System.out.println(System.getProperty("user.home"));
            return ResponseEntity.status(500).body(e.getMessage());
        }

    }

    //פונקציה לחילוץ אודיו
    private void extractAudio(String videoPath, String audioOutputPath) throws Exception {
        //קריאת השמע מתוך קובץ הווידאו
        try (FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(videoPath)) {
            grabber.start();//פתיחת קובץ הווידאו והכנה לקריאה
            //יצירת קובץ WAV חדש
            try (FFmpegFrameRecorder recorder = new FFmpegFrameRecorder(audioOutputPath, 1)) {
                //המהרה של השמע לפורמט WAV בקידוד PCM 16-bit
                recorder.setFormat("wav");
                recorder.setAudioCodec(org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_PCM_S16LE);
                recorder.setSampleRate(16000);
                recorder.start();

                Frame frame;//משתנה לשמירת דגימת השמע שנקראה
                //קריאת דגימות השמע מהסרטון וכתיבה לקןבץ WAV חדש
                while ((frame = grabber.grabSamples()) != null) {
                    recorder.record(frame);
                }
                recorder.stop();
            }
            grabber.stop();
        }
    }

    @PostMapping("/delete/{id}")
    public String deleteVideo(@PathVariable String id) {
        try {
            videosService.deleteVideos(id);
            return "וידאו נמחק בהצלחה";
        } catch (RuntimeException e) {
            return "שגיאה במחיקת הוידאו";
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<Videos> getVideoById(@PathVariable String id) {
        return videosService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
    @GetMapping("/{id}/timeline")
    public ResponseEntity<?> getTimeline(@PathVariable String id) {
        System.out.println("Timeline requested for id = " + id);

        Videos video = videosService.findById(id)
                .orElseThrow(() -> new RuntimeException("Video not found"));
        Map<String,Object> response = new HashMap<>();

        response.put("finished", video.isAnalysisFinished());
        response.put("timeline", video.getTimeline());

        return ResponseEntity.ok(response);
    }

}