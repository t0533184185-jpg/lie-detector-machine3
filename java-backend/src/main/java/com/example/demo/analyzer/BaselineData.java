package com.example.demo.analyzer;
import lombok.Getter;
import lombok.Setter;
import org.springframework.stereotype.Component;

@Getter @Setter
@Component

public class BaselineData {

    private double shoulderMean;
    private double shoulderStd;

    private double browMean;
    private double browStd;

    private double lipMean;
    private double lipStd;

    private double eyeMean;
    private double eyeStd;

    private double fearMean;
    private double fearStd;

    private double shoulderWidthMean;
    private double shoulderWidthStd;
}
