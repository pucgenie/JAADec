package net.sourceforge.jaad.aac.sbr;

import net.sourceforge.jaad.aac.DecoderConfig;
import net.sourceforge.jaad.aac.ps.PS;
import net.sourceforge.jaad.aac.syntax.BitStream;

import java.util.Arrays;

/**
 * Created by IntelliJ IDEA.
 * User: stueken
 * Date: 13.05.21
 * Time: 19:12
 */
public class SBR1 extends SBR {

	static final int EXTENSION_ID_PS = 2;

    final Channel ch0;
   	final Channel ch1;

   	final SynthesisFilterbank qmfs0;
   	SynthesisFilterbank qmfs1;

	PS ps;
	boolean ps_used;
	boolean psResetFlag;

    public SBR1(DecoderConfig config) {
        super(config);

        ch0 = new Channel();
        qmfs0 = new SynthesisFilterbank((downSampledSBR) ? 32 : 64);

        ch1 = new Channel();
        qmfs1 = new SynthesisFilterbank((downSampledSBR) ? 32 : 64);
    }


    /* table 5 */
   	protected int sbr_data(BitStream ld) {
   		int result;

   		if(ld.readBool()) {
   			ld.readBits(4); //reserved
   		}

   		if((result = sbr_grid(ld, ch0))>0)
   			return result;

   		sbr_dtdf(ld, ch0);
   		invf_mode(ld, ch0);
   		sbr_envelope(ld, ch0, false);
   		sbr_noise(ld, ch0, false);

   		NoiseEnvelope.dequantChannel(this, ch0);

   		Arrays.fill(ch0.bs_add_harmonic, 0, 64, 0);
   		//Arrays.fill(ch1.bs_add_harmonic, 0, 64, 0);

   		ch0.bs_add_harmonic_flag = ld.readBool();
   		if(ch0.bs_add_harmonic_flag)
   			sinusoidal_coding(ld, ch0);

		readExtendedData(ld);

   		return 0;
   	}

	protected void sbr_extension(BitStream ld, int bs_extension_id) {
   		if(bs_extension_id==EXTENSION_ID_PS) {
			if(ps==null) {
				this.ps = new PS(this);
				this.qmfs1 = new SynthesisFilterbank((downSampledSBR) ? 32 : 64);
			}
			if(this.psResetFlag) {
				this.ps.header_read = false;
			}
			ps.decode(ld);

			/* enable PS if and only if: a header has been decoded */
			if(!ps_used&&ps.header_read) {
				this.ps_used = true;
			}

			if(ps.header_read) {
				this.psResetFlag = false;
			}

		} else
			super.sbr_extension(ld, bs_extension_id);
	}

	public void process(float[] left_chan, float[] right_chan) {
		if(ps_used) {
			processPS(left_chan, right_chan);
		} else {
			process(left_chan);
			System.arraycopy(left_chan, 0, right_chan, 0, right_chan.length);
		}
	}


	public void process(float[] channel) {
		float[][][] X = new float[MAX_NTSR][64][2];

		sbr_process_channel(channel, X, ch0, this.reset);

		/* subband synthesis */
		if(downSampledSBR) {
			qmfs0.sbr_qmf_synthesis_32(this, X, channel);
		}
		else {
			qmfs0.sbr_qmf_synthesis_64(this, X, channel);
		}

		if(this.hdr!=null) {
			sbr_save_prev_data(ch0);
		}

		sbr_save_matrix(ch0);

		this.frame++;
	}

	public int processPS(float[] left_channel, float[] right_channel) {
		int ret = 0;
		float[][][] X_left = new float[38][64][2];
		float[][][] X_right = new float[38][64][2];

		sbr_process_channel(left_channel, X_left, ch0, this.reset);

		/* copy some extra data for PS */
		for(int l = this.numTimeSlotsRate; l<this.numTimeSlotsRate+6; l++) {
			for(int k = 0; k<5; k++) {
				X_left[l][k][0] = ch0.Xsbr[this.tHFAdj+l][k][0];
				X_left[l][k][1] = ch0.Xsbr[this.tHFAdj+l][k][1];
			}
		}

		/* perform parametric stereo */
		ps.process(X_left, X_right);

		/* subband synthesis */
		if(downSampledSBR) {
			qmfs0.sbr_qmf_synthesis_32(this, X_left, left_channel);
			qmfs1.sbr_qmf_synthesis_32(this, X_right, right_channel);
		}
		else {
			qmfs0.sbr_qmf_synthesis_64(this, X_left, left_channel);
			qmfs1.sbr_qmf_synthesis_64(this, X_right, right_channel);
		}

		if(this.hdr!=null&&ret==0) {
			sbr_save_prev_data(ch0);
		}

		sbr_save_matrix(ch0);

		this.frame++;

		return 0;
	}

	public boolean isPSUsed() {
		return ps_used;
	}

}
