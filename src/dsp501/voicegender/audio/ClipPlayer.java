package dsp501.voicegender.audio;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import java.util.function.DoubleConsumer;

public final class ClipPlayer {

    private Thread worker;
    private SourceDataLine line;
    private volatile boolean playing;
    private volatile boolean stopRequested;

    public synchronized void play(VoiceNote note, DoubleConsumer progress, Runnable onDone)
            throws LineUnavailableException {
        stop();
        stopRequested = false;
        playing = true;
        AudioFormat format = note.format;
        DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
        line = (SourceDataLine) AudioSystem.getLine(info);
        line.open(format);
        line.start();

        worker = new Thread(() -> {
            try {
                byte[] pcm = note.pcm;
                int frameSize = format.getFrameSize();
                int chunk = Math.max(frameSize * 128, frameSize);
                int offset = 0;
                while (offset < pcm.length && !stopRequested) {
                    int n = Math.min(chunk, pcm.length - offset);
                    n -= n % frameSize;
                    if (n <= 0) {
                        break;
                    }
                    line.write(pcm, offset, n);
                    offset += n;
                    if (progress != null && pcm.length > 0) {
                        progress.accept(offset / (double) pcm.length);
                    }
                }
                if (!stopRequested) {
                    line.drain();
                }
            } catch (Exception ignored) {
            } finally {
                playing = false;
                closeLine();
                if (onDone != null) {
                    onDone.run();
                }
            }
        }, "clip-player");
        worker.setDaemon(true);
        worker.start();
    }

    public synchronized void stop() {
        stopRequested = true;
        closeLine();
        if (worker != null) {
            try {
                worker.join(400);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            worker = null;
        }
        playing = false;
    }

    public boolean isPlaying() {
        return playing;
    }

    private void closeLine() {
        if (line != null) {
            try {
                line.stop();
                line.flush();
                line.close();
            } catch (Exception ignored) {
            }
            line = null;
        }
    }
}
