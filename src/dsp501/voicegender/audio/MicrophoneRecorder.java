package dsp501.voicegender.audio;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.TargetDataLine;
import java.io.ByteArrayOutputStream;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.DoubleConsumer;

public final class MicrophoneRecorder {

    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile DoubleConsumer levelListener;
    private Thread worker;
    private TargetDataLine line;
    private ByteArrayOutputStream buffer;
    private AudioFormat format;
    private volatile Exception error;

    public synchronized void start() throws LineUnavailableException {
        if (running.get()) {
            return;
        }
        error = null;
        format = openLine();
        buffer = new ByteArrayOutputStream();
        running.set(true);
        worker = new Thread(this::captureLoop, "mic-capture");
        worker.setDaemon(true);
        worker.start();
    }

    public synchronized VoiceNote stop() {
        running.set(false);
        if (line != null) {
            try {
                line.stop();
                line.flush();
                line.close();
            } catch (Exception ignored) {
            }
            line = null;
        }
        if (worker != null) {
            try {
                worker.join(1500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            worker = null;
        }
        if (error != null) {
            throw new IllegalStateException("Microphone error: " + error.getMessage(), error);
        }
        if (buffer == null || format == null) {
            return null;
        }
        byte[] pcm = buffer.toByteArray();
        if (pcm.length < format.getFrameSize() * 400) {
            return null;
        }
        return VoiceNote.fromCapture(pcm, format);
    }

    public synchronized void cancel() {
        stop();
    }

    public void setLevelListener(DoubleConsumer levelListener) {
        this.levelListener = levelListener;
    }

    public boolean isRunning() {
        return running.get();
    }

    public AudioFormat getFormat() {
        return format;
    }

    private void captureLoop() {
        byte[] chunk = new byte[Math.max(512, format.getFrameSize() * 256)];
        try {
            line.start();
            while (running.get()) {
                int n = line.read(chunk, 0, chunk.length);
                if (n > 0) {
                    synchronized (this) {
                        buffer.write(chunk, 0, n);
                    }
                    DoubleConsumer listener = levelListener;
                    if (listener != null) {
                        listener.accept(rms16(chunk, n));
                    }
                }
            }
        } catch (Exception e) {
            error = e;
            running.set(false);
        }
    }

    private AudioFormat openLine() throws LineUnavailableException {
        float[] rates = {16000f, 44100f, 48000f, 22050f};
        int[] channels = {1, 2};
        LineUnavailableException last = null;

        for (float rate : rates) {
            for (int ch : channels) {
                AudioFormat fmt = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, rate, 16, ch,
                        ch * 2, rate, false);
                DataLine.Info info = new DataLine.Info(TargetDataLine.class, fmt);
                if (!AudioSystem.isLineSupported(info)) {
                    continue;
                }
                try {
                    line = (TargetDataLine) AudioSystem.getLine(info);
                    line.open(fmt, (int) (rate * ch * 2 * 0.25));
                    return fmt;
                } catch (LineUnavailableException e) {
                    last = e;
                    closeQuietly();
                }
            }
        }

        Mixer.Info[] mixers = AudioSystem.getMixerInfo();
        for (Mixer.Info mixerInfo : mixers) {
            Mixer mixer = AudioSystem.getMixer(mixerInfo);
            for (float rate : rates) {
                AudioFormat fmt = AudioIo.pcmMono(rate);
                DataLine.Info info = new DataLine.Info(TargetDataLine.class, fmt);
                if (!mixer.isLineSupported(info)) {
                    continue;
                }
                try {
                    line = (TargetDataLine) mixer.getLine(info);
                    line.open(fmt);
                    return fmt;
                } catch (Exception e) {
                    closeQuietly();
                }
            }
        }

        if (last != null) {
            throw last;
        }
        throw new LineUnavailableException("No compatible microphone line found.");
    }

    private static double rms16(byte[] pcm, int len) {
        int n = len / 2;
        if (n <= 0) {
            return 0;
        }
        double e = 0;
        for (int i = 0; i + 1 < len; i += 2) {
            int s = (short) ((pcm[i + 1] << 8) | (pcm[i] & 0xff));
            double v = s / 32768.0;
            e += v * v;
        }
        return Math.sqrt(e / n);
    }

    private void closeQuietly() {
        if (line != null) {
            try {
                line.close();
            } catch (Exception ignored) {
            }
            line = null;
        }
    }
}
