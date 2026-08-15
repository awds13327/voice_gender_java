package dsp501.voicegender.ui;

import dsp501.voicegender.audio.ClipPlayer;
import dsp501.voicegender.audio.MicrophoneRecorder;
import dsp501.voicegender.audio.VoiceNote;
import dsp501.voicegender.dsp.GenderAnalyzer;

import javax.sound.sampled.LineUnavailableException;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.util.function.Consumer;

public final class ChatFrame extends JFrame {

    private final JPanel feed = new JPanel();
    private final JScrollPane scroll;
    private final ComposerBar composer;
    private final ClipPlayer player = new ClipPlayer();
    private VoiceBubble playingBubble;

    public ChatFrame() {
        super("Voice Gender Chat");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(420, 640));
        setSize(460, 760);
        setLocationRelativeTo(null);

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(Theme.BG);
        root.add(buildHeader(), BorderLayout.NORTH);

        feed.setLayout(new BoxLayout(feed, BoxLayout.Y_AXIS));
        feed.setBackground(Theme.BG);
        feed.setBorder(BorderFactory.createEmptyBorder(16, 14, 16, 14));
        feed.add(Box.createVerticalGlue());

        scroll = new JScrollPane(feed);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getViewport().setBackground(Theme.BG);
        scroll.getVerticalScrollBar().setUnitIncrement(18);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        root.add(scroll, BorderLayout.CENTER);

        composer = new ComposerBar(this::onSend, this::playPreview);
        root.add(composer, BorderLayout.SOUTH);
        setContentPane(root);

