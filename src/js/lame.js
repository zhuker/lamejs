fs = require('fs');

console.log('hello');

var a = 2 + 2;
console.log(a);
Lame = {};
var LAME_MAXALBUMART = (128 * 1024);

/**
 * maximum size of mp3buffer needed if you encode at most 1152 samples for
 * each call to lame_encode_buffer. see lame_encode_buffer() below
 * (LAME_MAXMP3BUFFER is now obsolete)
 */
var LAME_MAXMP3BUFFER = (16384 + LAME_MAXALBUMART);
Lame.LAME_MAXMP3BUFFER = LAME_MAXMP3BUFFER;

var r = fs.readFileSync('/Users/zhukov/git/tle1.3x/test-data/wav/440880.wav');
eval(fs.readFileSync('./common.js', 'utf8'));
function VbrMode(ordinal) {
    this.ordinal = ordinal;
}
VbrMode.vbr_off = new VbrMode(0);
VbrMode.vbr_mt = new VbrMode(1);
VbrMode.vbr_rh = new VbrMode(2);
VbrMode.vbr_abr = new VbrMode(3);
VbrMode.vbr_mtrh = new VbrMode(4);
VbrMode.vbr_default = VbrMode.vbr_mtrh;

/**
 * PSY Model related stuff
 */
function PSY(){
    /**
     * The dbQ stuff.
     */
    this.mask_adjust =0.;
    /**
     * The dbQ stuff.
     */
    this.mask_adjust_short=0.;
    /* at transition from one scalefactor band to next */
    /**
     * Band weight long scalefactor bands.
     */
    this.bo_l_weight = new_float(Encoder.SBMAX_l);
    /**
     * Band weight short scalefactor bands.
     */
    this.bo_s_weight = new_float(Encoder.SBMAX_s);
}

function LowPassHighPass() {
    this.lowerlimit = 0.;
}

function BandPass (bitrate, lPass) {
    this.lowpass = lPass;
}


eval(fs.readFileSync('./ATH.js', 'utf8'));
eval(fs.readFileSync('./Tables.js', 'utf8'));
eval(fs.readFileSync('./MPEGMode.js', 'utf8'));
eval(fs.readFileSync('./LameGlobalFlags.js', 'utf8'));
eval(fs.readFileSync('./BitStream.js', 'utf8'));
eval(fs.readFileSync('./QuantizePVT.js', 'utf8'));
eval(fs.readFileSync('./Encoder.js', 'utf8'));
eval(fs.readFileSync('./L3Side.js', 'utf8'));
eval(fs.readFileSync('./GrInfo.js', 'utf8'));
eval(fs.readFileSync('./IIISideInfo.js', 'utf8'));
eval(fs.readFileSync('./ScaleFac.js', 'utf8'));
eval(fs.readFileSync('./NsPsy.js', 'utf8'));
eval(fs.readFileSync('./VBRSeekInfo.js', 'utf8'));
eval(fs.readFileSync('./III_psy_xmin.js', 'utf8'));
eval(fs.readFileSync('./GainAnalysis.js', 'utf8'));
eval(fs.readFileSync('./ReplayGain.js', 'utf8'));
eval(fs.readFileSync('./LameInternalFlags.js', 'utf8'));

var LAME_ID = 0xFFF88E3B;

function lame_init_old(gfp) {
    var gfc;

    gfp.class_id = LAME_ID;

    gfc = gfp.internal_flags = new LameInternalFlags();

    /* Global flags. set defaults here for non-zero values */
    /* see lame.h for description */
    /*
     * set integer values to -1 to mean that LAME will compute the best
     * value, UNLESS the calling program as set it (and the value is no
     * longer -1)
     */

    gfp.mode = MPEGMode.NOT_SET;
    gfp.original = 1;
    gfp.in_samplerate = 44100;
    gfp.num_channels = 2;
    gfp.num_samples = -1;

    gfp.bWriteVbrTag = true;
    gfp.quality = -1;
    gfp.short_blocks = null;
    gfc.subblock_gain = -1;

    gfp.lowpassfreq = 0;
    gfp.highpassfreq = 0;
    gfp.lowpasswidth = -1;
    gfp.highpasswidth = -1;

    gfp.VBR = VbrMode.vbr_off;
    gfp.VBR_q = 4;
    gfp.ATHcurve = -1;
    gfp.VBR_mean_bitrate_kbps = 128;
    gfp.VBR_min_bitrate_kbps = 0;
    gfp.VBR_max_bitrate_kbps = 0;
    gfp.VBR_hard_min = 0;
    gfc.VBR_min_bitrate = 1;
    /* not 0 ????? */
    gfc.VBR_max_bitrate = 13;
    /* not 14 ????? */

    gfp.quant_comp = -1;
    gfp.quant_comp_short = -1;

    gfp.msfix = -1;

    gfc.resample_ratio = 1;

    gfc.OldValue[0] = 180;
    gfc.OldValue[1] = 180;
    gfc.CurrentStep[0] = 4;
    gfc.CurrentStep[1] = 4;
    gfc.masking_lower = 1;
    gfc.nsPsy.attackthre = -1;
    gfc.nsPsy.attackthre_s = -1;

    gfp.scale = -1;

    gfp.athaa_type = -1;
    gfp.ATHtype = -1;
    /* default = -1 = set in lame_init_params */
    gfp.athaa_loudapprox = -1;
    /* 1 = flat loudness approx. (total energy) */
    /* 2 = equal loudness curve */
    gfp.athaa_sensitivity = 0.0;
    /* no offset */
    gfp.useTemporal = null;
    gfp.interChRatio = -1;

    /*
     * The reason for int mf_samples_to_encode = ENCDELAY + POSTDELAY;
     * ENCDELAY = internal encoder delay. And then we have to add
     * POSTDELAY=288 because of the 50% MDCT overlap. A 576 MDCT granule
     * decodes to 1152 samples. To synthesize the 576 samples centered under
     * this granule we need the previous granule for the first 288 samples
     * (no problem), and the next granule for the next 288 samples (not
     * possible if this is last granule). So we need to pad with 288 samples
     * to make sure we can encode the 576 samples we are interested in.
     */
    gfc.mf_samples_to_encode = Encoder.ENCDELAY + Encoder.POSTDELAY;
    gfp.encoder_padding = 0;
    gfc.mf_size = Encoder.ENCDELAY - Encoder.MDCTDELAY;
    /*
     * we pad input with this many 0's
     */

    gfp.findReplayGain = false;
    gfp.decode_on_the_fly = false;

    gfc.decode_on_the_fly = false;
    gfc.findReplayGain = false;
    gfc.findPeakSample = false;

    gfc.RadioGain = 0;
    gfc.AudiophileGain = 0;
    gfc.noclipGainChange = 0;
    gfc.noclipScale = -1.0;

    gfp.preset = 0;

    gfp.write_id3tag_automatic = true;
    return 0;
}

