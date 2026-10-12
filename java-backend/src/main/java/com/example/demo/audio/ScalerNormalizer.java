package com.example.demo.audio;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import java.io.InputStream;
import java.util.Map;
import java.util.List;

public class ScalerNormalizer {
    private double[] mean;
    private double[] std;

    public ScalerNormalizer() throws Exception {
        InputStream is = new ClassPathResource("scaler_params.json").getInputStream();
        ObjectMapper mapper = new ObjectMapper();
        Map<String, Object> params = mapper.readValue(is, Map.class);

        List<Double> meanList = (List<Double>) params.get("mean");
        List<Double> stdList = (List<Double>) params.get("std");

        mean = meanList.stream().mapToDouble(Double::doubleValue).toArray();
        std = stdList.stream().mapToDouble(Double::doubleValue).toArray();

        System.out.println("✅ Scaler נטען בהצלחה. מספר מאפיינים: " + mean.length);
    }

    public double[] normalize(double[] features) {
        double[] normalized = new double[features.length];
        for (int i = 0; i < features.length; i++) {
            normalized[i] = (features[i] - mean[i]) / std[i];
        }
        return normalized;
    }
}