        addBotText("Send a voice note and I will say whether the speaker sounds male or female.",
                "I measure pitch (YIN + autocorrelation), spectral centroid, and LPC formants. No AI model.");
        addBotText("Tap the mic, speak naturally for 1-3 seconds, then send.",
                "You can replay any recorded note from its play button.");
    }

    private JComponent buildHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(Theme.HEADER);
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.LINE),
                BorderFactory.createEmptyBorder(14, 18, 14, 18)));

        JPanel titles = new JPanel();
        titles.setOpaque(false);
        titles.setLayout(new BoxLayout(titles, BoxLayout.Y_AXIS));
        JLabel title = new JLabel("Voice Gender Chat");
        title.setForeground(Theme.TEXT);
        title.setFont(Theme.TITLE);
        JLabel sub = new JLabel("DSP-501  ·  F0  ·  spectrum  ·  LPC");
        sub.setForeground(Theme.MUTED);
        sub.setFont(Theme.SMALL);
        titles.add(title);
        titles.add(Box.createVerticalStrut(2));
        titles.add(sub);

        JLabel badge = new JLabel("no AI");
        badge.setForeground(Theme.ACCENT);
        badge.setFont(Theme.MICRO);
        badge.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.ACCENT.darker(), 1, true),
                BorderFactory.createEmptyBorder(3, 8, 3, 8)));

        header.add(titles, BorderLayout.CENTER);
        header.add(badge, BorderLayout.EAST);
        return header;
    }

    private void onSend(VoiceNote note) {
        VoiceBubble bubble = new VoiceBubble(note, this::togglePlay);
        addRow(bubble, true);
        JPanel thinking = addBotText("Analyzing with DSP…", "Pitch, centroid, formants");

        new Thread(() -> {
            GenderAnalyzer.Result result = GenderAnalyzer.analyze(note.analysisSamples, note.analysisRate);
            SwingUtilities.invokeLater(() -> {
                feed.remove(thinking);
                addRow(new ResultBubble(result), false);
                feed.revalidate();
                feed.repaint();
                scrollDown();
            });
        }, "dsp-analyze").start();
    }

    private void playPreview(VoiceNote note) {
        togglePlay(null, note);
    }

    private void togglePlay(VoiceBubble bubble, VoiceNote note) {
        if (player.isPlaying() && playingBubble == bubble) {
            player.stop();
            if (playingBubble != null) {
                playingBubble.setPlaying(false, 0);
            }
            playingBubble = null;
            return;
        }
        if (playingBubble != null) {
            playingBubble.setPlaying(false, 0);
        }
        playingBubble = bubble;
        if (bubble != null) {
            bubble.setPlaying(true, 0);
        }
        try {
            player.play(note, p -> SwingUtilities.invokeLater(() -> {
                if (playingBubble != null) {
                    playingBubble.setPlaying(true, p);
                }
                composer.setPreviewProgress(p);
            }), () -> SwingUtilities.invokeLater(() -> {
                if (playingBubble != null) {
                    playingBubble.setPlaying(false, 0);
                }
                playingBubble = null;
                composer.setPreviewProgress(0);
            }));
        } catch (LineUnavailableException e) {
            addBotText("Could not play audio.", e.getMessage());
        }
    }

    private JPanel addBotText(String title, String body) {
        return addRow(new TextBubble(title, body), false);
    }

    private JPanel addRow(JComponent bubble, boolean user) {
        JPanel row = new JPanel(new BorderLayout());
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, bubble.getPreferredSize().height + 18));
        if (user) {
            row.add(bubble, BorderLayout.EAST);
        } else {
            row.add(bubble, BorderLayout.WEST);
        }
        feed.add(row);
        feed.revalidate();
        feed.repaint();
        scrollDown();
        return row;
    }

    private void scrollDown() {
        SwingUtilities.invokeLater(() -> {
            scroll.getVerticalScrollBar().setValue(scroll.getVerticalScrollBar().getMaximum());
        });
    }

        private static final class TextBubble extends RoundedPanel {
        TextBubble(String heading, String body) {
            super(Theme.BOT_BUBBLE, 18);
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
            setBorder(BorderFactory.createEmptyBorder(10, 14, 12, 14));
            add(label(heading, Theme.TEXT, Theme.UI));
            add(Box.createVerticalStrut(4));
            add(label(body, Theme.MUTED, Theme.SMALL));
            setPreferredSize(new Dimension(340, 64));
            setMaximumSize(new Dimension(340, 80));
        }
    }

    private static final class VoiceBubble extends RoundedPanel {
        private final VoiceNote note;
        private final PlayButton play = new PlayButton();
        private final WaveformPanel wave = new WaveformPanel();
        private boolean playing;

        VoiceBubble(VoiceNote note, PlayHandler handler) {
            super(Theme.USER_BUBBLE, 20);
            this.note = note;
            setLayout(new GridBagLayout());
            setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 12));
            wave.setSamples(note.analysisSamples);
            wave.setColors(new Color(255, 255, 255, 140), Color.WHITE);
            wave.setPreferredSize(new Dimension(150, 34));
            play.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    handler.toggle(VoiceBubble.this, note);
                }
            });

            JLabel time = label(note.durationLabel(), Color.WHITE, Theme.MICRO);
            GridBagConstraints c = new GridBagConstraints();
            c.insets = new Insets(0, 4, 0, 8);
            c.gridx = 0;
            add(play, c);
            c.gridx = 1;
            c.weightx = 1;
            c.fill = GridBagConstraints.HORIZONTAL;
            c.insets = new Insets(0, 0, 0, 8);
            add(wave, c);
            c.gridx = 2;
            c.weightx = 0;
            c.fill = GridBagConstraints.NONE;
            add(time, c);
            setPreferredSize(new Dimension(268, 54));
            setMaximumSize(new Dimension(300, 58));
        }

        void setPlaying(boolean playing, double progress) {
            this.playing = playing;
            play.setPlaying(playing);
            wave.setProgress(playing ? progress : 0);
        }
    }

    private static final class ResultBubble extends RoundedPanel {
        ResultBubble(GenderAnalyzer.Result result) {
            super(Theme.BOT_BUBBLE, 18);
            setLayout(new BorderLayout(10, 0));
            setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));

            Color accent = switch (result.gender) {
                case MALE -> Theme.MALE;
                case FEMALE -> Theme.FEMALE;
                case UNCERTAIN -> Theme.UNCERTAIN;
            };

            JComponent avatar = new GenderMark(result.gender, accent);
            JPanel text = new JPanel();
            text.setOpaque(false);
            text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
            JLabel gender = label(result.gender.label, accent, Theme.UI_BOLD);
            gender.setFont(Theme.UI_BOLD.deriveFont(17f));
            String conf = result.gender == GenderAnalyzer.Gender.UNCERTAIN
                    ? result.summary
                    : String.format("%.0f%% confidence", result.confidence * 100);
            JLabel line2 = label(conf, Theme.TEXT, Theme.SMALL);
            javax.swing.JTextArea line3 = new javax.swing.JTextArea(result.summary);
            line3.setEditable(false);
            line3.setOpaque(false);
            line3.setLineWrap(true);
            line3.setWrapStyleWord(true);
            line3.setForeground(Theme.MUTED);
            line3.setFont(Theme.MICRO);
            line3.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));
            line3.setAlignmentX(Component.LEFT_ALIGNMENT);
            text.add(gender);
            text.add(Box.createVerticalStrut(2));
            text.add(line2);
            text.add(line3);

            add(avatar, BorderLayout.WEST);
            add(text, BorderLayout.CENTER);
            setPreferredSize(new Dimension(340, 96));
            setMaximumSize(new Dimension(360, 120));
        }
    }

    private static JLabel label(String text, Color color, Font font) {
        JLabel l = new JLabel(text);
        l.setForeground(color);
        l.setFont(font);
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        return l;
    }

    @FunctionalInterface
    private interface PlayHandler {
        void toggle(VoiceBubble bubble, VoiceNote note);
    }

    private static class RoundedPanel extends JPanel {
        private final Color fill;
        private final int arc;

        RoundedPanel(Color fill, int arc) {
            this.fill = fill;
            this.arc = arc;
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(fill);
            g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), arc, arc));
            g2.dispose();
            super.paintComponent(g);
        }
    }

    private static final class PlayButton extends JComponent {
        private boolean playing;

        PlayButton() {
            setPreferredSize(new Dimension(32, 32));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setOpaque(false);
        }

        void setPlaying(boolean playing) {
            this.playing = playing;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int s = Math.min(getWidth(), getHeight());
            g2.setColor(new Color(255, 255, 255, 40));
            g2.fillOval(0, 0, s - 1, s - 1);
            g2.setColor(Color.WHITE);
            if (playing) {
                g2.fillRoundRect(10, 9, 4, 14, 2, 2);
                g2.fillRoundRect(18, 9, 4, 14, 2, 2);
            } else {
                int[] xs = {12, 12, 23};
                int[] ys = {8, 24, 16};
                g2.fillPolygon(xs, ys, 3);
            }
            g2.dispose();
        }
    }

    private static final class GenderMark extends JComponent {
        private final GenderAnalyzer.Gender gender;
        private final Color accent;

        GenderMark(GenderAnalyzer.Gender gender, Color accent) {
            this.gender = gender;
            this.accent = accent;
            setPreferredSize(new Dimension(42, 42));
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 40));
            g2.fillOval(0, 0, 42, 42);
            g2.setColor(accent);
            g2.setFont(Theme.UI_BOLD.deriveFont(16f));
            String s = switch (gender) {
                case MALE -> "♂";
                case FEMALE -> "♀";
                case UNCERTAIN -> "?";
            };
            FontMetrics fm = g2.getFontMetrics();
            int x = (42 - fm.stringWidth(s)) / 2;
            int y = (42 - fm.getHeight()) / 2 + fm.getAscent();
            g2.drawString(s, x, y);
            g2.dispose();
        }
    }

    private final class ComposerBar extends JPanel {
        private enum Mode {IDLE, RECORDING, REVIEW}

        private Mode mode = Mode.IDLE;
        private final MicrophoneRecorder recorder = new MicrophoneRecorder();
        private VoiceNote draft;
        private final JLabel status = new JLabel("Tap the mic and speak");
        private final WaveformPanel previewWave = new WaveformPanel();
        private final CircleButton recordBtn = new CircleButton();
        private final JButton sendBtn = pill("Send", Theme.ACCENT, Theme.BG);
        private final JButton discardBtn = pill("Discard", Theme.SURFACE, Theme.TEXT);
        private final JButton playBtn = pill("Play", Theme.SURFACE, Theme.TEXT);
        private final JPanel meter = new LevelMeter();
        private final Timer recTimer;
        private long recStarted;
        private final Consumer<VoiceNote> onSend;
        private final Consumer<VoiceNote> onPlay;

        ComposerBar(Consumer<VoiceNote> onSend, Consumer<VoiceNote> onPlay) {
            this.onSend = onSend;
            this.onPlay = onPlay;
            setBackground(Theme.HEADER);
            setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.LINE),
                    BorderFactory.createEmptyBorder(12, 14, 16, 14)));
            setLayout(new BorderLayout(10, 8));

            status.setForeground(Theme.MUTED);
            status.setFont(Theme.SMALL);
            previewWave.setColors(Theme.MUTED, Theme.ACCENT);
            previewWave.setVisible(false);
            previewWave.setPreferredSize(new Dimension(180, 28));

            JPanel top = new JPanel(new BorderLayout(8, 0));
            top.setOpaque(false);
            top.add(status, BorderLayout.WEST);
            top.add(previewWave, BorderLayout.CENTER);
            add(top, BorderLayout.NORTH);

            JPanel actions = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
            actions.setOpaque(false);
            actions.add(discardBtn);
            actions.add(recordBtn);
            actions.add(playBtn);
            actions.add(sendBtn);
            add(actions, BorderLayout.CENTER);
            add(meter, BorderLayout.SOUTH);

            recTimer = new Timer(100, e -> {
                if (mode == Mode.RECORDING) {
                    double sec = (System.currentTimeMillis() - recStarted) / 1000.0;
                    status.setText(String.format("Recording…  %.1fs", sec));
                    if (sec >= 12) {
                        stopRecording();
                    }
                }
            });

            recorder.setLevelListener(rms -> SwingUtilities.invokeLater(() -> ((LevelMeter) meter).setLevel(rms)));

            recordBtn.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (mode == Mode.IDLE || mode == Mode.REVIEW) {
                        startRecording();
                    } else {
                        stopRecording();
                    }
                }
            });
            sendBtn.addActionListener(e -> {
                if (draft != null) {
                    onSend.accept(draft);
                    resetIdle();
                }
            });
            discardBtn.addActionListener(e -> {
                player.stop();
                resetIdle();
            });
            playBtn.addActionListener(e -> {
                if (draft != null) {
                    onPlay.accept(draft);
                }
            });
            applyMode();
        }

        void setPreviewProgress(double p) {
            previewWave.setProgress(p);
        }

        private void startRecording() {
            player.stop();
            draft = null;
            try {
                recorder.start();
                recStarted = System.currentTimeMillis();
                mode = Mode.RECORDING;
                recTimer.start();
                applyMode();
            } catch (LineUnavailableException e) {
                status.setText("Microphone unavailable: " + e.getMessage());
                status.setForeground(Theme.RECORD);
            }
        }

        private void stopRecording() {
            recTimer.stop();
            VoiceNote note = recorder.stop();
            if (note == null) {
                status.setForeground(Theme.MUTED);
                status.setText("Too short — try again");
                mode = Mode.IDLE;
                applyMode();
                return;
            }
            draft = note;
            previewWave.setSamples(note.analysisSamples);
            mode = Mode.REVIEW;
            applyMode();
        }

        private void resetIdle() {
            recTimer.stop();
            draft = null;
            mode = Mode.IDLE;
            ((LevelMeter) meter).setLevel(0);
            applyMode();
        }

        private void applyMode() {
            status.setForeground(Theme.MUTED);
            previewWave.setVisible(mode == Mode.REVIEW);
            sendBtn.setVisible(mode == Mode.REVIEW);
            discardBtn.setVisible(mode == Mode.REVIEW);
            playBtn.setVisible(mode == Mode.REVIEW);
            recordBtn.setRecording(mode == Mode.RECORDING);
            switch (mode) {
                case IDLE -> status.setText("Tap the mic and speak");
                case RECORDING -> status.setText("Recording…");
                case REVIEW -> status.setText("Preview, then send  ·  " + draft.durationLabel());
            }
            revalidate();
            repaint();
        }

        private JButton pill(String text, Color bg, Color fg) {
            JButton b = new JButton(text) {
                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(getBackground());
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), 18, 18);
                    g2.dispose();
                    super.paintComponent(g);
                }
            };
            b.setBackground(bg);
            b.setForeground(fg);
            b.setFont(Theme.SMALL);
            b.setFocusPainted(false);
            b.setBorderPainted(false);
            b.setContentAreaFilled(false);
            b.setOpaque(false);
            b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            b.setBorder(BorderFactory.createEmptyBorder(8, 16, 8, 16));
            return b;
        }
    }

    private static final class CircleButton extends JComponent {
        private boolean recording;

        CircleButton() {
            setPreferredSize(new Dimension(64, 64));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setOpaque(false);
            setToolTipText("Record");
        }

        void setRecording(boolean recording) {
            this.recording = recording;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int s = Math.min(getWidth(), getHeight()) - 2;
            Color fill = recording ? Theme.RECORD : Theme.ACCENT;
            g2.setColor(new Color(fill.getRed(), fill.getGreen(), fill.getBlue(), 50));
            g2.fillOval(0, 0, s, s);
            g2.setColor(fill);
            g2.fillOval(6, 6, s - 12, s - 12);
            if (recording) {
                g2.setColor(Color.WHITE);
                g2.fillRoundRect(s / 2 - 8, s / 2 - 8, 16, 16, 4, 4);
            } else {
                WaveformPanel.paintMic(g2, s / 2, s / 2, 12, Theme.BG);
            }
            g2.dispose();
        }
    }

    private static final class LevelMeter extends JPanel {
        private double level;

        LevelMeter() {
            setOpaque(false);
            setPreferredSize(new Dimension(100, 8));
        }

        void setLevel(double rms) {
            this.level = Math.min(1.0, rms * 4);
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth();
            int h = getHeight();
            g2.setColor(Theme.SURFACE);
            g2.fillRoundRect(0, 2, w, h - 4, 6, 6);
            int fw = (int) Math.round(w * level);
            g2.setColor(Theme.ACCENT);
            g2.setStroke(new BasicStroke(h - 4, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            if (fw > 4) {
                g2.fillRoundRect(0, 2, fw, h - 4, 6, 6);
            }
            g2.dispose();
        }
    }
}
