package com.example.demo.service;

import com.example.demo.model.TranscribedSentence;
import org.springframework.stereotype.Service;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class SpeechToTextService {

    private final String whisperCliPath = "C:/whisper/Release/whisper-cli.exe"; // נתיב לקובץ ההפעלה
    private final String modelPath = "C:/whisper/Release/ggml-medium.bin";    // נתיב למודל

    public List<TranscribedSentence> transcribe(String videoFilePath) {
        List<TranscribedSentence> sentencesList = new ArrayList<>();

        // בדיקת תקינות קובץ
        java.io.File file = new java.io.File(videoFilePath);
        if (!file.exists() || file.length() == 0) {
            System.out.println(" קובץ האודיו לא קיים או ריק: " + videoFilePath);
            return sentencesList;
        }

        try {
            // בניית פקודת ההרצה
            List<String> command = new ArrayList<>();
            command.add(whisperCliPath);
            command.add("-m");
            command.add(modelPath);
            command.add("-l");
            command.add("he");
            command.add("-f");
            command.add(videoFilePath);

            System.out.println("DEBUG: Executing command: " + String.join(" ", command));

            ProcessBuilder processBuilder = new ProcessBuilder(command);
            processBuilder.redirectErrorStream(true);
            Process process = processBuilder.start();

            // הגדרת ה-Regex שמפרק שורת תמלול של Whisper לזמנים וטקסט נקי
            Pattern pattern = Pattern.compile("\\[(\\d{2}:\\d{2}:\\d{2}\\.\\d{3})\\s*-->\\s*(\\d{2}:\\d{2}:\\d{2}\\.\\d{3})\\]\\s*(.*)");

            // קריאת הפלט בלולאה אחת
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), "UTF-8"))) {
                String whisperLine;

                while ((whisperLine = reader.readLine()) != null) {
                    // 1. מדפיס את הלוג הגולמי למסך לצורכי דיבאג (כמו שהיה לך מקודם)
                    System.out.println("Whisper Engine: " + whisperLine);

                    // 2. מנסה להתאים את השורה הנוכחית לפורמט של תמלול עם זמנים
                    Matcher matcher = pattern.matcher(whisperLine);

                    if (matcher.find()) {
                        String startTime = matcher.group(1); // יחלץ למשל: "00:00:00.000"
                        String endTime = matcher.group(2);   // יחלץ למשל: "00:00:05.680"
                        String cleanText = matcher.group(3).trim(); // הטקסט הנקי ללא תגיות הזמן

                        if (!cleanText.isEmpty()) {
                            // מוסיף לרשימה שמוחזרת בסוף המתודה
                            sentencesList.add(new TranscribedSentence(cleanText, startTime, endTime));
                        }
                    }
                }
            }

            // המחרה לסיום התהליך במערכת ההפעלה
            process.waitFor();
            return sentencesList;

        } catch (Exception e) {
            System.err.println(" שגיאה במהלך התמלול ב-SpeechToTextService:");
            e.printStackTrace();
            return sentencesList;
        }
    }
}