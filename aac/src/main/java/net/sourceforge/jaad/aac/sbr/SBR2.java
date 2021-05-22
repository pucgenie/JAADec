package net.sourceforge.jaad.aac.sbr;

import net.sourceforge.jaad.aac.DecoderConfig;
import net.sourceforge.jaad.aac.syntax.BitStream;

import java.util.Arrays;

/**
 * Created by IntelliJ IDEA.
 * User: stueken
 * Date: 13.05.21
 * Time: 19:24
 */
public class SBR2 extends SBR {

    final Channel ch0;
   	final Channel ch1;

	boolean bs_coupling;

   	final SynthesisFilterbank qmfs0;
   	SynthesisFilterbank qmfs1;

    public SBR2(DecoderConfig config) {
        super(config);

        ch0 = new Channel();
        qmfs0 = new SynthesisFilterbank((downSampledSBR) ? 32 : 64);

        ch1 = new Channel();
        qmfs1 = new SynthesisFilterbank((downSampledSBR) ? 32 : 64);
    }

    /* table 6 */
    protected int sbr_data(BitStream ld) {
   		int result;

   		if(ld.readBool()) {
   			//reserved
   			ld.readBits(4);
   			ld.readBits(4);
   		}

   		this.bs_coupling = ld.readBool();

   		if(this.bs_coupling) {
   			if((result = sbr_grid(ld, ch0))>0)
   				return result;

   			/* need to copy some data from left to right */
   			ch1.bs_frame_class = ch0.bs_frame_class;
   			ch1.L_E = ch0.L_E;
   			ch1.L_Q = ch0.L_Q;
   			ch1.bs_pointer = ch0.bs_pointer;

   			for(int n = 0; n<=ch0.L_E; n++) {
   				ch1.t_E[n] = ch0.t_E[n];
   				ch1.f[n] = ch0.f[n];
   			}
   			for(int n = 0; n<=ch0.L_Q; n++) {
   				ch1.t_Q[n] = ch0.t_Q[n];
   			}

   			sbr_dtdf(ld, ch0);
   			sbr_dtdf(ld, ch1);
   			invf_mode(ld, ch0);

   			/* more copying */
   			for(int n = 0; n<this.N_Q; n++) {
   				ch1.bs_invf_mode[n] = ch0.bs_invf_mode[n];
   			}

   			sbr_envelope(ld, ch0, false);
   			sbr_noise(ld, ch0, false);
   			sbr_envelope(ld, ch1, this.bs_coupling);
   			sbr_noise(ld, ch1, this.bs_coupling);

   			Arrays.fill(ch0.bs_add_harmonic, 0, 64, 0);
   			Arrays.fill(ch1.bs_add_harmonic, 0, 64, 0);

   			ch0.bs_add_harmonic_flag = ld.readBool();
   			if(ch0.bs_add_harmonic_flag)
   				sinusoidal_coding(ld, ch0);

   			ch1.bs_add_harmonic_flag = ld.readBool();
   			if(ch1.bs_add_harmonic_flag)
   				sinusoidal_coding(ld, ch1);
   		}
   		else {
   			int[] saved_t_E = new int[6], saved_t_Q = new int[3];
   			int saved_L_E = ch0.L_E;
   			int saved_L_Q = ch0.L_Q;
   			FrameClass saved_frame_class = ch0.bs_frame_class;

   			for(int n = 0; n<saved_L_E; n++) {
   				saved_t_E[n] = ch0.t_E[n];
   			}
   			for(int n = 0; n<saved_L_Q; n++) {
   				saved_t_Q[n] = ch0.t_Q[n];
   			}

   			if((result = sbr_grid(ld, ch0))>0)
   				return result;
   			if((result = sbr_grid(ld, ch1))>0) {
   				/* restore first channel data as well */
   				ch0.bs_frame_class = saved_frame_class;
   				ch0.L_E = saved_L_E;
   				ch0.L_Q = saved_L_Q;
   				for(int n = 0; n<6; n++) {
   					ch0.t_E[n] = saved_t_E[n];
   				}
   				for(int n = 0; n<3; n++) {
   					ch0.t_Q[n] = saved_t_Q[n];
   				}

   				return result;
   			}
   			sbr_dtdf(ld, ch0);
   			sbr_dtdf(ld, ch1);
   			invf_mode(ld, ch0);
   			invf_mode(ld, ch1);
   			sbr_envelope(ld, ch0, false);
   			sbr_envelope(ld, ch1, false);
   			sbr_noise(ld, ch0, this.bs_coupling);
   			sbr_noise(ld, ch1, this.bs_coupling);

   			Arrays.fill(ch0.bs_add_harmonic, 0, 64, 0);
   			Arrays.fill(ch1.bs_add_harmonic, 0, 64, 0);

   			ch0.bs_add_harmonic_flag = ld.readBool();
   			if(ch0.bs_add_harmonic_flag)
   				sinusoidal_coding(ld, ch0);

   			ch1.bs_add_harmonic_flag = ld.readBool();
   			if(ch1.bs_add_harmonic_flag)
   				sinusoidal_coding(ld, ch1);
   		}

   		if(!this.bs_coupling) {
			NoiseEnvelope.dequantChannel(this, ch0);
			NoiseEnvelope.dequantChannel(this, ch1);
		} else {
			NoiseEnvelope.unmap(this);
		}

		readExtendedData(ld);

   		return 0;
   	}

	public void process(float[] left_chan, float[] right_chan) {
		float[][][] X = new float[MAX_NTSR][64][2];

		sbr_process_channel(left_chan, X, ch0, this.reset);
		/* subband synthesis */
		if(downSampledSBR) {
			qmfs0.sbr_qmf_synthesis_32(this, X, left_chan);
		}
		else {
			qmfs0.sbr_qmf_synthesis_64(this, X, left_chan);
		}

		sbr_process_channel(right_chan, X, ch1, false);
		/* subband synthesis */
		if(downSampledSBR) {
			qmfs1.sbr_qmf_synthesis_32(this, X, right_chan);
		}
		else {
			qmfs1.sbr_qmf_synthesis_64(this, X, right_chan);
		}

		if(this.hdr!=null) {
			sbr_save_prev_data(ch0);
			sbr_save_prev_data(ch1);
		}

		sbr_save_matrix(ch0);
		sbr_save_matrix(ch1);

		this.frame++;
	}
}
