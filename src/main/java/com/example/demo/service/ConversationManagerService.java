package com.example.demo.service;

import com.example.demo.model.*;
import com.example.demo.repository.neo4j.EntityRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ConversationManagerService {

    private final SpeechToTextService speechToTextService;
    private final PythonConnector pythonConnector;
    private final EntityRepository entityRepository;
    private final ContradictionService contradictionService;
    private final FactExtractionService factExtractionService;
    private final GraphPersistenceService graphPersistenceService;
    private final FusionService fusionService;
    private final ObjectMapper objectMapper;
    private final EntityResolutionService entityResolutionService;

    @Value("${nlp.contradiction.threshold:0.7}")
    private double contradictionThreshold;

    public ConversationManagerService(SpeechToTextService speechToTextService,
                                      PythonConnector pythonConnector,
                                      EntityRepository entityRepository,
                                      ContradictionService contradictionService,
                                      FactExtractionService factExtractionService,
                                      GraphPersistenceService graphPersistenceService,
                                      FusionService fusionService,
                                      ObjectMapper objectMapper,
                                      EntityResolutionService entityResolutionService) {
        this.speechToTextService = speechToTextService;
        this.pythonConnector = pythonConnector;
        this.entityRepository = entityRepository;
        this.contradictionService = contradictionService;
        this.factExtractionService = factExtractionService;
        this.graphPersistenceService = graphPersistenceService;
        this.fusionService = fusionService;
        this.objectMapper = objectMapper;
        this.entityResolutionService = entityResolutionService;
    }

    @Async
    public void processAudioSegment(String videoFilePath, String speaker) {
        System.out.println(" מתחיל עיבוד לקובץ: " + videoFilePath);
        System.out.println(" [קונפיגורציה] עובד עם רף סתירה (Threshold) דינמי: " + contradictionThreshold);

        List<TranscribedSentence> transcribedSentences = speechToTextService.transcribe(videoFilePath);
        Map<Integer, List<NlpResult>> nlpWindowResults = new HashMap<>();

        if (transcribedSentences == null || transcribedSentences.isEmpty()) {
            return;
        }

        for (TranscribedSentence segment : transcribedSentences) {
            String newSentence = segment.getText();
            String startTime = segment.getStartTime();
            String endTime = segment.getEndTime();
            double startSec = timeToSeconds(startTime);
            double endSec = timeToSeconds(endTime);

//            double middleTime = (startSec + endSec) / 2.0;
//            int windowIndex = (int)(middleTime / 3);

            System.out.println("\n מעבד משפט: [" + newSentence + "] בזמן: [" + startTime + " --> " + endTime + "]");

            List<ExtractedFact> currentFacts = factExtractionService.extractFactsFromSentence(newSentence);

            double maxSentenceContradictionScore = 0.0;
            double maxSentenceConfidence = 0.75;

            for (ExtractedFact fact : currentFacts) {
                if (fact.objectName() == null || fact.objectName().isBlank()) {
                    fact = new ExtractedFact(
                            fact.subjectName(),
                            fact.subjectUniqueId(),
                            "IS_A",
                            fact.action(),
                            entityResolutionService.resolveUniqueId(fact.action()),
                            fact.negationFlag(),
                            fact.contexts(),
                            fact.naturalSentenceEn(),
                            fact.naturalSentenceHe()

                    );
                }
                if ("extracted_verb_or_state".equals(fact.action()) || "object_id_or_null".equals(fact.objectUniqueId())) {
                    System.out.println("⚠️ [Ollama Warning] ה-LLM החזיר ערכי תבנית ריקים (Placeholder). מדלג על העובדה הנוכחית.");
                    continue;
                }

                String subject = fact.subjectUniqueId();
                String action = fact.action();
                String object = fact.objectName();
                Map<String, String> newContexts = fact.contexts();

                //חילוץ של כל ההקשרים עם אותו נושא ומושא
                List<EntityRelationship> existingRelations = contradictionService.findRelationshipsForSubjectAndAction(subject, action);
                boolean directContradictionFound = false;
                EntityRelationship conflictingRelation = null;

                if (existingRelations != null) {
                    //העברה על כל העובדות הקודמות
                    for (EntityRelationship existing : existingRelations) {
                        Map<String, String> oldContexts = existing.getContexts();
                        boolean hasOverlapInContextTypes = false;
                        boolean contextConflict = false;

                        //בדיקת התנגשות בין ההקשרים
                        for (String contextType : newContexts.keySet()) {
                            if (oldContexts.containsKey(contextType)) {
                                hasOverlapInContextTypes = true;
                                if (!newContexts.get(contextType).equals(oldContexts.get(contextType))) {
                                    contextConflict = true;
                                }
                            }
                        }

                        //בדיקה סופית אם מצאנו שקר
                        if (hasOverlapInContextTypes) {
                            //צריך להוסיף אם יש גם שלילה על אותו
                            if (!contextConflict && !existing.getTargetNode().getName().equals(object)) {
                                directContradictionFound = true;
                                conflictingRelation = existing;
                                System.out.println(" [ALERT - סתירה ישירה בגרף] נמצאה התנגשות תחת אותו הקשר משותף!");
                                break;
                            }
                        }
                    }
                }

                double factContradictionScore = 0.0;
                double factConfidence = 0.75;
                String contradictionReason = "";
                String oldFact = "";
                String newFactText = "";

                if (directContradictionFound) {
                    factContradictionScore = 1.0;
                    factConfidence = 1.0;


                    contradictionReason = "סתירה ישירה מול ידע קודם";

                    oldFact = conflictingRelation.getNaturalSentenceHe();
                    newFactText = fact.naturalSentenceHe();

//                    fusionService.addSuspicionReason(
//                            windowIndex,
//                            "סתירה: \"" + subject + " " + action + " " + object + "\""
//                    );

                    System.out.println(" [חסימת עובדה]: סתירה מוחלטת בהקשרים תואמים ב-Java.");
                } else {
                    System.out.println(" [בדיקת סתירות] - מכין נתונים עבור פייתון...");

                    List<Map<String, Object>> historyFacts = graphPersistenceService.getAllFactsFromGraph();

                    Map<String, Object> payload = new HashMap<>();
                    payload.put("history", historyFacts);
                    payload.put("newFact", fact);
                    payload.put("sentenceFacts", currentFacts);

                    try {

                        String payloadJson = objectMapper.writeValueAsString(payload);
                        String combinedMessage = "NLP:" + payloadJson;
                        System.out.println(fact);
                        System.out.println(">>> Sending:");
                        System.out.println(combinedMessage);
                        String pythonResponse = pythonConnector.sendToPython(combinedMessage);
                        System.out.println(" [Python Response] תשובה: " + pythonResponse);

                        if (pythonResponse != null && pythonResponse.contains("SCORE:")) {
                            String scorePart = pythonResponse.split("SCORE:")[1];
                            factContradictionScore = Double.parseDouble(scorePart.split("\\|")[0]);

                            if (pythonResponse.contains("|REASON:")) {
                                contradictionReason =
                                        pythonResponse.split("\\|REASON:")[1].split("\\|")[0];
                            }

                            if (pythonResponse.contains("|OLD:")) {
                                oldFact =
                                        pythonResponse.split("\\|OLD:")[1].split("\\|")[0];
                            }

                            if (pythonResponse.contains("|NEW:")) {
                                newFactText =
                                        pythonResponse.split("\\|NEW:")[1].trim();
                            }

                        }
                    } catch (Exception e) {
                        System.out.println(" שגיאה בתהליך מול פייתון: " + e.getMessage());
                        continue;
                    }
                }

                if (factContradictionScore > maxSentenceContradictionScore) {
                    maxSentenceContradictionScore = factContradictionScore;
                    maxSentenceConfidence = factConfidence;
                }

                String fullTimestamp = startTime + " --> " + endTime;

                if (factContradictionScore >= contradictionThreshold) {

                    String fullReason = contradictionReason;

                    if (!oldFact.isBlank()) {
                        fullReason += "\nידע קודם: " + oldFact;
                    }

                    if (!newFactText.isBlank()) {
                        fullReason += "\nהמשפט החדש: " + newFactText;
                    }

                    System.out.println("📌 Reason saved to timeline:");
                    System.out.println(fullReason);

                    fusionService.addSuspicionReasonSpreadOverTime(
                            startSec,
                            endSec,
                            factContradictionScore,
                            fullReason
                    );

                    fact.contexts().put("reliability_status", "CONTRADICTED");
                    fact.contexts().put("contradiction_score", String.valueOf(factContradictionScore));

                    graphPersistenceService.persistFact(fact, speaker, fullTimestamp);
                }else {
                    fact.contexts().put("reliability_status", "VERIFIED");
                    fact.contexts().put("contradiction_score", "0.0");
                    graphPersistenceService.persistFact(fact, speaker, fullTimestamp);
                    System.out.println(" 💾 [שמירת עובדה]: העובדה נשמרה בהצלחה כמאומתת.");
                }
            } // סוף לולאת העובדות

            NlpResult sentenceResult = new NlpResult(newSentence, maxSentenceContradictionScore, maxSentenceConfidence);
//            nlpWindowResults.computeIfAbsent(windowIndex, k -> new ArrayList<>()).add(sentenceResult);

            // הזרקה אמינה מבוססת שניות - קובעת את הציון לאורך ציר הזמן האמיתי
            fusionService.setContentScoreSpreadOverTime(startSec, endSec, maxSentenceContradictionScore, maxSentenceConfidence);
        } // סוף לולאת המשפטים


        // 📊 תהליך סיכום בלבד ללוגים - ה-fusionService.setContentScore נמחק מכאן כדי למנוע דריסת נתונים
        System.out.println("\n===== CONTENT SCORES TEMPORAL ALIGNMENT SUMMARY =====");
        for (Map.Entry<Integer, List<NlpResult>> entry : nlpWindowResults.entrySet()) {
            int midPointWindowIndex = entry.getKey();
            List<NlpResult> results = entry.getValue();

            NlpResult maxResult = results.stream()
                    .max((r1, r2) -> Double.compare(r1.getScore(), r2.getScore()))
                    .orElse(null);

            double maxScore = (maxResult != null) ? maxResult.getScore() : 0.0;
            System.out.println("Window (Mid-point Base) " + midPointWindowIndex + " -> " + maxScore);
        }

        fusionService.contentFinished();

    }

    private double timeToSeconds(String time) {
        String[] parts = time.split(":");

        double hours = Double.parseDouble(parts[0]);
        double minutes = Double.parseDouble(parts[1]);
        double seconds = Double.parseDouble(parts[2]);

        return hours * 3600 + minutes * 60 + seconds;
    }
}