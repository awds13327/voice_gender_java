package dsp501.voicegender;

import dsp501.voicegender.dsp.GenderAnalyzer;
import dsp501.voicegender.ui.ChatFrame;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;

public final class App {

    public static void main(String[] args) {
        if (args.length > 0 && "--self-test".equals(args[0])) {
            int code = selfTest() ? 0 : 1;
            System.exit(code);
            return;
        }
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
            } catch (Exception ignored) {
            }
            ChatFrame frame = new ChatFrame();
            frame.setVisible(true);
        });
    }

    static boolean selfTest() {
        GenderAnalyzer.Result male = GenderAnalyzer.analyze(
                GenderAnalyzer.harmonicBuzz(120, GenderAnalyzer.TARGET_RATE, 1.6),
                GenderAnalyzer.TARGET_RATE);
        GenderAnalyzer.Result female = GenderAnalyzer.analyze(
                GenderAnalyzer.harmonicBuzz(210, GenderAnalyzer.TARGET_RATE, 1.6),
                GenderAnalyzer.TARGET_RATE);
        System.out.printf("120 Hz buzz -> %s  F0=%.1f Hz%n", male.gender.label, male.f0Hz);
        System.out.printf("210 Hz buzz -> %s  F0=%.1f Hz%n", female.gender.label, female.f0Hz);
        boolean ok = male.gender == GenderAnalyzer.Gender.MALE
                && female.gender == GenderAnalyzer.Gender.FEMALE;
        System.out.println(ok ? "SELF-TEST PASS" : "SELF-TEST FAIL");
        return ok;
    }
}
