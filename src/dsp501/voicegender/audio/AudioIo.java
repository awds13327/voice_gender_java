package dsp501.voicegender.audio;

import javax.sound.sampled.AudioFormat;

public final class AudioIo {

    public static final int ANALYSIS_RATE = 16_000;

    private AudioIo() {
    }

    public static AudioFormat pcmMono(float sampleRate) {
        return new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, sampleRate, 16, 1, 2, sampleRate, false);
    }

    public static double[] pcm16leToDoubles(byte[] pcm, int channels) {
        int frameSize = channels * 2;
        int frames = pcm.length / frameSize;
        double[] x = new double[frames];
        for (int i = 0; i < frames; i++) {
            int off = i * frameSize;
            int acc = 0;
            for (int c = 0; c < channels; c++) {
                int lo = pcm[off + c * 2] & 0xff;
                int hi = pcm[off + c * 2 + 1];
                acc += (short) ((hi << 8) | lo);
            }
            x[i] = (acc / (double) channels) / 32768.0;
        }
        return x;
    }

    public static byte[] doublesToPcm16le(double[] x) {
        byte[] pcm = new byte[x.length * 2];
        for (int i = 0; i < x.length; i++) {
            int s = (int) Math.round(Math.max(-1.0, Math.min(1.0, x[i])) * 32767.0);
            pcm[i * 2] = (byte) (s & 0xff);
            pcm[i * 2 + 1] = (byte) ((s >> 8) & 0xff);
        }
        return pcm;
    }
}
