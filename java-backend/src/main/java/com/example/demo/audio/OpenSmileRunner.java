package com.example.demo.audio;

import java.io.*;
import java.util.*;

public class OpenSmileRunner {
    // שני עדכן לנתיב המדויק של openSMILE אצלך
    private static final String OPENSMILE_EXE = "C:\\Users\\user\\Desktop\\תכנות\\פרויקט יד\\opensmile-3.0.2-windows-x86_64\\opensmile-3.0.2-windows-x86_64\\bin\\SMILExtract.exe";
    private static final String CONFIG_FILE = "C:\\Users\\user\\Desktop\\תכנות\\פרויקט יד\\opensmile-3.0.2-windows-x86_64\\opensmile-3.0.2-windows-x86_64\\config\\egemaps\\v02\\eGeMAPSv02.conf";

    public double[] extractFeatures(String audioFilePath) throws Exception {
        File outputCsv = File.createTempFile("smile_output", ".csv");

        ProcessBuilder pb = new ProcessBuilder(
                OPENSMILE_EXE,
                "-C", CONFIG_FILE,
                "-I", audioFilePath,
                "-csvoutput", outputCsv.getAbsolutePath(),
                "-instname", "segment"
        );
        pb.redirectErrorStream(true);
        Process process = pb.start();

        // הדפסת כל הפלט של openSMILE לדיבאג
        String openSmileOutput = new String(process.getInputStream().readAllBytes());
        process.waitFor();

        System.out.println("=== openSMILE output ===");
        System.out.println(openSmileOutput);
        System.out.println("=== CSV path: " + outputCsv.getAbsolutePath() + " ===");
        System.out.println("=== CSV exists: " + outputCsv.exists() + ", size: " + outputCsv.length() + " bytes ===");

        // הדפסת תוכן ה-CSV
        if (outputCsv.exists() && outputCsv.length() > 0) {
            String csvContent = java.nio.file.Files.readString(outputCsv.toPath());            System.out.println("=== CSV content (first 500 chars) ===");
            System.out.println(csvContent.substring(0, Math.min(500, csvContent.length())));
        }

        double[] features = parseCsvOutput(outputCsv.getAbsolutePath());
        outputCsv.delete();
        return features;
    }

    private double[] parseCsvOutput(String csvPath) throws Exception {
        BufferedReader reader = new BufferedReader(new FileReader(csvPath));
        String dataLine = reader.readLine(); // שורה אחת בלבד — אין שורת כותרות ב-CSV הזה
        reader.close();

        if (dataLine == null || dataLine.trim().isEmpty())
            throw new Exception("openSMILE לא החזיר נתונים!");

        String[] parts = dataLine.split(";");
        // parts[0] = 'segment' (שם)
        // parts[1] = start time
        // parts[2..89] = 88 המאפיינים

        if (parts.length < 90)
            throw new Exception("מספר עמודות שגוי: " + parts.length + " (צפוי 90+)");

        double[] features = new double[88];
        for (int i = 0; i < 88; i++) {
            features[i] = Double.parseDouble(parts[i + 2]);
        }
        return features;
    }
}