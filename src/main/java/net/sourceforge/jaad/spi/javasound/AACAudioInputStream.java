package net.sourceforge.jaad.spi.javasound;

import net.sourceforge.jaad.aac.Decoder;
import net.sourceforge.jaad.SampleBuffer;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import javax.sound.sampled.AudioFormat;
import net.sourceforge.jaad.adts.ADTSDemultiplexer;

class AACAudioInputStream extends AsynchronousAudioInputStream {

	private final ADTSDemultiplexer adts;
	private final Decoder decoder;
	private final SampleBuffer sampleBuffer;
	private AudioFormat audioFormat = null;
	private byte[] saved;

	AACAudioInputStream(InputStream in, AudioFormat format, long length) throws IOException {
		super(in, format, length);
		adts = new ADTSDemultiplexer(in);
		decoder = Decoder.create(adts.getDecoderInfo());
		// pucgenie: Somehow it always decodes to 16 bit (=2 bytes)
		sampleBuffer = new SampleBuffer(decoder.getConfig().getSampleLength() * adts.getChannelCount() * 2);
	}

	@Override
	public AudioFormat getFormat() {
		if(audioFormat==null) {
			//read first frame
			try {
				decoder.decodeFrame(adts.readNextFrame(null), sampleBuffer);
				audioFormat = new AudioFormat(sampleBuffer.getSampleRate(), sampleBuffer.getBitsPerSample(), sampleBuffer.getChannels(), true, true);
				ByteBuffer bufferedData = sampleBuffer.getBB();
				saved = new byte[bufferedData.position()];
				bufferedData.flip().get(saved);
			}
			catch(IOException e) {
				return null;
			}
		}
		return audioFormat;
	}

	public void execute() {
		try {
			if(saved==null) {
				decoder.decodeFrame(adts.readNextFrame(null), sampleBuffer);
				ByteBuffer bufferedData = sampleBuffer.getBB();
				saved = new byte[bufferedData.remaining()];
				bufferedData.flip().get(saved);
			}
			buffer.write(saved);
			saved = null;
		}
		catch(IOException e) {
			buffer.close();
			return;
		}
	}
}
