package dsp501.voicegender.audio;

import dsp501.voicegender.dsp.Signals;

import javax.sound.sampled.AudioFormat;

public final class VoiceNote {

    public final byte[] pcm;
    public final AudioFormat format;
    public final double[] analysisSamples;
    public final int analysisRate;
    public final long durationMs;

    public VoiceNote(byte[] pcm, AudioFormat format, double[] analysisSamples, int analysisRate) {
        this.pcm = pcm;
        this.format = format;
        this.analysisSamples = analysisSamples;
        this.analysisRate = analysisRate;
        long frames = pcm.length / Math.max(1, format.getFrameSize());
        this.durationMs = Math.round(1000.0 * frames / format.getSampleRate());
    }

    public static VoiceNote fromCapture(byte[] pcm, AudioFormat format) {
        double[] samples = AudioIo.pcm16leToDoubles(pcm, format.getChannels());
        int fromRate = Math.round(format.getSampleRate());
        double[] analysis = fromRate == AudioIo.ANALYSIS_RATE
                ? samples
                : Signals.resample(samples, fromRate, AudioIo.ANALYSIS_RATE);
        return new VoiceNote(pcm, format, analysis, AudioIo.ANALYSIS_RATE);
    }

    public String durationLabel() {
        double sec = durationMs / 1000.0;
        return String.format("%.1fs", sec);
    }
}
