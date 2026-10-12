package com.example.demo.service;

import com.example.demo.repository.neo4j.EntityRepository;
import com.example.demo.model.EntityNode;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class EntityResolutionService {

    private final EntityRepository entityRepository;

    public EntityResolutionService(EntityRepository entityRepository) {
        this.entityRepository = entityRepository;
    }

    /**
     * אלגוריתם פתרון ישויות חכם - מבוסס על מילת הבסיס שה-LLM נרמל עבורנו
     */
    public String resolveUniqueId(String normalizedEntityName) {
       //בדיקה אם LMM שלח שם של ישות
        if (normalizedEntityName == null || normalizedEntityName.trim().isEmpty()) {
            return "unknown";
        }

        // הופך למשל את "מכונית" ל-"car_machonit" או "מכונית_entity"
        String cleanName = normalizedEntityName.trim().toLowerCase();

        // בדיקה קצרה בגרף האם המושג הזה כבר קיים כדי למנוע כפילויות
        List<EntityNode> existingNodes = entityRepository.findAll();
        for (EntityNode node : existingNodes) {
            if (cleanName != null && cleanName.equalsIgnoreCase(node.getName()))  {
                return node.getUniqueId(); // נמצאה ישות קיימת נחזיר את ה-ID שלה
            }
        }

        // אם זו ישות חדשה, נייצר לה ID קבוע ומובנה בצורה אוטומטית
        return cleanName.replaceAll("\\s+", "_") + "_entity";
    }
}