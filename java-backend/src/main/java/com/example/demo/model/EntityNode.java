package com.example.demo.model;

import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Relationship;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Node("Entity")
public class EntityNode {

    @Id
    private String elementId;  //  ללא @GeneratedValue בכלל

    private String uniqueId;// המפתח הלוגי המנורמל באנגלית
    private String name;// השם הטבעי של הישות

    @Relationship(type = "RELATED_TO", direction = Relationship.Direction.OUTGOING)
    private List<EntityRelationship> relationships = new ArrayList<>();

    public EntityNode() {
        this.elementId = UUID.randomUUID().toString(); // ← אנחנו מייצרים את ה-ID
    }

    public EntityNode(String uniqueId, String name) {
        this.elementId = UUID.randomUUID().toString(); // ← אנחנו מייצרים את ה-ID
        this.uniqueId = uniqueId;
        this.name = name;
    }

    public void addRelationship(EntityRelationship rel) {
        this.relationships.add(rel);
    }

    public String getElementId() { return elementId; }
    public String getUniqueId() { return uniqueId; }
    public void setUniqueId(String uniqueId) { this.uniqueId = uniqueId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public List<EntityRelationship> getRelationships() { return relationships; }
}