package net.sourceforge.jaad.aac.sbr;

import net.sourceforge.jaad.aac.AACException;
import net.sourceforge.jaad.aac.DecoderConfig;
import net.sourceforge.jaad.aac.SampleFrequency;
import net.sourceforge.jaad.aac.SampleRate;
import net.sourceforge.jaad.aac.ps.PS;
import net.sourceforge.jaad.aac.syntax.BitStream;

import java.util.Arrays;
import java.util.logging.Logger;

import static net.sourceforge.jaad.aac.sbr.HuffmanTables.*;

public class SBR {

	static final Logger LOGGER = Logger.getLogger("jaad.aac.sbr.SBR"); //for debugging

	static final int EXTENSION_ID_PS = 2;
	static final int MAX_NTSR = 32; //max number_time_slots * rate, ok for DRM and not DRM mode
	static final int MAX_M = 49; //maximum value for M
	public static final int MAX_L_E = 5; //maximum value for L_E

	static final int EXT_SBR_DATA = 13;
	static final int EXT_SBR_DATA_CRC = 14;
	static final int NO_TIME_SLOTS_960 = 15;
	static final int NO_TIME_SLOTS = 16;
	static final int RATE = 2;
	static final int NOISE_FLOOR_OFFSET = 6;
	static final int T_HFGEN = 8;
	static final int T_HFADJ = 2;

	private final boolean downSampledSBR;
	final SampleFrequency sample_rate;

	boolean valid = false;

	public void invalidate() {
		valid = false;
	}

	public boolean isValid() {
		return valid;
	}

	int rate;

	int k0;
	int kx;
	int M;
	int N_master;
	int N_high;
	int N_low;
	int N_Q;
	int[] N_L = new int[4];
	int[] n = new int[2];

	int[] f_master = new int[64];
	int[][] f_table_res = new int[2][64];
	int[] f_table_noise = new int[64];
	int[][] f_table_lim = new int[4][64];

	int[] table_map_k_to_g = new int[64];

	final Channel ch0;
	final Channel ch1;

	final SynthesisFilterbank qmfs0;
	SynthesisFilterbank qmfs1;
	
	int kx_prev;
	int bsco;
	int bsco_prev;
	int M_prev;

	boolean reset;
	int frame;

	boolean stereo;

	int noPatches;
	int[] patchNoSubbands = new int[64];
	int[] patchStartSubband = new int[64];

	int numTimeSlotsRate;
	int numTimeSlots;
	int tHFGen;
	int tHFAdj;

	PS ps;
	boolean ps_used;
	boolean psResetFlag;

	/* to get it compiling
	/* we'll see during the coding of all the tools, whether these are all used or not.
	 */
	int bs_sbr_crc_bits;

	Header hdr = null;
	Header hdr_saved = null;
	
	int bs_samplerate_mode;

	boolean bs_extended_data;
	int bs_extension_id;
	int bs_extension_data;
	boolean bs_coupling;

	public static SBR open(DecoderConfig config, boolean stereo) {
		config.setSBRPresent();
		return new SBR(config.isSmallFrameUsed(), stereo, config.getOutputFrequency(), config.isSBRDownSampled());
	}

	public SBR(boolean smallFrames, boolean stereo, SampleRate sample_rate, boolean downSampledSBR) {
		this.downSampledSBR = downSampledSBR;
		this.stereo = stereo;
		this.sample_rate = sample_rate.getNominal();

		this.bs_samplerate_mode = 1;

		this.tHFGen = T_HFGEN;
		this.tHFAdj = T_HFADJ;

		this.bsco = 0;
		this.bsco_prev = 0;
		this.M_prev = 0;

		if(smallFrames) {
			this.numTimeSlotsRate = RATE*NO_TIME_SLOTS_960;
			this.numTimeSlots = NO_TIME_SLOTS_960;
		}
		else {
			this.numTimeSlotsRate = RATE*NO_TIME_SLOTS;
			this.numTimeSlots = NO_TIME_SLOTS;
		}

		if(stereo) {
			ch0 = new Channel();
			qmfs0 = new SynthesisFilterbank((downSampledSBR) ? 32 : 64);
			
			ch1 = new Channel();
			qmfs1 = new SynthesisFilterbank((downSampledSBR) ? 32 : 64);
		}
		else {
			ch0 = new Channel();
			qmfs0 = new SynthesisFilterbank((downSampledSBR) ? 32 : 64);
			
			ch1 = null;
			qmfs1 = null;
		}
	}

