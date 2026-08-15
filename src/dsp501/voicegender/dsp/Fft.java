package dsp501.voicegender.dsp;

/**
 * In-place radix-2 Cooley–Tukey FFT (decimation in time).
 * Length must be a power of two.
 */
public final class Fft {

    private Fft() {
    }

    public static int nextPowerOfTwo(int n) {
        int p = 1;
        while (p < n) {
            p <<= 1;
        }
        return p;
    }

    public static void fft(double[] real, double[] imag) {
        transform(real, imag, false);
    }

    public static void ifft(double[] real, double[] imag) {
        transform(real, imag, true);
    }

    /**
     * Magnitude spectrum of a real signal, zero-padded to {@code nfft}.
     */
    public static double[] magnitude(double[] signal, int nfft) {
        double[] re = new double[nfft];
        double[] im = new double[nfft];
        int n = Math.min(signal.length, nfft);
        System.arraycopy(signal, 0, re, 0, n);
        fft(re, im);
        double[] mag = new double[nfft / 2 + 1];
        for (int k = 0; k < mag.length; k++) {
            mag[k] = Math.hypot(re[k], im[k]);
        }
        return mag;
    }

    private static void transform(double[] re, double[] im, boolean inverse) {
        int n = re.length;
        if (n == 0 || (n & (n - 1)) != 0) {
            throw new IllegalArgumentException("FFT length must be a power of two");
        }
        if (im.length != n) {
            throw new IllegalArgumentException("real/imag length mismatch");
        }

        bitReverse(re, im);

        for (int len = 2; len <= n; len <<= 1) {
            double ang = 2.0 * Math.PI / len * (inverse ? 1 : -1);
            double wLenRe = Math.cos(ang);
            double wLenIm = Math.sin(ang);
            for (int i = 0; i < n; i += len) {
                double wRe = 1.0;
                double wIm = 0.0;
                int half = len >> 1;
                for (int j = 0; j < half; j++) {
                    int u = i + j;
                    int v = u + half;
                    double tRe = wRe * re[v] - wIm * im[v];
                    double tIm = wRe * im[v] + wIm * re[v];
                    re[v] = re[u] - tRe;
                    im[v] = im[u] - tIm;
                    re[u] += tRe;
                    im[u] += tIm;
                    double nextRe = wRe * wLenRe - wIm * wLenIm;
                    wIm = wRe * wLenIm + wIm * wLenRe;
                    wRe = nextRe;
                }
            }
        }

        if (inverse) {
            double inv = 1.0 / n;
            for (int i = 0; i < n; i++) {
                re[i] *= inv;
                im[i] *= inv;
            }
        }
    }

    private static void bitReverse(double[] re, double[] im) {
        int n = re.length;
        int j = 0;
        for (int i = 1; i < n; i++) {
            int bit = n >> 1;
            while ((j & bit) != 0) {
                j ^= bit;
                bit >>= 1;
            }
            j ^= bit;
            if (i < j) {
                swap(re, i, j);
                swap(im, i, j);
            }
        }
    }

    private static void swap(double[] a, int i, int j) {
        double t = a[i];
        a[i] = a[j];
        a[j] = t;
    }
}
