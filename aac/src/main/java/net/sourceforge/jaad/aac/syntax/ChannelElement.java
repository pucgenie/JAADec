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

    abstract void decode(BitStream in, DecoderConfig conf);

    public boolean isStereo() {
    	return false;
	}

    private SBR sbr;

    int decodeSBR(BitStream in, SampleFrequency sf, int count, boolean crc, boolean downSampled, boolean smallFrames) {

   		if(sbr==null) {
               /* implicit SBR signalling, see 4.6.18.2.6 */
   			int fq = sf.getFrequency();
   			if(fq<24000 && !downSampled)
   			    sf = SampleFrequency.forFrequency(2*fq);
   			sbr = new SBR(smallFrames, isStereo(), sf, downSampled);
   		}

   		return sbr.decode(in, count, crc);
   	}

   	boolean isSBRPresent() {
   		return sbr!=null;
   	}

   	SBR getSBR() {
   		return sbr;
   	}
}
