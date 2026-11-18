package com.liveimager.app;

import android.media.AudioRecord;

/** Base for AudioRecord-backed sources (16-bit stereo, 48 kHz). */
abstract class RecordSource implements Engine.Source {
    static final int SR = 48000;
    protected AudioRecord rec;
    private short[] tmp = new short[0];

    protected void begin(AudioRecord r) {
        if (r.getState() != AudioRecord.STATE_INITIALIZED) {
            r.release();
            throw new IllegalStateException("Audio input could not be opened");
        }
        rec = r;
        rec.startRecording();
    }

    @Override public int sampleRate() { return rec.getSampleRate(); }

    @Override
    public int read(float[] out, int maxFrames) {
        int need = maxFrames * 2;
        if (tmp.length < need) tmp = new short[need];
        int n = rec.read(tmp, 0, need, AudioRecord.READ_BLOCKING);
        if (n < 0) throw new IllegalStateException("Audio read error " + n);
        int frames = n / 2;
        for (int i = 0; i < frames * 2; i++) out[i] = tmp[i] / 32768f;
        return frames;
    }

    @Override
    public void close() {
        if (rec == null) return;
        try { rec.stop(); } catch (Exception ignored) { }
        rec.release();
    }
}
