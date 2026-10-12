package com.example.demo.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@ToString
@Builder

@Document
public class Person {
    @Id
    @JsonProperty("id")
    private String id;
    private String userName;
    private String password;
    private String passwordcheck;
    private String email;
    private String phone;
    private int age;
    private String role;
}
