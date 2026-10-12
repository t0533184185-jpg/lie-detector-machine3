package com.example.demo.API;

import com.example.demo.model.Person;
import com.example.demo.service.PersonService;
import org.springframework.http.ResponseEntity; // 🟢 תיקון: האימפורט הנכון של ספרינג!
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/persons")
@CrossOrigin(origins = "http://localhost:3000")
public class PersonController {

    private final PersonService personService;

    public PersonController(PersonService personService) {
        this.personService = personService;
    }

    @GetMapping("/get")
    public List<Person> getAllPersons() {
        return personService.findAll();
    }

    // 🟢 תיקון פונקציית ההרשמה - מחזירה את המשתמש המלא עם ה-ID שלו!
    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody Person person) {
        try {
            // שמירת המשתמש במונגו.
            // אם מתודת addPerson ב-Service שלך מחזירה את האובייקט שנשמר (Person), נשתמש בו:
            personService.addPerson(person);

            // 🔥 שולחים ל-React את ה-person המלא. מונגו כבר יצר לו ID והוא נמצא בפנים!
            return ResponseEntity.ok(person);

        } catch (RuntimeException e) {
            // 🔥 אם יש שגיאה, מחזירים סטטוס שגיאה (400) כדי ש-React ידע לקפוץ ישר ל-.catch ולא ימשיך לעמוד הבית
            return ResponseEntity.badRequest().body("שגיאה ברישום המשתמש: " + e.getMessage());
        }
    }

    // 🟢 תיקון פונקציית ההתחברות - מחזירה שגיאה מפורשת אם המשתמש לא קיים
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> loginData) {
        String userName = loginData.get("userName");
        String password = loginData.get("password");

        Person person = personService.findAll().stream()
                .filter(e -> e.getUserName().equals(userName) && e.getPassword().equals(password))
                .findFirst().orElse(null);

        if (person != null) {
            return ResponseEntity.ok(person); // 🔥 נמצא משתמש - מחזירים אותו עם ה-ID שלו
        } else {
            return ResponseEntity.status(401).body("שם משתמש או סיסמה שגויים"); // 🔥 שגיאת התחברות
        }
    }

    @PostMapping("/delete/{id}")
    public ResponseEntity<String> deletePerson(@PathVariable String id) {
        try {
            personService.deletePerson(id);
            return ResponseEntity.ok("אדם נמחק בהצלחה");
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body("שגיאה במחיקה");
        }
    }
}