function lame_init() {
    var gfp = new LameGlobalFlags();

    var ret = lame_init_old(gfp);
    if (ret != 0) {
        return null;
    }

    gfp.lame_allocated_gfp = 1;
    return gfp;
}
function filter_coef(x) {
    if (x > 1.0)
        return 0.0;
    if (x <= 0.0)
        return 1.0;

    return Math.cos(Math.PI / 2 * x);
}

function nearestBitrateFullIndex(bitrate) {
    /* borrowed from DM abr presets */

    var full_bitrate_table = [ 8, 16, 24, 32, 40, 48, 56, 64, 80,
        96, 112, 128, 160, 192, 224, 256, 320 ];

    var lower_range = 0, lower_range_kbps = 0, upper_range = 0, upper_range_kbps = 0;

    /* We assume specified bitrate will be 320kbps */
    upper_range_kbps = full_bitrate_table[16];
    upper_range = 16;
    lower_range_kbps = full_bitrate_table[16];
    lower_range = 16;

    /*
     * Determine which significant bitrates the value specified falls
     * between, if loop ends without breaking then we were correct above
     * that the value was 320
     */
    for (var b = 0; b < 16; b++) {
        if ((Math.max(bitrate, full_bitrate_table[b + 1])) != bitrate) {
            upper_range_kbps = full_bitrate_table[b + 1];
            upper_range = b + 1;
            lower_range_kbps = full_bitrate_table[b];
            lower_range = (b);
            break; /* We found upper range */
        }
    }

    /* Determine which range the value specified is closer to */
    if ((upper_range_kbps - bitrate) > (bitrate - lower_range_kbps)) {
        return lower_range;
    }
    return upper_range;
}

function optimum_samplefreq(lowpassfreq, input_samplefreq) {
    /*
     * Rules:
     *
     * - if possible, sfb21 should NOT be used
     */
    var suggested_samplefreq = 44100;

    if (input_samplefreq >= 48000)
        suggested_samplefreq = 48000;
    else if (input_samplefreq >= 44100)
        suggested_samplefreq = 44100;
    else if (input_samplefreq >= 32000)
        suggested_samplefreq = 32000;
    else if (input_samplefreq >= 24000)
        suggested_samplefreq = 24000;
    else if (input_samplefreq >= 22050)
        suggested_samplefreq = 22050;
    else if (input_samplefreq >= 16000)
        suggested_samplefreq = 16000;
    else if (input_samplefreq >= 12000)
        suggested_samplefreq = 12000;
    else if (input_samplefreq >= 11025)
        suggested_samplefreq = 11025;
    else if (input_samplefreq >= 8000)
        suggested_samplefreq = 8000;

    if (lowpassfreq == -1)
        return suggested_samplefreq;

    if (lowpassfreq <= 15960)
        suggested_samplefreq = 44100;
    if (lowpassfreq <= 15250)
        suggested_samplefreq = 32000;
    if (lowpassfreq <= 11220)
        suggested_samplefreq = 24000;
    if (lowpassfreq <= 9970)
        suggested_samplefreq = 22050;
    if (lowpassfreq <= 7230)
        suggested_samplefreq = 16000;
    if (lowpassfreq <= 5420)
        suggested_samplefreq = 12000;
    if (lowpassfreq <= 4510)
        suggested_samplefreq = 11025;
    if (lowpassfreq <= 3970)
        suggested_samplefreq = 8000;

    if (input_samplefreq < suggested_samplefreq) {
        /*
         * choose a valid MPEG sample frequency above the input sample
         * frequency to avoid SFB21/12 bitrate bloat rh 061115
         */
        if (input_samplefreq > 44100) {
            return 48000;
        }
        if (input_samplefreq > 32000) {
            return 44100;
        }
        if (input_samplefreq > 24000) {
            return 32000;
        }
        if (input_samplefreq > 22050) {
            return 24000;
        }
        if (input_samplefreq > 16000) {
            return 22050;
        }
        if (input_samplefreq > 12000) {
            return 16000;
        }
        if (input_samplefreq > 11025) {
            return 12000;
        }
        if (input_samplefreq > 8000) {
            return 11025;
        }
        return 8000;
    }
    return suggested_samplefreq;
}
/**
 * convert samp freq in Hz to index
 */
