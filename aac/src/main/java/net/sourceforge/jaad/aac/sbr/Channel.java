package net.sourceforge.jaad.aac.sbr;

/**
 * Created by IntelliJ IDEA.
 * User: stueken
 * Date: 13.05.21
 * Time: 18:58
 */
class Channel {

    boolean amp_res;

    int abs_bord_lead;
    int abs_bord_trail;
    int n_rel_lead;
    int n_rel_trail;

    int L_E;
    int L_E_prev;
    int L_Q;

    int[] t_E = new int[SBR.MAX_L_E + 1];
    int[] t_Q = new int[3];
    int[] f = new int[SBR.MAX_L_E + 1];
    int f_prev;

    float[][] G_temp_prev = new float[5][64];
    float[][] Q_temp_prev = new float[5][64];
    int GQ_ringbuf_index = 0;

    int[][] E = new int[64][SBR.MAX_L_E];
    int[] E_prev = new int[64];
    float[][] E_orig = new float[64][SBR.MAX_L_E];
    float[][] E_curr = new float[64][SBR.MAX_L_E];
    int[][] Q = new int[64][2];
    float[][] Q_div = new float[64][2];
    float[][] Q_div2 = new float[64][2];
    int[] Q_prev = new int[64];

    int l_A;
    int l_A_prev;

    int[] bs_invf_mode = new int[SBR.MAX_L_E];
    int[] bs_invf_mode_prev = new int[SBR.MAX_L_E];
    float[] bwArray = new float[64];
    float[] bwArray_prev = new float[64];

    int[] bs_add_harmonic = new int[64];
    int[] bs_add_harmonic_prev = new int[64];

    int index_noise_prev;
    int psi_is_prev;

    int prevEnvIsShort = -1;

    final AnalysisFilterbank qmfa;

    public static final int MAX_NTSRHFG = 40; //maximum of number_time_slots * rate + HFGen. 16*2+8
    float[][][] Xsbr = new float[MAX_NTSRHFG][64][2];

    FrameClass bs_frame_class;
    int[] bs_rel_bord = new int[9];
    int[] bs_rel_bord_0 = new int[9];
    int[] bs_rel_bord_1 = new int[9];
    int bs_pointer;
    int bs_abs_bord_0;
    int bs_abs_bord_1;
    int bs_num_rel_0;
    int bs_num_rel_1;
    int[] bs_df_env = new int[9];
    int[] bs_df_noise = new int[3];

    boolean bs_add_harmonic_flag;
    boolean bs_add_harmonic_flag_prev;

    public Channel() {
        qmfa = new AnalysisFilterbank(32);
    }
}
