# JAADec changelog

The upstream JAADec forked https://sourceforge.net/projects/jaadec/ and performed minimal changes to the original code.
The original code itself was a conversion of the original https://sourceforge.net/projects/faac/ written in 'C'.
Therefor most of the Java code still looks like plain 'c'. 
Instead of using objects and interfaces most classes are data structures copying byte arrays back and forth.
In order to get a better understanding of how AAC works, I will start with massive refactorings.

**2017-12-27**

To get a better logging the global logging was replaced by local loggers.
Also static logger setup blocks are removed in favor of a configuration file.

**2018-03-29**

The initial 'c' code style preferred to put statements into a single line if possible.
In Java this is hard to debug because it you may only set line breakpoints.
Thus I started to break up those lines.

**2018-04-02**

The initial fork of Austin Keener started with the previous sourceforge project.
To get a better understanding of the history I prepended all the previeous commits from the sourceforge project and connected both using a git-replace.
To see the full history you have to [checkout the refs](https://stackoverflow.com/questions/20069482/how-to-push-refs-replace-without-pushing-any-other-refs-in-git), too.

**2018-08-25**

The Packages mp4 and aac seem to be separate modules.
To understand the dependencies of both I separated them into different modules.
It turns out that aac depends on mp4.od.DecoderSpecificInfo which just returns a byte[].
Thus there is a real change to separate both completely.

Both packages use a different implementation to read bits/bytes.
aac uses a SampleBuffer while mp4 uses MP4InputStream.
Both implementations are quite complex.
Especially the MP4InputStream which uses a complicated mixture of RandomAccessFile and InputStream.

**2018-08-31**

Due to a bug in some decodes the MetaBox may be coded differently.
To handle those broken files the MP4InputStream needs to peek ahead the next word.
This makes MP4InputStream extremely complicated.

A work around fixes this problem without a peek function. 
This enables a complete refactoring of MP4InputStream. 

**2018-09-01**

Replaced all usages of MP4InputStream by interface MP4Input.
Split out separate implementations based on RandomAccessFile and InputStream.
  
**2018-09-18**

CCE was completely broken. The code taken from the original C-code (faad2) 
was never adapted to the rearranged java data arrays.
           
**2018-12-21** 

AACException was a checked Exception. 
This every single function has to declare throwing an AACException.
Turning AACException into a RuntimeException makes all those declarations obsolete.
This also enables to use AAC functions as lambda expressions.  

**2018-12-24**

BitStream was turned into an interface.
ByteArrayBitStream is an implementation based on byte[].
Future implementations may base on ByteBuffer. 

**2019-04-15**

Decoding PCE from decoderSpecificInfo gets the wrong Profile. 
The index must be incremented by 1 (see ISO-14496 Table 4.83 – Object type index)

LTP was broken. LTPrediction records are allocated on demand but are needed to carry sample data of previous frames.
This throws NPE when calling updateState(). LTPrediction records must be allocated ahed.

Things become very complicated if CPE.isCommonWindow(): In this case only one ICSInfo record is decoded,
but two different LTPrediction records are needed. To solve this problem each ICSInfo carries two LTPrediction records.
In case of CPE.isCommonWindow() the right channel takes is LTP from the secondary LTP element of the left channel.

To ease this situation each ICSInfo carries its own single LTP. In case of a common window, the right ICSInfo copies
its data from the left ICSInfo and the reads its LTP (if present).

Each ICSInfo allocates its LTP ahead to enable sample data buffering.

Moving the ltpDataPresent flag from ICSInfo into LTPrediction simplifies the handling further.

LTP needs FFT forward processing which used a wrong scaling factor. FFT was simlpified slightly.

Temporary double[] arrays are often allocated for each call. This is no problem within C-code,
but with Java this is very inefficient and produces a lot of garbage objects. 
Some of those arrays are turned into member variables to be reusable.