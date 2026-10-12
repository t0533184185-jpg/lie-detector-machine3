package com.example.demo.audio;

import com.example.demo.service.FusionService;
import org.deeplearning4j.nn.modelimport.keras.KerasModelImport;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import javax.sound.sampled.*;
import java.io.*;
@Service
public class LieDetectorPipeline {
    private final FusionService fusionService;

    public LieDetectorPipeline(FusionService fusionService) {
        this.fusionService = fusionService;
    }

    public void analyze(String videoId,String audioPath) throws Exception {
        System.out.println("🔍 מתחיל ניתוח: " + audioPath);

        // טעינת כלים
        MultiLayerNetwork model = KerasModelImport
                .importKerasSequentialModelAndWeights("mlp_model_fixed.h5");
        ScalerNormalizer scaler = new ScalerNormalizer();
        OpenSmileRunner openSmile = new OpenSmileRunner();
        ViterbiDecoder viterbi = new ViterbiDecoder();

        // חיתוך ל-3 שניות וניתוח כל חלון

        List<double[]> emissionList = new ArrayList<>();double duration = getAudioDuration(audioPath);
        double windowSize = 3.0;
        double hopSize = 1.0;

        int windowIndex = 0;

        while ((windowIndex * hopSize) + windowSize <= duration) {

            double start = windowIndex * hopSize;
            double end = start + windowSize;

            String chunkPath = cropAudio(audioPath, start, end, windowIndex);
            double[] features = openSmile.extractFeatures(chunkPath);
            double[] normalized = scaler.normalize(features);

            INDArray input = Nd4j.create(normalized, new int[]{1, 88});
            INDArray output = model.output(input);
            double probTrue = output.getDouble(0);
            double probLie = output.getDouble(1);

            emissionList.add(new double[]{probTrue, probLie});

            double audioConfidence = 0.85; // נגדיר שלמודל האודיו יש אמינות/ביטחון בסיסית של 85%

            System.out.printf("[Pipeline] Window %d analyzed. Sending to Fusion -> Score: %.2f, Confidence: %.2f%n",
                    windowIndex, probLie, audioConfidence);

            fusionService.addSuspicionReasonSpreadOverTime(
                    start,
                    end,
                    probLie,
                    String.format("טון דיבור: חשד לשקר (ציון: %.2f)", probLie)
            );

            // קריאה לפונקציית ה-HTTP שיצרת בתוך LoadModel
            LoadModel.sendAudioResultToFusion(
                    videoId,
                    start,
                    end,
                    probLie,
                    audioConfidence
            );
            new File(chunkPath).delete();
            windowIndex++;
        }

        // Viterbi
        double[][] emissions = emissionList.toArray(new double[0][]);
        String[] timeline = viterbi.decode(emissions);

        // פלט
        System.out.println("\n📊 ניתוח אמינות לפי ציר זמן:");
        System.out.println("================================");
        for (int i = 0; i < timeline.length; i++) {
            double start = i * hopSize;
            double end = start + windowSize;

            System.out.printf("⏱ %.0f-%.0f שניות: %s%n",
                    start,
                    end,
                    timeline[i]);        }
    }

    private static String extractAudio(String videoPath) throws Exception {
        String audioPath = videoPath.replace(".mp4", "_audio.wav");
        ProcessBuilder pb = new ProcessBuilder(
                "ffmpeg", "-i", videoPath,
                "-ar", "16000", "-ac", "1",
                audioPath, "-y"
        );
        pb.start().waitFor();
        return audioPath;
    }



    private static double getAudioDuration(String audioPath) throws Exception {
        AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(new File(audioPath));
        AudioFormat format = audioInputStream.getFormat();
        long frames = audioInputStream.getFrameLength();
        double duration = frames / format.getFrameRate();
        audioInputStream.close();
        return duration;
    }

    private static String cropAudio(String audioPath, double start, double end, int index) throws Exception {
        String chunkPath = System.getProperty("java.io.tmpdir") + "\\chunk_" + index + ".wav";

        AudioInputStream fullAudio = AudioSystem.getAudioInputStream(new File(audioPath));
        AudioFormat format = fullAudio.getFormat();

        long startFrame = (long)(start * format.getFrameRate());
        long endFrame = (long)(end * format.getFrameRate());
        long chunkFrames = endFrame - startFrame;

        // דילוג לנקודת ההתחלה
        fullAudio.skip(startFrame * format.getFrameSize());

        // קריאת הקטע הרצוי
        AudioInputStream chunkAudio = new AudioInputStream(fullAudio, format, chunkFrames);
        AudioSystem.write(chunkAudio, AudioFileFormat.Type.WAVE, new File(chunkPath));

        fullAudio.close();
        return chunkPath;
    }
}