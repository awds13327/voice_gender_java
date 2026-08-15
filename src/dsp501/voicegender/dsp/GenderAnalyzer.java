package dsp501.voicegender.dsp;

import java.util.ArrayList;
import java.util.List;

/**
 * Male / female decision from classical speech DSP — no trained model.
 *
 * <p>Adult male F0 is typically 85–155 Hz; adult female F0 is typically
 * 165–255 Hz. Spectral centroid and the first LPC formant (F1) shift
 * the decision when pitch sits in the overlap band.
 */
public final class GenderAnalyzer {

    public static final int TARGET_RATE = 16_000;

    public enum Gender {
        MALE("Male"),
        FEMALE("Female"),
        UNCERTAIN("Uncertain");

        public final String label;

        Gender(String label) {
            this.label = label;
        }
    }

    public static final class Result {
        public final Gender gender;
        public final double femaleScore;
        public final double confidence;
        public final double f0Hz;
        public final double centroidHz;
        public final double formantF1Hz;
        public final double formantF2Hz;
        public final int voicedFrames;
        public final int totalFrames;
        public final String pitchMethod;
        public final String summary;

        public Result(Gender gender, double femaleScore, double confidence, double f0Hz,
                      double centroidHz, double formantF1Hz, double formantF2Hz,
                      int voicedFrames, int totalFrames, String pitchMethod, String summary) {
            this.gender = gender;
            this.femaleScore = femaleScore;
            this.confidence = confidence;
            this.f0Hz = f0Hz;
            this.centroidHz = centroidHz;
            this.formantF1Hz = formantF1Hz;
            this.formantF2Hz = formantF2Hz;
            this.voicedFrames = voicedFrames;
            this.totalFrames = totalFrames;
            this.pitchMethod = pitchMethod;
            this.summary = summary;
        }
    }

    private GenderAnalyzer() {
    }

    public static Result analyze(double[] samples, int sampleRate) {
        if (samples == null || samples.length < TARGET_RATE / 5) {
            return fail("Recording is too short. Please speak for about one second.");
        }

        double[] x = Signals.resample(samples, sampleRate, TARGET_RATE);
        Signals.removeDc(x);
        x = Signals.preemphasis(x, 0.97);

        int frameLen = (int) (0.030 * TARGET_RATE);
        int hop = (int) (0.010 * TARGET_RATE);
        List<double[]> frames = Signals.frames(x, frameLen, hop);
        if (frames.isEmpty()) {
            return fail("Not enough audio to analyze.");
        }

        double[] energies = new double[frames.size()];
        for (int i = 0; i < frames.size(); i++) {
            energies[i] = Signals.energy(frames.get(i));
        }
        double energyMed = Signals.median(energies);
        double energyGate = Math.max(energyMed * 0.15, 1e-6);

        List<Double> f0s = new ArrayList<>();
        List<Double> cents = new ArrayList<>();
        List<Double> f1s = new ArrayList<>();
        List<Double> f2s = new ArrayList<>();
        int yinVotes = 0;
        int acfVotes = 0;
        int voiced = 0;

        for (int i = 0; i < frames.size(); i++) {
            double[] frame = frames.get(i);
            if (energies[i] < energyGate) {
                continue;
            }
            double zcr = Signals.zeroCrossingRate(frame);
            if (zcr > 0.28) {
                continue;
            }
            voiced++;

            double[] win = frame.clone();
            Signals.hamming(win);

            PitchDetector.Estimate pitch = PitchDetector.detect(win, TARGET_RATE);
            if (!Double.isNaN(pitch.f0Hz) && pitch.confidence >= 0.35) {
                f0s.add(pitch.f0Hz);
                if ("YIN".equals(pitch.method)) {
                    yinVotes++;
                } else {
                    acfVotes++;
                }
            }

            SpectralAnalyzer.Features spec = SpectralAnalyzer.analyze(win, TARGET_RATE);
            if (!Double.isNaN(spec.centroidHz)) {
                cents.add(spec.centroidHz);
            }
            if (!Double.isNaN(spec.f1Hz)) {
                f1s.add(spec.f1Hz);
            }
            if (!Double.isNaN(spec.f2Hz)) {
                f2s.add(spec.f2Hz);
            }
        }

        if (f0s.size() < 3) {
            return fail("No clear voiced pitch found. Try speaking closer to the mic.");
        }

        double f0 = Signals.median(toArray(f0s));
        double centroid = cents.isEmpty() ? Double.NaN : Signals.median(toArray(cents));
        double f1 = f1s.isEmpty() ? Double.NaN : Signals.median(toArray(f1s));
        double f2 = f2s.isEmpty() ? Double.NaN : Signals.median(toArray(f2s));
        String method = yinVotes >= acfVotes ? "YIN" : "autocorrelation";

        double pitchScore;
        if (f0 <= 145) {
            pitchScore = 0.0;
        } else if (f0 >= 185) {
            pitchScore = 1.0;
        } else {
            pitchScore = (f0 - 145.0) / 40.0;
        }

        double centroidScore = 0.5;
        if (!Double.isNaN(centroid)) {
            centroidScore = Signals.clamp((centroid - 1400.0) / 1200.0, 0, 1);
        }
        double formantScore = 0.5;
        if (!Double.isNaN(f1)) {
            formantScore = Signals.clamp((f1 - 520.0) / 280.0, 0, 1);
        }

        double femaleScore = 0.78 * pitchScore + 0.14 * centroidScore + 0.08 * formantScore;
        Gender gender = femaleScore >= 0.5 ? Gender.FEMALE : Gender.MALE;
        double confidence = Signals.clamp(0.52 + Math.abs(femaleScore - 0.5) * 0.9, 0.52, 0.97);

        String summary = String.format(
                "F0 = %.0f Hz (%s) · centroid = %.0f Hz · F1 = %.0f Hz · %d voiced frames",
                f0,
                method,
                Double.isNaN(centroid) ? 0 : centroid,
                Double.isNaN(f1) ? 0 : f1,
                f0s.size());

        return new Result(gender, femaleScore, confidence, f0, centroid, f1, f2,
                f0s.size(), frames.size(), method, summary);
    }

    private static Result fail(String message) {
        return new Result(Gender.UNCERTAIN, 0.5, 0, Double.NaN, Double.NaN, Double.NaN, Double.NaN,
                0, 0, "-", message);
    }

    private static double[] toArray(List<Double> list) {
        double[] a = new double[list.size()];
        for (int i = 0; i < list.size(); i++) {
            a[i] = list.get(i);
        }
        return a;
    }

    /** Harmonic buzz used to sanity-check the pipeline without a microphone. */
    public static double[] harmonicBuzz(double f0, int sampleRate, double seconds) {
        int n = (int) (sampleRate * seconds);
        double[] x = new double[n];
        for (int i = 0; i < n; i++) {
            double t = i / (double) sampleRate;
            double s = 0;
            for (int h = 1; h <= 6; h++) {
                s += (1.0 / h) * Math.sin(2.0 * Math.PI * h * f0 * t);
            }
            x[i] = 0.25 * s;
        }
        return x;
    }
}
