import net.sourceforge.jaad.Play;
import net.sourceforge.jaad.mp4.MP4Input;
import org.testng.annotations.Test;

import javax.sound.sampled.LineUnavailableException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

public class PlayGoldDust extends Play {

    @Test
    public void testPlayMP4() {
        try (var instream = new FileInputStream(new File(System.getenv("CD"), "mzaf_8033856765369564727.plus.aac.ep.m4a"))) {
            decodeMP4(MP4Input.open(instream));
        } catch (FileNotFoundException e) {
            throw new RuntimeException("Environment missing sound file.", e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (LineUnavailableException e) {
            throw new RuntimeException("No audio output possible.", e);
        }
    }

    @Test
    public void testPlayAAC() {
        try (var instream = new FileInputStream(new File(System.getenv("CD"), "mzaf_8033856765369564727.plus.aac.ep.aac"))) {
            decodeAAC(instream);
        } catch (FileNotFoundException e) {
            throw new RuntimeException("Environment missing sound file.", e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (LineUnavailableException e) {
            throw new RuntimeException("No audio output possible.", e);
        }
    }
}
