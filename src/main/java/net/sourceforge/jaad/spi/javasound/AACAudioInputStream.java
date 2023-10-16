package net.sourceforge.jaad.spi.javasound;

import net.sourceforge.jaad.aac.Decoder;
import net.sourceforge.jaad.SampleBuffer;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import javax.sound.sampled.AudioFormat;

import net.sourceforge.jaad.aac.syntax.ByteArrayBitStream;
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

	protected final ByteArrayBitStream bitStream = new ByteArrayBitStream();
	protected final ByteBuffer cbb = ByteBuffer.allocateDirect(ADTSDemultiplexer.MAXIMUM_FRAME_SIZE);

	protected void readNextFrameInternal() throws IOException {
		adts.readNextFrame(cbb);
		cbb.flip();
		bitStream.setData(cbb);
		cbb.clear();
		decoder.decode0(bitStream, sampleBuffer);
		audioFormat = new AudioFormat(sampleBuffer.getSampleRate(), sampleBuffer.getBitsPerSample(), sampleBuffer.getChannels(), true, true);
		ByteBuffer bufferedData = sampleBuffer.getBB();
		saved = new byte[bufferedData.position()];
		bufferedData.flip().get(saved);
	}

	@Override
	public AudioFormat getFormat() {
		if(audioFormat==null) {
			//read first frame
			try {
				readNextFrameInternal();
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
				readNextFrameInternal();
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
