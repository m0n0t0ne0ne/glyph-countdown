package com.glyphcountdown.app;

import android.content.Context;
import android.media.AudioFormat;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.Uri;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

/**
 * Paces the time's-up animation to the alarm sound. The chosen sound is decoded once to find its
 * length and where its notes start; the animation loop is then stretched to a whole number of
 * sound repeats (or a whole fraction of a long sound), and each note adds a short flash.
 */
final class SoundSync {
    /** Length of one play of the sound and the start of each note, in ms. */
    static final class Profile {
        final long lengthMs; final long[] onsetsMs;
        Profile(long lengthMs, long[] onsetsMs) { this.lengthMs = lengthMs; this.onsetsMs = onsetsMs; }
    }

    private static final long MAX_DECODE_US = 60_000_000L;  // notes after a minute get no flashes
    private static final int WIN_MS = 10;
    private static final long ACCENT_MS = 350;

    private static String cachedKey;
    private static Profile cached;

    private SoundSync() {}

    /** Cached analysis of {@code uri}, or null if it hasn't been (or can't be) analysed. */
    static synchronized Profile cached(Uri uri) {
        return uri != null && uri.toString().equals(cachedKey) ? cached : null;
    }

    /** Decode and analyse the sound (slow: call off the main thread). Null if it can't be read. */
    static Profile analyze(Context c, Uri uri) {
        if (uri == null) return null;
        Profile p = cached(uri);
        if (p != null) return p;
        try {
            p = decode(c, uri);
        } catch (Throwable t) {
            p = null;
        }
        if (p != null) {
            synchronized (SoundSync.class) { cachedKey = uri.toString(); cached = p; }
        }
        return p;
    }

    // ---- pacing (plain maths, no Android) ---------------------------------------------

    /** Animation loop length: whole repeats of a short sound, or an even split of a long one. */
    static double loopMs(double naturalMs, long soundMs) {
        if (soundMs <= 0) return naturalMs;
        if (soundMs <= naturalMs * 1.5) return Math.max(1, Math.round(naturalMs / soundMs)) * (double) soundMs;
        return soundMs / (double) Math.max(1, Math.round(soundMs / naturalMs));
    }

    /** Flash strength 0..1 at {@code posMs} into one play of the sound: peaks on each note, fades in 350 ms. */
    static double accent(long[] onsets, long posMs) {
        double best = 0;
        for (long o : onsets) {
            if (o > posMs) break;
            long dt = posMs - o;
            if (dt < ACCENT_MS) best = Math.max(best, Math.exp(-dt / 90.0));
        }
        return best;
    }

    private static final double[] PEAKS = {1.0, 0.7, 0.9, 0.55};
    private static final double FLOOR = 0.2, GAP = 0.04;

    /**
     * Brightness 0..1 at {@code posMs} into one play of the sound, stepping through dimness
     * levels so the matrix blinks with the notes: a short dark gap just before each note, a flash
     * at that note's peak (peaks vary from note to note), then two dimmer steps down to a low glow.
     */
    static double level(long[] onsets, long posMs, long lengthMs) {
        if (onsets.length == 0) return 1;
        int i = -1;
        for (int k = 0; k < onsets.length && onsets[k] <= posMs; k++) i = k;
        long next = i + 1 < onsets.length ? onsets[i + 1] : onsets[0] + lengthMs;
        long dt = i >= 0 ? posMs - onsets[i] : Long.MAX_VALUE;
        if (dt >= 80 && next - posMs <= 70) return GAP;
        if (i < 0) return FLOOR;
        double peak = PEAKS[i % PEAKS.length];
        if (dt < 80) return peak;
        if (dt < 160) return peak * 0.6;
        if (dt < 260) return peak * 0.35;
        return FLOOR;
    }

    /** Note starts from a 10 ms loudness envelope: a sharp rise well above the recent level. */
    static long[] onsets(float[] env) {
        float max = 0;
        for (float e : env) max = Math.max(max, e);
        List<Long> out = new ArrayList<>();
        if (max <= 0) return new long[0];
        float floor = max * 0.12f;
        long last = -1_000;
        for (int i = 0; i < env.length; i++) {
            float prev = 0;
            int n = 0;
            for (int k = Math.max(0, i - 4); k < i; k++) { prev += env[k]; n++; }
            prev = n > 0 ? prev / n : 0;
            long t = (long) i * WIN_MS;
            if (env[i] > floor && env[i] > prev * 1.8f + max * 0.04f && t - last >= 120) {
                out.add(t);
                last = t;
            }
        }
        long[] a = new long[out.size()];
        for (int i = 0; i < a.length; i++) a[i] = out.get(i);
        return a;
    }

