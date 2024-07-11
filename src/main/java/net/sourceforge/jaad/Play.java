package net.sourceforge.jaad;

import net.sourceforge.jaad.aac.AACException;
import net.sourceforge.jaad.aac.Decoder;
import net.sourceforge.jaad.aac.DecoderConfig;
import net.sourceforge.jaad.aac.syntax.BitStream;
import net.sourceforge.jaad.aac.syntax.ByteArrayBitStream;
import net.sourceforge.jaad.adts.ADTSDemultiplexer;
import net.sourceforge.jaad.mp4.MP4Container;
import net.sourceforge.jaad.mp4.MP4Input;
import net.sourceforge.jaad.mp4.api.AudioTrack;
import net.sourceforge.jaad.mp4.api.Frame;
import net.sourceforge.jaad.mp4.api.Movie;
import net.sourceforge.jaad.mp4.api.Track;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import java.io.*;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.util.List;
import java.util.logging.Logger;

/**
 * Command line example, that can decode an AAC file and play it.
 * @author in-somnia
 */
public class Play {

	static final Logger LOGGER = Logger.getLogger("Play"); //for debugging

	private static final String USAGE = "usage:\nnet.sourceforge.jaad.Play [-mp4] <infile>\n\n\t-mp4\tinput file is in MP4 container format";

	public static void main(String[] args) {
		try {
			if(args.length<1)
				printUsage();
			if(args[0].equals("-mp4")) {
				if(args.length<2)
					printUsage();
				else
					decodeMP4(args[1]);
			}
			else
				decodeAAC(args[0]);
		}
		catch(Exception e) {
			e.printStackTrace();
			System.err.println("error while decoding: "+e.toString());
		}
	}

	private static void printUsage() {
		System.out.println(USAGE);
		System.exit(1);
	}

	private static void decodeMP4(String in) throws Exception {
		if(in.startsWith("http:"))
			decodeMP4(new URL(in).openStream());
		else
			//decodeMP4(new FileInputStream(in));
			decodeMP4(new RandomAccessFile(in, "r"));
	}

	protected static void decodeMP4(InputStream in) throws IOException, LineUnavailableException {
			decodeMP4(MP4Input.open(in));
	}

	protected static void decodeMP4(RandomAccessFile in) throws IOException, LineUnavailableException {
			decodeMP4(MP4Input.open(in));
	}

	protected static void decodeMP4(MP4Input in) throws IOException, LineUnavailableException {

		//create container
		final MP4Container cont = new MP4Container(in);
		final Movie movie = cont.getMovie();
		//find AAC track
		final List<Track> tracks = movie.getTracks(AudioTrack.AudioCodec.AAC);
		if(tracks.isEmpty())
			throw new UnsupportedOperationException("movie does not contain any AAC track");
		final AudioTrack track = (AudioTrack) tracks.get(0);

		//create AAC decoder
		Decoder dec = Decoder.create(track.getDecoderSpecificInfo().getData());

		//create audio format
		DecoderConfig conf = dec.getConfig();
		AudioFormat aufmt = dec.getAudioFormat();//new AudioFormat(conf.getOutputFrequency().getFrequency(), 16, conf.getChannelCount(), true, true);

		boolean lineStarted = false;
		try(final var line =  AudioSystem.getSourceDataLine(aufmt)) {
			line.open();

			//decode
			final SampleBuffer buf = new SampleBuffer(aufmt, conf.getSampleLength() * Math.max(2, aufmt.getChannels()) * aufmt.getSampleSizeInBits()/Byte.SIZE);
			final ByteBuffer _bb = buf.getBB();

			final byte[] primitiveSampleBuffer = new byte[_bb.capacity()];
			final var bitStream = new ByteArrayBitStream();
			while(track.hasMoreFrames()) {
				Frame frame = track.readNextFrame();

				try {
					bitStream.setData(frame.getData());
					dec.decodeFrame(bitStream, buf);
					int length = _bb.position();
					_bb.flip().get(primitiveSampleBuffer, 0, length);
					//buf.getData(primitiveSampleBuffer);
					line.write(primitiveSampleBuffer, 0, length);

					if (!lineStarted) {
						// pucgenie: Just to make things more complicated - or so I thought.
						line.start();
						lineStarted = true;
					}
				}
				catch(AACException e) {
					e.printStackTrace();
					//since the frames are separate, decoding can continue if one fails
				}

				//if(dec.frames>100)
				//	break;
			}
			line.drain();
		}
	}

    private static void decodeAAC(String in) throws Exception {
		if (in.startsWith("http") && (in.charAt(4) == ':' ||
				(in.charAt(4) == 's' && in.charAt(5) == ':'))
		)
			decodeAAC(new URL(in).openStream());
		else
			decodeAAC(new FileInputStream(in));
	}

	protected static void decodeAAC(InputStream in) throws IOException, LineUnavailableException {

		final ADTSDemultiplexer adts = new ADTSDemultiplexer(in);
		final Decoder dec = Decoder.create(adts.getDecoderInfo());
		AudioFormat aufmt = dec.getAudioFormat();
		final SampleBuffer buf = new SampleBuffer(aufmt, dec.getConfig().getSampleLength() * Math.max(2, aufmt.getChannels()) * aufmt.getSampleSizeInBits()/Byte.SIZE);
		final ByteBuffer _bb = buf.getBB();
		final byte[] primitiveSampleBuffer = new byte[_bb.capacity()];

		boolean lineStarted = false;
		try (SourceDataLine line = AudioSystem.getSourceDataLine(aufmt)) {
			try {
				final var bitStream = new ByteArrayBitStream();
				line.open();
				final var cbb = ByteBuffer.allocateDirect(ADTSDemultiplexer.MAXIMUM_FRAME_SIZE);
				while (true) {
					// pucgenie: So many useless copies...
					adts.readNextFrame(cbb);
					cbb.flip();
					bitStream.setData(cbb);
					cbb.clear();
					dec.decode0(bitStream, buf);
					int length = _bb.position();
					_bb.flip().get(primitiveSampleBuffer, 0, length);
					//buf.getData(primitiveSampleBuffer);
					assert line.write(primitiveSampleBuffer, 0, length) == length : "Need to start line before writing as it seems...";
					if (!lineStarted) {
						lineStarted = true;
						// pucgenie: Just to make things more complicated - or so I thought.
						line.start();
					}
					//aos.flush();
				}
			} finally {
				if (line.isRunning()) {
					line.drain();
				}
			}
		}
	}
}
