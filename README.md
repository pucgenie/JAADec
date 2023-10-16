# JAADec
**This is a fork of https://sourceforge.net/projects/jaadec/ containing fixes to make it play nice with other Java Sound Providers.**

The original project was licensed under Public Domain and as such this fork is also licensed under Public Domain. Use as you like!

JAAD is an AAC decoder and MP4 demultiplexer library written completely in Java. It uses no native libraries, is platform-independent and portable. It can read MP4 container from almost every input-stream (files, network sockets etc.) and decode AAC-LC (Low Complexity) and HE-AAC (High Efficiency/AAC+).

This library is available on Bintray's `jcenter` as a Maven/Gradle download.<br>
https://bintray.com/dv8fromtheworld/maven/JAADec/view

#### For Gradle:

```groovy
repositories {
  jcenter()
}
```
```groovy
dependencies {
  compile 'net.sourceforge.jaadec:jaad:0.8.6'
}
```

#### For Maven:

```xml
<repositories>
  <repository>
    <id>central</id>
    <name>bintray</name>
    <url>http://jcenter.bintray.com</url>
  </repository>
</repositories>
```
```xml
<dependencies>
  <dependency>
    <groupId>net.sourceforge.jaadec</groupId>
    <artifactId>jaad</artifactId>
    <version>0.8.6</version>
  </dependency>
</dependencies>
```

### See also
https://github.com/bencampion/adts-tool
