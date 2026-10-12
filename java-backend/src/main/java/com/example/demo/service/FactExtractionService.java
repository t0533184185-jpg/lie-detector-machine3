package com.example.demo.service;

import com.example.demo.model.ExtractedFact;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class FactExtractionService {

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;

    // רשימת הגנה למקרה שהמודל בכל זאת דוחף מילת זמן בתור נושא (Subject)
    private static final Set<String> TIME_WORDS = Set.of(
            "אתמול", "היום", "מחר", "שלשום", "השבוע", "חודש", "שנה", "עכשיו", "קודם"
    );

    public FactExtractionService(ChatClient.Builder chatClientBuilder, ObjectMapper objectMapper) {
        this.chatClient = chatClientBuilder.build();
        this.objectMapper = objectMapper;
    }

    public List<ExtractedFact> extractFactsFromSentence(String sentence) {
        String prompt = """
Task:
Analyze the following Hebrew sentence and extract ALL logical facts into a valid JSON array.

Sentence:
"%s"

==================================================
OUTPUT FORMAT
==================================================

Return ONLY a valid JSON array.
Do NOT return markdown.
Do NOT explain anything.
Do NOT return any text before or after the JSON.

Each object MUST contain EXACTLY these fields:

{
  "tense": "...",
  "subjectName": "...",
  "subjectUniqueId": "...",
  "action": "...",
  "objectName": "...",
  "objectUniqueId": "...",
  "negationFlag": false,
  "contexts": {},
  "naturalSentenceEn": "..."
}

==================================================
RULES
==================================================

1. Extract EVERY logical fact from the sentence.

2. subjectName and objectName MUST remain in Hebrew.

3. naturalSentenceEn MUST be written ONLY in fluent English.

4. naturalSentenceEn must express EXACTLY the meaning of this fact.
It must include:
- negation
- tense
- time expressions
- location if relevant

5. Never use the action identifier inside naturalSentenceEn.

GOOD:
"I ate ice cream yesterday."

BAD:
"I ATE ice cream yesterday."

6. subjectUniqueId and objectUniqueId:

- Use only lowercase English letters, digits and underscores.
- Same entity = same ID.
- If subjectName is "אני", subjectUniqueId MUST be "speaker_root".

7. Time expressions:

Words such as:
אתמול
היום
מחר
בשנה שעברה
לפני שבוע
עכשיו

MUST NEVER appear as subjectName or objectName.

They MUST appear only inside:

"contexts":{
    "time":"..."
}

8. contexts must always exist.

If there is no context:

"contexts":{}

9. action:

- Must contain exactly one relation or verb.
- Never concatenate strings.
- Never generate code.
- Never use '+'.
- Never leave action empty if a verb exists.

10. State sentences:

אני טבעונית

↓

action = "IS_A"

==================================================
EXAMPLE 1
==================================================

Input:

אני גרה בחיפה, ולפני שנה עברתי מתל אביב.

Output:

[
  {
    "tense":"present",
    "subjectName":"אני",
    "subjectUniqueId":"speaker_root",
    "action":"LIVES_IN",
    "objectName":"חיפה",
    "objectUniqueId":"haifa",
    "negationFlag":false,
    "contexts":{},
    "naturalSentenceEn":"I live in Haifa."
  },
  {
    "tense":"past",
    "subjectName":"אני",
    "subjectUniqueId":"speaker_root",
    "action":"MOVED_FROM",
    "objectName":"תל אביב",
    "objectUniqueId":"tel_aviv",
    "negationFlag":false,
    "contexts":{
        "time":"לפני שנה"
    },
    "naturalSentenceEn":"A year ago I moved from Tel Aviv."
  }
]

==================================================
EXAMPLE 2
==================================================

Input:

אני עובדת כמורה, ובשנה שעברה לימדתי מתמטיקה.

Output:

[
  {
    "tense":"present",
    "subjectName":"אני",
    "subjectUniqueId":"speaker_root",
    "action":"WORKS_AS",
    "objectName":"מורה",
    "objectUniqueId":"teacher",
    "negationFlag":false,
    "contexts":{},
    "naturalSentenceEn":"I work as a teacher."
  },
  {
    "tense":"past",
    "subjectName":"אני",
    "subjectUniqueId":"speaker_root",
    "action":"TAUGHT",
    "objectName":"מתמטיקה",
    "objectUniqueId":"mathematics",
    "negationFlag":false,
    "contexts":{
        "time":"בשנה שעברה"
    },
    "naturalSentenceEn":"Last year I taught mathematics."
  }
]

==================================================
VERY IMPORTANT
==================================================

Every fact MUST contain a non-empty naturalSentenceEn.

If naturalSentenceEn is missing, empty, or written in Hebrew, the answer is INVALID.

Return ONLY the JSON array.
""".formatted(sentence);

        try {
            String rawResponse = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();

            System.out.println("========== RAW LLM RESPONSE ==========");
            System.out.println(rawResponse);
            System.out.println("======================================");

            if (rawResponse == null || rawResponse.trim().isEmpty()) {
                return new ArrayList<>();
            }

            // 1. ניקוי ראשוני (Markdown וחיתוך הקדמות עודפות)
            String cleanJson = rawResponse.trim();
            cleanJson = cleanJson.replaceAll("```json", "").replaceAll("```", "").trim();

            int firstArrayBracket = cleanJson.indexOf('[');
            int lastArrayBracket = cleanJson.lastIndexOf(']');

            int firstObjectBracket = cleanJson.indexOf('{');
            int lastObjectBracket = cleanJson.lastIndexOf('}');

            if (firstArrayBracket >= 0 && lastArrayBracket > firstArrayBracket) {
                cleanJson = cleanJson.substring(firstArrayBracket, lastArrayBracket + 1);
            } else if (firstObjectBracket >= 0 && lastObjectBracket > firstObjectBracket) {
                cleanJson = cleanJson.substring(firstObjectBracket, lastObjectBracket + 1);
                cleanJson = "[" + cleanJson + "]";
                System.out.println("⚠️ [NLP Repair]: Autowrapped single object into a JSON array.");
            } else {
                System.err.println("❌ המודל לא החזיר מבנה JSON תקני בכלל: " + rawResponse);
                return new ArrayList<>();
            }

            System.out.println("📬 [NLP Protected Response]: " + cleanJson);

            cleanJson = cleanJson.replaceAll("\"\\s*\\+\\s*\"", "");
            // 2. חיטוי עץ ה-JSON (Tree Model Sanitization)
            JsonNode rootNode = objectMapper.readTree(cleanJson);

            if (rootNode.isArray()) {
                ArrayNode array = (ArrayNode) rootNode;

                for (int i = 0; i < array.size(); i++) {
                    JsonNode node = array.get(i);

                    // שלך: טיפול ב-string JSON
                    if (node.isTextual()) {
                        try {
                            array.set(i, objectMapper.readTree(node.asText()));
                        } catch (Exception e) {
                            System.out.println("⚠️ Skipping invalid node at index " + i);
                            array.set(i, objectMapper.createObjectNode());
                        }
                    }

                    // 👇 פה מוסיפים את contexts fix
                    JsonNode item = array.get(i);

                    if (item.isObject()) {
                        ObjectNode factNode = (ObjectNode) item;

                        JsonNode ctx = factNode.get("contexts");

                        if (ctx == null || !ctx.isObject()) {
                            factNode.set("contexts", objectMapper.createObjectNode());
                        } else {
                            ObjectNode contextsNode = (ObjectNode) ctx;

                            List<String> fields = new ArrayList<>();
                            contextsNode.fieldNames().forEachRemaining(fields::add);

                            for (String f : fields) {
                                JsonNode value = contextsNode.get(f);

                                if (value == null || value.isContainerNode()) {
                                    contextsNode.remove(f);
                                }
                            }
                        }
                    }
                }
            }
            // 3. המרה לרשימת אובייקטים זמנית
            List<ExtractedFact> facts = objectMapper.convertValue(
                    rootNode,
                    new TypeReference<List<ExtractedFact>>() {}
            );

            List<ExtractedFact> validFacts = new ArrayList<>();

            // 4. לולאת עיבוד, סינון ונרמול אקטיבי של העובדות
            for (ExtractedFact fact : facts) {
                String subject = cleanText(fact.subjectName());
                String action = cleanText(fact.action());
                String object = cleanText(fact.objectName());

                if (subject == null || subject.isBlank() || action == null || action.isBlank()) {
                    System.out.println("⚠️ Fact rejected - missing subject or action");

                    continue;
                }

                Map<String, String> currentContexts = fact.contexts();
                String finalSubject = subject;
                String finalSubjectId = fact.subjectUniqueId();

                // א. הגנה מפני זליגת מילות זמן לנושא
                if (TIME_WORDS.contains(subject)) {
                    System.out.println("⚡ [Java Sanitizer]: Moved time subject '" + subject + "' to contexts.");
                    if (currentContexts == null) currentContexts = new java.util.HashMap<>();
                    currentContexts.put("time", subject);
                    finalSubject = "אני";
                    finalSubjectId = "speaker_root";
                }

                // ב. נרמול אקטיבי של מזהה הדובר (למניעת פיצול קודקודים בגרף)
                if ("אני".equals(finalSubject) || "speaker_root".equals(finalSubjectId)) {
                    finalSubjectId = "speaker_root";
                }

                // ג. ניקוי וחיטוי מזהה האובייקט (מחיקת תווים זרים כמו סינית/עברית מה-ID)
                String finalObjectId = fact.objectUniqueId();
                if (finalObjectId != null) {
                    finalObjectId = finalObjectId.toLowerCase().replaceAll("[^a-z0-9_]", "");
                    if (finalObjectId.isEmpty()) {
                        finalObjectId = "unknown_object";
                    }
                }

                ExtractedFact cleanedFact = new ExtractedFact(
                        finalSubject,
                        finalSubjectId,
                        action,
                        object,
                        finalObjectId,
                        fact.negationFlag(),
                        currentContexts,
                        fact.naturalSentenceEn(),
                        sentence
                );

                validFacts.add(cleanedFact);
            }

            return validFacts;

        } catch (Exception e) {
            System.err.println("❌ שגיאה סופית בפענוח ה-JSON של ה-NLP: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    private String cleanText(String input) {
        if (input == null) return null;
        String cleaned = input.trim();
        return cleaned.isEmpty() ? null : cleaned;
    }
}