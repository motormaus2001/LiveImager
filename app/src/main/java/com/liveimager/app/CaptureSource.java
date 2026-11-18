package com.liveimager.app;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioPlaybackCaptureConfiguration;
import android.media.AudioRecord;
import android.media.projection.MediaProjection;

/** Captures what other apps are playing (Android 10+) for real-time analysis. */
@TargetApi(29)
final class CaptureSource extends RecordSource {
    @SuppressLint("MissingPermission")
    CaptureSource(MediaProjection projection) {
        AudioPlaybackCaptureConfiguration cfg = new AudioPlaybackCaptureConfiguration.Builder(projection)
                .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                .addMatchingUsage(AudioAttributes.USAGE_GAME)
                .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                .build();
        int min = AudioRecord.getMinBufferSize(SR, AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_16BIT);
        if (min <= 0) min = 8192;
        begin(new AudioRecord.Builder()
                .setAudioFormat(new AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SR)
                        .setChannelMask(AudioFormat.CHANNEL_IN_STEREO)
                        .build())
                .setBufferSizeInBytes(min * 4)
                .setAudioPlaybackCaptureConfig(cfg)
                .build());
    }

    @Override public int blockFrames() { return 512; }
    @Override public boolean playsOutput() { return false; }  // Android can't replace other apps' output
    @Override public boolean lowLatency() { return false; }
}
