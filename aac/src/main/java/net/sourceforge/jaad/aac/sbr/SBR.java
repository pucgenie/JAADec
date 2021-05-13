package net.sourceforge.jaad.aac.sbr;

import net.sourceforge.jaad.aac.AACException;
import net.sourceforge.jaad.aac.DecoderConfig;
import net.sourceforge.jaad.aac.SampleFrequency;
import net.sourceforge.jaad.aac.syntax.BitStream;

import java.util.logging.Logger;

import static net.sourceforge.jaad.aac.sbr.HuffmanTables.*;

abstract public class SBR {

	static final Logger LOGGER = Logger.getLogger("jaad.aac.sbr.SBR"); //for debugging

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

	protected final boolean downSampledSBR;
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

	int kx_prev;
	int bsco;
	int bsco_prev;
	int M_prev;

	boolean reset;
	int frame;

	int noPatches;
	int[] patchNoSubbands = new int[64];
	int[] patchStartSubband = new int[64];

	int numTimeSlotsRate;
	int numTimeSlots;
	int tHFGen;
	int tHFAdj;

	/* to get it compiling
	/* we'll see during the coding of all the tools, whether these are all used or not.
	 */
	int bs_sbr_crc_bits;

	Header hdr = null;
	Header hdr_saved = null;
	
	int bs_samplerate_mode;

	public static SBR open(DecoderConfig config, boolean stereo) {
		config.setSBRPresent();
		return stereo ? new SBR2(config) : new SBR1(config) ;
	}

