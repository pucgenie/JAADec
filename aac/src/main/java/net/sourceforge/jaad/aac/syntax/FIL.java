package net.sourceforge.jaad.aac.syntax;

import net.sourceforge.jaad.aac.AACException;
import net.sourceforge.jaad.aac.DecoderConfig;
import net.sourceforge.jaad.aac.SampleFrequency;

/**
 * fill_element: Abbreviation FIL.
 *
 * Syntactic element that contains fill data.
 *
 * There may be any number of fill elements, that can come
 * in any order in the raw data block.
 */

class FIL extends Element {

	private static final int TYPE_FILL = 0;
	private static final int TYPE_FILL_DATA = 1;
	private static final int TYPE_EXT_DATA_ELEMENT = 2;
	private static final int TYPE_DYNAMIC_RANGE = 11;
	private static final int TYPE_SBR_DATA = 13;
	private static final int TYPE_SBR_DATA_CRC = 14;

	private DRC dri;

	FIL() {
		super();
	}

	@Override
	protected int readElementInstanceTag(BitStream in) {
		super.readElementInstanceTag(in);
		if(elementInstanceTag==15)
			elementInstanceTag += in.readBits(8)-1;

		return elementInstanceTag;
	}

	void decode(BitStream in, ChannelElement prev) {

		// for FIL elements the instance tag is a size instead.
		final int count =  8 * readElementInstanceTag(in);

		final int pos = in.getPosition();
		int left = count;
		while(left>0) {
			left = decodeExtensionPayload(in, left, prev);
		}

		final int pos2 = in.getPosition()-pos;
		final int bitsLeft = count-pos2;
		if(bitsLeft>0)
			in.skipBits(pos2);

		else if(bitsLeft<0)
			throw new AACException("FIL element overread: "+bitsLeft);
	}

	private int decodeExtensionPayload(BitStream in, int count, ChannelElement prev) {
		final int type = in.readBits(4);
		int ret = count-4;
		switch(type) {
			case TYPE_DYNAMIC_RANGE:
				ret = decodeDynamicRangeInfo(in, ret);
				break;
			case TYPE_SBR_DATA:
			case TYPE_SBR_DATA_CRC:
				prev.decodeSBR(in, ret, (type==TYPE_SBR_DATA_CRC));
				ret = 0;
				break;
			case TYPE_FILL:
			case TYPE_FILL_DATA:
			case TYPE_EXT_DATA_ELEMENT:
			default:
				in.skipBits(ret);
				ret = 0;
				break;
		}
		return ret;
	}

	private int decodeDynamicRangeInfo(BitStream in, int count) {
		if (dri == null)
			dri = new DRC();

		return dri.decode(in, count);
	}
}
