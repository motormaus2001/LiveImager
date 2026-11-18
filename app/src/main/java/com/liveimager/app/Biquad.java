package com.liveimager.app;

/** 2nd-order Butterworth filter (RBJ cookbook), transposed direct form II. */
final class Biquad {
    private double b0, b1, b2, a1, a2, z1, z2;

    void lowpass(double f, double fs) {
        f = Math.min(f, fs * 0.45);
        double w = 2 * Math.PI * f / fs, c = Math.cos(w), al = Math.sin(w) / (2 * 0.7071067811865476);
        double a0 = 1 + al;
        b0 = (1 - c) / 2 / a0; b1 = (1 - c) / a0; b2 = b0;
        a1 = -2 * c / a0; a2 = (1 - al) / a0;
    }

    void highpass(double f, double fs) {
        f = Math.min(f, fs * 0.45);
        double w = 2 * Math.PI * f / fs, c = Math.cos(w), al = Math.sin(w) / (2 * 0.7071067811865476);
        double a0 = 1 + al;
        b0 = (1 + c) / 2 / a0; b1 = -(1 + c) / a0; b2 = b0;
        a1 = -2 * c / a0; a2 = (1 - al) / a0;
    }

    float process(float x) {
        double y = b0 * x + z1;
        z1 = b1 * x - a1 * y + z2;
        z2 = b2 * x - a2 * y;
        if (Math.abs(z1) < 1e-25) z1 = 0;   // avoid denormals
        if (Math.abs(z2) < 1e-25) z2 = 0;
        return (float) y;
    }
}
