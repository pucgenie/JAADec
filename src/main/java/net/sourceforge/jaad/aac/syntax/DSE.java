package net.sourceforge.jaad.aac.syntax;

import net.sourceforge.jaad.aac.AACException;

/**
 * data_stream_element Abbreviation DSE.
 *
 * Syntactic element that contains data.
 * Again, there are 16 element_instance_tags.
 * There is, however, no restriction on the number
 * of data_stream_element’s with any one instance tag,
 * as a single data stream may continue across multiple
 * data_stream_element’s with the same instance tag.
 */

class DSE extends Element {

	private byte[] dataStreamBytes;

	DSE() {
		super();
	}

	void decode(BitStream in) throws AACException {
		final boolean byteAlign = in.readBool();
		int count = in.readBits(8);
		if(count==255)
			count += in.readBits(8);

		if(byteAlign)
			in.byteAlign();

		dataStreamBytes = new byte[count];
		for(int i = 0; i<count; i++) {
			dataStreamBytes[i] = (byte) in.readBits(8);
		}
	}
}
