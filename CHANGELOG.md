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