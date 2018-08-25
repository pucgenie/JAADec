package net.sourceforge.jaad.aac;

import net.sourceforge.jaad.aac.syntax.BitStream;
import net.sourceforge.jaad.aac.syntax.PCE;
import net.sourceforge.jaad.aac.syntax.SyntacticElements;
import net.sourceforge.jaad.aac.filterbank.FilterBank;
import net.sourceforge.jaad.aac.transport.ADIFHeader;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Main AAC decoder class
 * @author in-somnia
 */
public class Decoder {

	static final Logger LOGGER = Logger.getLogger("jaad.aac.Decoder"); //for debugging

	private final DecoderConfig config;
	private final SyntacticElements syntacticElements;
	private final FilterBank filterBank;
	private BitStream in;
	private int frames=0;
	private ADIFHeader adifHeader;

	/**
	 * The methods returns true, if a profile is supported by the decoder.
	 * @param profile an AAC profile
	 * @return true if the specified profile can be decoded
	 * @see Profile#isDecodingSupported()
	 */
	public static boolean canDecode(Profile profile) {
		return profile.isDecodingSupported();
	}

	public static Decoder create(byte[] data) throws AACException {
		return create(new BitStream(data));
	}

	public static Decoder create(BitStream in) throws AACException {
		DecoderConfig config = DecoderConfig.decode(in);
		return create(config);
	}

	public static Decoder create(AudioDecoderInfo info) throws AACException {
		DecoderConfig config = DecoderConfig.create(info);
		return create(config);
	}

	public static Decoder create(DecoderConfig config) throws AACException {
		if(config==null)
			throw new IllegalArgumentException("illegal MP4 decoder specific info");
		return new Decoder(config);
	}

	/**
	 * Initializes the decoder with a MP4 decoder specific info.
	 *
	 * After this the MP4 frames can be passed to the
	 * <code>decodeFrame(byte[], SampleBuffer)</code> method to decode them.
	 * 
	 * @param config decoder specific info from an MP4 container
	 * @throws AACException if the specified profile is not supported
	 */
	private Decoder(DecoderConfig config) throws AACException {
		//config = DecoderConfig.parseMP4DecoderSpecificInfo(decoderSpecificInfo);

		this.config = config;

		syntacticElements = new SyntacticElements(config);
		filterBank = new FilterBank(config.isSmallFrameUsed(), config.getChannelConfiguration().getChannelCount());

		in = new BitStream();

		LOGGER.log(Level.FINE, "profile: {0}", config.getProfile());
		LOGGER.log(Level.FINE, "sf: {0}", config.getSampleFrequency().getFrequency());
		LOGGER.log(Level.FINE, "channels: {0}", config.getChannelConfiguration().getDescription());
	}

	public DecoderConfig getConfig() {
		return config;
	}

	/**
	 * Decodes one frame of AAC data in frame mode and returns the raw PCM
	 * data.
	 * @param frame the AAC frame
	 * @param buffer a buffer to hold the decoded PCM data
	 * @throws AACException if decoding fails
	 */
	public void decodeFrame(byte[] frame, SampleBuffer buffer) throws AACException {
		if(frame!=null)
			in.setData(frame);

		try {
			LOGGER.log(Level.INFO, ()->String.format("frame %d @%d", frames, 8*frame.length));
			decode(buffer);
			LOGGER.log(Level.INFO, ()->String.format("left %d", in.getBitsLeft()));
		}
		catch(AACException e) {
			if(!e.isEndOfStream())
				throw e;
			else
				LOGGER.log(Level.WARNING,"unexpected end of frame",e);
		} finally {
			++frames;
		}
	}

	private void decode(SampleBuffer buffer) throws AACException {
		if(ADIFHeader.isPresent(in)) {
			adifHeader = ADIFHeader.readHeader(in);
			final PCE pce = adifHeader.getFirstPCE();
			config.setProfile(pce.getProfile());
			config.setSampleFrequency(pce.getSampleFrequency());
			config.setChannelConfiguration(ChannelConfiguration.forInt(pce.getChannelCount()));
		}

		if(!canDecode(config.getProfile()))
			throw new AACException("unsupported profile: "+config.getProfile().getDescription());

		syntacticElements.startNewFrame();

		try {
			//1: bitstream parsing and noiseless coding
			syntacticElements.decode(in);
			//2: spectral processing
			syntacticElements.process(filterBank);
			//3: send to output buffer
			syntacticElements.sendToOutput(buffer);
		}
		catch(AACException e) {
			buffer.setData(new byte[0], 0, 0, 0, 0);
			throw e;
		}
		catch(Exception e) {
			buffer.setData(new byte[0], 0, 0, 0, 0);
			throw new AACException(e);
		}
	}
}
