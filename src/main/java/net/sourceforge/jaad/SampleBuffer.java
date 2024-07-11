package net.sourceforge.jaad;

import net.sourceforge.jaad.aac.Receiver;

import javax.sound.sampled.AudioFormat;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Collection;
import java.util.List;

/**
 * The SampleBuffer holds the decoded AAC frame. It contains the raw PCM data
 * and its format.
 * @author in-somnia
 */
public class SampleBuffer implements Receiver {

	private int sampleRate, channels, bitsPerSample;
	private double length, bitrate;
	//private byte[] data;
	//private boolean bigEndian;
	private ByteBuffer bb;

	/**
	 * pucgenie: Might have broken all code using this because of ******* ByteOrder.
	 * @param sampleLength
	 */
	public SampleBuffer(int sampleLength) {
		bb = ByteBuffer.allocateDirect(sampleLength);
		bb.order(ByteOrder.BIG_ENDIAN);
		sampleRate = 0;
		channels = 0;
		bitsPerSample = 0;
	}

	public SampleBuffer(AudioFormat af, int sampleLength) {
		sampleRate = (int) af.getSampleRate();
		channels = af.getChannels();
		bitsPerSample = af.getSampleSizeInBits();
		bb = ByteBuffer.allocateDirect(sampleLength);
		bb.order(af.isBigEndian() ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN);
	}

	/**
	 * Returns the buffer's PCM data.
	 * @return the audio data
	 */
	public ByteBuffer getBB() {
		return bb;
	}

	/**
	 *
	 * @deprecated Use {@link #getBB()} instead.
	 * @param primitiveSampleBuffer
	 * @return
	 */
	@Deprecated
	public byte[] getData(byte[] primitiveSampleBuffer) {
		if (primitiveSampleBuffer == null || primitiveSampleBuffer.length < bb.position()) {
			if (primitiveSampleBuffer != null) {
				System.err.println("reallocating primitiveSampleBuffer to " + bb.position());
			}
			primitiveSampleBuffer = new byte[bb.position()];
		}
		bb.flip().get(primitiveSampleBuffer);
		return primitiveSampleBuffer;
	}

	/**
	 * Returns the data's sample rate.
	 * @return the sample rate
	 */
	public int getSampleRate() {
		return sampleRate;
	}

	/**
	 * Returns the number of channels stored in the data buffer.
	 * @return the number of channels
	 */
	public int getChannels() {
		return channels;
	}

	/**
	 * Returns the number of bits per sample. Usually this is 16, meaning a
	 * sample is stored in two bytes.
	 * @return the number of bits per sample
	 */
	public int getBitsPerSample() {
		return bitsPerSample;
	}

	/**
	 * Returns the length of the current frame in seconds.
	 * length = samplesPerChannel / sampleRate
	 * @return the length in seconds
	 */
	public double getLength() {
		return length;
	}

	/**
	 * Returns the bitrate of the decoded PCM data.
	 * <code>bitrate = (samplesPerChannel * bitsPerSample) / length</code>
	 * @return the bitrate
	 */
	public double getBitrate() {
		return bitrate;
	}

	/**
	 * Indicates the endianness for the data.
	 * 
	 * @return true if the data is in big endian, false if it is in little endian
	 */
	public boolean isBigEndian() {
		return bb.order().equals(ByteOrder.BIG_ENDIAN);
	}

	/**
	 * Sets the endianness for the data.
	 * 
	 * @param bigEndian if true the data will be in big endian, else in little 
	 * endian
	 */
	public void setBigEndian(boolean bigEndian) {
		if(bigEndian!=isBigEndian()) {
			byte tmp;
			final int bbLength = bb.position();
			for(int i = 0; i < bbLength; i += 2) {
				tmp = bb.get(i);
				bb.put(i, bb.get(i+1));
				bb.put(i+1, tmp);
			}
			bb.order(bigEndian ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN);
		}
	}

	public void setData(byte[] data, int sampleRate, int channels, int bitsPerSample, int bitsRead) {
		if (this.bb.capacity() >= data.length) {
			this.bb.clear();
			this.bb.put(data);
		} else {
			final boolean bigEndian = isBigEndian();
			this.bb = ByteBuffer.wrap(data);
			bb.order(bigEndian ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN);
		}
		this.sampleRate = sampleRate;
		this.channels = channels;
		this.bitsPerSample = bitsPerSample;

		if(sampleRate==0) {
			length = 0;
			bitrate = 0;
		}
		else {
			final int bytesPerSample = bitsPerSample/8; //usually 2
			final int samplesPerChannel = data.length/(bytesPerSample*channels); //=1024
			length = (double) samplesPerChannel/(double) sampleRate;
			bitrate = (double) (samplesPerChannel*bitsPerSample*channels)/length;
			//encodedBitrate = (double) bitsRead/length;
		}
	}

	@Override
	public void accept(final Collection<float[]> samples, final int sampleLength, final int sampleRate) {

		this.sampleRate = sampleRate;
		this.channels = samples.size();
		this.bitsPerSample = Short.SIZE;

		// pucgenie: Why hardcoded to 16 bits?
		final int bytes = samples.size() * bitsPerSample/Byte.SIZE * sampleLength;
		if(bb.capacity() < bytes) {
			System.err.println("reallocating bb data ByteBuffer, previous capacity: " + bb.capacity());
			final boolean bigEndian = isBigEndian();
			this.bb = ByteBuffer.allocateDirect(bytes);
			bb.order(bigEndian ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN);
		} else {
			bb.rewind();
		}

		this.length = (double) sampleLength/sampleRate;
		this.bitrate = (double) sampleLength*bitsPerSample*channels/bytes;

		for(int is=0; is<sampleLength; ++is) {
			for (float[] sample : samples) {
				int k = sample.length * is / sampleLength;
				float s = sample[k];
				// pucgenie: Why convert here? Simply use float AudioFormat...
				int pulse = Math.round(s);
				// #clamp is coming with Java 21: https://download.java.net/java/early_access/jdk21/docs/api/java.base/java/lang/Math.html#clamp(long,long,long)
				//bb.putShort((short) Math.clamp(pulse, Short.MIN_VALUE, Short.MAX_VALUE));
				if (pulse > Short.MAX_VALUE || pulse < Short.MIN_VALUE) {
					System.err.println("out of Short (16 bit) bounds: " + s);
				}
				bb.putShort((short) (
						(pulse > Short.MAX_VALUE)
						? Short.MAX_VALUE
				 		: (pulse < Short.MIN_VALUE)
							? Short.MIN_VALUE
							: pulse
						)
				);
			}
		}
	}
}
