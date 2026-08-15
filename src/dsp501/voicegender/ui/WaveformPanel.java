package dsp501.voicegender.ui;

import javax.swing.JComponent;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;

public final class WaveformPanel extends JComponent {

    private double[] peaks = new double[0];
    private double progress = 0;
    private Color wave = Theme.TEXT;
    private Color played = Theme.ACCENT;

    public WaveformPanel() {
        setOpaque(false);
        setPreferredSize(new Dimension(160, 36));
    }

    public void setSamples(double[] samples) {
        int bins = 48;
        peaks = new double[bins];
        if (samples == null || samples.length == 0) {
            repaint();
            return;
        }
        int binSize = Math.max(1, samples.length / bins);
        for (int b = 0; b < bins; b++) {
            int start = b * binSize;
            int end = Math.min(samples.length, start + binSize);
            double max = 0;
            for (int i = start; i < end; i++) {
                max = Math.max(max, Math.abs(samples[i]));
            }
            peaks[b] = max;
        }
        double peak = 1e-6;
        for (double p : peaks) {
            peak = Math.max(peak, p);
        }
        for (int i = 0; i < peaks.length; i++) {
            peaks[i] = Math.min(1.0, peaks[i] / peak);
        }
        repaint();
    }

    public void setProgress(double progress) {
        this.progress = Math.max(0, Math.min(1, progress));
        repaint();
    }

    public void setColors(Color wave, Color played) {
        this.wave = wave;
        this.played = played;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int w = getWidth();
        int h = getHeight();
        int n = Math.max(1, peaks.length);
        float gap = 2.2f;
        float barW = Math.max(1.6f, (w - gap * (n - 1)) / n);
        int mid = h / 2;
        int playedUntil = (int) Math.round(progress * n);

        for (int i = 0; i < n; i++) {
            float amp = peaks.length == 0 ? 0.18f : (float) (0.18 + 0.82 * peaks[i]);
            int bh = Math.max(3, Math.round(amp * (h - 6)));
            float x = i * (barW + gap);
            g2.setColor(i < playedUntil ? played : wave);
            g2.setStroke(new BasicStroke(barW, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.drawLine(Math.round(x + barW / 2), mid - bh / 2, Math.round(x + barW / 2), mid + bh / 2);
        }
        g2.dispose();
    }

    public static void paintMic(Graphics2D g2, int cx, int cy, int r, Color color) {
        g2.setColor(color);
        g2.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        int capH = (int) (r * 0.85);
        g2.drawRoundRect(cx - r / 3, cy - capH / 2 - 2, (2 * r) / 3, capH, 10, 10);
        Path2D yoke = new Path2D.Float();
        yoke.moveTo(cx - r * 0.55, cy + 2);
        yoke.curveTo(cx - r * 0.55, cy + r * 0.7, cx + r * 0.55, cy + r * 0.7, cx + r * 0.55, cy + 2);
        g2.draw(yoke);
        g2.drawLine(cx, cy + (int) (r * 0.7), cx, cy + r);
        g2.drawLine(cx - r / 3, cy + r, cx + r / 3, cy + r);
    }
}
