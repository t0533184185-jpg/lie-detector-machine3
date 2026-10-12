package com.example.demo.repository.neo4j;

import com.example.demo.model.ContradictionResult;
import com.example.demo.model.EntityNode;
import com.example.demo.model.EntityRelationship;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.stereotype.Repository;
import org.springframework.data.repository.query.Param;
import java.util.List;

@Repository
public interface EntityRepository extends Neo4jRepository<EntityNode, String> {
    //שאילתה אוטומטית המחפשת בגרף צומת של ישות לפי המזהה הייחודי שלה ומחזירה את כל הצומת
    EntityNode findByUniqueId(String uniqueId);

    //מחיקת  כל הגרף
    @Query("MATCH (n) DETACH DELETE n")
    void deleteAllNodes();

    //למציאת סתירות ישירות
    @Query("""
    MATCH (s:Entity {uniqueId: $subjectUniqueId})-[r:RELATED_TO]->(t:Entity)
    WHERE r.action = $action
    RETURN s, collect(r), collect(t)
    """)
    List<EntityNode> findEntitiesWithRelationshipsBySubjectAndAction(
            @Param("subjectUniqueId") String subjectUniqueId,
            @Param("action") String action
    );

    //שאילתה להצגת כל העובדות בגרף
    @Query("MATCH (n:Entity)-[r:RELATED_TO]->(m:Entity) " +
            "RETURN n.name + ' ' + r.action + ' ' + m.name + ' (דובר: ' + r.speaker + ')'")
    List<String> findAllFacts();

//    //הצגת עובדות עבור ישות ספציפית
    @Query("MATCH (n:Entity {uniqueId: $uniqueId})-[r:RELATED_TO]->(m:Entity) " +
            "RETURN n.name + ' ' + r.action + ' ' + m.name + ' (דובר: ' + r.speaker + ')'")
    List<String> findAllFactsForEntity(String uniqueId);

}