function SmpFrqIndex(sample_freq, gpf) {
    switch (sample_freq) {
        case 44100:
            gpf.version = 1;
            return 0;
        case 48000:
            gpf.version = 1;
            return 1;
        case 32000:
            gpf.version = 1;
            return 2;
        case 22050:
            gpf.version = 0;
            return 0;
        case 24000:
            gpf.version = 0;
            return 1;
        case 16000:
            gpf.version = 0;
            return 2;
        case 11025:
            gpf.version = 0;
            return 0;
        case 12000:
            gpf.version = 0;
            return 1;
        case 8000:
            gpf.version = 0;
            return 2;
        default:
            gpf.version = 0;
            return -1;
    }
}

/**
 * @param bRate
 *            legal rates from 8 to 320
 */
function FindNearestBitrate(bRate, version, samplerate) {
    /* MPEG-1 or MPEG-2 LSF */
    if (samplerate < 16000)
        version = 2;

    var bitrate = Tables.bitrate_table[version][1];

    for (var i = 2; i <= 14; i++) {
        if (Tables.bitrate_table[version][i] > 0) {
            if (Math.abs(Tables.bitrate_table[version][i] - bRate) < Math
                    .abs(bitrate - bRate))
                bitrate = Tables.bitrate_table[version][i];
        }
    }
    return bitrate;
}

/**
 * @param bRate
 *            legal rates from 32 to 448 kbps
 * @param version
 *            MPEG-1 or MPEG-2/2.5 LSF
 */
function BitrateIndex(bRate, version, samplerate) {
    /* convert bitrate in kbps to index */
    if (samplerate < 16000)
        version = 2;
    for (var i = 0; i <= 14; i++) {
        if (Tables.bitrate_table[version][i] > 0) {
            if (Tables.bitrate_table[version][i] == bRate) {
                return i;
            }
        }
    }
    return -1;
}

function optimum_bandwidth(lh, bitrate) {
    /**
     * <PRE>
     *  Input:
     *      bitrate     total bitrate in kbps
     *
     *   Output:
     *      lowerlimit: best lowpass frequency limit for input filter in Hz
     *      upperlimit: best highpass frequency limit for input filter in Hz
     * </PRE>
     */
    var freq_map = [ new BandPass(8, 2000),
        new BandPass(16, 3700), new BandPass(24, 3900),
        new BandPass(32, 5500), new BandPass(40, 7000),
        new BandPass(48, 7500), new BandPass(56, 10000),
        new BandPass(64, 11000), new BandPass(80, 13500),
        new BandPass(96, 15100), new BandPass(112, 15600),
        new BandPass(128, 17000), new BandPass(160, 17500),
        new BandPass(192, 18600), new BandPass(224, 19400),
        new BandPass(256, 19700), new BandPass(320, 20500) ];

    var table_index = nearestBitrateFullIndex(bitrate);
    lh.lowerlimit = freq_map[table_index].lowpass;
}

function lame_init_params_ppflt(gfp) {
    var gfc = gfp.internal_flags;
    /***************************************************************/
    /* compute info needed for polyphase filter (filter type==0, default) */
    /***************************************************************/

    var lowpass_band = 32;
    var highpass_band = -1;

    if (gfc.lowpass1 > 0) {
        var minband = 999;
        for (var band = 0; band <= 31; band++) {
            var freq = (band / 31.0);
            /* this band and above will be zeroed: */
            if (freq >= gfc.lowpass2) {
                lowpass_band = Math.min(lowpass_band, band);
            }
            if (gfc.lowpass1 < freq && freq < gfc.lowpass2) {
                minband = Math.min(minband, band);
            }
        }

        /*
         * compute the *actual* transition band implemented by the polyphase
         * filter
         */
        if (minband == 999) {
            gfc.lowpass1 = (lowpass_band - .75) / 31.0;
        } else {
            gfc.lowpass1 = (minband - .75) / 31.0;
        }
        gfc.lowpass2 = lowpass_band / 31.0;
    }

    /*
     * make sure highpass filter is within 90% of what the effective
     * highpass frequency will be
     */
    if (gfc.highpass2 > 0) {
        if (gfc.highpass2 < .9 * (.75 / 31.0)) {
            gfc.highpass1 = 0;
            gfc.highpass2 = 0;
            System.err.println("Warning: highpass filter disabled.  "
                + "highpass frequency too small\n");
        }
    }

    if (gfc.highpass2 > 0) {
        var maxband = -1;
        for (var band = 0; band <= 31; band++) {
            var freq = band / 31.0;
            /* this band and below will be zereod */
            if (freq <= gfc.highpass1) {
                highpass_band = Math.max(highpass_band, band);
            }
            if (gfc.highpass1 < freq && freq < gfc.highpass2) {
                maxband = Math.max(maxband, band);
            }
        }
        /*
         * compute the *actual* transition band implemented by the polyphase
         * filter
         */
        gfc.highpass1 = highpass_band / 31.0;
        if (maxband == -1) {
            gfc.highpass2 = (highpass_band + .75) / 31.0;
        } else {
            gfc.highpass2 = (maxband + .75) / 31.0;
        }
    }

    for (var band = 0; band < 32; band++) {
        var fc1, fc2;
        var freq = band / 31.0;
        if (gfc.highpass2 > gfc.highpass1) {
            fc1 = filter_coef((gfc.highpass2 - freq)
                / (gfc.highpass2 - gfc.highpass1 + 1e-20));
        } else {
            fc1 = 1.0;
        }
        if (gfc.lowpass2 > gfc.lowpass1) {
            fc2 = filter_coef((freq - gfc.lowpass1)
                / (gfc.lowpass2 - gfc.lowpass1 + 1e-20));
        } else {
            fc2 = 1.0;
        }
        gfc.amp_filter[band] = (fc1 * fc2);
    }
}

