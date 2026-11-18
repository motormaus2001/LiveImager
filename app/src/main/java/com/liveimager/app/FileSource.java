package com.liveimager.app;

import android.content.Context;
import android.media.AudioFormat;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.Uri;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Decodes any audio file Android supports (mp3, aac/m4a, flac, wav, ogg, opus...) and loops it. */
final class FileSource implements Engine.Source {
    private final MediaExtractor ex = new MediaExtractor();
    private final MediaCodec codec;
    private final MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
    private volatile int rate;
    private int ch;
    private boolean isFloat, inputDone;
    private ByteBuffer pending;
    private int pendingIdx = -1;
    private boolean pendingEos;

    FileSource(Context c, Uri uri) throws IOException {
        ex.setDataSource(c, uri, null);
        int track = -1;
        MediaFormat fmt = null;
        for (int i = 0; i < ex.getTrackCount(); i++) {
            MediaFormat f = ex.getTrackFormat(i);
            String mime = f.getString(MediaFormat.KEY_MIME);
            if (mime != null && mime.startsWith("audio/")) { track = i; fmt = f; break; }
        }
        if (track < 0) {
            ex.release();
            throw new IOException("No audio track in this file");
        }
        ex.selectTrack(track);
        rate = fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE);
        ch = fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
        codec = MediaCodec.createDecoderByType(fmt.getString(MediaFormat.KEY_MIME));
        codec.configure(fmt, null, null, 0);
        codec.start();
    }

    @Override public int sampleRate() { return rate; }
    @Override public int blockFrames() { return 1024; }
    @Override public boolean playsOutput() { return true; }
    @Override public boolean lowLatency() { return false; }

    private void feedInput() {
        if (inputDone) return;
        int ii = codec.dequeueInputBuffer(2000);
        if (ii < 0) return;
        ByteBuffer b = codec.getInputBuffer(ii);
        int size = b == null ? -1 : ex.readSampleData(b, 0);
        if (size < 0) {
            codec.queueInputBuffer(ii, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
            inputDone = true;
        } else {
            codec.queueInputBuffer(ii, 0, size, ex.getSampleTime(), 0);
            ex.advance();
        }
    }

    private void rewind() {
        ex.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
        codec.flush();
        inputDone = false;
    }

    private float sample() {
        return isFloat ? pending.getFloat() : pending.getShort() / 32768f;
    }

    @Override
    public int read(float[] out, int max) {
        if (pending == null) {
            feedInput();
            int idx = codec.dequeueOutputBuffer(info, 10000);
            if (idx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                MediaFormat of = codec.getOutputFormat();
                if (of.containsKey(MediaFormat.KEY_SAMPLE_RATE)) rate = of.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                if (of.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) ch = of.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
                isFloat = of.containsKey(MediaFormat.KEY_PCM_ENCODING)
                        && of.getInteger(MediaFormat.KEY_PCM_ENCODING) == AudioFormat.ENCODING_PCM_FLOAT;
                return 0;
            }
            if (idx < 0) return 0;
            boolean eos = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
            ByteBuffer b = codec.getOutputBuffer(idx);
            if (b == null || info.size <= 0) {
                codec.releaseOutputBuffer(idx, false);
                if (eos) rewind();
                return 0;
            }
            b.position(info.offset);
            b.limit(info.offset + info.size);
            b.order(ByteOrder.LITTLE_ENDIAN);
            pending = b;
            pendingIdx = idx;
            pendingEos = eos;
        }

        int chans = Math.max(1, ch);
        int frameBytes = (isFloat ? 4 : 2) * chans;
        int frames = Math.min(max, pending.remaining() / frameBytes);
        for (int f = 0; f < frames; f++) {
            float l = sample(), r = l;
            if (chans > 1) {
                r = sample();
                for (int k = 2; k < chans; k++) sample();
            }
            out[2 * f] = l;
            out[2 * f + 1] = r;
        }
        if (pending.remaining() < frameBytes) {
            codec.releaseOutputBuffer(pendingIdx, false);
            pending = null;
            if (pendingEos) rewind();   // loop playback
        }
        return frames;
    }

    @Override
    public void close() {
        try { codec.stop(); } catch (Exception ignored) { }
        try { codec.release(); } catch (Exception ignored) { }
        try { ex.release(); } catch (Exception ignored) { }
    }
}
