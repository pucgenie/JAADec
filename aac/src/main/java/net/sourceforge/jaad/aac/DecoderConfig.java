package net.sourceforge.jaad.aac;

import net.sourceforge.jaad.aac.syntax.BitStream;
import net.sourceforge.jaad.aac.syntax.Constants;
import net.sourceforge.jaad.aac.syntax.PCE;

/**
 * DecoderConfig that must be passed to the
 * <code>Decoder</code> constructor. Typically it is created via one of the
 * static parsing methods.
 *
 * @author in-somnia
 */
public class DecoderConfig {

	private Profile profile, extProfile = Profile.UNKNOWN;
	private SampleFrequency sampleFrequency;
	private ChannelConfiguration channelConfiguration;
	private boolean frameLengthFlag=false;
	private boolean dependsOnCoreCoder=false;
	private int coreCoderDelay = 0;
	private boolean extensionFlag=false;
	//extension: SBR
	private boolean sbrPresent=false, downSampledSBR=false, sbrEnabled=true;
	//extension: error resilience
	private boolean sectionDataResilience=false, scalefactorResilience=false, spectralDataResilience=false;

	private DecoderConfig() {
		profile = Profile.AAC_MAIN;
		sampleFrequency = SampleFrequency.SAMPLE_FREQUENCY_NONE;
		channelConfiguration = ChannelConfiguration.CHANNEL_CONFIG_UNSUPPORTED;
	}

	private DecoderConfig(AudioDecoderInfo info) {
		this.profile = info.getProfile();
		this.sampleFrequency = info.getSampleFrequency();
		this.channelConfiguration = info.getChannelConfiguration();
	}

	/* ========== gets/sets ========== */
	public ChannelConfiguration getChannelConfiguration() {
		return channelConfiguration;
	}

	public void setChannelConfiguration(ChannelConfiguration channelConfiguration) {
		this.channelConfiguration = channelConfiguration;
	}

	public int getCoreCoderDelay() {
		return coreCoderDelay;
	}

	public void setCoreCoderDelay(int coreCoderDelay) {
		this.coreCoderDelay = coreCoderDelay;
	}

	public boolean isDependsOnCoreCoder() {
		return dependsOnCoreCoder;
	}

	public void setDependsOnCoreCoder(boolean dependsOnCoreCoder) {
		this.dependsOnCoreCoder = dependsOnCoreCoder;
	}

	public Profile getExtObjectType() {
		return extProfile;
	}

	public void setExtObjectType(Profile extObjectType) {
		this.extProfile = extObjectType;
	}

	public int getFrameLength() {
		return frameLengthFlag ? Constants.WINDOW_SMALL_LEN_LONG : Constants.WINDOW_LEN_LONG;
	}

	public boolean isSmallFrameUsed() {
		return frameLengthFlag;
	}

	public void setSmallFrameUsed(boolean shortFrame) {
		this.frameLengthFlag = shortFrame;
	}

	public Profile getProfile() {
		return profile;
	}

	public void setProfile(Profile profile) {
		this.profile = profile;
	}

	public SampleFrequency getSampleFrequency() {
		return sampleFrequency;
	}

	public void setSampleFrequency(SampleFrequency sampleFrequency) {
		this.sampleFrequency = sampleFrequency;
	}

	//=========== SBR =============
	public boolean isSBRPresent() {
		return sbrPresent;
	}

	public boolean isSBRDownSampled() {
		return downSampledSBR;
	}

	public boolean isSBREnabled() {
		return sbrEnabled;
	}

	public void setSBREnabled(boolean enabled) {
		sbrEnabled = enabled;
	}

	//=========== ER =============
	public boolean isScalefactorResilienceUsed() {
		return scalefactorResilience;
	}

	public boolean isSectionDataResilienceUsed() {
		return sectionDataResilience;
	}

	public boolean isSpectralDataResilienceUsed() {
		return spectralDataResilience;
	}