	void sbrReset() {
		int j;
		if(ch0.qmfa!=null)
			ch0.qmfa.reset();
		if(ch1.qmfa!=null)
			ch1.qmfa.reset();
		if(qmfs0!=null)
			qmfs0.reset();
		if(qmfs1!=null)
			qmfs1.reset();

		for(j = 0; j<5; j++) {
			if(ch0.G_temp_prev[j]!=null)
				Arrays.fill(ch0.G_temp_prev[j], 0);
			if(ch1.G_temp_prev[j]!=null)
				Arrays.fill(ch1.G_temp_prev[j], 0);
			if(ch0.Q_temp_prev[j]!=null)
				Arrays.fill(ch0.Q_temp_prev[j], 0);
			if(ch1.Q_temp_prev[j]!=null)
				Arrays.fill(ch1.Q_temp_prev[j], 0);
		}

		for(int i = 0; i<40; i++) {
			for(int k = 0; k<64; k++) {
				ch0.Xsbr[i][j][0] = 0;
				ch0.Xsbr[i][j][1] = 0;
				ch1.Xsbr[i][j][0] = 0;
				ch1.Xsbr[i][j][1] = 0;
			}
		}

		ch0.GQ_ringbuf_index = 0;
		ch1.GQ_ringbuf_index = 0;

		ch0.L_E_prev = 0;
		ch1.L_E_prev = 0;

		this.bs_samplerate_mode = 1;
		ch0.prevEnvIsShort = -1;
		ch1.prevEnvIsShort = -1;
		this.bsco = 0;
		this.bsco_prev = 0;
		this.M_prev = 0;

		ch0.f_prev = 0;
		ch1.f_prev = 0;
		for(j = 0; j<MAX_M; j++) {
			ch0.E_prev[j] = 0;
			ch0.Q_prev[j] = 0;
			ch1.E_prev[j] = 0;
			ch1.Q_prev[j] = 0;
			ch0.bs_add_harmonic_prev[j] = 0;
			ch1.bs_add_harmonic_prev[j] = 0;
		}
		ch0.bs_add_harmonic_flag_prev = false;
		ch1.bs_add_harmonic_flag_prev = false;
	}

	int calc_sbr_tables(Header hdr) {
		int result = 0;
		int k2;

		/* calculate the Master Frequency Table */
		k0 = FBT.qmf_start_channel(hdr.bs_start_freq, this.bs_samplerate_mode, this.sample_rate);
		k2 = FBT.qmf_stop_channel(hdr.bs_stop_freq, this.sample_rate, k0);

		/* check k0 and k2 */
		if(this.sample_rate.getFrequency()>=48000) {
			if((k2-k0)>32)
				result += 1;
		}
		else if(this.sample_rate.getFrequency()<=32000) {
			if((k2-k0)>48)
				result += 1;
		}
		else { /* (sbr.sample_rate == 44100) */

			if((k2-k0)>45)
				result += 1;
		}

		if(hdr.bs_freq_scale==0) {
			result += FBT.master_frequency_table_fs0(this, k0, k2, hdr.bs_alter_scale);
		}
		else {
			result += FBT.master_frequency_table(this, k0, k2, hdr.bs_freq_scale, hdr.bs_alter_scale);
		}
		result += FBT.derived_frequency_table(this, hdr.bs_xover_band, k2);

		result = (result>0) ? 1 : 0;

		return result;
	}

	/* table 2 */
	public void decode(BitStream ld, boolean crc) {

		if(crc) {
			this.bs_sbr_crc_bits = ld.readBits(10);
		} else
			this.bs_sbr_crc_bits = -1;

		reset = readHeader(ld);

		if(reset) {
			int rt = calc_sbr_tables(this.hdr);

			/* if an error occurred with the new header values revert to the old ones */
			if (rt > 0) {
				calc_sbr_tables(swapHeaders());
			}
		}

		if(this.hdr!=null) {
			int result = sbr_data(ld);

			valid = (result==0);
		} else
			valid = true;
	}

