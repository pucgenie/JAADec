package net.sourceforge.jaad.aac.syntax;

import net.sourceforge.jaad.aac.DecoderConfig;
import net.sourceforge.jaad.aac.SampleFrequency;
import net.sourceforge.jaad.aac.sbr.SBR;

/**
 * Created by IntelliJ IDEA.
 * User: stueken
 * Date: 16.04.19
 * Time: 16:55
 */
abstract public class ChannelElement extends Element {

	protected final DecoderConfig config;

	protected ChannelElement(DecoderConfig config) {
		this.config = config;
	}

	abstract void decode(BitStream in, DecoderConfig conf);

	/**
	 * @return if this element represents a channel pair.
	 */
	abstract public boolean isChannelPair();

	/**
	 * A single channel may produce stereo using parametric stereo.
	 * @return if this stereo.
	 */
	abstract public boolean isStereo();


	protected SBR sbr;

    int decodeSBR(BitStream in, SampleFrequency sf, int count, boolean crc, boolean downSampled, boolean smallFrames) {

   		if(sbr==null) {
               /* implicit SBR signalling, see 4.6.18.2.6 */
   			int fq = sf.getFrequency();
   			if(fq<24000 && !downSampled)
   			    sf = SampleFrequency.forFrequency(2*fq);
   			sbr = new SBR(smallFrames, isChannelPair(), sf, downSampled);
   		}

   		return sbr.decode(in, count, crc);
   	}

   	boolean isSBRPresent() {
   		return sbr!=null;
   	}

   	SBR getSBR() {
   		return sbr;
   	}

    private float[] dataL, dataR;

    public float[] getDataL() {
    	if(dataL==null)
			dataL = new float[config.getFrameLength()];
    	return dataL;
	}

	public float[] getDataR() {
		if(dataR==null)
			dataR = new float[config.getFrameLength()];
		return dataR;
    }

	/**
	 * A SCE or LFE may return a second channel if isStereo()
	 * Else the left channel is returned for both.
	 *
	 * @param ch to read.
	 * @return a float array.
	 */
	public float[] getChannelData(int ch) {
		if(ch==0)
			return getDataL();

		if(ch==1) {
			if(isStereo())
				return getDataR();
			else
				return getDataL();
		}

		throw new IndexOutOfBoundsException("invalid channel: " + ch);
	}
}
