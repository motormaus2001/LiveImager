package com.liveimager.app;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;

import java.util.Arrays;

/** Runs one audio source at a time on a real-time thread: read -> imager -> (speaker) + scope. */
final class Engine {
    interface Source {
        int sampleRate();
        int blockFrames();
        boolean playsOutput();
        boolean lowLatency();
        /** Fills interleaved stereo floats. Returns frames read, 0 if none yet, -1 at end. */
        int read(float[] out, int maxFrames) throws Exception;
        void close();
    }

    static final int SCOPE_SIZE = 4096;  // power of two
    private static final Engine INSTANCE = new Engine();
    static Engine get() { return INSTANCE; }

    final StereoImager imager = new StereoImager();
    final float[] scopeL = new float[SCOPE_SIZE], scopeR = new float[SCOPE_SIZE];
    volatile int scopePos;

    private volatile Source current;
    private volatile Thread thread;
    private volatile boolean paused;
    private volatile String status = "Pick a source: audio file, microphone or system audio.";

    String status() { return status; }
    void setStatus(String s) { status = s; }
    boolean isRunning() { return current != null; }
    boolean isPaused() { return paused; }
    void setPaused(boolean p) { paused = p; }

    synchronized void start(Source src, String label) {
        stopLocked();
        current = src;
        paused = false;
        status = label;
        Thread t = new Thread(() -> loop(src), "imager-audio");
        thread = t;
        t.start();
    }

    synchronized void stop() {
        stopLocked();
        status = "Stopped";
    }

    synchronized void stopIf(Source src) {
        if (current == src) stop();
    }

    private void stopLocked() {
        current = null;
        Thread t = thread;
        thread = null;
        if (t != null && t != Thread.currentThread()) {
            try { t.join(1500); } catch (InterruptedException ignored) { }
        }
        Arrays.fill(scopeL, 0); Arrays.fill(scopeR, 0);
    }

    private void loop(Source src) {
        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO);
        float[] buf = new float[2 * 4096];
        AudioTrack track = null;
        int trackRate = 0;
        try {
            while (current == src) {
                if (paused) {
                    if (track != null && track.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) track.pause();
                    Thread.sleep(15);
                    continue;
                }
                int n = src.read(buf, Math.min(src.blockFrames(), 4096));
                if (n < 0) { status = "Finished"; break; }
                if (n == 0) continue;
                int sr = src.sampleRate();
                imager.setSampleRate(sr);
                imager.process(buf, n);
                pushScope(buf, n);
                if (src.playsOutput()) {
                    if (track == null || trackRate != sr) {
                        if (track != null) track.release();
                        track = makeTrack(sr, src.lowLatency());
                        trackRate = sr;
                    }
                    if (track.getPlayState() != AudioTrack.PLAYSTATE_PLAYING) track.play();
                    track.write(buf, 0, n * 2, AudioTrack.WRITE_BLOCKING);
                }
            }
        } catch (Throwable e) {
            if (current == src) {
                status = "Error: " + e.getMessage();
                current = null;
            }
        } finally {
            if (track != null) {
                try { track.stop(); } catch (Exception ignored) { }
                track.release();
            }
            src.close();
        }
    }

    private void pushScope(float[] buf, int n) {
        int p = scopePos;
        for (int i = 0; i < n; i++) {
            scopeL[p] = buf[2 * i];
            scopeR[p] = buf[2 * i + 1];
            p = (p + 1) & (SCOPE_SIZE - 1);
        }
        scopePos = p;
    }

    private static AudioTrack makeTrack(int sr, boolean lowLatency) {
        int min = AudioTrack.getMinBufferSize(sr, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_FLOAT);
        if (min <= 0) min = 16384;
        AudioTrack.Builder b = new AudioTrack.Builder()
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build())
                .setAudioFormat(new AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                        .setSampleRate(sr)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                        .build())
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setBufferSizeInBytes(lowLatency ? min * 2 : Math.max(min, 8192 * 8));
        if (lowLatency) b.setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY);
        return b.build();
    }
}