	/**
	 * Save current header and return the previously saved header.
	 * @return the saved header.
	 */
	private Header swapHeaders() {

		// save current header and recycle old one (if exists)
		Header hdr = this.hdr_saved;
		this.hdr_saved = this.hdr;

		if(hdr==null)
			hdr = new Header();
		this.hdr = hdr;

		return hdr;
	}

	/**
	 * Read a new header and return if the header parameter changed.
	 * See: 5.3.1 Decoding process.
	 * 
	 * @param ld input data.
	 * @return true if relevant parameters changed.
	 */

	private boolean readHeader(BitStream ld) {
		boolean bs_header_flag = ld.readBool();

		if(bs_header_flag) {
			Header hdr = swapHeaders();
			hdr.decode(ld);
			return hdr.differs(hdr_saved);
		} else
			return false;
	}

	/* table 4 */
	private int sbr_data(BitStream ld) {
		int result;

		this.rate = (this.bs_samplerate_mode!=0) ? 2 : 1;

		if(stereo) {
			if((result = sbr_channel_pair_element(ld))>0)
				return result;
		}
		else {
			if((result = sbr_single_channel_element(ld))>0)
				return result;
		}

		return 0;
	}

	/* table 5 */
	private int sbr_single_channel_element(BitStream ld) {
		int result;

		if(ld.readBool()) {
			ld.readBits(4); //reserved
		}

		if((result = sbr_grid(ld, ch0))>0)
			return result;

		sbr_dtdf(ld, ch0);
		invf_mode(ld, ch0);
		sbr_envelope(ld, ch0);
		sbr_noise(ld, ch0);

		NoiseEnvelope.dequantChannel(this, ch0);

		Arrays.fill(ch0.bs_add_harmonic, 0, 64, 0);
		//Arrays.fill(ch1.bs_add_harmonic, 0, 64, 0);

		ch0.bs_add_harmonic_flag = ld.readBool();
		if(ch0.bs_add_harmonic_flag)
			sinusoidal_coding(ld, ch0);

		this.bs_extended_data = ld.readBool();

		if(this.bs_extended_data) {
			int nr_bits_left;
			int ps_ext_read = 0;
			int cnt = ld.readBits(4);
			if(cnt==15) {
				cnt += ld.readBits(8);
			}

			nr_bits_left = 8*cnt;
			while(nr_bits_left>7) {
				int tmp_nr_bits = 0;

				this.bs_extension_id = ld.readBits(2);
				tmp_nr_bits += 2;

				/* allow only 1 PS extension element per extension data */
				if(this.bs_extension_id==EXTENSION_ID_PS) {
					if(ps_ext_read==0) {
						ps_ext_read = 1;
					}
					else {
						/* to be safe make it 3, will switch to "default"
						 * in sbr_extension() */
						this.bs_extension_id = 3;
					}
				}

				tmp_nr_bits += sbr_extension(ld, this.bs_extension_id, nr_bits_left);

				/* check if the data read is bigger than the number of available bits */
				if(tmp_nr_bits>nr_bits_left)
					return 1;

				nr_bits_left -= tmp_nr_bits;
			}

			/* Corrigendum */
			if(nr_bits_left>0) {
				ld.readBits(nr_bits_left);
			}
		}

		return 0;
	}

