package net.sourceforge.jaad.mp4.boxes.impl;

import java.io.IOException;
import net.sourceforge.jaad.mp4.MP4InputStream;
import net.sourceforge.jaad.mp4.boxes.FullBox;

/**
 * This box within a Media Box declares the process by which the media-data in
 * the track is presented, and thus, the nature of the media in a track. For
 * example, a video track would be handled by a video handler.
 *
 * This box when present within a Meta Box, declares the structure or format of
 * the 'meta' box contents.
 *
 * There is a general handler for metadata streams of any type; the specific
 * format is identified by the sample entry, as for video or audio, for example.
 * If they are in text, then a MIME format is supplied to document their format;
 * if in XML, each sample is a complete XML document, and the namespace of the
 * XML is also supplied.
 * @author in-somnia
 */
public class HandlerBox extends FullBox {

	//ISO BMFF types
	public static final int TYPE_VIDEO = 1986618469; //vide
	public static final int TYPE_SOUND = 1936684398; //soun
	public static final int TYPE_HINT = 1751740020; //hint
	public static final int TYPE_META = 1835365473; //meta
	public static final int TYPE_NULL = 1853189228; //null
	//MP4 types
	public static final int TYPE_ODSM = 1868854125; //odsm
	public static final int TYPE_CRSM = 1668445037; //crsm
	public static final int TYPE_SDSM = 1935962989; //sdsm
	public static final int TYPE_M7SM = 1832350573; //m7sm
	public static final int TYPE_OCSM = 1868788589; //ocsm
	public static final int TYPE_IPSM = 1768977261; //ipsm
	public static final int TYPE_MJSM = 1835692909; //mjsm

	public static void main(String[] args) {
		System.out.println("odsm: "+toLong("odsm"));
		System.out.println("crsm: "+toLong("crsm"));
		System.out.println("sdsm: "+toLong("sdsm"));
		System.out.println("m7sm: "+toLong("m7sm"));
		System.out.println("ocsm: "+toLong("ocsm"));
		System.out.println("ipsm: "+toLong("ipsm"));
		System.out.println("mjsm: "+toLong("mjsm"));
	}

	private static long toLong(String s) {
		long l = 0;
		for(int i = 0; i<4; i++) {
			l <<= 8;
			l |= s.charAt(i);
		}
		return l;
	}
	private long handlerType;
	private String handlerName;

	public HandlerBox() {
		super("Handler Box");
	}

	@Override
	public void decode(MP4InputStream in) throws IOException {
		super.decode(in);

		in.skipBytes(4); //pre-defined: 0

		handlerType = in.readBytes(4);

		in.readBytes(4); //reserved
		in.readBytes(4); //reserved
		in.readBytes(4); //reserved
		left -= 20;

		handlerName = in.readUTFString((int) left, MP4InputStream.UTF8);
		left -= handlerName.length()+1;
	}

	/**
	 * When present in a media box, the handler type is an integer containing
	 * one of the following values:
	 * <ul>
	 * <li>'vide': Video track</li>
	 * <li>'soun': Audio track</li>
	 * <li>'hint': Hint track</li>
	 * <li>'meta': Timed Metadata track</li>
	 * </ul>
	 *
	 * When present in a meta box, it contains an appropriate value to indicate
	 * the format of the meta box contents. The value 'null' can be used in the
	 * primary meta box to indicate that it is merely being used to hold
	 * resources.
	 *
	 * @return the handler type
	 */
	public long getHandlerType() {
		return handlerType;
	}

	/**
	 * The name gives a human-readable name for the track type (for debugging
	 * and inspection purposes).
	 * 
	 * @return the handler type's name
	 */
	public String getHandlerName() {
		return handlerName;
	}
}
