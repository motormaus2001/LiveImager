package com.liveimager.app;

/**
 * 4-band mid/side stereo imager with "Stereoize" and output metering.
 * Width per band: -1 = mono, 0 = unchanged, +1 = side doubled.
 * Bands are split with complementary filters (band sum == input), so
 * with every width at 0 the processing is fully transparent.
 */
final class StereoImager {
    static final int BANDS = 4;
    static final float MIN_F = 30f, MAX_F = 16000f;
    static final float[] DEFAULT_XOVER = {150f, 1200f, 6000f};

    final float[] width = new float[BANDS];
    final float[] xover = DEFAULT_XOVER.clone();
    volatile float stereoize;          // 0..1
    volatile boolean bypass;

    // Output meters (read by the UI)
    final float[] meterWidth = new float[BANDS];  // share of side energy per band, 0..1
    volatile float correlation = 1f;

    private volatile int version = 1;
    private int applied = 0;
    private float fs = 48000f;

    private final Biquad[] split = new Biquad[3], meterMid = new Biquad[3], meterSide = new Biquad[3];
    private final Biquad stereoizeHp = new Biquad();
    private final float[] delay = new float[8192];
    private int dPos, dLen = 600;

    private final double[] em = new double[BANDS], es = new double[BANDS];
    private final float[] smMid = new float[BANDS], smSide = new float[BANDS];
    private float sLR, sLL, sRR;

    StereoImager() {
        for (int i = 0; i < 3; i++) {
            split[i] = new Biquad(); meterMid[i] = new Biquad(); meterSide[i] = new Biquad();
        }
    }

    void changed() { version++; }

    void setSampleRate(float sr) {
        if (sr > 0 && sr != fs) { fs = sr; version++; }
    }

    void setCrossover(int i, float f) {
        float lo = i == 0 ? MIN_F : xover[i - 1] * 1.25f;
        float hi = i == 2 ? MAX_F : xover[i + 1] / 1.25f;
        xover[i] = Math.max(lo, Math.min(hi, f));
        version++;
    }

    void reset() {
        for (int i = 0; i < BANDS; i++) width[i] = 0;
        System.arraycopy(DEFAULT_XOVER, 0, xover, 0, 3);
        stereoize = 0;
        version++;
    }

    private void apply() {
        int v = version;
        for (int i = 0; i < 3; i++) {
            split[i].lowpass(xover[i], fs);
            meterMid[i].lowpass(xover[i], fs);
            meterSide[i].lowpass(xover[i], fs);
        }
        stereoizeHp.highpass(300, fs);
        dLen = Math.min(delay.length - 1, Math.round(0.014f * fs));  // 14 ms Haas-style decorrelation
        applied = v;
    }

    /** Processes interleaved stereo float samples in place. */
    void process(float[] buf, int frames) {
        if (applied != version) apply();
        final float w0 = 1 + width[0], w1 = 1 + width[1], w2 = 1 + width[2], w3 = 1 + width[3];
        final float st = stereoize;
        final boolean byp = bypass;
        for (int i = 0; i < BANDS; i++) { em[i] = 0; es[i] = 0; }
        double bLR = 0, bLL = 0, bRR = 0;

        for (int f = 0; f < frames; f++) {
            float L = buf[2 * f], R = buf[2 * f + 1];
            if (!byp) {
                float M = (L + R) * 0.5f, S = (L - R) * 0.5f;

                // Stereoize: add a delayed, high-passed copy of mid into side (mono-compatible).
                delay[dPos] = M;
                int rp = dPos - dLen; if (rp < 0) rp += delay.length;
                float dm = delay[rp];
                if (++dPos == delay.length) dPos = 0;
                if (st > 0) S += st * 0.7f * stereoizeHp.process(dm);

                float b0 = split[0].process(S); float r = S - b0;
                float b1 = split[1].process(r); r -= b1;
                float b2 = split[2].process(r); float b3 = r - b2;
                S = w0 * b0 + w1 * b1 + w2 * b2 + w3 * b3;

                L = M + S; R = M - S;
                if (L > 1f) L = 1f; else if (L < -1f) L = -1f;
                if (R > 1f) R = 1f; else if (R < -1f) R = -1f;
                buf[2 * f] = L; buf[2 * f + 1] = R;
            }

            // Metering on the output signal
            float m = (L + R) * 0.5f, s = (L - R) * 0.5f;
            float m0 = meterMid[0].process(m); float mr = m - m0;
            float m1 = meterMid[1].process(mr); mr -= m1;
            float m2 = meterMid[2].process(mr); float m3 = mr - m2;
            float s0 = meterSide[0].process(s); float sr = s - s0;
            float s1 = meterSide[1].process(sr); sr -= s1;
            float s2 = meterSide[2].process(sr); float s3 = sr - s2;
            em[0] += m0 * m0; em[1] += m1 * m1; em[2] += m2 * m2; em[3] += m3 * m3;
            es[0] += s0 * s0; es[1] += s1 * s1; es[2] += s2 * s2; es[3] += s3 * s3;
            bLR += L * R; bLL += L * L; bRR += R * R;
        }

        if (frames <= 0) return;
        float a = (float) Math.exp(-frames / (fs * 0.25));
        float k = 1 - a;
        for (int i = 0; i < BANDS; i++) {
            smMid[i] = a * smMid[i] + k * (float) (em[i] / frames);
            smSide[i] = a * smSide[i] + k * (float) (es[i] / frames);
            meterWidth[i] = smSide[i] / (smMid[i] + smSide[i] + 1e-12f);
        }
        sLR = a * sLR + k * (float) (bLR / frames);
        sLL = a * sLL + k * (float) (bLL / frames);
        sRR = a * sRR + k * (float) (bRR / frames);
        double den = Math.sqrt((double) sLL * sRR);
        if (den > 1e-9) correlation = (float) Math.max(-1, Math.min(1, sLR / den));
    }
}
