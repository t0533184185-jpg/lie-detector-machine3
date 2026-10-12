package com.example.demo.model;

import java.util.Map;

//אובייקט שמייצג סתירה
public record ContradictionResult(
        String subject,
        String action,
        String object1,
        String object2,
        String speaker,
        Map<String, String> oldContexts, // כל ההקשרים של המשפט הקודם
        Map<String, String> newContexts  // כל ההקשרים של המשפט החדש
) {}