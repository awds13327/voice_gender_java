package dsp501.voicegender.dsp;

/**
 * Spectral centroid and LPC formant estimate (Levinson–Durbin + envelope peaks).
 */
public final class SpectralAnalyzer {

    private SpectralAnalyzer() {
    }

    public static final class Features {
        public final double centroidHz;
        public final double f1Hz;
        public final double f2Hz;

        public Features(double centroidHz, double f1Hz, double f2Hz) {
            this.centroidHz = centroidHz;
            this.f1Hz = f1Hz;
            this.f2Hz = f2Hz;
        }
    }

    public static Features analyze(double[] windowedFrame, int sampleRate) {
        int nfft = Fft.nextPowerOfTwo(Math.max(512, windowedFrame.length * 2));
        double[] mag = Fft.magnitude(windowedFrame, nfft);
        double centroid = spectralCentroid(mag, sampleRate, nfft);
        double[] formants = lpcFormants(windowedFrame, sampleRate, 14, nfft);
        return new Features(centroid, formants[0], formants[1]);
    }

    /**
     * SC = Σ f[k] |X[k]| / Σ |X[k]|  (positive frequencies only).
     */
    public static double spectralCentroid(double[] mag, int sampleRate, int nfft) {
        double num = 0;
        double den = 0;
        int bins = mag.length;
        for (int k = 1; k < bins; k++) {
            double f = k * (sampleRate / (double) nfft);
            if (f > 4000) {
                break;
            }
            num += f * mag[k];
            den += mag[k];
        }
        if (den < 1e-12) {
            return Double.NaN;
        }
        return num / den;
    }

    /**
     * Autocorrelation LPC via Levinson–Durbin. Formants are peaks of
     * the all-pole envelope |1 / A(e^{jω})| evaluated with an FFT.
     */
    public static double[] lpcFormants(double[] frame, int sampleRate, int order, int nfft) {
        double[] a = levinsonDurbin(frame, order);
        if (a == null) {
            return new double[] {Double.NaN, Double.NaN};
        }

        double[] re = new double[nfft];
        double[] im = new double[nfft];
        re[0] = 1.0;
        for (int k = 1; k < a.length && k < nfft; k++) {
            re[k] = -a[k];
        }
        Fft.fft(re, im);

        double[] env = new double[nfft / 2];
        for (int k = 0; k < env.length; k++) {
            double mag = Math.hypot(re[k], im[k]);
            env[k] = 1.0 / Math.max(mag, 1e-12);
        }

        double f1 = peakHz(env, sampleRate, nfft, 250, 950);
        double f2 = peakHz(env, sampleRate, nfft, 800, 2500);
        if (!Double.isNaN(f1) && !Double.isNaN(f2) && f2 - f1 < 150) {
            f2 = peakHz(env, sampleRate, nfft, f1 + 200, 2800);
        }
        return new double[] {f1, f2};
    }

    /**
     * Returns LPC coefficients a[1..p] for A(z) = 1 − Σ a_k z^{−k}.
     * a[0] is unused (kept 0).
     */
    static double[] levinsonDurbin(double[] x, int p) {
        int n = x.length;
        if (n <= p + 8) {
            return null;
        }
        double[] r = new double[p + 1];
        for (int lag = 0; lag <= p; lag++) {
            double s = 0;
            for (int i = 0; i < n - lag; i++) {
                s += x[i] * x[i + lag];
            }
            r[lag] = s;
        }
        if (r[0] < 1e-12) {
            return null;
        }

        double[] a = new double[p + 1];
        double[] aPrev = new double[p + 1];
        double e = r[0];
        for (int i = 1; i <= p; i++) {
            double acc = r[i];
            for (int j = 1; j < i; j++) {
                acc -= aPrev[j] * r[i - j];
            }
            double k = acc / e;
            a[i] = k;
            for (int j = 1; j < i; j++) {
                a[j] = aPrev[j] - k * aPrev[i - j];
            }
            e *= (1.0 - k * k);
            if (e <= 1e-12) {
                return null;
            }
            System.arraycopy(a, 0, aPrev, 0, i + 1);
        }
        return a;
    }

    private static double peakHz(double[] env, int sampleRate, int nfft, double fLo, double fHi) {
        int kLo = Math.max(2, (int) Math.round(fLo * nfft / sampleRate));
        int kHi = Math.min(env.length - 2, (int) Math.round(fHi * nfft / sampleRate));
        if (kHi <= kLo) {
            return Double.NaN;
        }
        int best = -1;
        double bestVal = 0;
        for (int k = kLo; k <= kHi; k++) {
            if (env[k] > env[k - 1] && env[k] >= env[k + 1] && env[k] > bestVal) {
                bestVal = env[k];
                best = k;
            }
        }
        if (best < 0) {
            return Double.NaN;
        }
        double binHz = sampleRate / (double) nfft;
        return best * binHz;
    }
}
