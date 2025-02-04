package net.sourceforge.jaad;

import net.sourceforge.jaad.aac.Decoder;
import net.sourceforge.jaad.aac.syntax.ByteArrayBitStream;
import net.sourceforge.jaad.adts.ADTSDemultiplexer;
import net.sourceforge.jaad.mp4.MP4Container;
import net.sourceforge.jaad.mp4.MP4Input;
import net.sourceforge.jaad.mp4.api.AudioTrack;
import net.sourceforge.jaad.mp4.api.Frame;
import net.sourceforge.jaad.mp4.api.Movie;
import net.sourceforge.jaad.mp4.api.Track;
import net.sourceforge.jaad.util.wav.WaveFileWriter;

import java.io.*;
import java.nio.ByteBuffer;
import java.util.List;

/**
 * Command line example, that can decode an AAC file to a WAVE file.
 * @author in-somnia
 */
public class Main {

	private static final String USAGE = "usage:\nnet.sourceforge.jaad.Main [-mp4] <infile> <outfile>\n\n\t-mp4\tinput file is in MP4 container format";

	public static void main(String[] args) {
		try {
			if(args.length<2)
				printUsage();
			if(args[0].equals("-mp4")) {
				if(args.length<3)
					printUsage();
				else
					decodeMP4(args[1], args[2]);
			}
			else
				decodeAAC(args[0], args[1]);
		}
		catch(Exception e) {
			System.err.println("error while decoding: "+e.toString());
		}
	}

	private static void printUsage() {
		System.out.println(USAGE);
		System.exit(1);
	}

	private static void decodeMP4(String in, String out) throws Exception {
		WaveFileWriter wav = null;
		try {
			final MP4Input is = MP4Input.open(new RandomAccessFile(in, "r"));
			final MP4Container cont = new MP4Container(is);
			final Movie movie = cont.getMovie();
			final List<Track> tracks = movie.getTracks(AudioTrack.AudioCodec.AAC);
			if(tracks.isEmpty())
			    throw new Exception("movie does not contain any AAC track");
			final AudioTrack track = (AudioTrack) tracks.get(0);

			wav = new WaveFileWriter(new File(out), track.getSampleRate(), track.getChannelCount(), track.getSampleSize());

			final Decoder dec = Decoder.create(track.getDecoderSpecificInfo().getData());

			Frame frame;
			final SampleBuffer buf = new SampleBuffer(dec.getConfig().getSampleLength() * Math.max(2, track.getChannelCount()) * track.getSampleSize()/Byte.SIZE);
			byte[] primitiveSampleBuffer = null;
			final var bitStream = new ByteArrayBitStream();
			while(track.hasMoreFrames()) {
				frame = track.readNextFrame();
				bitStream.setData(frame.getData());
				dec.decodeFrame(bitStream, buf);
				primitiveSampleBuffer = buf.getData(primitiveSampleBuffer);
				wav.write(primitiveSampleBuffer);
			}
		}
		finally {
			if(wav!=null)
				wav.close();
		}
	}

	private static void decodeAAC(String in, String out) throws IOException {
		final ADTSDemultiplexer adts = new ADTSDemultiplexer(new FileInputStream(in));
		final Decoder dec = Decoder.create(adts.getDecoderInfo());

		// heuristic
		final SampleBuffer buf = new SampleBuffer(dec.getConfig().getSampleLength() * 4);

		final var cbb = ByteBuffer.allocateDirect(ADTSDemultiplexer.MAXIMUM_FRAME_SIZE);
		final var bitStream = new ByteArrayBitStream();
		// initializes buf.bitsPerSample
		adts.readNextFrame(cbb);
		cbb.flip();
		bitStream.setData(cbb);
		cbb.clear();
		dec.decode0(bitStream, buf);

		final byte[] primitiveSampleBuffer = buf.getData(null);
		try (var wav = new WaveFileWriter(new File(out), adts.getSampleFrequency(), adts.getChannelCount(), buf.getBitsPerSample())) {
			wav.write(primitiveSampleBuffer);
			while (true) {
				adts.readNextFrame(cbb);
				cbb.flip();
				bitStream.setData(cbb);
				cbb.rewind();
				dec.decode0(bitStream, buf);
				buf.getData(primitiveSampleBuffer);
				wav.write(primitiveSampleBuffer);
			}
		}
	}
}
