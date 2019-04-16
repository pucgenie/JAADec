package net.sourceforge.jaad.aac.syntax;

import net.sourceforge.jaad.aac.DecoderConfig;


/**
 * single_channel_element: abbreviaton SCE.
 *
 * Syntactic element of the bitstream containing coded
 * data for a single audio channel. A single_channel_element basically
 * consists of an individual_channel_stream. There may be up to 16
 * such elements per raw data block, each one must have a unique
 * element_instance_tag.
 *
 * lfe_channel_element: Abbreviation LFE.
 *
 * Syntactic element that contains a low sampling frequency enhancement channel.
 * The rules for the number of lfe_channel_element()’s and instance tags are
 * as for single_channel_element’s.
 */

class SCE_LFE extends ChannelElement {

	private final ICStream ics;

	SCE_LFE(DecoderConfig config) {
		super();
		ics = new ICStream(config);
	}

	void decode(BitStream in, DecoderConfig conf) {
		readElementInstanceTag(in);
		ics.decode(in, false, conf);
	}

	public ICStream getICStream() {
		return ics;
	}
}
