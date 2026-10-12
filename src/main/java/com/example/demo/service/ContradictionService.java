package com.example.demo.service;

import com.example.demo.model.ContradictionResult;
import com.example.demo.model.EntityNode;
import com.example.demo.model.EntityRelationship;
import com.example.demo.repository.neo4j.EntityRepository;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
public class ContradictionService {

    private final EntityRepository entityRepository;

    public ContradictionService(EntityRepository entityRepository) {
        this.entityRepository = entityRepository;
    }

    // ─── סתירות ישירות מהגרף ─────

    /**
     * שולף מהגרף את כל הקשרים הקיימים עבור ישות ספציפית ופועל ספציפי.
     * משמש את ה-ConversationManagerService כדי לבצע השוואת הקשרים דינמית (זמן, מקום וכו') ב-Java.
     */
    @Transactional(readOnly = true)
    public List<EntityRelationship> findRelationshipsForSubjectAndAction(String subjectUniqueId, String action) {

        System.out.println("📊 [Neo4j] שולף מהגרף קשרים עבור הישות: [" + subjectUniqueId + "] עם הפועל: [" + action + "]");

        List<EntityNode> entities =
                entityRepository.findEntitiesWithRelationshipsBySubjectAndAction(subjectUniqueId, action);

        if (entities.isEmpty()) {
            return List.of();
        }

        return entities.get(0).getRelationships().stream().filter(r -> action.equals(r.getAction())).toList();
    }


}