package com.liveimager.app;

import android.annotation.SuppressLint;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;

/** Live input (built-in mics, USB interface, wired headset) processed and played back in real time. */
final class MicSource extends RecordSource {
    @SuppressLint("MissingPermission")
    MicSource() {
        int min = AudioRecord.getMinBufferSize(SR, AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_16BIT);
        if (min <= 0) min = 4096;
        begin(new AudioRecord.Builder()
                .setAudioSource(MediaRecorder.AudioSource.CAMCORDER)  // usually the stereo mic pair
                .setAudioFormat(new AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SR)
                        .setChannelMask(AudioFormat.CHANNEL_IN_STEREO)
                        .build())
                .setBufferSizeInBytes(min * 2)
                .build());
    }

    @Override public int blockFrames() { return 256; }
    @Override public boolean playsOutput() { return true; }
    @Override public boolean lowLatency() { return true; }
}
