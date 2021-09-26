package net.sourceforge.jaad.aac.ps;

import net.sourceforge.jaad.aac.syntax.BitStream;

/**
 * Created by IntelliJ IDEA.
 * User: stueken
 * Date: 25.09.21
 * Time: 18:05
 */
class ExtData {

    boolean enabled = false;

    final PDData ipd = PDMode.IpdData();
    final PDData opd = PDMode.OpdData();

    void setMode(IIDMode mode) {
        ipd.setMode(mode);
        opd.setMode(mode);
    }

    void readData(BitStream ld, int num_env) {
        enabled = ld.readBool();
        if (enabled) {
            ipd.readData(ld, num_env);
            opd.readData(ld, num_env);
        }
        ld.readBit(); //reserved
    }

    void decode(int num_env) {
        if (enabled) {
            ipd.decode(num_env);
            opd.decode(num_env);
        }
    }

    void update(int num_env) {
        ipd.update(num_env);
        opd.update(num_env);
    }

    void restore(int num_env) {
        ipd.update(num_env);
        opd.update(num_env);
    }
}
