package net.sourceforge.jaad.aac;

import java.util.Collection;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: stueken
 * Date: 17.10.20
 * Time: 17:09
 */
public interface Receiver {

    void accept(Collection<float[]> samples, int sampleLength, int sampleRate);
}
