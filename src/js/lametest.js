require('use-strict');

fs = require('fs');
var common = require('./common.js');
var System = common.System;
var VbrMode = common.VbrMode;
var Float = common.Float;
var ShortBlock = common.ShortBlock;
var Util = common.Util;
var Arrays = common.Arrays;
var new_array_n = common.new_array_n;
var new_byte = common.new_byte;
var new_double = common.new_double;
var new_float = common.new_float;
var new_float_n = common.new_float_n;
var new_int = common.new_int;
var new_int_n = common.new_int_n;
var assert = common.assert;

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

function MPGLib() {
}

function ID3Tag() {
    var bits;
    var ver;

    this.setModules = function (_bits, _ver) {
        bits = _bits;
        ver = _ver;
    }
}

function Mp3Encoder(channels, samplerate, kbps) {
    if (arguments.length != 3) {
        console.error('WARN: Mp3Encoder(channels, samplerate, kbps) not specified');
        channels = 1;
        samplerate = 44100;
        kbps = 128;
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

    gfp.num_channels = channels;
    gfp.in_samplerate = samplerate;
    gfp.out_samplerate = samplerate;
    gfp.brate = kbps;
    gfp.mode = MPEGMode.STEREO;
    gfp.quality = 3;
    gfp.bWriteVbrTag = false;
    gfp.disable_reservoir = true;
    gfp.write_id3tag_automatic = false;

    var retcode = lame.lame_init_params(gfp);
    assert(0 == retcode);
    var maxSamples = 1152;
    var mp3buf_size = 0 | (1.25 * maxSamples + 7200);
    var mp3buf = new_byte(mp3buf_size);

    this.encodeBuffer = function (left, right) {
        if (channels == 1) {
            right = left;
        }
        assert(left.length == right.length);
        if (left.length > maxSamples) {
            maxSamples = left.length;
            mp3buf_size = 0 | (1.25 * maxSamples + 7200);
            mp3buf = new_byte(mp3buf_size);
        }

        var _sz = lame.lame_encode_buffer(gfp, left, right, left.length, mp3buf, 0, mp3buf_size);
        return new Int8Array(mp3buf.subarray(0, _sz));
    };

    this.flush = function () {
        var _sz = lame.lame_encode_flush(gfp, mp3buf, 0, mp3buf_size);
        return new Int8Array(mp3buf.subarray(0, _sz));
    };
}

function WavHeader() {
    this.dataOffset = 0;
    this.dataLen = 0;
    this.channels = 0;
    this.sampleRate = 0;
}

function fourccToInt(fourcc) {
    return fourcc.charCodeAt(0) << 24 | fourcc.charCodeAt(1) << 16 | fourcc.charCodeAt(2) << 8 | fourcc.charCodeAt(3);
}

WavHeader.RIFF = fourccToInt("RIFF");
WavHeader.WAVE = fourccToInt("WAVE");
WavHeader.fmt_ = fourccToInt("fmt ");
WavHeader.data = fourccToInt("data");

WavHeader.readHeader = function (dataView) {
    var w = new WavHeader();

    var header = dataView.getUint32(0, false);
    if (WavHeader.RIFF != header) {
        return;
    }
    var fileLen = dataView.getUint32(4, true);
    if (WavHeader.WAVE != dataView.getUint32(8, false)) {
        return;
    }
    if (WavHeader.fmt_ != dataView.getUint32(12, false)) {
        return;
    }
    var fmtLen = dataView.getUint32(16, true);
    var pos = 16 + 4;
    switch (fmtLen) {
        case 16:
        case 18:
            w.channels = dataView.getUint16(pos + 2, true);
            w.sampleRate = dataView.getUint32(pos + 4, true);
            break;
        default:
            throw 'extended fmt chunk not implemented';
            break;
    }
    pos += fmtLen;
    var data = WavHeader.data;
    var len = 0;
    while (data != header) {
        header = dataView.getUint32(pos, false);
        len = dataView.getUint32(pos + 4, true);
        if (data == header) {
            break;
        }
        pos += (len + 8);
    }
    w.dataLen = len;
    w.dataOffset = pos + 8;
    return w;
};

function testFullLength() {
    var r = fs.readFileSync("testdata/IMG_0373.wav");
    var sampleBuf = new Uint8Array(r).buffer;
    var w = WavHeader.readHeader(new DataView(sampleBuf));
    var samples = new Int16Array(sampleBuf, w.dataOffset, w.dataLen / 2);
    var remaining = samples.length;
    var lameEnc = new Mp3Encoder(); //w.channels, w.sampleRate, 128);
    var maxSamples = 1152;

    var fd = fs.openSync("testjs2.mp3", "w");
    var time = new Date().getTime();
    for (var i = 0; remaining >= maxSamples; i += maxSamples) {
        var left = samples.subarray(i, i + maxSamples);
        var right = samples.subarray(i, i + maxSamples);

        var mp3buf = lameEnc.encodeBuffer(left, right);
        if (mp3buf.length > 0) {
            fs.writeSync(fd, new Buffer(mp3buf), 0, mp3buf.length);
        }
        remaining -= maxSamples;
    }
    var mp3buf = lameEnc.flush();
    if (mp3buf.length > 0) {
        fs.writeSync(fd, new Buffer(mp3buf), 0, mp3buf.length);
    }
    fs.closeSync(fd);
    time = new Date().getTime() - time;
    console.log('done in ' + time + 'msec');
}

function testStereo44100() {
    var r1 = fs.readFileSync("testdata/Left44100.wav");
    var r2 = fs.readFileSync("testdata/Right44100.wav");
    var fd = fs.openSync("stereo.mp3", "w");

    var sampleBuf1 = new Uint8Array(r1).buffer;
    var sampleBuf2 = new Uint8Array(r2).buffer;
    var w1 = WavHeader.readHeader(new DataView(sampleBuf1));
    var w2 = WavHeader.readHeader(new DataView(sampleBuf2));

    var samples1 = new Int16Array(sampleBuf1, w1.dataOffset, w1.dataLen / 2);
    var samples2 = new Int16Array(sampleBuf2, w2.dataOffset, w2.dataLen / 2);
    var remaining1 = samples1.length;
    var remaining2 = samples2.length;
    assert(remaining1 == remaining2);
    assert(w1.sampleRate == w2.sampleRate);

    var lameEnc = new Mp3Encoder(2, w1.sampleRate, 128);
    var maxSamples = 1152;

    var time = new Date().getTime();
    for (var i = 0; remaining1 >= maxSamples; i += maxSamples) {
        var left = samples1.subarray(i, i + maxSamples);
        var right = samples2.subarray(i, i + maxSamples);

        var mp3buf = lameEnc.encodeBuffer(left, right);
        if (mp3buf.length > 0) {
            fs.writeSync(fd, new Buffer(mp3buf), 0, mp3buf.length);
        }
        remaining1 -= maxSamples;

    }
    var mp3buf = lameEnc.flush();
    if (mp3buf.length > 0) {
        fs.writeSync(fd, new Buffer(mp3buf), 0, mp3buf.length);
    }
    fs.closeSync(fd);
    time = new Date().getTime() - time;
    console.log('done in ' + time + 'msec');
}

//testStereo44100();
//testFullLength();
