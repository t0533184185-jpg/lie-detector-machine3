package com.example.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import org.springframework.data.neo4j.repository.config.EnableNeo4jRepositories;
import org.springframework.scheduling.annotation.EnableAsync;
@EnableAsync

@SpringBootApplication
// אומרים למונגו לסרוק רק את התיקייה שלו
@EnableMongoRepositories(basePackages = "com.example.demo.repository.mongo")
// אומרים ל-Neo4j לסרוק רק את התיקייה שלו
@EnableNeo4jRepositories(basePackages = "com.example.demo.repository.neo4j")
public class Demo1Application {
    public static void main(String[] args) {
        SpringApplication.run(Demo1Application.class, args);
    }
}