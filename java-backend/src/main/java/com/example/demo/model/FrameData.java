package com.example.demo.model;

import lombok.*;
import java.util.Map;
// שימי לב לאימפורטים החדשים של ה-Data של Spring (עבור NoSQL)
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@ToString
@Builder
@Document(collection = "frame_data") // מגדיר את האוסף ב-MongoDB
public class FrameData {

    @Id // האנוטציה מגיעה מ-org.springframework.data.annotation.Id
    private int frameNumber; // ישמש כמפתח הראשי של המסמך

    // ב-MongoDB שומרים את המפות (Map) כפי שהן! הן יישמרו אוטומטית כתת-אובייקט (Embedded Document)
    private Map<Integer, Point3D> poseLandmarks;

    private Map<Integer, Point3D> faceLandmarks;

    private Map<Integer, Point3D> handLandmarks;
}