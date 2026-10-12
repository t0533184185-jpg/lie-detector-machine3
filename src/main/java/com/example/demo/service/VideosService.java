package com.example.demo.service;


import com.example.demo.repository.mongo.VideosRepo;
import com.example.demo.model.Videos;
import org.opencv.video.Video;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class VideosService {
    private final VideosRepo videosRepo;
    @Autowired

    public VideosService(VideosRepo videosRepo) {
        this.videosRepo = videosRepo;
    }


        public Videos addVideo (Videos video){
            return videosRepo.save(video); // הוספה למסד נתונים
        }

        public List<Videos> findAll () {
            return videosRepo.findAll();
        }

        public void deleteVideos (String id)
        {
            if (!videosRepo.existsById(id)) {
                throw new RuntimeException("לא קיים אדם כזה");

            }
            videosRepo.deleteById(id);

        }
    public List<Videos> findByPersonId (String id) {
        return videosRepo.findByPersonId(id);
    }
    public Optional<Videos> findById(String id) {
        return videosRepo.findById(id);
    }
}
