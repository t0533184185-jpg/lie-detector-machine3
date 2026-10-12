package com.example.demo.repository.mongo;

import com.example.demo.model.Videos;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface VideosRepo extends MongoRepository<Videos,String> {
    List<Videos> findByPersonId(String personId);

}
