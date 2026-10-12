package com.example.demo.API;

import com.example.demo.model.ContradictionResult;
import com.example.demo.service.ContradictionService;
import com.example.demo.service.PythonConnector;
import com.example.demo.service.SpeechToTextService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;
import java.util.Map;

@RestController
public class ContradictionTestController {

    private final ContradictionService contradictionService;
    private final PythonConnector pythonConnector;
    // 1. הוספת המשתנה עבור שירות ה-SpeechToText המנוהל של Spring
    private final SpeechToTextService speechToTextService;

    // 2. עדכון ה-Constructor שיזריק גם אותו אוטומטית לתוך הקלאס
    public ContradictionTestController(
                                       ContradictionService contradictionService,
                                       PythonConnector pythonConnector,
                                       SpeechToTextService speechToTextService) {
        this.contradictionService = contradictionService;
        this.pythonConnector = pythonConnector;
        this.speechToTextService = speechToTextService;
    }

//    /**
//     * נקודת קצה המכינה את הגרף לניתוח סרטון חדש (מנקה את המאגר)
//     */
//    @GetMapping("/test/build-graph")
//    public String buildGraph() {
//        graphTestService.prepareGraphForNewVideo();
//        return "✅ Neo4j Graph cleared and prepared for the video content!";
//    }

//    /**
//     * מציג את כל הסתירות הישירות שנמצאו בבסיס הנתונים הנוכחי
//     */
//    @GetMapping("/test/direct-only")
//    public List<ContradictionResult> testDirectOnly() {
//        return contradictionService.findDirectContradictions();
//    }
//
//    /**
//     * מריץ את הבדיקה המלאה של מנוע הסתירות על הגרף הקיים
//     */
//    @GetMapping("/test/full-analysis")
//    public Map<String, Object> testFullAnalysis() {
//        return contradictionService.runFullAnalysis();
//    }

//    /**
//     * טסט משופר: מריץ את עיבוד הוידאו והפריימים המלא מקצה לקצה
//     */
//    @GetMapping("/test/process-video")
//    public String testVideo() {
//        // הגדרת הנתיב המלא לקובץ ה-WAV שמצאת
//        String audioPath = "C:\\Users\\user\\my_uploads\\6a193adddf0f3d13d3d3506a\\WIN_20260529_11_07_58_Pro.mp4_frames_1780042106395\\original_audio.wav";
//
//        System.out.println("Executing test speech-to-text on: " + audioPath);
//
//        // 3. התיקון: שימוש במופע המוזרק (אות קטנה בהתחלה) במקום קריאה סטטית
//        String transcript = speechToTextService.transcribe(audioPath);
//        System.out.println("Transcript Result: " + transcript);
//
//        // במידה ואת רוצה לבדוק "קשיח" את משפט הטבעונות בלי תלות במה שבאמת נאמר בהקלטה, שחררי את השורה הבאה:
//        // transcript = "אני טבעונית ואתמול אכלתי בשר בקר";
//
//        // 4. שליחת הטקסט לשרת הפייתון בפורט 5050
//// ✅ השורה המתוקנת שמשתמשת במתודה הקיימת בקונקטור שלך:
//        String pythonResponse = pythonConnector.sendToPython("NLP:" + transcript);        System.out.println("Python Server Response: " + pythonResponse);
//
//        return "STT Result: [" + transcript + "] | Python Response: [" + pythonResponse + "]";
//    }
}