package com.example.demo.model;

import lombok.*;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@ToString
@Builder

// Point3D.java
public class Point3D {
    private Long id;
    private double x;
    private double y;
    private double z;

    public Point3D(double x, double y, double z) {
        this.x = x; this.y = y; this.z = z;
    }
    public double getX() { return x; }
    public double getY() { return y; }
    public double getZ() { return z; }
}

