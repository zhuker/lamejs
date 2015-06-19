# lamejs
Fast mp3 encoder written in JavaScript.
On my machine it works 20x faster than realtime (it will encode 132 second long sample in 6.5 seconds) both on node and chrome.
lamejs is a rewrite of jump3r-code which is a rewrite of libmp3lame.

# Quick Start

```javascript
<script src='lame.all.js'></script>
lib = new lamejs();
mp3encoder = new lib.Mp3Encoder(1, 44100, 128); //mono 44.1khz encode to 128kbps
samples = new Int16Array(44100); //one second of silence
var mp3 = mp3encoder.encodeBuffer(samples); //encode mp3
//write mp3 here
mp3 = mp3encoder.flush();
//write mp3 here
```

# Real Example

Either see [example.html](https://github.com/zhuker/lamejs/blob/master/example.html) for full example of wav file encoding in browser or use this:

```javascript
<script src='lame.all.js'></script>
<script>
lib = new lamejs();
channels = 1; //1 for mono or 2 for stereo
sampleRate = 44100; //44.1khz (normal mp3 samplerate)
kbps = 128; //encode 128kbps mp3
mp3encoder = new lib.Mp3Encoder(channels, sampleRate, kbps);

samples = new Int16Array(44100); //one second of silence (get your data from the source you have)
sampleBlockSize = 1152; //can be anything but make it a multiple of 576 to make encoders life easier

for (var i = 0; i < samples.length; i += sampleBlockSize) {
  sampleChunk = samples.subarray(i, i + sampleBlockSize);
  var mp3buf = mp3encoder.encodeBuffer(sampleChunk);
  if (mp3buf.length > 0) {
    //TODO: write your mp3 here
  }
}
var mp3buf = mp3encoder.flush();   //finish writing mp3

if (mp3buf.length > 0) {
    //TODO: finish writing your mp3 here
}
</script>
```

# Stereo

If you want to encode stereo mp3 use separate sample buffers for left and right channel

```javascript
<script src='lame.all.js'></script>
<script>
lib = new lamejs();
mp3encoder = new lib.Mp3Encoder(2, 44100, 128);

left = new Int16Array(44100); //one second of silence (get your data from the source you have)
right = new Int16Array(44100); //one second of silence (get your data from the source you have)
sampleBlockSize = 1152; //can be anything but make it a multiple of 576 to make encoders life easier

for (var i = 0; i < samples.length; i += sampleBlockSize) {
  leftChunk = left.subarray(i, i + sampleBlockSize);
  rightChunk = right.subarray(i, i + sampleBlockSize);
  var mp3buf = mp3encoder.encodeBuffer(leftChunk, rightChunk);
  if (mp3buf.length > 0) {
    //TODO: write your mp3 here
  }
}
var mp3buf = mp3encoder.flush();   //finish writing mp3

if (mp3buf.length > 0) {
    //TODO: finish writing your mp3 here
}
</script>
```

