package net.sourceforge.jaad.aac.ps;

/**
 * Created by IntelliJ IDEA.
 * User: stueken
 * Date: 25.09.21
 * Time: 12:54
 */
public class PDData extends EnvData<PDMode> {

    private final PDMode[] modes;

    PDData(PDMode[] modes) {
        super(34);
        this.modes = modes;
    }

    void setMode(IIDMode mode) {
        this.mode = mode==null ? null : mode(mode.id);
    }

    @Override
    protected PDMode mode(int id) {
        return modes[id];
    }
}
