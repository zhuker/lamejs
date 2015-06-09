require('use-strict');

assert = console.assert;
fs = require('fs');
Lame = require('./Lame.js');
Presets = require('./Presets.js');
GainAnalysis = require('./GainAnalysis.js');
QuantizePVT = require('./QuantizePVT.js');
Quantize = require('./Quantize.js');
Takehiro = require('./Takehiro.js');
Reservoir = require('./Reservoir.js');
MPEGMode = require('./MPEGMode.js');
BitStream = require('./BitStream.js');
var Encoder = require('./Encoder.js');
var Version = require('./Version.js');
var VBRTag = require('./VBRTag.js');

function GetAudio() {
    var parse;
    var mpg;

    this.setModules = function (parse2, mpg2) {
        parse = parse2;
        mpg = mpg2;
    }
}


function Parse() {
    var ver;
    var id3;
    var pre;

    this.setModules = function (ver2, id32, pre2) {
        ver = ver2;
        id3 = id32;
        pre = pre2;
    }
}

function BRHist() {
    console.log("TODO: BRHist");
}
function MPGLib() {
    console.log("TODO: MPGLib");
}

function ID3Tag() {
    var bits;
    var ver;

    this.setModules = function (_bits, _ver) {
        bits = _bits;
        ver = _ver;
    }
}

var lame = new Lame();
var gaud = new GetAudio();
var ga = new GainAnalysis();
var bs = new BitStream();
var p = new Presets();
var qupvt = new QuantizePVT();
var qu = new Quantize();
var vbr = new VBRTag();
var ver = new Version();
var id3 = new ID3Tag();
var rv = new Reservoir();
var tak = new Takehiro();
var parse = new Parse();
var mpg = new MPGLib();

lame.setModules(ga, bs, p, qupvt, qu, vbr, ver, id3, mpg);
bs.setModules(ga, mpg, ver, vbr);
id3.setModules(bs, ver);
p.setModules(lame);
qu.setModules(bs, rv, qupvt, tak);
qupvt.setModules(tak, rv, lame.enc.psy);
rv.setModules(bs);
tak.setModules(qupvt);
vbr.setModules(lame, bs, ver);
gaud.setModules(parse, mpg);
parse.setModules(ver, id3, p);

var gfp = lame.lame_init();

gfp.num_channels = 1;
gfp.in_samplerate = 48000;
gfp.brate = 128;
gfp.mode = MPEGMode.STEREO;
gfp.quality = 3;
gfp.bWriteVbrTag = false;
gfp.disable_reservoir = true;
gfp.write_id3tag_automatic = false;

var retcode = lame.lame_init_params(gfp);
console.log("DONE " + retcode);


var r = fs.readFileSync('/Users/zhukov/git/tle1.3x/test-data/wav/440880.wav');
sampleBuf = new Uint8Array(r).buffer;
var dataLen = new DataView(sampleBuf).getInt32(0x28, true);
console.log(dataLen);
samples = new Int16Array(sampleBuf, 0x2c, dataLen / 2);
assert(samples[1] == 0x05e6);
console.log(samples.length);

var remaining = samples.length;
var left = new Int16Array(samples);
var right = new Int16Array(samples);
var mp3buf_size = 0 | (1.25 * remaining + 7200);
var mp3buf = new Int8Array(mp3buf_size);
var mp3bufPos = 0;
var _sz = lame.lame_encode_buffer(gfp, left, right, remaining,
    mp3buf, mp3bufPos, mp3buf_size);
console.log("lame_encode_buffer: " + _sz);
for (var i = 0; i < _sz; i++) {
    console.log(mp3buf[i]);
}

_sz = lame.lame_encode_flush(gfp, mp3buf, mp3bufPos, mp3buf_size);
console.log("lame_encode_flush: " + _sz);