	/* table 6 */
	private int sbr_channel_pair_element(BitStream ld) {
		int n, result;

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

			for(n = 0; n<=ch0.L_E; n++) {
				ch1.t_E[n] = ch0.t_E[n];
				ch1.f[n] = ch0.f[n];
			}
			for(n = 0; n<=ch0.L_Q; n++) {
				ch1.t_Q[n] = ch0.t_Q[n];
			}

			sbr_dtdf(ld, ch0);
			sbr_dtdf(ld, ch1);
			invf_mode(ld, ch0);

			/* more copying */
			for(n = 0; n<this.N_Q; n++) {
				ch1.bs_invf_mode[n] = ch0.bs_invf_mode[n];
			}

			sbr_envelope(ld, ch0);
			sbr_noise(ld, ch0);
			sbr_envelope(ld, ch1);
			sbr_noise(ld, ch1);

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

			for(n = 0; n<saved_L_E; n++) {
				saved_t_E[n] = ch0.t_E[n];
			}
			for(n = 0; n<saved_L_Q; n++) {
				saved_t_Q[n] = ch0.t_Q[n];
			}

			if((result = sbr_grid(ld, ch0))>0)
				return result;
			if((result = sbr_grid(ld, ch1))>0) {
				/* restore first channel data as well */
				ch0.bs_frame_class = saved_frame_class;
				ch0.L_E = saved_L_E;
				ch0.L_Q = saved_L_Q;
				for(n = 0; n<6; n++) {
					ch0.t_E[n] = saved_t_E[n];
				}
				for(n = 0; n<3; n++) {
					ch0.t_Q[n] = saved_t_Q[n];
				}

				return result;
			}
			sbr_dtdf(ld, ch0);
			sbr_dtdf(ld, ch1);
			invf_mode(ld, ch0);
			invf_mode(ld, ch1);
			sbr_envelope(ld, ch0);
			sbr_envelope(ld, ch1);
			sbr_noise(ld, ch0);
			sbr_noise(ld, ch1);

			Arrays.fill(ch0.bs_add_harmonic, 0, 64, 0);
			Arrays.fill(ch1.bs_add_harmonic, 0, 64, 0);

			ch0.bs_add_harmonic_flag = ld.readBool();
			if(ch0.bs_add_harmonic_flag)
				sinusoidal_coding(ld, ch0);

			ch1.bs_add_harmonic_flag = ld.readBool();
			if(ch1.bs_add_harmonic_flag)
				sinusoidal_coding(ld, ch1);
		}
		NoiseEnvelope.dequantChannel(this, ch0);
		NoiseEnvelope.dequantChannel(this, ch1);

		if(this.bs_coupling)
			NoiseEnvelope.unmap(this);

		this.bs_extended_data = ld.readBool();
		if(this.bs_extended_data) {
			int nr_bits_left;
			int cnt = ld.readBits(4);
			if(cnt==15) {
				cnt += ld.readBits(8);
			}

			nr_bits_left = 8*cnt;
			while(nr_bits_left>7) {
				int tmp_nr_bits = 0;

				this.bs_extension_id = ld.readBits(2);
				tmp_nr_bits += 2;
				tmp_nr_bits += sbr_extension(ld, this.bs_extension_id, nr_bits_left);

				/* check if the data read is bigger than the number of available bits */
				if(tmp_nr_bits>nr_bits_left)
					return 1;

				nr_bits_left -= tmp_nr_bits;
			}

			/* Corrigendum */
			if(nr_bits_left>0) {
				ld.readBits(nr_bits_left);
			}
		}

