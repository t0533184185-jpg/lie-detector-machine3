package com.example.demo.service;

import com.example.demo.model.EntityNode;
import com.example.demo.model.EntityRelationship;
import com.example.demo.model.ExtractedFact;
import com.example.demo.repository.neo4j.EntityRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.ArrayList;

import java.util.HashMap;
import java.util.Map;

@Service
public class GraphPersistenceService {

    private final EntityRepository entityRepository;
    private final EntityResolutionService entityResolutionService;

    public GraphPersistenceService(EntityRepository entityRepository, EntityResolutionService entityResolutionService) {
        this.entityRepository = entityRepository;
        this.entityResolutionService = entityResolutionService;
    }
//שמירה בגרף
    @Transactional
    public void persistFact(ExtractedFact fact, String speaker, String timestamp) {
        // פתרון ישויות - מציאת ה-ID הנכון כדי למנוע כפילויות בגרף
        String subjectId = fact.subjectUniqueId() != null && !fact.subjectUniqueId().isEmpty()
                ? fact.subjectUniqueId()
                : entityResolutionService.resolveUniqueId(fact.subjectName());
        //אם הLMM פישל נקרא לפונקציה של resolveuniqeid שמונע כפילויות שהמערכת לא תקרוס
        String objectId = null;

        if (fact.objectName() != null &&
                !fact.objectName().isBlank()) {

            objectId =
                    fact.objectUniqueId() != null &&
                            !fact.objectUniqueId().isEmpty()
                            ? fact.objectUniqueId()
                            : entityResolutionService.resolveUniqueId(
                            fact.objectName()
                    );
        }

        //  שליפת נושא או יצירתו מחדש
        EntityNode subjectNode = entityRepository.findByUniqueId(subjectId);
        if (subjectNode == null) {
            subjectNode = new EntityNode(subjectId, fact.subjectName());
        }

        // שליפת מושא או יצירתו מחדש
        EntityNode objectNode = null;

        if (objectId != null) {

            objectNode = entityRepository.findByUniqueId(objectId);

            if (objectNode == null) {

                objectNode = new EntityNode(objectId, fact.objectName());

                entityRepository.save(objectNode);
            }
        }

        //  יצירת הקשר (החץ) עם המאפיינים הדינמיים מהסרטון
        EntityRelationship relationship = new EntityRelationship(
                fact.action(),
                1.0, // קביעת ודאות בסיסית, ניתן לשנות בהתאם לצורך
                timestamp,
                speaker,
                fact.negationFlag(),
                objectNode
        );
        relationship.setNaturalSentenceEn(fact.naturalSentenceEn());
        relationship.setNaturalSentenceHe(fact.naturalSentenceHe());

        if (fact.contexts() != null) {
            relationship.setContexts(fact.contexts());
        }

        //  חיבור הקשר לנושא ושמירת העץ המעודכן
        subjectNode.addRelationship(relationship);
        entityRepository.save(subjectNode);
        System.out.println("💾 העובדה נשמרה בהצלחה ב-Neo4j: [" + fact.subjectName() + "] -> (" + fact.action() + ") -> [" + fact.objectName() + "]");
    }

    /**
     * מוחקת את כל הנתונים והעובדות הישנות מ-Neo4j כדי להכין את המערכת לסרטון חדש וסטרילי
     */
    @Transactional
    public void prepareGraphForNewVideo() {
        try {
            entityRepository.deleteAllNodes();
            System.out.println(" [Graph Cleaned] Neo4j Graph cleared successfully for the new video content.");
        } catch (Exception e) {
            System.err.println(" שגיאה בניקוי בסיס נתונים Neo4j: " + e.getMessage());
        }
    }

    //מחזיר את כל העובדות מהגרף כדי לשלוח לפייתון
    public List<Map<String, Object>> getAllFactsFromGraph() {
        List<Map<String, Object>> historyFacts = new ArrayList<>();

        for (EntityNode node : entityRepository.findAll()) {

            if (node.getRelationships() == null) {
                continue;
            }

            for (EntityRelationship rel : node.getRelationships()) {

                Map<String, Object> factMap = new HashMap<>();

                factMap.put("subject", node.getName());
                factMap.put("action", rel.getAction());

                if (rel.getTargetNode() != null) {
                    factMap.put("object", rel.getTargetNode().getName());
                }

                factMap.put("contexts", rel.getContexts());
                factMap.put("negationFlag", rel.isNegationFlag());

                factMap.put("naturalSentenceEn", rel.getNaturalSentenceEn());
                factMap.put("naturalSentenceHe", rel.getNaturalSentenceHe());


                historyFacts.add(factMap);
            }
        }

        return historyFacts;
    }
}