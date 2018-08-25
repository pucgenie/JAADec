package net.sourceforge.jaad.mp4;

import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;

/**
 * Created by IntelliJ IDEA.
 * User: stueken
 * Date: 25.08.18
 * Time: 17:25
 */
public interface MP4InputStream {

    static MP4InputStream open(InputStream in) {
        return new MP4FileInputStream(in);
    }

    static MP4InputStream open(RandomAccessFile in) {
        return new MP4FileInputStream(in);
    }

    String UTF8 = "UTF-8";
    String UTF16 = "UTF-16";

    int read() throws IOException;

    void peek(byte[] b, int off, int len) throws IOException;

    void read(byte[] b, int off, int len) throws IOException;

    long peekBytes(int n) throws IOException;

    long readBytes(int n) throws IOException;
    
    void readBytes(byte[] b) throws IOException;

    String readString(int n) throws IOException;

    String readUTFString(int max, String encoding) throws IOException;

    String readUTFString(int max) throws IOException;

    byte[] readTerminated(int max, int terminator) throws IOException;

    double readFixedPoint(int m, int n) throws IOException;

    void skipBytes(long n) throws IOException;

    long getOffset() throws IOException;

    void seek(long pos) throws IOException;

    boolean hasRandomAccess();

    boolean hasLeft() throws IOException;
}
