package dsp501.voicegender.dsp;

/**
 * Pitch (F0) estimators used for voiced speech.
 * <ul>
 *   <li>YIN — difference function + cumulative mean normalization</li>
 *   <li>Autocorrelation — lag of the first strong peak</li>
 * </ul>
 */
public final class PitchDetector {

    public static final double F0_MIN_HZ = 80.0;
    public static final double F0_MAX_HZ = 320.0;

    private PitchDetector() {
    }

    public static final class Estimate {
        public final double f0Hz;
        public final double confidence;
        public final String method;

        public Estimate(double f0Hz, double confidence, String method) {
            this.f0Hz = f0Hz;
            this.confidence = confidence;
            this.method = method;
        }
    }

    public static Estimate detect(double[] frame, int sampleRate) {
        Estimate yin = yin(frame, sampleRate);
        if (yin.confidence >= 0.45) {
            return yin;
        }
        Estimate acf = autocorrelation(frame, sampleRate);
        if (acf.confidence > yin.confidence) {
            return acf;
        }
        return yin;
    }

    /**
     * YIN (de Cheveigné &amp; Kawahara, 2002).
     * d(τ) = Σ (x[j] − x[j+τ])², then d'(τ) = d(τ) / ((1/τ) Σ d[k]).
     */
    public static Estimate yin(double[] x, int sampleRate) {
        int tauMin = Math.max(2, (int) Math.floor(sampleRate / F0_MAX_HZ));
        int tauMax = Math.min(x.length / 2, (int) Math.ceil(sampleRate / F0_MIN_HZ));
        if (tauMax <= tauMin + 2 || x.length < tauMax + 32) {
            return new Estimate(Double.NaN, 0, "YIN");
        }

        double[] d = new double[tauMax + 1];
        int n = x.length - tauMax;
        for (int tau = 1; tau <= tauMax; tau++) {
            double sum = 0;
            for (int j = 0; j < n; j++) {
                double diff = x[j] - x[j + tau];
                sum += diff * diff;
            }
            d[tau] = sum;
        }

        double[] cmnd = new double[tauMax + 1];
        cmnd[0] = 1;
        double running = 0;
        for (int tau = 1; tau <= tauMax; tau++) {
            running += d[tau];
            cmnd[tau] = d[tau] * tau / Math.max(running, 1e-12);
        }

        final double threshold = 0.12;
        int tauEst = -1;
        for (int tau = tauMin; tau < tauMax; tau++) {
            if (cmnd[tau] < threshold) {
                while (tau + 1 <= tauMax && cmnd[tau + 1] < cmnd[tau]) {
                    tau++;
                }
                tauEst = tau;
                break;
            }
        }
        if (tauEst < 0) {
            double minVal = Double.POSITIVE_INFINITY;
            for (int tau = tauMin; tau <= tauMax; tau++) {
                if (cmnd[tau] < minVal) {
                    minVal = cmnd[tau];
                    tauEst = tau;
                }
            }
            if (minVal > 0.45) {
                return new Estimate(Double.NaN, 0, "YIN");
            }
        }

        double betterTau = parabolic(cmnd, tauEst);
        double f0 = sampleRate / betterTau;
        if (f0 < F0_MIN_HZ || f0 > F0_MAX_HZ) {
            return new Estimate(Double.NaN, 0, "YIN");
        }
        double conf = Signals.clamp(1.0 - cmnd[tauEst], 0, 1);
        return new Estimate(f0, conf, "YIN");
    }

    /**
     * Normalized autocorrelation peak in the legal lag range.
     * r[k] = Σ x[n]x[n+k] / Σ x[n]²
     */
    public static Estimate autocorrelation(double[] x, int sampleRate) {
        int tauMin = Math.max(2, (int) Math.floor(sampleRate / F0_MAX_HZ));
        int tauMax = Math.min(x.length - 2, (int) Math.ceil(sampleRate / F0_MIN_HZ));
        if (tauMax <= tauMin + 2) {
            return new Estimate(Double.NaN, 0, "ACF");
        }

        double r0 = 0;
        for (double v : x) {
            r0 += v * v;
        }
        if (r0 < 1e-12) {
            return new Estimate(Double.NaN, 0, "ACF");
        }

        int bestTau = tauMin;
        double bestR = -1;
        for (int tau = tauMin; tau <= tauMax; tau++) {
            double r = 0;
            int n = x.length - tau;
            for (int i = 0; i < n; i++) {
                r += x[i] * x[i + tau];
            }
            r /= r0;
            if (r > bestR) {
                bestR = r;
                bestTau = tau;
            }
        }

        if (bestR < 0.25) {
            return new Estimate(Double.NaN, 0, "ACF");
        }
        double f0 = sampleRate / (double) bestTau;
        return new Estimate(f0, Signals.clamp(bestR, 0, 1), "ACF");
    }

    private static double parabolic(double[] y, int i) {
        if (i <= 0 || i >= y.length - 1) {
            return i;
        }
        double s0 = y[i - 1];
        double s1 = y[i];
        double s2 = y[i + 1];
        double denom = s0 - 2.0 * s1 + s2;
        if (Math.abs(denom) < 1e-12) {
            return i;
        }
        return i + 0.5 * (s0 - s2) / denom;
    }
}
