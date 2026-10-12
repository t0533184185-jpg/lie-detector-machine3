package com.example.demo.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.neo4j.core.schema.GeneratedValue;

import java.util.List;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@ToString
@Builder

@Document
public class Videos
{
    @Id
    private String id;
    private String name;
    private String personId;
    private String transcript;
    private List<WindowScores> timeline;

    private boolean analysisFinished;

//    private String analysis;

}
