package dsp501.voicegender.dsp;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Core 1-D DSP helpers used by the gender pipeline. */
public final class Signals {

    private Signals() {
    }

    public static double[] preemphasis(double[] x, double alpha) {
        double[] y = new double[x.length];
        if (x.length == 0) {
            return y;
        }
        y[0] = x[0];
        for (int n = 1; n < x.length; n++) {
            y[n] = x[n] - alpha * x[n - 1];
        }
        return y;
    }

    public static void removeDc(double[] x) {
        if (x.length == 0) {
            return;
        }
        double mean = 0;
        for (double v : x) {
            mean += v;
        }
        mean /= x.length;
        for (int i = 0; i < x.length; i++) {
            x[i] -= mean;
        }
    }

    public static void hamming(double[] frame) {
        int n = frame.length;
        if (n <= 1) {
            return;
        }
        for (int i = 0; i < n; i++) {
            frame[i] *= 0.54 - 0.46 * Math.cos(2.0 * Math.PI * i / (n - 1));
        }
    }

    public static double energy(double[] x) {
        double e = 0;
        for (double v : x) {
            e += v * v;
        }
        return e;
    }

    public static double rms(double[] x) {
        if (x.length == 0) {
            return 0;
        }
        return Math.sqrt(energy(x) / x.length);
    }

    /** Zero-crossing rate in crossings per sample. */
    public static double zeroCrossingRate(double[] x) {
        if (x.length < 2) {
            return 0;
        }
        int zc = 0;
        for (int i = 1; i < x.length; i++) {
            if ((x[i] >= 0) != (x[i - 1] >= 0)) {
                zc++;
            }
        }
        return zc / (double) (x.length - 1);
    }

    public static List<double[]> frames(double[] x, int frameLen, int hop) {
        List<double[]> out = new ArrayList<>();
        if (x.length < frameLen || hop <= 0) {
            return out;
        }
        for (int start = 0; start + frameLen <= x.length; start += hop) {
            out.add(Arrays.copyOfRange(x, start, start + frameLen));
        }
        return out;
    }

    public static double median(double[] values) {
        if (values.length == 0) {
            return Double.NaN;
        }
        double[] copy = Arrays.copyOf(values, values.length);
        Arrays.sort(copy);
        int m = copy.length / 2;
        if ((copy.length & 1) == 1) {
            return copy[m];
        }
        return 0.5 * (copy[m - 1] + copy[m]);
    }

    public static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    /**
     * Linear-interpolation resampler with a cheap moving-average anti-alias
     * filter when downsampling.
     */
    public static double[] resample(double[] x, int fromRate, int toRate) {
        if (fromRate == toRate) {
            return Arrays.copyOf(x, x.length);
        }
        double[] src = x;
        if (toRate < fromRate) {
            int win = Math.max(1, (int) Math.round(fromRate / (double) toRate));
            src = movingAverage(x, win);
        }
        int outLen = (int) Math.round(src.length * (toRate / (double) fromRate));
        outLen = Math.max(1, outLen);
        double[] y = new double[outLen];
        double step = fromRate / (double) toRate;
        for (int i = 0; i < outLen; i++) {
            double pos = i * step;
            int i0 = (int) Math.floor(pos);
            int i1 = Math.min(i0 + 1, src.length - 1);
            i0 = Math.min(i0, src.length - 1);
            double frac = pos - i0;
            y[i] = src[i0] * (1.0 - frac) + src[i1] * frac;
        }
        return y;
    }

    private static double[] movingAverage(double[] x, int win) {
        if (win <= 1) {
            return x;
        }
        double[] y = new double[x.length];
        double acc = 0;
        int half = win / 2;
        for (int i = 0; i < x.length; i++) {
            acc += x[i];
            if (i >= win) {
                acc -= x[i - win];
            }
            int count = Math.min(i + 1, win);
            int outIdx = i - half;
            if (outIdx >= 0) {
                y[outIdx] = acc / count;
            }
        }
        for (int i = Math.max(0, x.length - half); i < x.length; i++) {
            y[i] = x[i];
        }
        return y;
    }
}
