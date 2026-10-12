package com.example.demo.model;

import java.util.Map;

/**
 * Record המייצג את המידע המפורק שהתקבל מ-Spring AI.
 * שדות אלו משקפים בדיוק את המבנה שמודל השפה (LLM) יחזיר,
 * והם תואמים בדיוק למה שה-ConversationManagerService מצפה לקבל.
 */
public record ExtractedFact(

        String subjectName,      // השם הטבעי של הנושא בעברית (למשל: "מכונית", "דני")
        String subjectUniqueId,  // מזהה ייחודי קונטקסטואלי באנגלית (למשל: "car_vehicle", "dani_boss")
        String action,           // הפועל או מהות הקשר (למשל: "נמצא ב", "אוהב")
        String objectName,       // השם הטבעי של המושא בעברית (למשל: "תל אביב", "רוני")
        String objectUniqueId,   // מזהה ייחודי קונטקסטואלי באנגלית עבור המושא (למשל: "tel_aviv_city")
        boolean negationFlag,    // האם קיים סימן שלילה במשפט (true = "לא נמצא ב", false = "נמצא ב")
        Map<String, String>contexts, //מפה של הקשר והערך שלו
        String naturalSentenceEn,
        String naturalSentenceHe
) {}