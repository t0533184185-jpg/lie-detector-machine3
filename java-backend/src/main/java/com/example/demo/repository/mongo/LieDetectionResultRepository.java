package com.example.demo.repository.mongo; // או com.example.demo.DAL (תלוי איפה שמת אותו)

import com.example.demo.model.LieDetectionResult;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LieDetectionResultRepository extends MongoRepository<LieDetectionResult, Long> {
    // Spring מייצר אוטומטית פונקציות כמו save(), findById(), delete() ועוד.
}