/********************************************************************
 * initialize internal params based on data in gf (globalflags struct filled
 * in by calling program)
 *
 * OUTLINE:
 *
 * We first have some complex code to determine bitrate, output samplerate
 * and mode. It is complicated by the fact that we allow the user to set
 * some or all of these parameters, and need to determine best possible
 * values for the rest of them:
 *
 * 1. set some CPU related flags 2. check if we are mono.mono, stereo.mono
 * or stereo.stereo 3. compute bitrate and output samplerate: user may have
 * set compression ratio user may have set a bitrate user may have set a
 * output samplerate 4. set some options which depend on output samplerate
 * 5. compute the actual compression ratio 6. set mode based on compression
 * ratio
 *
 * The remaining code is much simpler - it just sets options based on the
 * mode & compression ratio:
 *
 * set allow_diff_short based on mode select lowpass filter based on
 * compression ratio & mode set the bitrate index, and min/max bitrates for
 * VBR modes disable VBR tag if it is not appropriate initialize the
 * bitstream initialize scalefac_band data set sideinfo_len (based on
 * channels, CRC, out_samplerate) write an id3v2 tag into the bitstream
 * write VBR tag into the bitstream set mpeg1/2 flag estimate the number of
 * frames (based on a lot of data)
 *
 * now we set more flags: nspsytune: see code VBR modes see code CBR/ABR see
 * code
 *
 * Finally, we set the algorithm flags based on the gfp.quality value
 * lame_init_qval(gfp);
 *
 ********************************************************************/
