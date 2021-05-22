package net.sourceforge.jaad.aac.sbr;

class TFGrid  {


	/* function constructs new time border vector */
	/* first build into temp vector to be able to use previous vector on error */
	public static int envelope_time_border_vector(SBR sbr, Channel ch) {
		int[] t_E_temp = new int[6];

		t_E_temp[0] = sbr.rate*ch.abs_bord_lead;
		t_E_temp[ch.L_E] = sbr.rate*ch.abs_bord_trail;

		switch(ch.bs_frame_class) {
			case FIXFIX:
				switch(ch.L_E) {
					case 4:
						int temp = (sbr.numTimeSlots/4);
						t_E_temp[3] = sbr.rate*3*temp;
						t_E_temp[2] = sbr.rate*2*temp;
						t_E_temp[1] = sbr.rate*temp;
						break;
					case 2:
						t_E_temp[1] = sbr.rate*(sbr.numTimeSlots/2);
						break;
					default:
						break;
				}
				break;

			case FIXVAR:
				if(ch.L_E>1) {
					int i = ch.L_E;
					int border = ch.abs_bord_trail;

					for(int l = 0; l<(ch.L_E-1); l++) {
						if(border<ch.bs_rel_bord[l])
							return 1;

						border -= ch.bs_rel_bord[l];
						t_E_temp[--i] = sbr.rate*border;
					}
				}
				break;

			case VARFIX:
				if(ch.L_E>1) {
					int i = 1;
					int border = ch.abs_bord_lead;

					for(int l = 0; l<(ch.L_E-1); l++) {
						border += ch.bs_rel_bord[l];

						if(sbr.rate*border+sbr.tHFAdj>sbr.numTimeSlotsRate+sbr.tHFGen)
							return 1;

						t_E_temp[i++] = sbr.rate*border;
					}
				}
				break;

			case VARVAR:
				if(ch.bs_num_rel_0!=0) {
					int i = 1;
					int border = ch.abs_bord_lead;

					for(int l = 0; l<ch.bs_num_rel_0; l++) {
						border += ch.bs_rel_bord_0[l];

						if(sbr.rate*border+sbr.tHFAdj>sbr.numTimeSlotsRate+sbr.tHFGen)
							return 1;

						t_E_temp[i++] = sbr.rate*border;
					}
				}

				if(ch.bs_num_rel_1!=0) {
					int i = ch.L_E;
					int border = ch.abs_bord_trail;

					for(int l = 0; l<ch.bs_num_rel_1; l++) {
						if(border<ch.bs_rel_bord_1[l])
							return 1;

						border -= ch.bs_rel_bord_1[l];
						t_E_temp[--i] = sbr.rate*border;
					}
				}
				break;
		}

		/* no error occured, we can safely use this t_E vector */
		for(int l = 0; l<6; l++) {
			ch.t_E[l] = t_E_temp[l];
		}

		return 0;
	}

	public static void noise_floor_time_border_vector(SBR sbr, Channel ch) {
		ch.t_Q[0] = ch.t_E[0];

		if(ch.L_E==1) {
			ch.t_Q[1] = ch.t_E[1];
			ch.t_Q[2] = 0;
		}
		else {
			int index = middleBorder(sbr, ch);
			ch.t_Q[1] = ch.t_E[index];
			ch.t_Q[2] = ch.t_E[ch.L_E];
		}
	}

	private static int middleBorder(SBR sbr, Channel ch) {
		int retval = 0;

		switch(ch.bs_frame_class) {
			case FIXFIX:
				retval = ch.L_E/2;
				break;
			case VARFIX:
				if(ch.bs_pointer==0)
					retval = 1;
				else if(ch.bs_pointer==1)
					retval = ch.L_E-1;
				else
					retval = ch.bs_pointer-1;
				break;
			case FIXVAR:
			case VARVAR:
				if(ch.bs_pointer>1)
					retval = ch.L_E+1-ch.bs_pointer;
				else
					retval = ch.L_E-1;
				break;
		}

		return (retval>0) ? retval : 0;
	}

}