	public SBR(DecoderConfig config) {
		this.downSampledSBR = config.isSBRDownSampled();
		this.sample_rate = config.getOutputFrequency().getNominal();

		this.bs_samplerate_mode = 1;
		this.rate = (this.bs_samplerate_mode!=0) ? 2 : 1;

		this.tHFGen = T_HFGEN;
		this.tHFAdj = T_HFADJ;

		this.bsco = 0;
		this.bsco_prev = 0;
		this.M_prev = 0;

		if(config.isSmallFrameUsed()) {
			this.numTimeSlotsRate = RATE*NO_TIME_SLOTS_960;
			this.numTimeSlots = NO_TIME_SLOTS_960;
		}
		else {
			this.numTimeSlotsRate = RATE*NO_TIME_SLOTS;
			this.numTimeSlots = NO_TIME_SLOTS;
		}
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

	public boolean isPSUsed() {
		return false;
	}

	abstract protected int sbr_data(BitStream ld);

	public abstract void process(float[] left_chan, float[] right_chan);

	/* integer log[2](x): input range [0,10) */
	private int sbr_log2(int val) {
		int log2tab[] = {0, 0, 1, 2, 2, 3, 3, 3, 3, 4};
		if(val<10&&val>=0)
			return log2tab[val];
		else
			return 0;
	}


	/* table 7 */
	protected int sbr_grid(BitStream ld, Channel ch) {
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
	protected void sbr_dtdf(BitStream ld, Channel ch) {
		int i;

		for(i = 0; i<ch.L_E; i++) {
			ch.bs_df_env[i] = ld.readBit();
		}

		for(i = 0; i<ch.L_Q; i++) {
			ch.bs_df_noise[i] = ld.readBit();
		}
	}

	/* table 9 */
	protected void invf_mode(BitStream ld, Channel ch) {
		int n;

		for(n = 0; n<this.N_Q; n++) {
			ch.bs_invf_mode[n] = ld.readBits(2);
		}
	}

	protected void readExtendedData(BitStream ld) {
		boolean bs_extended_data = ld.readBool();
		if(bs_extended_data) {

			int cnt = ld.readBits(4);
			if(cnt==15) {
				cnt += ld.readBits(8);
			}

			ld = ld.readSubStream(8*cnt);
			while(ld.getBitsLeft()>7) {
				int bs_extension_id = ld.readBits(2);
				sbr_extension(ld, bs_extension_id);
			}
		}
	}

	protected void sbr_extension(BitStream ld, int bs_extension_id) {

	}

	/* table 12 */
	protected void sinusoidal_coding(BitStream ld, Channel ch) {
		int n;

		for(n = 0; n<this.N_high; n++) {
			ch.bs_add_harmonic[n] = ld.readBit();
		}
	}
	/* table 10 */

	protected void sbr_envelope(BitStream ld, Channel ch, boolean coupled) {
		int env, band;
		int delta = 0;
		int[][] t_huff, f_huff;

		if((ch.L_E==1)&&(ch.bs_frame_class== FrameClass.FIXFIX))
			ch.amp_res = false;
		else
			ch.amp_res = this.hdr.bs_amp_res;

		if(coupled) {
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
				if(coupled) {
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
	protected void sbr_noise(BitStream ld, Channel ch, boolean coupled) {
		int noise, band;
		int delta = 0;
		int[][] t_huff, f_huff;

		if(coupled) {
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
				if(coupled) {
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
		int index = 0;

		while(index>=0) {
			int bit = ld.readBit();
			index = t_huff[index][bit];
		}

		return index+64;
	}

	protected void sbr_save_prev_data(Channel ch) {

		/* save data for next frame */
		this.kx_prev = this.kx;
		this.M_prev = this.M;
		this.bsco_prev = this.bsco;

		ch.L_E_prev = ch.L_E;

		/* sbr.L_E[ch] can become 0 on files with bit errors */
		if(ch.L_E<=0)
			throw new AACException("L_E<0");

		ch.f_prev = ch.f[ch.L_E-1];
		for(int i = 0; i<MAX_M; i++) {
			ch.E_prev[i] = ch.E[i][ch.L_E-1];
			ch.Q_prev[i] = ch.Q[i][ch.L_Q-1];
		}

		for(int i = 0; i<MAX_M; i++) {
			ch.bs_add_harmonic_prev[i] = ch.bs_add_harmonic[i];
		}
		ch.bs_add_harmonic_flag_prev = ch.bs_add_harmonic_flag;

		if(ch.l_A==ch.L_E)
			ch.prevEnvIsShort = 0;
		else
			ch.prevEnvIsShort = -1;
	}

	protected void sbr_save_matrix(Channel ch) {

		for(int i = 0; i<this.tHFGen; i++) {
			for(int j = 0; j<64; j++) {
				ch.Xsbr[i][j][0] = ch.Xsbr[i+numTimeSlotsRate][j][0];
				ch.Xsbr[i][j][1] = ch.Xsbr[i+numTimeSlotsRate][j][1];
			}
		}
		for(int i = this.tHFGen; i<Channel.MAX_NTSRHFG; i++) {
			for(int j = 0; j<64; j++) {
				ch.Xsbr[i][j][0] = 0;
				ch.Xsbr[i][j][1] = 0;
			}
		}
	}

	protected void sbr_process_channel(float[] channel_buf, float[][][] X, Channel ch, boolean reset) {

		this.bsco = 0;

		boolean dont_process = this.hdr==null;

		/* subband analysis */
		if(dont_process)
			ch.qmfa.sbr_qmf_analysis_32(this, channel_buf, ch.Xsbr, this.tHFGen, 32);
		else
			ch.qmfa.sbr_qmf_analysis_32(this, channel_buf, ch.Xsbr, this.tHFGen, this.kx);

		if(!dont_process) {
			/* insert high frequencies here */
			/* hf generation using patching */
			HFGeneration.hf_generation(this, ch.Xsbr, ch.Xsbr, ch, reset);


			/* hf adjustment */
			HFAdjustment.hf_adjustment(this, ch.Xsbr, ch);
		}

		if(dont_process) {
			for(int l = 0; l<this.numTimeSlotsRate; l++) {
				for(int k = 0; k<32; k++) {
					X[l][k][0] = ch.Xsbr[l+this.tHFAdj][k][0];
					X[l][k][1] = ch.Xsbr[l+this.tHFAdj][k][1];
				}
				for(int k = 32; k<64; k++) {
					X[l][k][0] = 0;
					X[l][k][1] = 0;
				}
			}
		}
		else {
			for(int l = 0; l<this.numTimeSlotsRate; l++) {
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

				for(int k = 0; k<kx_band+bsco_band; k++) {
					X[l][k][0] = ch.Xsbr[l+this.tHFAdj][k][0];
					X[l][k][1] = ch.Xsbr[l+this.tHFAdj][k][1];
				}
				for(int k = kx_band+bsco_band; k<kx_band+M_band; k++) {
					X[l][k][0] = ch.Xsbr[l+this.tHFAdj][k][0];
					X[l][k][1] = ch.Xsbr[l+this.tHFAdj][k][1];
				}
				for(int k = Math.max(kx_band+bsco_band, kx_band+M_band); k<64; k++) {
					X[l][k][0] = 0;
					X[l][k][1] = 0;
				}
			}
		}
	}

	public static void upsample(float[] data) {

		for(int i=data.length/2-1; i>0; --i) {
			float v = data[i];
			data[2*i] = v;
			data[2*i+1] = v;
		}
	}
}