	/* ======== static builder ========= */

	public static DecoderConfig create(AudioDecoderInfo info) {
		return new DecoderConfig(info);
	}

	/**
	 * Parses the input arrays as a DecoderSpecificInfo, as used in MP4
	 * containers.
	 * 
	 * @return a DecoderConfig
	 */
	public static DecoderConfig decode(BitStream in) throws AACException {
		final DecoderConfig config = new DecoderConfig();

		try {
			config.profile = readProfile(in);

			int sf = in.readBits(4);
			if(sf==0xF)
				config.sampleFrequency = SampleFrequency.forFrequency(in.readBits(24));
			else
				config.sampleFrequency = SampleFrequency.forInt(sf);

			config.channelConfiguration = ChannelConfiguration.forInt(in.readBits(4));

			switch(config.profile) {
				case AAC_SBR:
					config.extProfile = config.profile;
					config.sbrPresent = true;
					sf = in.readBits(4);
					//TODO: 24 bits already read; read again?
					//if(sf==0xF) config.sampleFrequency = SampleFrequency.forFrequency(in.readBits(24));
					//if sample frequencies are the same: downsample SBR
					config.downSampledSBR = config.sampleFrequency.getIndex()==sf;
					config.sampleFrequency = SampleFrequency.forInt(sf);
					config.profile = readProfile(in);
					break;

				case AAC_MAIN:
				case AAC_LC:
				case AAC_SSR:
				case AAC_LTP:
				case ER_AAC_LC:
				case ER_AAC_LTP:
				case ER_AAC_LD:
					//ga-specific info:
					config.frameLengthFlag = in.readBool();
					if(config.frameLengthFlag)
						throw new AACException("config uses 960-sample frames, not yet supported"); //TODO: are 960-frames working yet?

					config.dependsOnCoreCoder = in.readBool();

					if(config.dependsOnCoreCoder)
						config.coreCoderDelay = in.readBits(14);
					else
						config.coreCoderDelay = 0;

					config.extensionFlag = in.readBool();

					if(config.extensionFlag) {
						if(config.profile.isErrorResilientProfile()) {
							config.sectionDataResilience = in.readBool();
							config.scalefactorResilience = in.readBool();
							config.spectralDataResilience = in.readBool();
						}
						//extensionFlag3
						in.skipBit();
					}

					if(config.channelConfiguration==ChannelConfiguration.CHANNEL_CONFIG_NONE) {
						//TODO: is this working correct? -> ISO 14496-3 part 1: 1.A.4.3
						//in.skipBits(3); //PCE
						PCE pce = new PCE();
						pce.decode(in);
						config.profile = pce.getProfile();
						config.sampleFrequency = pce.getSampleFrequency();
						config.channelConfiguration = ChannelConfiguration.forInt(pce.getChannelCount());
					}

					if(in.getBitsLeft()>10)
						config.readSyncExtension(in);

					break;

				default:
					throw new AACException("profile not supported: "+config.profile.getIndex());
			}
			return config;
		}
		finally {
			in.destroy();
		}
	}

	private static Profile readProfile(BitStream in) throws AACException {
		int i = in.readBits(5);
		if(i==31)
			i = 32+in.readBits(6);
		return Profile.forInt(i);
	}

	private void readSyncExtension(BitStream in) throws AACException {
		final int type = in.readBits(11);
		switch(type) {
			case 0x2B7:
				final Profile profile = Profile.forInt(in.readBits(5));

				if(profile.equals(Profile.AAC_SBR)) {
					sbrPresent = in.readBool();
					if(sbrPresent) {
						this.profile = profile;

						int tmp = in.readBits(4);

						if(tmp==sampleFrequency.getIndex())
							downSampledSBR = true;

						if(tmp==15) {
							throw new AACException("sample rate specified explicitly, not supported yet!");
							//tmp = in.readBits(24);
						}
					}
				}
				break;
		}
	}
}
