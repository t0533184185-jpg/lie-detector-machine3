package com.example.demo.model;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.neo4j.core.schema.CompositeProperty;
import org.springframework.data.neo4j.core.schema.RelationshipId;
import org.springframework.data.neo4j.core.schema.RelationshipProperties;
import org.springframework.data.neo4j.core.schema.TargetNode;

import java.util.HashMap;
import java.util.Map;
@Getter
@Setter
@RelationshipProperties // מגדיר שהמחלקה הזו מייצגת את המאפיינים שיושבים על החץ
public class EntityRelationship {

    @RelationshipId
    private Long id;

    private String action;          // הפועל/הקשר (למשל: "נמצא ב", "אוכל", "אוהב")
    private double confidenceScore; //  מדד ודאות של חילוץ המידע ע"י הLMM
    private String timestamp;       // מתי נאמרה הטענה
    private String speaker;         // מי הדובר
    private boolean negationFlag;// מסמל האם הקשר חיובי או שלילי (true = "לא נמצא ב")
    private String naturalSentenceEn;
    private String naturalSentenceHe;

    //  שמירת כל סוגי ההקשרים דינמית בתוך ה-DB (זמן, מקום, מצב, כמות וכו')
    @CompositeProperty(prefix = "ctx")
    private Map<String, String> contexts = new HashMap<>();


    @TargetNode
    private EntityNode targetNode;  // צומת היעד (המושא של המשפט)

    public EntityRelationship() {}

    public EntityRelationship(
                              String action,
                              double score,
                              String timestamp,
                              String speaker,
                              boolean negationFlag,
                              EntityNode targetNode) {
        this.action = action;
        this.confidenceScore = score;
        this.timestamp = timestamp;
        this.speaker = speaker;
        this.negationFlag = negationFlag;
        this.targetNode = targetNode;
    }
//
//    public EntityRelationship(String action, double v, String timestamp, String speaker, boolean b, EntityNode objectNode) {
//    }

    public String getContextValue(String key) {
        return contexts.get(key);
    }
    public String getNaturalSentenceEn() {
        return naturalSentenceEn;
    }

    public String getNaturalSentenceHe() {
        return naturalSentenceHe;
    }
}