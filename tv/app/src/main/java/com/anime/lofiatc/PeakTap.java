package com.anime.lofiatc;

import android.content.Context;

import androidx.annotation.OptIn;
import androidx.media3.common.C;
import androidx.media3.common.audio.AudioProcessor;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.audio.AudioSink;
import androidx.media3.exoplayer.audio.DefaultAudioSink;
import androidx.media3.exoplayer.audio.TeeAudioProcessor;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Listens in on the ATC player's decoded audio and keeps its recent loudness, for the talk light. */
@OptIn(markerClass = UnstableApi.class)
class PeakTap implements TeeAudioProcessor.AudioBufferSink {
    private volatile float level;
    private volatile long at;
    private int encoding;

    DefaultRenderersFactory renderers(Context context) {
        TeeAudioProcessor tee = new TeeAudioProcessor(this);
        return new DefaultRenderersFactory(context) {
            @Override
            protected AudioSink buildAudioSink(Context c, boolean enableFloatOutput, boolean enableAudioTrackPlaybackParams) {
                return new DefaultAudioSink.Builder(c).setAudioProcessors(new AudioProcessor[]{tee}).build();
            }
        };
    }

    /** Peak of the last ~0.3 s, 0..1. */
    float level() {
        return System.currentTimeMillis() - at < 300 ? level : 0;
    }

    @Override
    public void flush(int sampleRateHz, int channelCount, int encoding) {
        this.encoding = encoding;
    }

    @Override
    public void handleBuffer(ByteBuffer buffer) {
        if (encoding != C.ENCODING_PCM_16BIT) return;
        ByteBuffer b = buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN);
        int peak = 0;
        while (b.remaining() >= 2) peak = Math.max(peak, Math.abs((int) b.getShort()));
        float now = peak / 32768f;
        // Hold the loudest moment briefly so short words still light it.
        if (now >= level || System.currentTimeMillis() - at > 300) { level = now; at = System.currentTimeMillis(); }
    }
}
