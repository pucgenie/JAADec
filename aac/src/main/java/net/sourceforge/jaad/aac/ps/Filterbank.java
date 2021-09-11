package net.sourceforge.jaad.aac.ps;

class Filterbank implements PSTables {

	private final int frame_len;

	private static final Filter[] filter20 = {Filter8.f20, Filter2.f, Filter2.f};
	private static final Filter[] filter34 = {Filter12.f, Filter8.f34, Filter4.f, Filter4.f, Filter4.f};

	private final float[][] work;
	private final float[][][] buffer;
	private final float[][][] temp;

	Filterbank(int numTimeSlotsRate) {
		frame_len = numTimeSlotsRate;
		work = new float[(this.frame_len+12)][2];
		buffer = new float[5][frame_len][2];
		temp = new float[frame_len][12][2];
	}

	void hybrid_analysis(float[][][] X, float[][][] X_hybrid, boolean use34, int numTimeSlotsRate) {
		int qmf_bands = (use34) ? 5 : 3;
		Filter[] filter = (use34) ? filter34 : filter20;

		for(int band = 0, offset = 0; band<qmf_bands; band++) {
			/* build working buffer */
			//memcpy(this.work, this.buffer[band], 12*sizeof(qmf_t));
			for(int i = 0; i<12; i++) {
				work[i][0] = buffer[band][i][0];
				work[i][1] = buffer[band][i][1];
			}

			/* add new samples */
			for(int n = 0; n<this.frame_len; n++) {
				this.work[12+n][0] = X[n+6 /*delay*/][band][0];
				this.work[12+n][1] = X[n+6 /*delay*/][band][1];
			}

			/* store samples */
			//memcpy(this.buffer[band], this.work+this.frame_len, 12*sizeof(qmf_t));
			for(int i = 0; i<12; i++) {
				buffer[band][i][0] = work[frame_len+i][0];
				buffer[band][i][1] = work[frame_len+i][1];
			}

			Filter f = filter[band];

			int resolution = f.filter(frame_len, work, temp);

			for(int n = 0; n<this.frame_len; n++) {
				for(int k = 0; k<f.resolution(); k++) {
					X_hybrid[n][offset+k][0] = this.temp[n][k][0];
					X_hybrid[n][offset+k][1] = this.temp[n][k][1];
				}
			}
			offset += resolution;
		}

		/* group hybrid channels */
		if(!use34) {
			for(int n = 0; n<numTimeSlotsRate; n++) {
				X_hybrid[n][3][0] += X_hybrid[n][4][0];
				X_hybrid[n][3][1] += X_hybrid[n][4][1];
				X_hybrid[n][4][0] = 0;
				X_hybrid[n][4][1] = 0;

				X_hybrid[n][2][0] += X_hybrid[n][5][0];
				X_hybrid[n][2][1] += X_hybrid[n][5][1];
				X_hybrid[n][5][0] = 0;
				X_hybrid[n][5][1] = 0;
			}
		}
	}

	void hybrid_synthesis(float[][][] X, float[][][] X_hybrid,
		boolean use34, int numTimeSlotsRate) {
		int qmf_bands = (use34) ? 5 : 3;
		Filter[] filter = (use34) ? filter34 : filter20;

		for(int band = 0, offset = 0; band<qmf_bands; band++) {
			int resolution = filter[band].resolution();

			for(int n = 0; n<this.frame_len; n++) {
				X[n][band][0] = 0;
				X[n][band][1] = 0;

				for(int k = 0; k<resolution; k++) {
					X[n][band][0] += X_hybrid[n][offset+k][0];
					X[n][band][1] += X_hybrid[n][offset+k][1];
				}
			}
			offset += resolution;
		}
	}
}