    // ---- decoding ----------------------------------------------------------------------

    private static Profile decode(Context c, Uri uri) throws Exception {
        MediaExtractor ex = new MediaExtractor();
        MediaCodec codec = null;
        try {
            ex.setDataSource(c, uri, null);
            int track = -1;
            MediaFormat fmt = null;
            for (int i = 0; i < ex.getTrackCount(); i++) {
                MediaFormat f = ex.getTrackFormat(i);
                String mime = f.getString(MediaFormat.KEY_MIME);
                if (mime != null && mime.startsWith("audio/")) { track = i; fmt = f; break; }
            }
            if (track < 0) return null;
            ex.selectTrack(track);
            long durUs = fmt.containsKey(MediaFormat.KEY_DURATION) ? fmt.getLong(MediaFormat.KEY_DURATION) : 0;
            int rate = fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE);
            int channels = fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
            boolean floatPcm = false;

            codec = MediaCodec.createDecoderByType(fmt.getString(MediaFormat.KEY_MIME));
            codec.configure(fmt, null, null, 0);
            codec.start();

            List<Float> env = new ArrayList<>();
            double acc = 0;
            int accN = 0;
            int win = Math.max(1, rate * WIN_MS / 1000);
            long frames = 0;
            boolean inDone = false, outDone = false;
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            long guard = System.currentTimeMillis() + 8000;
            while (!outDone && System.currentTimeMillis() < guard) {
                if (!inDone) {
                    int in = codec.dequeueInputBuffer(10_000);
                    if (in >= 0) {
                        ByteBuffer b = codec.getInputBuffer(in);
                        int n = ex.readSampleData(b, 0);
                        if (n < 0 || ex.getSampleTime() > MAX_DECODE_US) {
                            codec.queueInputBuffer(in, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            inDone = true;
                        } else {
                            codec.queueInputBuffer(in, 0, n, ex.getSampleTime(), 0);
                            ex.advance();
                        }
                    }
                }
                int out = codec.dequeueOutputBuffer(info, 10_000);
                if (out == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    MediaFormat of = codec.getOutputFormat();
                    if (of.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) channels = of.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
                    if (of.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                        rate = of.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                        win = Math.max(1, rate * WIN_MS / 1000);
                    }
                    if (of.containsKey(MediaFormat.KEY_PCM_ENCODING))
                        floatPcm = of.getInteger(MediaFormat.KEY_PCM_ENCODING) == AudioFormat.ENCODING_PCM_FLOAT;
                } else if (out >= 0) {
                    ByteBuffer b = codec.getOutputBuffer(out);
                    if (b != null && info.size > 0) {
                        b.position(info.offset);
                        b.limit(info.offset + info.size);
                        ByteBuffer o = b.slice().order(ByteOrder.nativeOrder());
                        int bytes = floatPcm ? 4 : 2;
                        int count = info.size / (bytes * channels);
                        for (int i = 0; i < count; i++) {
                            double s = 0;
                            for (int ch = 0; ch < channels; ch++) {
                                int at = (i * channels + ch) * bytes;
                                s += floatPcm ? o.getFloat(at) : o.getShort(at) / 32768.0;
                            }
                            s /= channels;
                            acc += s * s;
                            if (++accN == win) {
                                env.add((float) Math.sqrt(acc / accN));
                                acc = 0;
                                accN = 0;
                            }
                        }
                        frames += count;
                    }
                    codec.releaseOutputBuffer(out, false);
                    if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) outDone = true;
                }
            }
            if (env.isEmpty()) return null;
            float[] e = new float[env.size()];
            for (int i = 0; i < e.length; i++) e[i] = env.get(i);
            long lenMs = durUs > 0 ? durUs / 1000 : frames * 1000L / Math.max(1, rate);
            if (lenMs < 100) return null;
            return new Profile(lenMs, onsets(e));
        } finally {
            if (codec != null) {
                try { codec.stop(); } catch (Throwable ignored) {}
                try { codec.release(); } catch (Throwable ignored) {}
            }
            ex.release();
        }
    }
}
