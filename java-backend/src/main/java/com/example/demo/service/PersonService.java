package com.example.demo.service;


import com.example.demo.model.Person;
import com.example.demo.repository.mongo.PersonRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PersonService {

    @Autowired   // מופע של הקלאס הקודם שעשינו כדי להפעיל פעולות בסיסיות
    private PersonRepo personRepo;

    public void addPerson(Person person) {
        // הבדיקה תתבצע רק אם נשלח ID באופן ידני. אם לא נשלח, נדלג ישר לשמירה ומונגו ייצר ID בעצמו
        if (person.getId() != null && personRepo.existsById(person.getId())) {
            throw new RuntimeException("לא ניתן, משתמש עם ID זה כבר קיים");
        }
        personRepo.save(person);
    }

    public List<Person> findAll() {
        return personRepo.findAll();
    }

    // שינוי כאן: מ-Long id ל-String id
    public void deletePerson(String id) {
        if (!personRepo.existsById(id)) {
            throw new RuntimeException("לא קיים אדם כזה");
        }
        personRepo.deleteById(id);
    }

}