function lame_init_params(gfp) {
    var gfc = gfp.internal_flags;

    gfc.Class_ID = 0;
    if (gfc.ATH == null)
        gfc.ATH = new ATH();
    if (gfc.PSY == null)
        gfc.PSY = new PSY();
    if (gfc.rgdata == null)
        gfc.rgdata = new ReplayGain();

    gfc.channels_in = gfp.num_channels;
    if (gfc.channels_in == 1)
        gfp.mode = MPEGMode.MONO;
    gfc.channels_out = (gfp.mode == MPEGMode.MONO) ? 1 : 2;
    gfc.mode_ext = Encoder.MPG_MD_MS_LR;
    if (gfp.mode == MPEGMode.MONO)
        gfp.force_ms = false;
    /*
     * don't allow forced mid/side stereo for mono output
     */

    if (gfp.VBR == VbrMode.vbr_off && gfp.VBR_mean_bitrate_kbps != 128
        && gfp.brate == 0)
        gfp.brate = gfp.VBR_mean_bitrate_kbps;

    if (gfp.VBR == VbrMode.vbr_off || gfp.VBR == VbrMode.vbr_mtrh
        || gfp.VBR == VbrMode.vbr_mt) {
        /* these modes can handle free format condition */
    } else {
        gfp.free_format = false; /* mode can't be mixed with free format */
    }

    if (gfp.VBR == VbrMode.vbr_off && gfp.brate == 0) {
        /* no bitrate or compression ratio specified, use 11.025 */
        if (BitStream.EQ(gfp.compression_ratio, 0))
            gfp.compression_ratio = 11.025;
        /*
         * rate to compress a CD down to exactly 128000 bps
         */
    }

    /* find bitrate if user specify a compression ratio */
    if (gfp.VBR == VbrMode.vbr_off && gfp.compression_ratio > 0) {

        if (gfp.out_samplerate == 0)
            gfp.out_samplerate = map2MP3Frequency((int) (0.97 * gfp.in_samplerate));
        /*
         * round up with a margin of 3 %
         */

        /*
         * choose a bitrate for the output samplerate which achieves
         * specified compression ratio
         */
        gfp.brate = 0|(gfp.out_samplerate * 16 * gfc.channels_out / (1.e3 * gfp.compression_ratio));

        /* we need the version for the bitrate table look up */
        gfc.samplerate_index = SmpFrqIndex(gfp.out_samplerate, gfp);

        if (!gfp.free_format) /*
         * for non Free Format find the nearest allowed
         * bitrate
         */
            gfp.brate = FindNearestBitrate(gfp.brate, gfp.version,
                gfp.out_samplerate);
    }

    if (gfp.out_samplerate != 0) {
        if (gfp.out_samplerate < 16000) {
            gfp.VBR_mean_bitrate_kbps = Math.max(gfp.VBR_mean_bitrate_kbps,
                8);
            gfp.VBR_mean_bitrate_kbps = Math.min(gfp.VBR_mean_bitrate_kbps,
                64);
        } else if (gfp.out_samplerate < 32000) {
            gfp.VBR_mean_bitrate_kbps = Math.max(gfp.VBR_mean_bitrate_kbps,
                8);
            gfp.VBR_mean_bitrate_kbps = Math.min(gfp.VBR_mean_bitrate_kbps,
                160);
        } else {
            gfp.VBR_mean_bitrate_kbps = Math.max(gfp.VBR_mean_bitrate_kbps,
                32);
            gfp.VBR_mean_bitrate_kbps = Math.min(gfp.VBR_mean_bitrate_kbps,
                320);
        }
    }

    /****************************************************************/
    /* if a filter has not been enabled, see if we should add one: */
    /****************************************************************/
    if (gfp.lowpassfreq == 0) {
        var lowpass = 16000.;

        switch (gfp.VBR) {
            case VbrMode.vbr_off: {
                var lh = new LowPassHighPass();
                optimum_bandwidth(lh, gfp.brate);
                lowpass = lh.lowerlimit;
                break;
            }
            case VbrMode.vbr_abr: {
                var lh = new LowPassHighPass();
                optimum_bandwidth(lh, gfp.VBR_mean_bitrate_kbps);
                lowpass = lh.lowerlimit;
                break;
            }
            case VbrMode.vbr_rh: {
                var x = [ 19500, 19000, 18600, 18000, 17500, 16000,
                    15600, 14900, 12500, 10000, 3950 ];
                if (0 <= gfp.VBR_q && gfp.VBR_q <= 9) {
                    var a = x[gfp.VBR_q], b = x[gfp.VBR_q + 1], m = gfp.VBR_q_frac;
                    lowpass = linear_int(a, b, m);
                } else {
                    lowpass = 19500;
                }
                break;
            }
            default: {
                var x = [ 19500, 19000, 18500, 18000, 17500, 16500,
                    15500, 14500, 12500, 9500, 3950 ];
                if (0 <= gfp.VBR_q && gfp.VBR_q <= 9) {
                    var a = x[gfp.VBR_q], b = x[gfp.VBR_q + 1], m = gfp.VBR_q_frac;
                    lowpass = linear_int(a, b, m);
                } else {
                    lowpass = 19500;
                }
            }
        }
        if (gfp.mode == MPEGMode.MONO
            && (gfp.VBR == VbrMode.vbr_off || gfp.VBR == VbrMode.vbr_abr))
            lowpass *= 1.5;

        gfp.lowpassfreq = lowpass|0;
    }

    if (gfp.out_samplerate == 0) {
        if (2 * gfp.lowpassfreq > gfp.in_samplerate) {
            gfp.lowpassfreq = gfp.in_samplerate / 2;
        }
        gfp.out_samplerate = optimum_samplefreq( gfp.lowpassfreq|0,
            gfp.in_samplerate);
    }

    gfp.lowpassfreq = Math.min(20500, gfp.lowpassfreq);
    gfp.lowpassfreq = Math.min(gfp.out_samplerate / 2, gfp.lowpassfreq);

    if (gfp.VBR == VbrMode.vbr_off) {
        gfp.compression_ratio = gfp.out_samplerate * 16 * gfc.channels_out
            / (1.e3 * gfp.brate);
    }
    if (gfp.VBR == VbrMode.vbr_abr) {
        gfp.compression_ratio = gfp.out_samplerate * 16 * gfc.channels_out
            / (1.e3 * gfp.VBR_mean_bitrate_kbps);
    }

    /*
     * do not compute ReplayGain values and do not find the peak sample if
     * we can't store them
     */
    if (!gfp.bWriteVbrTag) {
        gfp.findReplayGain = false;
        gfp.decode_on_the_fly = false;
        gfc.findPeakSample = false;
    }
    gfc.findReplayGain = gfp.findReplayGain;
    gfc.decode_on_the_fly = gfp.decode_on_the_fly;

    if (gfc.decode_on_the_fly)
        gfc.findPeakSample = true;

    if (gfc.findReplayGain) {
        if (ga.InitGainAnalysis(gfc.rgdata, gfp.out_samplerate) == GainAnalysis.INIT_GAIN_ANALYSIS_ERROR) {
            gfp.internal_flags = null;
            return -6;
        }
    }

    if (gfc.decode_on_the_fly && !gfp.decode_only) {
        if (gfc.hip != null) {
            mpglib.hip_decode_exit(gfc.hip);
        }
        gfc.hip = mpglib.hip_decode_init();
    }

    gfc.mode_gr = gfp.out_samplerate <= 24000 ? 1 : 2;
    /*
     * Number of granules per frame
     */
    gfp.framesize = 576 * gfc.mode_gr;
    gfp.encoder_delay = Encoder.ENCDELAY;

    gfc.resample_ratio = gfp.in_samplerate / gfp.out_samplerate;

    /**
     * <PRE>
     *  sample freq       bitrate     compression ratio
     *     [kHz]      [kbps/channel]   for 16 bit input
     *     44.1            56               12.6
     *     44.1            64               11.025
     *     44.1            80                8.82
     *     22.05           24               14.7
     *     22.05           32               11.025
     *     22.05           40                8.82
     *     16              16               16.0
     *     16              24               10.667
     * </PRE>
     */
    /**
     * <PRE>
     *  For VBR, take a guess at the compression_ratio.
     *  For example:
     *
     *    VBR_q    compression     like
     *     -        4.4         320 kbps/44 kHz
     *   0...1      5.5         256 kbps/44 kHz
     *     2        7.3         192 kbps/44 kHz
     *     4        8.8         160 kbps/44 kHz
     *     6       11           128 kbps/44 kHz
     *     9       14.7          96 kbps
     *
     *  for lower bitrates, downsample with --resample
     * </PRE>
     */
    switch (gfp.VBR) {
        case VbrMode.vbr_mt:
        case VbrMode.vbr_rh:
        case VbrMode.vbr_mtrh: {
            /* numbers are a bit strange, but they determine the lowpass value */
            var cmp = [ 5.7, 6.5, 7.3, 8.2, 10, 11.9, 13, 14,
                15, 16.5 ];
            gfp.compression_ratio = cmp[gfp.VBR_q];
        }
            break;
        case VbrMode.vbr_abr:
            gfp.compression_ratio = gfp.out_samplerate * 16 * gfc.channels_out
                / (1.e3 * gfp.VBR_mean_bitrate_kbps);
            break;
        default:
            gfp.compression_ratio = gfp.out_samplerate * 16 * gfc.channels_out
                / (1.e3 * gfp.brate);
            break;
    }

    /*
     * mode = -1 (not set by user) or mode = MONO (because of only 1 input
     * channel). If mode has not been set, then select J-STEREO
     */
    if (gfp.mode == MPEGMode.NOT_SET) {
        gfp.mode = MPEGMode.JOINT_STEREO;
    }

    /* apply user driven high pass filter */
    if (gfp.highpassfreq > 0) {
        gfc.highpass1 = 2. * gfp.highpassfreq;

        if (gfp.highpasswidth >= 0)
            gfc.highpass2 = 2. * (gfp.highpassfreq + gfp.highpasswidth);
    else
        /* 0% above on default */
        gfc.highpass2 = (1 + 0.00) * 2. * gfp.highpassfreq;

        gfc.highpass1 /= gfp.out_samplerate;
        gfc.highpass2 /= gfp.out_samplerate;
    } else {
        gfc.highpass1 = 0;
        gfc.highpass2 = 0;
    }
    /* apply user driven low pass filter */
    if (gfp.lowpassfreq > 0) {
        gfc.lowpass2 = 2. * gfp.lowpassfreq;
        if (gfp.lowpasswidth >= 0) {
            gfc.lowpass1 = 2. * (gfp.lowpassfreq - gfp.lowpasswidth);
            if (gfc.lowpass1 < 0) /* has to be >= 0 */
                gfc.lowpass1 = 0;
        } else { /* 0% below on default */
            gfc.lowpass1 = (1 - 0.00) * 2. * gfp.lowpassfreq;
        }
        gfc.lowpass1 /= gfp.out_samplerate;
        gfc.lowpass2 /= gfp.out_samplerate;
    } else {
        gfc.lowpass1 = 0;
        gfc.lowpass2 = 0;
    }

    /**********************************************************************/
    /* compute info needed for polyphase filter (filter type==0, default) */
    /**********************************************************************/
    lame_init_params_ppflt(gfp);

    /*******************************************************
     * samplerate and bitrate index
     *******************************************************/
    gfc.samplerate_index = SmpFrqIndex(gfp.out_samplerate, gfp);
    if (gfc.samplerate_index < 0) {
        gfp.internal_flags = null;
        return -1;
    }

    if (gfp.VBR == VbrMode.vbr_off) {
        if (gfp.free_format) {
            gfc.bitrate_index = 0;
        } else {
            gfp.brate = FindNearestBitrate(gfp.brate, gfp.version,
                gfp.out_samplerate);
            gfc.bitrate_index = BitrateIndex(gfp.brate, gfp.version,
                gfp.out_samplerate);
            if (gfc.bitrate_index <= 0) {
                gfp.internal_flags = null;
                return -1;
            }
        }
    } else {
        gfc.bitrate_index = 1;
    }

    /* for CBR, we will write an "info" tag. */

    if (gfp.analysis)
        gfp.bWriteVbrTag = false;

    /* some file options not allowed if output is: not specified or stdout */
    if (gfc.pinfo != null)
        gfp.bWriteVbrTag = false; /* disable Xing VBR tag */

    bs.init_bit_stream_w(gfc);

    var j = gfc.samplerate_index + (3 * gfp.version) + 6
        * (gfp.out_samplerate < 16000 ? 1 : 0);
    for (var i = 0; i < Encoder.SBMAX_l + 1; i++)
    gfc.scalefac_band.l[i] = qupvt.sfBandIndex[j].l[i];

    for (var i = 0; i < Encoder.PSFB21 + 1; i++) {
        var size = (gfc.scalefac_band.l[22] - gfc.scalefac_band.l[21])
            / Encoder.PSFB21;
        var start = gfc.scalefac_band.l[21] + i * size;
        gfc.scalefac_band.psfb21[i] = start;
    }
    gfc.scalefac_band.psfb21[Encoder.PSFB21] = 576;

    for (var i = 0; i < Encoder.SBMAX_s + 1; i++)
    gfc.scalefac_band.s[i] = qupvt.sfBandIndex[j].s[i];

    for (var i = 0; i < Encoder.PSFB12 + 1; i++) {
        var size = (gfc.scalefac_band.s[13] - gfc.scalefac_band.s[12])
            / Encoder.PSFB12;
        var start = gfc.scalefac_band.s[12] + i * size;
        gfc.scalefac_band.psfb12[i] = start;
    }
    gfc.scalefac_band.psfb12[Encoder.PSFB12] = 192;

    /* determine the mean bitrate for main data */
    if (gfp.version == 1) /* MPEG 1 */
        gfc.sideinfo_len = (gfc.channels_out == 1) ? 4 + 17 : 4 + 32;
    else
    /* MPEG 2 */
        gfc.sideinfo_len = (gfc.channels_out == 1) ? 4 + 9 : 4 + 17;

    if (gfp.error_protection)
        gfc.sideinfo_len += 2;

    lame_init_bitstream(gfp);

    gfc.Class_ID = LAME_ID;

    {
        var k;

        for (k = 0; k < 19; k++)
            gfc.nsPsy.pefirbuf[k] = 700 * gfc.mode_gr * gfc.channels_out;

        if (gfp.ATHtype == -1)
            gfp.ATHtype = 4;
    }

    assert (gfp.VBR_q <= 9);
    assert (gfp.VBR_q >= 0);

    switch (gfp.VBR) {

        case vbr_mt:
            gfp.VBR = VbrMode.vbr_mtrh;
        //$FALL-THROUGH$
        case vbr_mtrh: {
            if (gfp.useTemporal == null) {
                gfp.useTemporal = false; /* off by default for this VBR mode */
            }

            p.apply_preset(gfp, 500 - (gfp.VBR_q * 10), 0);
            /**
             * <PRE>
             *   The newer VBR code supports only a limited
             * 	 subset of quality levels:
             * 	 9-5=5 are the same, uses x^3/4 quantization
             *   4-0=0 are the same  5 plus best huffman divide code
             * </PRE>
             */
            if (gfp.quality < 0)
                gfp.quality = LAME_DEFAULT_QUALITY;
            if (gfp.quality < 5)
                gfp.quality = 0;
            if (gfp.quality > 5)
                gfp.quality = 5;

            gfc.PSY.mask_adjust = gfp.maskingadjust;
            gfc.PSY.mask_adjust_short = gfp.maskingadjust_short;

            /*
             * sfb21 extra only with MPEG-1 at higher sampling rates
             */
            if (gfp.experimentalY)
                gfc.sfb21_extra = false;
            else
                gfc.sfb21_extra = (gfp.out_samplerate > 44000);

            gfc.iteration_loop = new VBRNewIterationLoop(qu);
            break;

        }
        case vbr_rh: {

            p.apply_preset(gfp, 500 - (gfp.VBR_q * 10), 0);

            gfc.PSY.mask_adjust = gfp.maskingadjust;
            gfc.PSY.mask_adjust_short = gfp.maskingadjust_short;

            /*
             * sfb21 extra only with MPEG-1 at higher sampling rates
             */
            if (gfp.experimentalY)
                gfc.sfb21_extra = false;
            else
                gfc.sfb21_extra = (gfp.out_samplerate > 44000);

            /*
             * VBR needs at least the output of GPSYCHO, so we have to garantee
             * that by setting a minimum quality level, actually level 6 does
             * it. down to level 6
             */
            if (gfp.quality > 6)
                gfp.quality = 6;

            if (gfp.quality < 0)
                gfp.quality = LAME_DEFAULT_QUALITY;

            gfc.iteration_loop = new VBROldIterationLoop(qu);
            break;
        }

        default: /* cbr/abr */{
            var vbrmode;

            /*
             * no sfb21 extra with CBR code
             */
            gfc.sfb21_extra = false;

            if (gfp.quality < 0)
                gfp.quality = LAME_DEFAULT_QUALITY;

            vbrmode = gfp.VBR;
            if (vbrmode == VbrMode.vbr_off)
                gfp.VBR_mean_bitrate_kbps = gfp.brate;
            /* second, set parameters depending on bitrate */
            p.apply_preset(gfp, gfp.VBR_mean_bitrate_kbps, 0);
            gfp.VBR = vbrmode;

            gfc.PSY.mask_adjust = gfp.maskingadjust;
            gfc.PSY.mask_adjust_short = gfp.maskingadjust_short;

            if (vbrmode == VbrMode.vbr_off) {
                gfc.iteration_loop = new CBRNewIterationLoop(qu);
            } else {
                gfc.iteration_loop = new ABRIterationLoop(qu);
            }
            break;
        }
    }

    /* initialize default values common for all modes */

    if (gfp.VBR != VbrMode.vbr_off) { /* choose a min/max bitrate for VBR */
        /* if the user didn't specify VBR_max_bitrate: */
        gfc.VBR_min_bitrate = 1;
        /*
         * default: allow 8 kbps (MPEG-2) or 32 kbps (MPEG-1)
         */
        gfc.VBR_max_bitrate = 14;
        /*
         * default: allow 160 kbps (MPEG-2) or 320 kbps (MPEG-1)
         */
        if (gfp.out_samplerate < 16000)
            gfc.VBR_max_bitrate = 8; /* default: allow 64 kbps (MPEG-2.5) */
        if (gfp.VBR_min_bitrate_kbps != 0) {
            gfp.VBR_min_bitrate_kbps = FindNearestBitrate(
                gfp.VBR_min_bitrate_kbps, gfp.version,
                gfp.out_samplerate);
            gfc.VBR_min_bitrate = BitrateIndex(gfp.VBR_min_bitrate_kbps,
                gfp.version, gfp.out_samplerate);
            if (gfc.VBR_min_bitrate < 0)
                return -1;
        }
        if (gfp.VBR_max_bitrate_kbps != 0) {
            gfp.VBR_max_bitrate_kbps = FindNearestBitrate(
                gfp.VBR_max_bitrate_kbps, gfp.version,
                gfp.out_samplerate);
            gfc.VBR_max_bitrate = BitrateIndex(gfp.VBR_max_bitrate_kbps,
                gfp.version, gfp.out_samplerate);
            if (gfc.VBR_max_bitrate < 0)
                return -1;
        }
        gfp.VBR_min_bitrate_kbps = Tables.bitrate_table[gfp.version][gfc.VBR_min_bitrate];
        gfp.VBR_max_bitrate_kbps = Tables.bitrate_table[gfp.version][gfc.VBR_max_bitrate];
        gfp.VBR_mean_bitrate_kbps = Math.min(
            Tables.bitrate_table[gfp.version][gfc.VBR_max_bitrate],
            gfp.VBR_mean_bitrate_kbps);
        gfp.VBR_mean_bitrate_kbps = Math.max(
            Tables.bitrate_table[gfp.version][gfc.VBR_min_bitrate],
            gfp.VBR_mean_bitrate_kbps);
    }

    /* just another daily changing developer switch */
    if (gfp.tune) {
        gfc.PSY.mask_adjust += gfp.tune_value_a;
        gfc.PSY.mask_adjust_short += gfp.tune_value_a;
    }

    /* initialize internal qval settings */
    lame_init_qval(gfp);

    /*
     * automatic ATH adjustment on
     */
    if (gfp.athaa_type < 0)
        gfc.ATH.useAdjust = 3;
    else
        gfc.ATH.useAdjust = gfp.athaa_type;

    /* initialize internal adaptive ATH settings -jd */
    gfc.ATH.aaSensitivityP =  Math.pow(10.0, gfp.athaa_sensitivity
        / -10.0);

    if (gfp.short_blocks == null) {
        gfp.short_blocks = ShortBlock.short_block_allowed;
    }

    /*
     * Note Jan/2003: Many hardware decoders cannot handle short blocks in
     * regular stereo mode unless they are coupled (same type in both
     * channels) it is a rare event (1 frame per min. or so) that LAME would
     * use uncoupled short blocks, so lets turn them off until we decide how
     * to handle this. No other encoders allow uncoupled short blocks, even
     * though it is in the standard.
     */
    /*
     * rh 20040217: coupling makes no sense for mono and dual-mono streams
     */
    if (gfp.short_blocks == ShortBlock.short_block_allowed
        && (gfp.mode == MPEGMode.JOINT_STEREO || gfp.mode == MPEGMode.STEREO)) {
        gfp.short_blocks = ShortBlock.short_block_coupled;
    }

    if (gfp.quant_comp < 0)
        gfp.quant_comp = 1;
    if (gfp.quant_comp_short < 0)
        gfp.quant_comp_short = 0;

    if (gfp.msfix < 0)
        gfp.msfix = 0;

    /* select psychoacoustic model */
    gfp.exp_nspsytune = gfp.exp_nspsytune | 1;

    if (gfp.internal_flags.nsPsy.attackthre < 0)
        gfp.internal_flags.nsPsy.attackthre = PsyModel.NSATTACKTHRE;
    if (gfp.internal_flags.nsPsy.attackthre_s < 0)
        gfp.internal_flags.nsPsy.attackthre_s = PsyModel.NSATTACKTHRE_S;

    if (gfp.scale < 0)
        gfp.scale = 1;

    if (gfp.ATHtype < 0)
        gfp.ATHtype = 4;

    if (gfp.ATHcurve < 0)
        gfp.ATHcurve = 4;

    if (gfp.athaa_loudapprox < 0)
        gfp.athaa_loudapprox = 2;

    if (gfp.interChRatio < 0)
        gfp.interChRatio = 0;

    if (gfp.useTemporal == null)
        gfp.useTemporal = true; /* on by default */

    /*
     * padding method as described in
     * "MPEG-Layer3 / Bitstream Syntax and Decoding" by Martin Sieler, Ralph
     * Sperschneider
     *
     * note: there is no padding for the very first frame
     *
     * Robert Hegemann 2000-06-22
     */
    gfc.slot_lag = gfc.frac_SpF = 0;
    if (gfp.VBR == VbrMode.vbr_off)
        gfc.slot_lag = gfc.frac_SpF = (((gfp.version + 1) * 72000 * gfp.brate) % gfp.out_samplerate)|0;

    qupvt.iteration_init(gfp);
    psy.psymodel_init(gfp);

    return 0;
}


var gfp = lame_init();
console.log(gfp);

gfp.numChannels=1;
gfp.sampleRate = 48000;
gfp.brate = 128;
gfp.mode = MPEGMode.STEREO;
gfp.quality = 3;
gfp.bWriteVbrTag = false;
gfp.disable_reservoir = true;

bs = new BitStream();
qupvt = new QuantizePVT();

var ret_code = lame_init_params(gfp);
