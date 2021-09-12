package net.sourceforge.jaad.aac.ps;

/**
 * Created by IntelliJ IDEA.
 * User: stueken
 * Date: 12.09.21
 * Time: 13:34
 */
public class FBType {

    public static final FBType T34 = new FBType(PSTables.group_border34, PSTables.map_group2bk34,
            32+18, 32, 34, 5,
            PSTables.Phi_Fract_SubQmf34, PSTables.Q_Fract_allpass_SubQmf34,
            new Filter[]{Filter12.f, Filter8.f34, Filter4.f, Filter4.f, Filter4.f});

    public static final FBType T20 = new FBType(PSTables.group_border20, PSTables.map_group2bk20,
            10+12, 10, 20, 3,
            PSTables.Phi_Fract_SubQmf20, PSTables.Q_Fract_allpass_SubQmf20,
            new Filter[]{Filter8.f20, Filter2.f, Filter2.f});

    final int num_groups;
   	final int num_hybrid_groups;
   	final int nr_par_bands;
   	final int decay_cutoff;
    final int[] group_border;
   	final int[] map_group2bk;
    final float[][] phiFract;
    final float[][][] qFractAllpassSubQmf;
    final Filter[] filters;

    public FBType(int[] group_border, int[] map_group2bk,
                  int num_groups, int num_hybrid_groups,
                  int nr_par_bands, int decay_cutoff,
                  float[][] phiFract, float[][][] qFractAllpassSubQmf,
                  Filter[] filters) {

        this.num_groups = num_groups;
        this.num_hybrid_groups = num_hybrid_groups;
        this.nr_par_bands = nr_par_bands;
        this.decay_cutoff = decay_cutoff;
        this.group_border = group_border;
        this.map_group2bk = map_group2bk;
        this.phiFract = phiFract;
        this.qFractAllpassSubQmf = qFractAllpassSubQmf;
        this.filters = filters;
    }
}
