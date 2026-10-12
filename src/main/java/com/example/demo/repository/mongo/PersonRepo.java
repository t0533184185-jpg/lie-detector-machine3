package com.example.demo.repository.mongo;

import com.example.demo.model.Person;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PersonRepo extends MongoRepository<Person,String> {
}
