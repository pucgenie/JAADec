package net.sourceforge.jaad.adts;

import net.sourceforge.jaad.aac.AudioDecoderInfo;
import net.sourceforge.jaad.aac.ChannelConfiguration;
import net.sourceforge.jaad.aac.Profile;
import net.sourceforge.jaad.aac.SampleFrequency;

import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.util.BitSet;

class ADTSFrame implements AudioDecoderInfo {

	//fixed
	private boolean id, protectionAbsent, privateBit, copy, home;

	private byte layer, profile, sampleFrequency, channelConfiguration;
	//variable
	private boolean copyrightIDBit, copyrightIDStart;

	private short frameLength, adtsBufferFullness;
	private byte rawDataBlockCount;
	//error check
	private int[] rawDataBlockPosition;
	private short crcCheck;
	//decoder specific info
	private byte[] info;

	ADTSFrame(DataInputStream in) throws IOException {
		readHeader(in);

		if(!protectionAbsent)
			crcCheck = (short) in.readUnsignedShort();

		if(rawDataBlockCount==0) {
			//raw_data_block();
		}
		else {
			//header error check
			if(!protectionAbsent) {
				rawDataBlockPosition = new int[rawDataBlockCount];
				for(int i = 0; i<rawDataBlockCount; i++) {
					rawDataBlockPosition[i] = in.readUnsignedShort();
				}
				crcCheck = (short) in.readUnsignedShort();
			}
			//raw data blocks
			for(int i = 0; i<rawDataBlockCount; i++) {
				//raw_data_block();
				if(!protectionAbsent)
					crcCheck = (short) in.readUnsignedShort();
			}
		}
	}

	private static boolean specialLog1;
	private static short maxFrameLength;

	public static void resetStats() {
		specialLog1 = true;
		maxFrameLength = 960;
	}
	static {
		resetStats();
	}

	private void readHeader(DataInputStream in) throws IOException {
		//fixed header:
		// pucgenie: higher bits not used for information?
		//1 bit ID, 2 bits layer, 1 bit protection absent
		int i = in.readByte();
		id =               (i & 0b0000_1000) != 0;
		layer =    (byte) ((i & 0b0000_0110) >> 1);
		protectionAbsent = (i & 0b0000_0001) == 1;
		//2 bits profile, 4 bits sample frequency, 1 bit private bit
		i = in.readByte();
		// pucgenie: I personally want to get rid of that "+1"
		profile =             (byte) (((i & 0b1100_0000) >>> 8-2) + 1);
		sampleFrequency =      (byte) ((i & 0b0011_1100) >> 6-4);
		privateBit =                   (i & 0b0000_0010) != 0;
		channelConfiguration = (byte) ((i & 0b0000_0001 ) << 2);
		//3 (1+2) bits channel configuration, 1 bit copy, 1 bit home
		i = in.readByte();
		channelConfiguration |= (i & 0b1100_0000) >>> 8-2;
		copy =                  (i & 0b0010_0000) != 0;
		home =                  (i & 0b0001_0000) != 0;
		//int emphasis = in.readBits(2);
		//variable header:
		//1 bit copyrightIDBit, 1 bit copyrightIDStart, 13 bits frame length,
		copyrightIDBit =        (i & 0b0000_1000) != 0;
		copyrightIDStart =      (i & 0b0000_0100) != 0;
		frameLength =  (short) ((i & 0b0000_0011) << 13-2);
		if (frameLength > maxFrameLength) {
			maxFrameLength = frameLength;
			System.err.println("Max. frame length: " + maxFrameLength);
		}
		i = in.readUnsignedShort();
		//11 bits adtsBufferFullness, 2 bits rawDataBlockCount
		frameLength |=                (i & 0b1111_1111_1110_0000) >>> 16-11;

		adtsBufferFullness = (short) ((i & 0b0000_0000_0001_1111) << 11-5);
		i = in.readByte();
		adtsBufferFullness |=      (i & 0b1111_1100) >> 8-6;
		if (adtsBufferFullness == 0x7FF && specialLog1) {
			specialLog1 = false;
			System.err.println("variable bitrate");
		}
		rawDataBlockCount = (byte) (i & 0b0000_0011);
		if (rawDataBlockCount > 0) {
			System.err.println("Raw AAC block count in ADTS frame: " + (rawDataBlockCount+1));
		}
	}

	int getFrameLength() {
		return frameLength-(protectionAbsent ? 7 : 9);
	}

	public Profile getProfile() {
		return Profile.forInt(profile);
	}

	public SampleFrequency getSampleFrequency() {
		return SampleFrequency.forInt(sampleFrequency);
	}

	public ChannelConfiguration getChannelConfiguration() {
		return ChannelConfiguration.forInt(channelConfiguration);
	}
}
