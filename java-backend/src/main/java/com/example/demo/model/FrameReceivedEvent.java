package com.example.demo.model;
import org.springframework.context.ApplicationEvent;

public class FrameReceivedEvent extends ApplicationEvent {
    private final FrameData frameData;

    public FrameReceivedEvent(Object source, FrameData frameData) {
        super(source);
        this.frameData = frameData;
    }

    public FrameData getFrameData() { return frameData; }
}