		return 0;
	}

	/* integer log[2](x): input range [0,10) */
	private int sbr_log2(int val) {
		int log2tab[] = {0, 0, 1, 2, 2, 3, 3, 3, 3, 4};
		if(val<10&&val>=0)
			return log2tab[val];
		else
			return 0;
	}


	/* table 7 */
	private int sbr_grid(BitStream ld, Channel ch) {
		int i, env, rel, result;
		int bs_abs_bord, bs_abs_bord_1;
		int bs_num_env = 0;
		int saved_L_E = ch.L_E;
		int saved_L_Q = ch.L_Q;
		FrameClass saved_frame_class = ch.bs_frame_class;

		ch.bs_frame_class = FrameClass.read(ld);

		switch(ch.bs_frame_class) {
			case FIXFIX:
				i = ld.readBits(2);

				bs_num_env = Math.min(1<<i, 5);

				i = ld.readBit();
				for(env = 0; env<bs_num_env; env++) {
					ch.f[env] = i;
				}

				ch.abs_bord_lead = 0;
				ch.abs_bord_trail = this.numTimeSlots;
				ch.n_rel_lead = bs_num_env-1;
				ch.n_rel_trail = 0;
				break;

			case FIXVAR:
				bs_abs_bord = ld.readBits(2)+this.numTimeSlots;
				bs_num_env = ld.readBits(2)+1;

				for(rel = 0; rel<bs_num_env-1; rel++) {
					ch.bs_rel_bord[rel] = 2*ld.readBits(2)+2;
				}
				i = sbr_log2(bs_num_env+1);
				ch.bs_pointer = ld.readBits(i);

				for(env = 0; env<bs_num_env; env++) {
					ch.f[bs_num_env-env-1] = ld.readBit();
				}

				ch.abs_bord_lead = 0;
				ch.abs_bord_trail = bs_abs_bord;
				ch.n_rel_lead = 0;
				ch.n_rel_trail = bs_num_env-1;
				break;

			case VARFIX:
				bs_abs_bord = ld.readBits(2);
				bs_num_env = ld.readBits(2)+1;

				for(rel = 0; rel<bs_num_env-1; rel++) {
					ch.bs_rel_bord[rel] = 2*ld.readBits(2)+2;
				}
				i = sbr_log2(bs_num_env+1);
				ch.bs_pointer = ld.readBits(i);

				for(env = 0; env<bs_num_env; env++) {
					ch.f[env] = ld.readBit();
				}

				ch.abs_bord_lead = bs_abs_bord;
				ch.abs_bord_trail = this.numTimeSlots;
				ch.n_rel_lead = bs_num_env-1;
				ch.n_rel_trail = 0;
				break;

			case VARVAR:
				bs_abs_bord = ld.readBits(2);
				bs_abs_bord_1 = ld.readBits(2)+this.numTimeSlots;
				ch.bs_num_rel_0 = ld.readBits(2);
				ch.bs_num_rel_1 = ld.readBits(2);

				bs_num_env = Math.min(5, ch.bs_num_rel_0+ch.bs_num_rel_1+1);

				for(rel = 0; rel<ch.bs_num_rel_0; rel++) {
					ch.bs_rel_bord_0[rel] = 2*ld.readBits(2)+2;
				}
				for(rel = 0; rel<ch.bs_num_rel_1; rel++) {
					ch.bs_rel_bord_1[rel] = 2*ld.readBits(2)+2;
				}
				i = sbr_log2(ch.bs_num_rel_0+ch.bs_num_rel_1+2);
				ch.bs_pointer = ld.readBits(i);

				for(env = 0; env<bs_num_env; env++) {
					ch.f[env] = ld.readBit();
				}

				ch.abs_bord_lead = bs_abs_bord;
				ch.abs_bord_trail = bs_abs_bord_1;
				ch.n_rel_lead = ch.bs_num_rel_0;
				ch.n_rel_trail = ch.bs_num_rel_1;
				break;
		}

		if(ch.bs_frame_class== FrameClass.VARVAR)
			ch.L_E = Math.min(bs_num_env, 5);
		else
			ch.L_E = Math.min(bs_num_env, 4);

		if(ch.L_E<=0)
			return 1;

		if(ch.L_E>1)
			ch.L_Q = 2;
		else
			ch.L_Q = 1;

		/* TODO: this code can probably be integrated into the code above! */
		if((result = TFGrid.envelope_time_border_vector(this, ch))>0) {
			ch.bs_frame_class = saved_frame_class;
			ch.L_E = saved_L_E;
			ch.L_Q = saved_L_Q;
			return result;
		}
		TFGrid.noise_floor_time_border_vector(this, ch);

		return 0;
	}

	/* table 8 */
	private void sbr_dtdf(BitStream ld, Channel ch) {
		int i;

		for(i = 0; i<ch.L_E; i++) {
			ch.bs_df_env[i] = ld.readBit();
		}

		for(i = 0; i<ch.L_Q; i++) {
			ch.bs_df_noise[i] = ld.readBit();
		}
	}

	/* table 9 */
	private void invf_mode(BitStream ld, Channel ch) {
		int n;

		for(n = 0; n<this.N_Q; n++) {
			ch.bs_invf_mode[n] = ld.readBits(2);
		}
	}

	private int sbr_extension(BitStream ld, int bs_extension_id, int num_bits_left) {
		int ret;

		switch(bs_extension_id) {
			case EXTENSION_ID_PS:
				if(ps==null) {
					this.ps = new PS(this.sample_rate, this.numTimeSlotsRate);
					this.qmfs1 = new SynthesisFilterbank((downSampledSBR) ? 32 : 64);
				}
				if(this.psResetFlag) {
					this.ps.header_read = false;
				}
				ret = ps.decode(ld);

				/* enable PS if and only if: a header has been decoded */
				if(!ps_used&&ps.header_read) {
					this.ps_used = true;
				}

				if(ps.header_read) {
					this.psResetFlag = false;
				}

				return ret;
			default:
				this.bs_extension_data = ld.readBits(6);
				return 6;
		}
	}

	/* table 12 */
	private void sinusoidal_coding(BitStream ld, Channel ch) {
		int n;

		for(n = 0; n<this.N_high; n++) {
			ch.bs_add_harmonic[n] = ld.readBit();
		}
	}
	/* table 10 */

	private void sbr_envelope(BitStream ld, Channel ch) {
		int env, band;
		int delta = 0;
		int[][] t_huff, f_huff;

		if((ch.L_E==1)&&(ch.bs_frame_class== FrameClass.FIXFIX))
			ch.amp_res = false;
		else
			ch.amp_res = this.hdr.bs_amp_res;

		if((this.bs_coupling)&&(ch==ch1)) {
			delta = 1;
			if(ch.amp_res) {
				t_huff = T_HUFFMAN_ENV_BAL_3_0DB;
				f_huff = F_HUFFMAN_ENV_BAL_3_0DB;
			}
			else {
				t_huff = T_HUFFMAN_ENV_BAL_1_5DB;
				f_huff = F_HUFFMAN_ENV_BAL_1_5DB;
			}
		}
		else {
			delta = 0;
			if(ch.amp_res) {
				t_huff = T_HUFFMAN_ENV_3_0DB;
				f_huff = F_HUFFMAN_ENV_3_0DB;
			}
			else {
				t_huff = T_HUFFMAN_ENV_1_5DB;
				f_huff = F_HUFFMAN_ENV_1_5DB;
			}
		}

		for(env = 0; env<ch.L_E; env++) {
			if(ch.bs_df_env[env]==0) {
				if(this.bs_coupling&&(ch==ch1)) {
					if(ch.amp_res) {
						ch.E[0][env] = ld.readBits(5)<<delta;
					}
					else {
						ch.E[0][env] = ld.readBits(6)<<delta;
					}
				}
				else {
					if(ch.amp_res) {
						ch.E[0][env] = ld.readBits(6)<<delta;
					}
					else {
						ch.E[0][env] = ld.readBits(7)<<delta;
					}
				}

				for(band = 1; band<this.n[ch.f[env]]; band++) {
					ch.E[band][env] = (decodeHuffman(ld, f_huff)<<delta);
				}

			}
			else {
				for(band = 0; band<this.n[ch.f[env]]; band++) {
					ch.E[band][env] = (decodeHuffman(ld, t_huff)<<delta);
				}
			}
		}

		NoiseEnvelope.extract_envelope_data(this, ch);
	}

	/* table 11 */
	private void sbr_noise(BitStream ld, Channel ch) {
		int noise, band;
		int delta = 0;
		int[][] t_huff, f_huff;

		if(this.bs_coupling&&(ch==ch1)) {
			delta = 1;
			t_huff = T_HUFFMAN_NOISE_BAL_3_0DB;
			f_huff = F_HUFFMAN_ENV_BAL_3_0DB;
		}
		else {
			delta = 0;
			t_huff = T_HUFFMAN_NOISE_3_0DB;
			f_huff = F_HUFFMAN_ENV_3_0DB;
		}

		for(noise = 0; noise<ch.L_Q; noise++) {
			if(ch.bs_df_noise[noise]==0) {
				if(this.bs_coupling&&(ch==ch1)) {
					ch.Q[0][noise] = ld.readBits(5)<<delta;
				}
				else {
					ch.Q[0][noise] = ld.readBits(5)<<delta;
				}
				for(band = 1; band<this.N_Q; band++) {
					ch.Q[band][noise] = (decodeHuffman(ld, f_huff)<<delta);
				}
			}
			else {
				for(band = 0; band<this.N_Q; band++) {
					ch.Q[band][noise] = (decodeHuffman(ld, t_huff)<<delta);
				}
			}
		}

		NoiseEnvelope.extract_noise_floor_data(this, ch);
	}

	private int decodeHuffman(BitStream ld, int[][] t_huff) {
		int bit;
		int index = 0;

		while(index>=0) {
			bit = ld.readBit();
			index = t_huff[index][bit];
		}

		return index+64;
	}

	private int sbr_save_prev_data(Channel ch) {
		int i;

		/* save data for next frame */
		this.kx_prev = this.kx;
		this.M_prev = this.M;
		this.bsco_prev = this.bsco;

		ch.L_E_prev = ch.L_E;

		/* sbr.L_E[ch] can become 0 on files with bit errors */
		if(ch.L_E<=0)
			return 19;

		ch.f_prev = ch.f[ch.L_E-1];
		for(i = 0; i<MAX_M; i++) {
			ch.E_prev[i] = ch.E[i][ch.L_E-1];
			ch.Q_prev[i] = ch.Q[i][ch.L_Q-1];
		}

		for(i = 0; i<MAX_M; i++) {
			ch.bs_add_harmonic_prev[i] = ch.bs_add_harmonic[i];
		}
		ch.bs_add_harmonic_flag_prev = ch.bs_add_harmonic_flag;

		if(ch.l_A==ch.L_E)
			ch.prevEnvIsShort = 0;
		else
			ch.prevEnvIsShort = -1;

		return 0;
	}

	private void sbr_save_matrix(Channel ch) {
		int i;

		for(i = 0; i<this.tHFGen; i++) {
			for(int j = 0; j<64; j++) {
				ch.Xsbr[i][j][0] = ch.Xsbr[i+numTimeSlotsRate][j][0];
				ch.Xsbr[i][j][1] = ch.Xsbr[i+numTimeSlotsRate][j][1];
			}
		}
		for(i = this.tHFGen; i<Channel.MAX_NTSRHFG; i++) {
			for(int j = 0; j<64; j++) {
				ch.Xsbr[i][j][0] = 0;
				ch.Xsbr[i][j][1] = 0;
			}
		}
	}

	private int sbr_process_channel(float[] channel_buf, float[][][] X,
		Channel ch, boolean dont_process) {
		int k, l;
		int ret = 0;

		this.bsco = 0;

		/* subband analysis */
		if(dont_process)
			ch.qmfa.sbr_qmf_analysis_32(this, channel_buf, ch.Xsbr, this.tHFGen, 32);
		else
			ch.qmfa.sbr_qmf_analysis_32(this, channel_buf, ch.Xsbr, this.tHFGen, this.kx);

		if(!dont_process) {
			/* insert high frequencies here */
			/* hf generation using patching */
			HFGeneration.hf_generation(this, ch.Xsbr, ch.Xsbr, ch);


			/* hf adjustment */
			ret = HFAdjustment.hf_adjustment(this, ch.Xsbr, ch);
			if(ret>0) {
				dont_process = true;
			}
		}

		if(dont_process) {
			for(l = 0; l<this.numTimeSlotsRate; l++) {
				for(k = 0; k<32; k++) {
					X[l][k][0] = ch.Xsbr[l+this.tHFAdj][k][0];
					X[l][k][1] = ch.Xsbr[l+this.tHFAdj][k][1];
				}
				for(k = 32; k<64; k++) {
					X[l][k][0] = 0;
					X[l][k][1] = 0;
				}
			}
		}
		else {
			for(l = 0; l<this.numTimeSlotsRate; l++) {
				int kx_band, M_band, bsco_band;

				if(l<ch.t_E[0]) {
					kx_band = this.kx_prev;
					M_band = this.M_prev;
					bsco_band = this.bsco_prev;
				}
				else {
					kx_band = this.kx;
					M_band = this.M;
					bsco_band = this.bsco;
				}

				for(k = 0; k<kx_band+bsco_band; k++) {
					X[l][k][0] = ch.Xsbr[l+this.tHFAdj][k][0];
					X[l][k][1] = ch.Xsbr[l+this.tHFAdj][k][1];
				}
				for(k = kx_band+bsco_band; k<kx_band+M_band; k++) {
					X[l][k][0] = ch.Xsbr[l+this.tHFAdj][k][0];
					X[l][k][1] = ch.Xsbr[l+this.tHFAdj][k][1];
				}
				for(k = Math.max(kx_band+bsco_band, kx_band+M_band); k<64; k++) {
					X[l][k][0] = 0;
					X[l][k][1] = 0;
				}
			}
		}

		return ret;
	}

	public int process(float[] left_chan, float[] right_chan) {
		boolean dont_process = false;
		int ret = 0;
		float[][][] X = new float[MAX_NTSR][64][2];

		/* case can occur due to bit errors */
		if(!stereo)
			throw new AACException("unexpected SBR stereo channel");

		if(this.hdr==null) {
			/* don't process just upsample */
			dont_process = true;
		}

		ret += sbr_process_channel(left_chan, X, ch0, dont_process);
		/* subband synthesis */
		if(downSampledSBR) {
			qmfs0.sbr_qmf_synthesis_32(this, X, left_chan);
		}
		else {
			qmfs0.sbr_qmf_synthesis_64(this, X, left_chan);
		}

		ret += sbr_process_channel(right_chan, X, ch1, dont_process);
		/* subband synthesis */
		if(downSampledSBR) {
			qmfs1.sbr_qmf_synthesis_32(this, X, right_chan);
		}
		else {
			qmfs1.sbr_qmf_synthesis_64(this, X, right_chan);
		}

		if(this.hdr!=null&&ret==0) {
			ret = sbr_save_prev_data(ch0);
			if(ret!=0)
				return ret;
			ret = sbr_save_prev_data(ch1);
			if(ret!=0)
				return ret;
		}

		sbr_save_matrix(ch0);
		sbr_save_matrix(ch1);

		this.frame++;

		return 0;
	}

	public int process(float[] channel) {
		boolean dont_process = false;
		int ret = 0;
		float[][][] X = new float[MAX_NTSR][64][2];

		/* case can occur due to bit errors */
		if(stereo)
			throw new AACException("unexpected SBR mono channel");

		if(this.hdr==null) {
			/* don't process just upsample */
			dont_process = true;
		}

		ret += sbr_process_channel(channel, X, ch0, dont_process);
		/* subband synthesis */
		if(downSampledSBR) {
			qmfs0.sbr_qmf_synthesis_32(this, X, channel);
		}
		else {
			qmfs0.sbr_qmf_synthesis_64(this, X, channel);
		}

		if(this.hdr!=null&&ret==0) {
			ret = sbr_save_prev_data(ch0);
			if(ret!=0)
				return ret;
		}

		sbr_save_matrix(ch0);

		this.frame++;

		return 0;
	}

	public int processPS(float[] left_channel, float[] right_channel) {
		int l, k;
		boolean dont_process = false;
		int ret = 0;
		float[][][] X_left = new float[38][64][2];
		float[][][] X_right = new float[38][64][2];

		/* case can occur due to bit errors */
		if(stereo)
			throw new AACException("PS on mono channel");

		if(this.hdr==null) {
			/* don't process just upsample */
			dont_process = true;
		}

		//if(qmfs1==null) {
		//	qmfs1 = new SynthesisFilterbank((downSampledSBR) ? 32 : 64);
		//}

		ret += sbr_process_channel(left_channel, X_left, ch0, dont_process);

		/* copy some extra data for PS */
		for(l = this.numTimeSlotsRate; l<this.numTimeSlotsRate+6; l++) {
			for(k = 0; k<5; k++) {
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
			ret = sbr_save_prev_data(ch0);
			if(ret!=0)
				return ret;
		}

		sbr_save_matrix(ch0);

		this.frame++;

		return 0;
	}

	public boolean isPSUsed() {
		return ps_used;
	}

	public static void upsample(float[] data) {

		for(int i=data.length/2-1; i>0; --i) {
			float v = data[i];
			data[2*i] = v;
			data[2*i+1] = v;
		}
	}
}
