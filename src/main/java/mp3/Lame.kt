/*
 *      LAME MP3 encoding engine
 *
 *      Copyright (c) 1999-2000 Mark Taylor
 *      Copyright (c) 2000-2005 Takehiro Tominaga
 *      Copyright (c) 2000-2005 Robert Hegemann
 *      Copyright (c) 2000-2005 Gabriel Bouvigne
 *      Copyright (c) 2000-2004 Alexander Leidinger
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Library General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the
 * Free Software Foundation, Inc., 59 Temple Place - Suite 330,
 * Boston, MA 02111-1307, USA.
 */

/* $Id: Lame.java,v 1.38 2011/05/24 21:15:54 kenchis Exp $ */

package mp3

import mpg.MPGLib

class Lame {
    internal lateinit var ga: GainAnalysis
    internal lateinit var bs: BitStream
    internal lateinit var p: Presets
    internal lateinit var qupvt: QuantizePVT
    internal lateinit var qu: Quantize
    internal var psy = PsyModel()
    internal lateinit var vbr: VBRTag
    internal lateinit var ver: Version
    internal lateinit var id3: ID3Tag
    internal lateinit var mpglib: MPGLib
    var enc = Encoder()

    fun setModules(ga: GainAnalysis, bs: BitStream, p: Presets,
                   qupvt: QuantizePVT, qu: Quantize, vbr: VBRTag, ver: Version,
                   id3: ID3Tag, mpglib: MPGLib) {
        this.ga = ga
        this.bs = bs
        this.p = p
        this.qupvt = qupvt
        this.qu = qu
        this.vbr = vbr
        this.ver = ver
        this.id3 = id3
        this.mpglib = mpglib
        this.enc.setModules(bs, psy, qupvt, vbr)
    }

    private fun filter_coef(x: Float): Float {
        if (x > 1.0)
            return 0.0f
        return if (x <= 0.0) 1.0f else Math.cos(Math.PI / 2 * x).toFloat()

    }

    private fun lame_init_params_ppflt(gfp: LameGlobalFlags) {
        val gfc = gfp.internal_flags
        /** */
        /* compute info needed for polyphase filter (filter type==0, default) */
        /** */

        var lowpass_band = 32
        var highpass_band = -1

        if (gfc.lowpass1 > 0) {
            var minband = 999
            for (band in 0..31) {
                val freq = (band / 31.0).toFloat()
                /* this band and above will be zeroed: */
                if (freq >= gfc.lowpass2) {
                    lowpass_band = Math.min(lowpass_band, band)
                }
                if (gfc.lowpass1 < freq && freq < gfc.lowpass2) {
                    minband = Math.min(minband, band)
                }
            }

            /*
			 * compute the *actual* transition band implemented by the polyphase
			 * filter
			 */
            if (minband == 999) {
                gfc.lowpass1 = (lowpass_band - .75f) / 31.0f
            } else {
                gfc.lowpass1 = (minband - .75f) / 31.0f
            }
            gfc.lowpass2 = lowpass_band / 31.0f
        }

        /*
		 * make sure highpass filter is within 90% of what the effective
		 * highpass frequency will be
		 */
        if (gfc.highpass2 > 0) {
            if (gfc.highpass2 < .9 * (.75 / 31.0)) {
                gfc.highpass1 = 0f
                gfc.highpass2 = 0f
                System.err.println("Warning: highpass filter disabled.  " + "highpass frequency too small\n")
            }
        }

        if (gfc.highpass2 > 0) {
            var maxband = -1
            for (band in 0..31) {
                val freq = band / 31.0f
                /* this band and below will be zereod */
                if (freq <= gfc.highpass1) {
                    highpass_band = Math.max(highpass_band, band)
                }
                if (gfc.highpass1 < freq && freq < gfc.highpass2) {
                    maxband = Math.max(maxband, band)
                }
            }
            /*
			 * compute the *actual* transition band implemented by the polyphase
			 * filter
			 */
            gfc.highpass1 = highpass_band / 31.0f
            if (maxband == -1) {
                gfc.highpass2 = (highpass_band + .75f) / 31.0f
            } else {
                gfc.highpass2 = (maxband + .75f) / 31.0f
            }
        }

        for (band in 0..31) {
            val fc1: Double
            val fc2: Double
            val freq = band / 31.0f
            if (gfc.highpass2 > gfc.highpass1) {
                fc1 = filter_coef((gfc.highpass2 - freq) / (gfc.highpass2 - gfc.highpass1 + 1e-20f)).toDouble()
            } else {
                fc1 = 1.0
            }
            if (gfc.lowpass2 > gfc.lowpass1) {
                fc2 = filter_coef((freq - gfc.lowpass1) / (gfc.lowpass2 - gfc.lowpass1 + 1e-20f)).toDouble()
            } else {
                fc2 = 1.0
            }
            gfc.amp_filter[band] = (fc1 * fc2).toFloat()
        }
    }

    protected class LowPassHighPass {
        internal var lowerlimit: Double = 0.toDouble()
    }

    private class BandPass(bitrate: Int, var lowpass: Int)

    private fun optimum_bandwidth(lh: LowPassHighPass, bitrate: Int) {
        /**
         * <PRE>
         * Input:
         * bitrate     total bitrate in kbps
         *
         * Output:
         * lowerlimit: best lowpass frequency limit for input filter in Hz
         * upperlimit: best highpass frequency limit for input filter in Hz
        </PRE> *
         */
        val freq_map = arrayOf(BandPass(8, 2000), BandPass(16, 3700), BandPass(24, 3900), BandPass(32, 5500), BandPass(40, 7000), BandPass(48, 7500), BandPass(56, 10000), BandPass(64, 11000), BandPass(80, 13500), BandPass(96, 15100), BandPass(112, 15600), BandPass(128, 17000), BandPass(160, 17500), BandPass(192, 18600), BandPass(224, 19400), BandPass(256, 19700), BandPass(320, 20500))

        val table_index = nearestBitrateFullIndex(bitrate)
        lh.lowerlimit = freq_map[table_index].lowpass.toDouble()
    }

    private fun optimum_samplefreq(lowpassfreq: Int,
                                   input_samplefreq: Int): Int {
        /*
		 * Rules:
		 *
		 * - if possible, sfb21 should NOT be used
		 */
        var suggested_samplefreq = 44100

        if (input_samplefreq >= 48000)
            suggested_samplefreq = 48000
        else if (input_samplefreq >= 44100)
            suggested_samplefreq = 44100
        else if (input_samplefreq >= 32000)
            suggested_samplefreq = 32000
        else if (input_samplefreq >= 24000)
            suggested_samplefreq = 24000
        else if (input_samplefreq >= 22050)
            suggested_samplefreq = 22050
        else if (input_samplefreq >= 16000)
            suggested_samplefreq = 16000
        else if (input_samplefreq >= 12000)
            suggested_samplefreq = 12000
        else if (input_samplefreq >= 11025)
            suggested_samplefreq = 11025
        else if (input_samplefreq >= 8000)
            suggested_samplefreq = 8000

        if (lowpassfreq == -1)
            return suggested_samplefreq

        if (lowpassfreq <= 15960)
            suggested_samplefreq = 44100
        if (lowpassfreq <= 15250)
            suggested_samplefreq = 32000
        if (lowpassfreq <= 11220)
            suggested_samplefreq = 24000
        if (lowpassfreq <= 9970)
            suggested_samplefreq = 22050
        if (lowpassfreq <= 7230)
            suggested_samplefreq = 16000
        if (lowpassfreq <= 5420)
            suggested_samplefreq = 12000
        if (lowpassfreq <= 4510)
            suggested_samplefreq = 11025
        if (lowpassfreq <= 3970)
            suggested_samplefreq = 8000

        if (input_samplefreq < suggested_samplefreq) {
            /*
			 * choose a valid MPEG sample frequency above the input sample
			 * frequency to avoid SFB21/12 bitrate bloat rh 061115
			 */
            if (input_samplefreq > 44100) {
                return 48000
            }
            if (input_samplefreq > 32000) {
                return 44100
            }
            if (input_samplefreq > 24000) {
                return 32000
            }
            if (input_samplefreq > 22050) {
                return 24000
            }
            if (input_samplefreq > 16000) {
                return 22050
            }
            if (input_samplefreq > 12000) {
                return 16000
            }
            if (input_samplefreq > 11025) {
                return 12000
            }
            return if (input_samplefreq > 8000) {
                11025
            } else 8000
        }
        return suggested_samplefreq
    }

    /**
     * set internal feature flags. USER should not access these since some
     * combinations will produce strange results
     */
    private fun lame_init_qval(gfp: LameGlobalFlags) {
        val gfc = gfp.internal_flags

        when (gfp.quality) {
            9 /* no psymodel, no noise shaping */ -> {
                gfc.psymodel = 0
                gfc.noise_shaping = 0
                gfc.noise_shaping_amp = 0
                gfc.noise_shaping_stop = 0
                gfc.use_best_huffman = 0
                gfc.full_outer_loop = 0
            }

            8 -> {
                gfp.quality = 7
                /*
			 * use psymodel (for short block and m/s switching), but no noise
			 * shapping
			 */
                gfc.psymodel = 1
                gfc.noise_shaping = 0
                gfc.noise_shaping_amp = 0
                gfc.noise_shaping_stop = 0
                gfc.use_best_huffman = 0
                gfc.full_outer_loop = 0
            }
            //$FALL-THROUGH$
            7 -> {
                gfc.psymodel = 1
                gfc.noise_shaping = 0
                gfc.noise_shaping_amp = 0
                gfc.noise_shaping_stop = 0
                gfc.use_best_huffman = 0
                gfc.full_outer_loop = 0
            }

            6 -> {
                gfc.psymodel = 1
                if (gfc.noise_shaping == 0)
                    gfc.noise_shaping = 1
                gfc.noise_shaping_amp = 0
                gfc.noise_shaping_stop = 0
                if (gfc.subblock_gain == -1)
                    gfc.subblock_gain = 1
                gfc.use_best_huffman = 0
                gfc.full_outer_loop = 0
            }

            5 -> {
                gfc.psymodel = 1
                if (gfc.noise_shaping == 0)
                    gfc.noise_shaping = 1
                gfc.noise_shaping_amp = 0
                gfc.noise_shaping_stop = 0
                if (gfc.subblock_gain == -1)
                    gfc.subblock_gain = 1
                gfc.use_best_huffman = 0
                gfc.full_outer_loop = 0
            }

            4 -> {
                gfc.psymodel = 1
                if (gfc.noise_shaping == 0)
                    gfc.noise_shaping = 1
                gfc.noise_shaping_amp = 0
                gfc.noise_shaping_stop = 0
                if (gfc.subblock_gain == -1)
                    gfc.subblock_gain = 1
                gfc.use_best_huffman = 1
                gfc.full_outer_loop = 0
            }

            3 -> {
                gfc.psymodel = 1
                if (gfc.noise_shaping == 0)
                    gfc.noise_shaping = 1
                gfc.noise_shaping_amp = 1
                gfc.noise_shaping_stop = 1
                if (gfc.subblock_gain == -1)
                    gfc.subblock_gain = 1
                gfc.use_best_huffman = 1
                gfc.full_outer_loop = 0
            }

            2 -> {
                gfc.psymodel = 1
                if (gfc.noise_shaping == 0)
                    gfc.noise_shaping = 1
                if (gfc.substep_shaping == 0)
                    gfc.substep_shaping = 2
                gfc.noise_shaping_amp = 1
                gfc.noise_shaping_stop = 1
                if (gfc.subblock_gain == -1)
                    gfc.subblock_gain = 1
                gfc.use_best_huffman = 1 /* inner loop */
                gfc.full_outer_loop = 0
            }

            1 -> {
                gfc.psymodel = 1
                if (gfc.noise_shaping == 0)
                    gfc.noise_shaping = 1
                if (gfc.substep_shaping == 0)
                    gfc.substep_shaping = 2
                gfc.noise_shaping_amp = 2
                gfc.noise_shaping_stop = 1
                if (gfc.subblock_gain == -1)
                    gfc.subblock_gain = 1
                gfc.use_best_huffman = 1
                gfc.full_outer_loop = 0
            }

            0 -> {
                gfc.psymodel = 1
                if (gfc.noise_shaping == 0)
                    gfc.noise_shaping = 1
                if (gfc.substep_shaping == 0)
                    gfc.substep_shaping = 2
                gfc.noise_shaping_amp = 2
                gfc.noise_shaping_stop = 1
                if (gfc.subblock_gain == -1)
                    gfc.subblock_gain = 1
                gfc.use_best_huffman = 1
                /*
			 * type 2 disabled because of it slowness, in favor of full outer
			 * loop search
			 */
                gfc.full_outer_loop = 0
            }
            else -> {
                gfc.psymodel = 0
                gfc.noise_shaping = 0
                gfc.noise_shaping_amp = 0
                gfc.noise_shaping_stop = 0
                gfc.use_best_huffman = 0
                gfc.full_outer_loop = 0
            }
        }/*
			 * full outer loop search disabled because of audible distortions it
			 * may generate rh 060629
			 */

    }

    private fun linear_int(a: Double, b: Double, m: Double): Double {
        return a + m * (b - a)
    }

    /**
     * @param bRate
     * legal rates from 8 to 320
     */
    private fun FindNearestBitrate(bRate: Int, version: Int,
                                   samplerate: Int): Int {
        var version = version
        /* MPEG-1 or MPEG-2 LSF */
        if (samplerate < 16000)
            version = 2

        var bitrate = Tables.bitrate_table[version][1]

        for (i in 2..14) {
            if (Tables.bitrate_table[version][i] > 0) {
                if (Math.abs(Tables.bitrate_table[version][i] - bRate) < Math
                                .abs(bitrate - bRate))
                    bitrate = Tables.bitrate_table[version][i]
            }
        }
        return bitrate
    }

    /**
     * Used to find table index when we need bitrate-based values determined
     * using tables
     *
     * bitrate in kbps
     *
     * Gabriel Bouvigne 2002-11-03
     */
    fun nearestBitrateFullIndex(bitrate: Int): Int {
        /* borrowed from DM abr presets */

        val full_bitrate_table = intArrayOf(8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320)

        var lower_range = 0
        var lower_range_kbps = 0
        var upper_range = 0
        var upper_range_kbps = 0

        /* We assume specified bitrate will be 320kbps */
        upper_range_kbps = full_bitrate_table[16]
        upper_range = 16
        lower_range_kbps = full_bitrate_table[16]
        lower_range = 16

        /*
		 * Determine which significant bitrates the value specified falls
		 * between, if loop ends without breaking then we were correct above
		 * that the value was 320
		 */
        for (b in 0..15) {
            if (Math.max(bitrate, full_bitrate_table[b + 1]) != bitrate) {
                upper_range_kbps = full_bitrate_table[b + 1]
                upper_range = b + 1
                lower_range_kbps = full_bitrate_table[b]
                lower_range = b
                break /* We found upper range */
            }
        }

        /* Determine which range the value specified is closer to */
        return if (upper_range_kbps - bitrate > bitrate - lower_range_kbps) {
            lower_range
        } else upper_range
    }

    /**
     * map frequency to a valid MP3 sample frequency
     *
     * Robert Hegemann 2000-07-01
     */
    private fun map2MP3Frequency(freq: Int): Int {
        if (freq <= 8000)
            return 8000
        if (freq <= 11025)
            return 11025
        if (freq <= 12000)
            return 12000
        if (freq <= 16000)
            return 16000
        if (freq <= 22050)
            return 22050
        if (freq <= 24000)
            return 24000
        if (freq <= 32000)
            return 32000
        return if (freq <= 44100) 44100 else 48000

    }

    /**
     * convert samp freq in Hz to index
     */
    private fun SmpFrqIndex(sample_freq: Int, gpf: LameGlobalFlags): Int {
        when (sample_freq) {
            44100 -> {
                gpf.version = 1
                return 0
            }
            48000 -> {
                gpf.version = 1
                return 1
            }
            32000 -> {
                gpf.version = 1
                return 2
            }
            22050 -> {
                gpf.version = 0
                return 0
            }
            24000 -> {
                gpf.version = 0
                return 1
            }
            16000 -> {
                gpf.version = 0
                return 2
            }
            11025 -> {
                gpf.version = 0
                return 0
            }
            12000 -> {
                gpf.version = 0
                return 1
            }
            8000 -> {
                gpf.version = 0
                return 2
            }
            else -> {
                gpf.version = 0
                return -1
            }
        }
    }

    /**
     * @param bRate
     * legal rates from 32 to 448 kbps
     * @param version
     * MPEG-1 or MPEG-2/2.5 LSF
     */
    fun BitrateIndex(bRate: Int, version: Int,
                     samplerate: Int): Int {
        var version = version
        /* convert bitrate in kbps to index */
        if (samplerate < 16000)
            version = 2
        for (i in 0..14) {
            if (Tables.bitrate_table[version][i] > 0) {
                if (Tables.bitrate_table[version][i] == bRate) {
                    return i
                }
            }
        }
        return -1
    }

    /**
     * Resampling via FIR filter, blackman window.
     */
    private fun blackman(x: Float, fcn: Float, l: Int): Float {
        var x = x
        /*
		 * This algorithm from: SIGNAL PROCESSING ALGORITHMS IN FORTRAN AND C
		 * S.D. Stearns and R.A. David, Prentice-Hall, 1992
		 */
        val wcn = (Math.PI * fcn).toFloat()

        x /= l.toFloat()
        if (x < 0)
            x = 0f
        if (x > 1)
            x = 1f
        val x2 = x - .5f

        val bkwn = 0.42f - 0.5f * Math.cos(2.0 * x.toDouble() * Math.PI).toFloat() + 0.08f * Math.cos(4.0 * x.toDouble() * Math.PI).toFloat()
        return if (Math.abs(x2) < 1e-9)
            (wcn / Math.PI).toFloat()
        else
            (bkwn * Math.sin((l.toFloat() * wcn * x2).toDouble()) / (Math.PI * l.toDouble() * x2.toDouble())).toFloat()
    }

    /**
     * Greatest common divisor.
     *
     * Joint work of Euclid and M. Hendry
     */
    private fun gcd(i: Int, j: Int): Int {
        return if (j != 0) gcd(j, i % j) else i
    }

    protected class NumUsed {
        internal var num_used: Int = 0
    }

    private fun fill_buffer_resample(gfp: LameGlobalFlags,
                                     outbuf: FloatArray, outbufPos: Int, desired_len: Int,
                                     inbuf: FloatArray, in_bufferPos: Int, len: Int,
                                     num_used: NumUsed, ch: Int): Int {
        val gfc = gfp.internal_flags
        var i: Int
        var j = 0
        var k: Int
        /* number of convolution functions to pre-compute */
        var bpc = gfp.out_samplerate / gcd(gfp.out_samplerate, gfp.in_samplerate)
        if (bpc > LameInternalFlags.BPC)
            bpc = LameInternalFlags.BPC

        val intratio = (if (Math.abs(gfc.resample_ratio - Math.floor(.5 + gfc.resample_ratio)) < .0001)
            1
        else
            0).toFloat()
        var fcn = 1.00f / gfc.resample_ratio.toFloat()
        if (fcn > 1.00)
            fcn = 1.00f
        var filter_l = 31
        if (0 == filter_l % 2)
            --filter_l /* must be odd */
        filter_l += intratio.toInt() /* unless resample_ratio=int, it must be even */

        val BLACKSIZE = filter_l + 1 /* size of data needed for FIR */

        if (gfc.fill_buffer_resample_init == 0) {
            gfc.inbuf_old[0] = FloatArray(BLACKSIZE)
            gfc.inbuf_old[1] = FloatArray(BLACKSIZE)
            i = 0
            while (i <= 2 * bpc) {
                gfc.blackfilt[i] = FloatArray(BLACKSIZE)
                ++i
            }

            gfc.itime[0] = 0.0
            gfc.itime[1] = 0.0

            /* precompute blackman filter coefficients */
            j = 0
            while (j <= 2 * bpc) {
                var sum = 0f
                val offset = (j - bpc) / (2f * bpc)
                i = 0
                while (i <= filter_l) {
                    gfc.blackfilt[j][i] = blackman(i - offset, fcn,
                            filter_l)
                    sum += gfc.blackfilt[j][i]
                    i++
                }
                i = 0
                while (i <= filter_l) {
                    gfc.blackfilt[j][i] /= sum
                    i++
                }
                j++
            }
            gfc.fill_buffer_resample_init = 1
        }

        val inbuf_old = gfc.inbuf_old[ch]

        /* time of j'th element in inbuf = itime + j/ifreq; */
        /* time of k'th element in outbuf = j/ofreq */
        k = 0
        while (k < desired_len) {
            val time0: Double
            val joff: Int

            time0 = k * gfc.resample_ratio /* time of k'th output sample */
            j = Math.floor(time0 - gfc.itime[ch]).toInt()

            /* check if we need more input data */
            if (filter_l + j - filter_l / 2 >= len)
                break

            /* blackman filter. by default, window centered at j+.5(filter_l%2) */
            /* but we want a window centered at time0. */
            val offset = (time0 - gfc.itime[ch] - (j + .5 * (filter_l % 2))).toFloat()
            assert(Math.abs(offset) <= .501)

            /* find the closest precomputed window for this offset: */
            joff = Math.floor((offset * 2f * bpc.toFloat()).toDouble() + bpc.toDouble() + .5).toInt()

            var xvalue = 0f
            i = 0
            while (i <= filter_l) {
                val j2 = i + j - filter_l / 2
                val y: Float
                assert(j2 < len)
                assert(j2 + BLACKSIZE >= 0)
                y = if (j2 < 0)
                    inbuf_old[BLACKSIZE + j2]
                else
                    inbuf[in_bufferPos + j2]
                xvalue += y * gfc.blackfilt[joff][i]
                ++i
            }
            outbuf[outbufPos + k] = xvalue
            k++
        }

        /* k = number of samples added to outbuf */
        /* last k sample used data from [j-filter_l/2,j+filter_l-filter_l/2] */

        /* how many samples of input data were used: */
        num_used.num_used = Math.min(len, filter_l + j - filter_l / 2)

        /*
		 * adjust our input time counter. Incriment by the number of samples
		 * used, then normalize so that next output sample is at time 0, next
		 * input buffer is at time itime[ch]
		 */
        gfc.itime[ch] += num_used.num_used - k * gfc.resample_ratio

        /* save the last BLACKSIZE samples into the inbuf_old buffer */
        if (num_used.num_used >= BLACKSIZE) {
            i = 0
            while (i < BLACKSIZE) {
                inbuf_old[i] = inbuf[in_bufferPos + num_used.num_used + i - BLACKSIZE]
                i++
            }
        } else {
            /* shift in num_used.num_used samples into inbuf_old */
            val n_shift = BLACKSIZE - num_used.num_used /*
														 * number of samples to
														 * shift
														 */

            /*
			 * shift n_shift samples by num_used.num_used, to make room for the
			 * num_used new samples
			 */
            i = 0
            while (i < n_shift) {
                inbuf_old[i] = inbuf_old[i + num_used.num_used]
                ++i
            }

            /* shift in the num_used.num_used samples */
            j = 0
            while (i < BLACKSIZE) {
                inbuf_old[i] = inbuf[in_bufferPos + j]
                ++i
                ++j
            }

            assert(j == num_used.num_used)
        }
        return k /* return the number samples created at the new samplerate */
    }

    /*
	 * copy in new samples from in_buffer into mfbuf, with resampling if
	 * necessary. n_in = number of samples from the input buffer that were used.
	 * n_out = number of samples copied into mfbuf
	 */

    private fun fill_buffer(gfp: LameGlobalFlags, mfbuf: Array<FloatArray>,
                            in_buffer: Array<FloatArray>, in_bufferPos: Int,
                            nsamples: Int, io: InOut) {
        val gfc = gfp.internal_flags

        /* copy in new samples into mfbuf, with resampling if necessary */
        if (gfc.resample_ratio < .9999 || gfc.resample_ratio > 1.0001) {
            for (ch in 0 until gfc.channels_out) {
                val numUsed = NumUsed()
                io.n_out = fill_buffer_resample(gfp, mfbuf[ch], gfc.mf_size,
                        gfp.framesize, in_buffer[ch], in_bufferPos, nsamples,
                        numUsed, ch)
                io.n_in = numUsed.num_used
            }
        } else {
            io.n_out = Math.min(gfp.framesize, nsamples)
            io.n_in = io.n_out
            for (i in 0 until io.n_out) {
                mfbuf[0][gfc.mf_size + i] = in_buffer[0][in_bufferPos + i]
                if (gfc.channels_out == 2)
                    mfbuf[1][gfc.mf_size + i] = in_buffer[1][in_bufferPos + i]
            }
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
     */
    fun lame_init_params(gfp: LameGlobalFlags): Int {
        val gfc = gfp.internal_flags

        gfc.Class_ID = 0
        if (gfc.ATH == null)
            gfc.ATH = ATH()
        if (gfc.PSY == null)
            gfc.PSY = PSY()
        if (gfc.rgdata == null)
            gfc.rgdata = ReplayGain()

        gfc.channels_in = gfp.num_channels
        if (gfc.channels_in == 1)
            gfp.mode = MPEGMode.MONO
        gfc.channels_out = if (gfp.mode == MPEGMode.MONO) 1 else 2
        gfc.mode_ext = Encoder.MPG_MD_MS_LR
        if (gfp.mode == MPEGMode.MONO)
            gfp.force_ms = false
        /*
		 * don't allow forced mid/side stereo for mono output
		 */

        if (gfp.VBR == VbrMode.vbr_off && gfp.VBR_mean_bitrate_kbps != 128
                && gfp.brate == 0)
            gfp.brate = gfp.VBR_mean_bitrate_kbps

        if (gfp.VBR == VbrMode.vbr_off || gfp.VBR == VbrMode.vbr_mtrh
                || gfp.VBR == VbrMode.vbr_mt) {
            /* these modes can handle free format condition */
        } else {
            gfp.free_format = false /* mode can't be mixed with free format */
        }

        if (gfp.VBR == VbrMode.vbr_off && gfp.brate == 0) {
            /* no bitrate or compression ratio specified, use 11.025 */
            if (BitStream.EQ(gfp.compression_ratio, 0f))
                gfp.compression_ratio = 11.025f
            /*
			 * rate to compress a CD down to exactly 128000 bps
			 */
        }

        /* find bitrate if user specify a compression ratio */
        if (gfp.VBR == VbrMode.vbr_off && gfp.compression_ratio > 0) {

            if (gfp.out_samplerate == 0)
                gfp.out_samplerate = map2MP3Frequency((0.97 * gfp.in_samplerate).toInt())
            /*
			 * round up with a margin of 3 %
			 */

            /*
			 * choose a bitrate for the output samplerate which achieves
			 * specified compression ratio
			 */
            gfp.brate = (gfp.out_samplerate * 16 * gfc.channels_out / (1e3f * gfp.compression_ratio)).toInt()

            /* we need the version for the bitrate table look up */
            gfc.samplerate_index = SmpFrqIndex(gfp.out_samplerate, gfp)

            if (!gfp.free_format)
            /*
								 * for non Free Format find the nearest allowed
								 * bitrate
								 */
                gfp.brate = FindNearestBitrate(gfp.brate, gfp.version,
                        gfp.out_samplerate)
        }

        if (gfp.out_samplerate != 0) {
            if (gfp.out_samplerate < 16000) {
                gfp.VBR_mean_bitrate_kbps = Math.max(gfp.VBR_mean_bitrate_kbps,
                        8)
                gfp.VBR_mean_bitrate_kbps = Math.min(gfp.VBR_mean_bitrate_kbps,
                        64)
            } else if (gfp.out_samplerate < 32000) {
                gfp.VBR_mean_bitrate_kbps = Math.max(gfp.VBR_mean_bitrate_kbps,
                        8)
                gfp.VBR_mean_bitrate_kbps = Math.min(gfp.VBR_mean_bitrate_kbps,
                        160)
            } else {
                gfp.VBR_mean_bitrate_kbps = Math.max(gfp.VBR_mean_bitrate_kbps,
                        32)
                gfp.VBR_mean_bitrate_kbps = Math.min(gfp.VBR_mean_bitrate_kbps,
                        320)
            }
        }

        /** */
        /* if a filter has not been enabled, see if we should add one: */
        /** */
        if (gfp.lowpassfreq == 0) {
            var lowpass = 16000.0

            when (gfp.VBR) {
                VbrMode.vbr_off -> {
                    val lh = LowPassHighPass()
                    optimum_bandwidth(lh, gfp.brate)
                    lowpass = lh.lowerlimit
                }
                VbrMode.vbr_abr -> {
                    val lh = LowPassHighPass()
                    optimum_bandwidth(lh, gfp.VBR_mean_bitrate_kbps)
                    lowpass = lh.lowerlimit
                }
                VbrMode.vbr_rh -> {
                    val x = intArrayOf(19500, 19000, 18600, 18000, 17500, 16000, 15600, 14900, 12500, 10000, 3950)
                    if (0 <= gfp.VBR_q && gfp.VBR_q <= 9) {
                        val a = x[gfp.VBR_q].toDouble()
                        val b = x[gfp.VBR_q + 1].toDouble()
                        val m = gfp.VBR_q_frac.toDouble()
                        lowpass = linear_int(a, b, m)
                    } else {
                        lowpass = 19500.0
                    }
                }
                else -> {
                    val x = intArrayOf(19500, 19000, 18500, 18000, 17500, 16500, 15500, 14500, 12500, 9500, 3950)
                    if (0 <= gfp.VBR_q && gfp.VBR_q <= 9) {
                        val a = x[gfp.VBR_q].toDouble()
                        val b = x[gfp.VBR_q + 1].toDouble()
                        val m = gfp.VBR_q_frac.toDouble()
                        lowpass = linear_int(a, b, m)
                    } else {
                        lowpass = 19500.0
                    }
                }
            }
            if (gfp.mode == MPEGMode.MONO && (gfp.VBR == VbrMode.vbr_off || gfp.VBR == VbrMode.vbr_abr))
                lowpass *= 1.5

            gfp.lowpassfreq = lowpass.toInt()
        }

        if (gfp.out_samplerate == 0) {
            if (2 * gfp.lowpassfreq > gfp.in_samplerate) {
                gfp.lowpassfreq = gfp.in_samplerate / 2
            }
            gfp.out_samplerate = optimum_samplefreq(gfp.lowpassfreq,
                    gfp.in_samplerate)
        }

        gfp.lowpassfreq = Math.min(20500, gfp.lowpassfreq)
        gfp.lowpassfreq = Math.min(gfp.out_samplerate / 2, gfp.lowpassfreq)

        if (gfp.VBR == VbrMode.vbr_off) {
            gfp.compression_ratio = gfp.out_samplerate * 16 * gfc.channels_out / (1e3f * gfp.brate)
        }
        if (gfp.VBR == VbrMode.vbr_abr) {
            gfp.compression_ratio = gfp.out_samplerate * 16 * gfc.channels_out / (1e3f * gfp.VBR_mean_bitrate_kbps)
        }

        /*
		 * do not compute ReplayGain values and do not find the peak sample if
		 * we can't store them
		 */
        if (!gfp.bWriteVbrTag) {
            gfp.findReplayGain = false
            gfp.decode_on_the_fly = false
            gfc.findPeakSample = false
        }
        gfc.findReplayGain = gfp.findReplayGain
        gfc.decode_on_the_fly = gfp.decode_on_the_fly

        if (gfc.decode_on_the_fly)
            gfc.findPeakSample = true

        if (gfc.findReplayGain) {
            if (ga.InitGainAnalysis(gfc.rgdata, gfp.out_samplerate.toLong()) == GainAnalysis.INIT_GAIN_ANALYSIS_ERROR) {
                gfp.internal_flags = null
                return -6
            }
        }

        if (gfc.decode_on_the_fly && !gfp.decode_only) {
            if (gfc.hip != null) {
                mpglib.hip_decode_exit(gfc.hip)
            }
            gfc.hip = mpglib.hip_decode_init()
        }

        gfc.mode_gr = if (gfp.out_samplerate <= 24000) 1 else 2
        /*
		 * Number of granules per frame
		 */
        gfp.framesize = 576 * gfc.mode_gr
        gfp.encoder_delay = Encoder.ENCDELAY

        gfc.resample_ratio = gfp.in_samplerate.toDouble() / gfp.out_samplerate

        /**
         * <PRE>
         * sample freq       bitrate     compression ratio
         * [kHz]      [kbps/channel]   for 16 bit input
         * 44.1            56               12.6
         * 44.1            64               11.025
         * 44.1            80                8.82
         * 22.05           24               14.7
         * 22.05           32               11.025
         * 22.05           40                8.82
         * 16              16               16.0
         * 16              24               10.667
        </PRE> *
         */
        /**
         * <PRE>
         * For VBR, take a guess at the compression_ratio.
         * For example:
         *
         * VBR_q    compression     like
         * -        4.4         320 kbps/44 kHz
         * 0...1      5.5         256 kbps/44 kHz
         * 2        7.3         192 kbps/44 kHz
         * 4        8.8         160 kbps/44 kHz
         * 6       11           128 kbps/44 kHz
         * 9       14.7          96 kbps
         *
         * for lower bitrates, downsample with --resample
        </PRE> *
         */
        when (gfp.VBR) {
            VbrMode.vbr_mt, VbrMode.vbr_rh, VbrMode.vbr_mtrh -> {
                /* numbers are a bit strange, but they determine the lowpass value */
                val cmp = floatArrayOf(5.7f, 6.5f, 7.3f, 8.2f, 10f, 11.9f, 13f, 14f, 15f, 16.5f)
                gfp.compression_ratio = cmp[gfp.VBR_q]
            }
            VbrMode.vbr_abr -> gfp.compression_ratio = gfp.out_samplerate * 16 * gfc.channels_out / (1e3f * gfp.VBR_mean_bitrate_kbps)
            else -> gfp.compression_ratio = gfp.out_samplerate * 16 * gfc.channels_out / (1e3f * gfp.brate)
        }

        /*
		 * mode = -1 (not set by user) or mode = MONO (because of only 1 input
		 * channel). If mode has not been set, then select J-STEREO
		 */
        if (gfp.mode == MPEGMode.NOT_SET) {
            gfp.mode = MPEGMode.JOINT_STEREO
        }

        /* apply user driven high pass filter */
        if (gfp.highpassfreq > 0) {
            gfc.highpass1 = 2f * gfp.highpassfreq

            if (gfp.highpasswidth >= 0)
                gfc.highpass2 = 2f * (gfp.highpassfreq + gfp.highpasswidth)
            else
            /* 0% above on default */
                gfc.highpass2 = (1 + 0.00f) * 2f * gfp.highpassfreq.toFloat()

            gfc.highpass1 /= gfp.out_samplerate.toFloat()
            gfc.highpass2 /= gfp.out_samplerate.toFloat()
        } else {
            gfc.highpass1 = 0f
            gfc.highpass2 = 0f
        }
        /* apply user driven low pass filter */
        if (gfp.lowpassfreq > 0) {
            gfc.lowpass2 = 2f * gfp.lowpassfreq
            if (gfp.lowpasswidth >= 0) {
                gfc.lowpass1 = 2f * (gfp.lowpassfreq - gfp.lowpasswidth)
                if (gfc.lowpass1 < 0)
                /* has to be >= 0 */
                    gfc.lowpass1 = 0f
            } else { /* 0% below on default */
                gfc.lowpass1 = (1 - 0.00f) * 2f * gfp.lowpassfreq.toFloat()
            }
            gfc.lowpass1 /= gfp.out_samplerate.toFloat()
            gfc.lowpass2 /= gfp.out_samplerate.toFloat()
        } else {
            gfc.lowpass1 = 0f
            gfc.lowpass2 = 0f
        }

        /** */
        /* compute info needed for polyphase filter (filter type==0, default) */
        /** */
        lame_init_params_ppflt(gfp)

        /*******************************************************
         * samplerate and bitrate index
         */
        gfc.samplerate_index = SmpFrqIndex(gfp.out_samplerate, gfp)
        if (gfc.samplerate_index < 0) {
            gfp.internal_flags = null
            return -1
        }

        if (gfp.VBR == VbrMode.vbr_off) {
            if (gfp.free_format) {
                gfc.bitrate_index = 0
            } else {
                gfp.brate = FindNearestBitrate(gfp.brate, gfp.version,
                        gfp.out_samplerate)
                gfc.bitrate_index = BitrateIndex(gfp.brate, gfp.version,
                        gfp.out_samplerate)
                if (gfc.bitrate_index <= 0) {
                    gfp.internal_flags = null
                    return -1
                }
            }
        } else {
            gfc.bitrate_index = 1
        }

        /* for CBR, we will write an "info" tag. */

        if (gfp.analysis)
            gfp.bWriteVbrTag = false

        /* some file options not allowed if output is: not specified or stdout */
        if (gfc.pinfo != null)
            gfp.bWriteVbrTag = false /* disable Xing VBR tag */

        bs.init_bit_stream_w(gfc)

        val j = gfc.samplerate_index + 3 * gfp.version + 6 * if (gfp.out_samplerate < 16000) 1 else 0
        for (i in 0 until Encoder.SBMAX_l + 1)
            gfc.scalefac_band.l[i] = qupvt.sfBandIndex[j].l[i]

        for (i in 0 until Encoder.PSFB21 + 1) {
            val size = (gfc.scalefac_band.l[22] - gfc.scalefac_band.l[21]) / Encoder.PSFB21
            val start = gfc.scalefac_band.l[21] + i * size
            gfc.scalefac_band.psfb21[i] = start
        }
        gfc.scalefac_band.psfb21[Encoder.PSFB21] = 576

        for (i in 0 until Encoder.SBMAX_s + 1)
            gfc.scalefac_band.s[i] = qupvt.sfBandIndex[j].s[i]

        for (i in 0 until Encoder.PSFB12 + 1) {
            val size = (gfc.scalefac_band.s[13] - gfc.scalefac_band.s[12]) / Encoder.PSFB12
            val start = gfc.scalefac_band.s[12] + i * size
            gfc.scalefac_band.psfb12[i] = start
        }
        gfc.scalefac_band.psfb12[Encoder.PSFB12] = 192

        /* determine the mean bitrate for main data */
        if (gfp.version == 1)
        /* MPEG 1 */
            gfc.sideinfo_len = if (gfc.channels_out == 1) 4 + 17 else 4 + 32
        else
        /* MPEG 2 */
            gfc.sideinfo_len = if (gfc.channels_out == 1) 4 + 9 else 4 + 17

        if (gfp.error_protection)
            gfc.sideinfo_len += 2

        lame_init_bitstream(gfp)

        gfc.Class_ID = LAME_ID

        run {
            var k: Int

            k = 0
            while (k < 19) {
                gfc.nsPsy.pefirbuf[k] = (700 * gfc.mode_gr * gfc.channels_out).toFloat()
                k++
            }

            if (gfp.ATHtype == -1)
                gfp.ATHtype = 4
        }

        assert(gfp.VBR_q <= 9)
        assert(gfp.VBR_q >= 0)

        when (gfp.VBR) {

            VbrMode.vbr_mt -> {
                gfp.VBR = VbrMode.vbr_mtrh
//                run {
                    if (gfp.useTemporal == null) {
                        gfp.useTemporal = false /* off by default for this VBR mode */
                    }

                    p.apply_preset(gfp, 500 - gfp.VBR_q * 10, 0)
                    /**
                     * <PRE>
                     * The newer VBR code supports only a limited
                     * subset of quality levels:
                     * 9-5=5 are the same, uses x^3/4 quantization
                     * 4-0=0 are the same  5 plus best huffman divide code
                    </PRE> *
                     */
                    if (gfp.quality < 0)
                        gfp.quality = LAME_DEFAULT_QUALITY
                    if (gfp.quality < 5)
                        gfp.quality = 0
                    if (gfp.quality > 5)
                        gfp.quality = 5

                    gfc.PSY.mask_adjust = gfp.maskingadjust
                    gfc.PSY.mask_adjust_short = gfp.maskingadjust_short

                    /*
			 * sfb21 extra only with MPEG-1 at higher sampling rates
			 */
                    if (gfp.experimentalY)
                        gfc.sfb21_extra = false
                    else
                        gfc.sfb21_extra = gfp.out_samplerate > 44000

                    gfc.iteration_loop = VBRNewIterationLoop(qu)
//                    break
//
//                }
            }
            //$FALL-THROUGH$
            VbrMode.vbr_mtrh -> {
                if (gfp.useTemporal == null) {
                    gfp.useTemporal = false
                }
                p.apply_preset(gfp, 500 - gfp.VBR_q * 10, 0)
                if (gfp.quality < 0)
                    gfp.quality = LAME_DEFAULT_QUALITY
                if (gfp.quality < 5)
                    gfp.quality = 0
                if (gfp.quality > 5)
                    gfp.quality = 5
                gfc.PSY.mask_adjust = gfp.maskingadjust
                gfc.PSY.mask_adjust_short = gfp.maskingadjust_short
                if (gfp.experimentalY)
                    gfc.sfb21_extra = false
                else
                    gfc.sfb21_extra = gfp.out_samplerate > 44000
                gfc.iteration_loop = VBRNewIterationLoop(qu)
            }
            VbrMode.vbr_rh -> {

                p.apply_preset(gfp, 500 - gfp.VBR_q * 10, 0)

                gfc.PSY.mask_adjust = gfp.maskingadjust
                gfc.PSY.mask_adjust_short = gfp.maskingadjust_short

                /*
			 * sfb21 extra only with MPEG-1 at higher sampling rates
			 */
                if (gfp.experimentalY)
                    gfc.sfb21_extra = false
                else
                    gfc.sfb21_extra = gfp.out_samplerate > 44000

                /*
			 * VBR needs at least the output of GPSYCHO, so we have to garantee
			 * that by setting a minimum quality level, actually level 6 does
			 * it. down to level 6
			 */
                if (gfp.quality > 6)
                    gfp.quality = 6

                if (gfp.quality < 0)
                    gfp.quality = LAME_DEFAULT_QUALITY

                gfc.iteration_loop = VBROldIterationLoop(qu)
            }

            else /* cbr/abr */ -> {
                val vbrmode: VbrMode

                /*
			 * no sfb21 extra with CBR code
			 */
                gfc.sfb21_extra = false

                if (gfp.quality < 0)
                    gfp.quality = LAME_DEFAULT_QUALITY

                vbrmode = gfp.VBR
                if (vbrmode == VbrMode.vbr_off)
                    gfp.VBR_mean_bitrate_kbps = gfp.brate
                /* second, set parameters depending on bitrate */
                p.apply_preset(gfp, gfp.VBR_mean_bitrate_kbps, 0)
                gfp.VBR = vbrmode

                gfc.PSY.mask_adjust = gfp.maskingadjust
                gfc.PSY.mask_adjust_short = gfp.maskingadjust_short

                if (vbrmode == VbrMode.vbr_off) {
                    gfc.iteration_loop = CBRNewIterationLoop(qu)
                } else {
                    gfc.iteration_loop = ABRIterationLoop(qu)
                }
            }
        }

        /* initialize default values common for all modes */

        if (gfp.VBR != VbrMode.vbr_off) { /* choose a min/max bitrate for VBR */
            /* if the user didn't specify VBR_max_bitrate: */
            gfc.VBR_min_bitrate = 1
            /*
			 * default: allow 8 kbps (MPEG-2) or 32 kbps (MPEG-1)
			 */
            gfc.VBR_max_bitrate = 14
            /*
			 * default: allow 160 kbps (MPEG-2) or 320 kbps (MPEG-1)
			 */
            if (gfp.out_samplerate < 16000)
                gfc.VBR_max_bitrate = 8 /* default: allow 64 kbps (MPEG-2.5) */
            if (gfp.VBR_min_bitrate_kbps != 0) {
                gfp.VBR_min_bitrate_kbps = FindNearestBitrate(
                        gfp.VBR_min_bitrate_kbps, gfp.version,
                        gfp.out_samplerate)
                gfc.VBR_min_bitrate = BitrateIndex(gfp.VBR_min_bitrate_kbps,
                        gfp.version, gfp.out_samplerate)
                if (gfc.VBR_min_bitrate < 0)
                    return -1
            }
            if (gfp.VBR_max_bitrate_kbps != 0) {
                gfp.VBR_max_bitrate_kbps = FindNearestBitrate(
                        gfp.VBR_max_bitrate_kbps, gfp.version,
                        gfp.out_samplerate)
                gfc.VBR_max_bitrate = BitrateIndex(gfp.VBR_max_bitrate_kbps,
                        gfp.version, gfp.out_samplerate)
                if (gfc.VBR_max_bitrate < 0)
                    return -1
            }
            gfp.VBR_min_bitrate_kbps = Tables.bitrate_table[gfp.version][gfc.VBR_min_bitrate]
            gfp.VBR_max_bitrate_kbps = Tables.bitrate_table[gfp.version][gfc.VBR_max_bitrate]
            gfp.VBR_mean_bitrate_kbps = Math.min(
                    Tables.bitrate_table[gfp.version][gfc.VBR_max_bitrate],
                    gfp.VBR_mean_bitrate_kbps)
            gfp.VBR_mean_bitrate_kbps = Math.max(
                    Tables.bitrate_table[gfp.version][gfc.VBR_min_bitrate],
                    gfp.VBR_mean_bitrate_kbps)
        }

        /* just another daily changing developer switch */
        if (gfp.tune) {
            gfc.PSY.mask_adjust += gfp.tune_value_a
            gfc.PSY.mask_adjust_short += gfp.tune_value_a
        }

        /* initialize internal qval settings */
        lame_init_qval(gfp)

        /*
		 * automatic ATH adjustment on
		 */
        if (gfp.athaa_type < 0)
            gfc.ATH.useAdjust = 3
        else
            gfc.ATH.useAdjust = gfp.athaa_type

        /* initialize internal adaptive ATH settings -jd */
        gfc.ATH.aaSensitivityP = Math.pow(10.0, gfp.athaa_sensitivity / -10.0).toFloat()

        if (gfp.short_blocks == null) {
            gfp.short_blocks = ShortBlock.short_block_allowed
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
        if (gfp.short_blocks == ShortBlock.short_block_allowed && (gfp.mode == MPEGMode.JOINT_STEREO || gfp.mode == MPEGMode.STEREO)) {
            gfp.short_blocks = ShortBlock.short_block_coupled
        }

        if (gfp.quant_comp < 0)
            gfp.quant_comp = 1
        if (gfp.quant_comp_short < 0)
            gfp.quant_comp_short = 0

        if (gfp.msfix < 0)
            gfp.msfix = 0f

        /* select psychoacoustic model */
        gfp.exp_nspsytune = gfp.exp_nspsytune or 1

        if (gfp.internal_flags.nsPsy.attackthre < 0)
            gfp.internal_flags.nsPsy.attackthre = PsyModel.NSATTACKTHRE
        if (gfp.internal_flags.nsPsy.attackthre_s < 0)
            gfp.internal_flags.nsPsy.attackthre_s = PsyModel.NSATTACKTHRE_S.toFloat()

        if (gfp.scale < 0)
            gfp.scale = 1f

        if (gfp.ATHtype < 0)
            gfp.ATHtype = 4

        if (gfp.ATHcurve < 0)
            gfp.ATHcurve = 4f

        if (gfp.athaa_loudapprox < 0)
            gfp.athaa_loudapprox = 2

        if (gfp.interChRatio < 0)
            gfp.interChRatio = 0f

        if (gfp.useTemporal == null)
            gfp.useTemporal = true /* on by default */

        /*
		 * padding method as described in
		 * "MPEG-Layer3 / Bitstream Syntax and Decoding" by Martin Sieler, Ralph
		 * Sperschneider
		 *
		 * note: there is no padding for the very first frame
		 *
		 * Robert Hegemann 2000-06-22
		 */
        gfc.frac_SpF = 0
        gfc.slot_lag = gfc.frac_SpF
        if (gfp.VBR == VbrMode.vbr_off) {
            gfc.frac_SpF = ((gfp.version + 1).toLong() * 72000L * gfp.brate.toLong() % gfp.out_samplerate).toInt()
            gfc.slot_lag = gfc.frac_SpF
        }

        qupvt.iteration_init(gfp)
        psy.psymodel_init(gfp)

        return 0
    }

    /**
     * Prints some selected information about the coding parameters via the
     * macro command MSGF(), which is currently mapped to lame_errorf (reports
     * via a error function?), which is a printf-like function for <stderr>.
    </stderr> */
    fun lame_print_config(gfp: LameGlobalFlags) {
        val gfc = gfp.internal_flags
        val out_samplerate = gfp.out_samplerate.toDouble()
        val in_samplerate = gfp.out_samplerate * gfc.resample_ratio

        System.out.printf("LAME %s %s (%s)\n", ver.lameVersion,
                ver.lameOsBitness, ver.lameUrl)

        if (gfp.num_channels == 2 && gfc.channels_out == 1 /* mono */) {
            System.out
                    .printf("Autoconverting from stereo to mono. Setting encoding to mono mode.\n")
        }

        if (BitStream.NEQ(gfc.resample_ratio.toFloat(), 1f)) {
            System.out.printf("Resampling:  input %g kHz  output %g kHz\n",
                    1e-3 * in_samplerate, 1e-3 * out_samplerate)
        }

        if (gfc.highpass2 > 0.0) {
            System.out
                    .printf("Using polyphase highpass filter, transition band: %5.0f Hz - %5.0f Hz\n",
                            0.5 * gfc.highpass1.toDouble() * out_samplerate, 0.5
                            * gfc.highpass2.toDouble() * out_samplerate)
        }
        if (0.0 < gfc.lowpass1 || 0.0 < gfc.lowpass2) {
            System.out
                    .printf("Using polyphase lowpass filter, transition band: %5.0f Hz - %5.0f Hz\n",
                            0.5 * gfc.lowpass1.toDouble() * out_samplerate, 0.5
                            * gfc.lowpass2.toDouble() * out_samplerate)
        } else {
            System.out.printf("polyphase lowpass filter disabled\n")
        }

        if (gfp.free_format) {
            System.err
                    .printf("Warning: many decoders cannot handle free format bitstreams\n")
            if (gfp.brate > 320) {
                System.err
                        .printf("Warning: many decoders cannot handle free format bitrates >320 kbps (see documentation)\n")
            }
        }
    }

    /**
     * rh: some pretty printing is very welcome at this point! so, if someone is
     * willing to do so, please do it! add more, if you see more...
     */
    fun lame_print_internals(gfp: LameGlobalFlags) {
        val gfc = gfp.internal_flags

        /*
		 * compiler/processor optimizations, operational, etc.
		 */
        System.err.printf("\nmisc:\n\n")

        System.err.printf("\tscaling: %g\n", gfp.scale)
        System.err.printf("\tch0 (left) scaling: %g\n", gfp.scale_left)
        System.err.printf("\tch1 (right) scaling: %g\n", gfp.scale_right)
        var pc: String
        when (gfc.use_best_huffman) {
            1 -> pc = "best (outside loop)"
            2 -> pc = "best (inside loop, slow)"
            else -> pc = "normal"
        }
        System.err.printf("\thuffman search: %s\n", pc)
        System.err.printf("\texperimental Y=%d\n", gfp.experimentalY)
        System.err.printf("\t...\n")

        /*
		 * everything controlling the stream format
		 */
        System.err.printf("\nstream format:\n\n")
        when (gfp.version) {
            0 -> pc = "2.5"
            1 -> pc = "1"
            2 -> pc = "2"
            else -> pc = "?"
        }
        System.err.printf("\tMPEG-%s Layer 3\n", pc)
        when (gfp.mode) {
            MPEGMode.JOINT_STEREO -> pc = "joint stereo"
            MPEGMode.STEREO -> pc = "stereo"
            MPEGMode.DUAL_CHANNEL -> pc = "dual channel"
            MPEGMode.MONO -> pc = "mono"
            MPEGMode.NOT_SET -> pc = "not set (error)"
            else -> pc = "unknown (error)"
        }
        System.err.printf("\t%d channel - %s\n", gfc.channels_out, pc)

        when (gfp.VBR) {
            VbrMode.vbr_off -> pc = "off"
            else -> pc = "all"
        }
        System.err.printf("\tpadding: %s\n", pc)

        if (VbrMode.vbr_default == gfp.VBR)
            pc = "(default)"
        else if (gfp.free_format)
            pc = "(free format)"
        else
            pc = ""
        when (gfp.VBR) {
            VbrMode.vbr_off -> System.err.printf("\tconstant bitrate - CBR %s\n", pc)
            VbrMode.vbr_abr -> System.err.printf("\tvariable bitrate - ABR %s\n", pc)
            VbrMode.vbr_rh -> System.err.printf("\tvariable bitrate - VBR rh %s\n", pc)
            VbrMode.vbr_mt -> System.err.printf("\tvariable bitrate - VBR mt %s\n", pc)
            VbrMode.vbr_mtrh -> System.err.printf("\tvariable bitrate - VBR mtrh %s\n", pc)
            else -> System.err.printf("\t ?? oops, some new one ?? \n")
        }
        if (gfp.bWriteVbrTag) {
            System.err.printf("\tusing LAME Tag\n")
        }
        System.err.printf("\t...\n")

        /*
		 * everything controlling psychoacoustic settings, like ATH, etc.
		 */
        System.err.printf("\npsychoacoustic:\n\n")

        when (gfp.short_blocks) {
            ShortBlock.short_block_allowed -> pc = "allowed"
            ShortBlock.short_block_coupled -> pc = "channel coupled"
            ShortBlock.short_block_dispensed -> pc = "dispensed"
            ShortBlock.short_block_forced -> pc = "forced"
            else -> pc = "?"
        }
        System.err.printf("\tusing short blocks: %s\n", pc)
        System.err.printf("\tsubblock gain: %d\n", gfc.subblock_gain)
        System.err.printf("\tadjust masking: %g dB\n", gfc.PSY.mask_adjust)
        System.err.printf("\tadjust masking short: %g dB\n",
                gfc.PSY.mask_adjust_short)
        System.err.printf("\tquantization comparison: %d\n", gfp.quant_comp)
        System.err.printf("\t ^ comparison short blocks: %d\n",
                gfp.quant_comp_short)
        System.err.printf("\tnoise shaping: %d\n", gfc.noise_shaping)
        System.err.printf("\t ^ amplification: %d\n", gfc.noise_shaping_amp)
        System.err.printf("\t ^ stopping: %d\n", gfc.noise_shaping_stop)

        pc = "using"
        if (gfp.ATHshort)
            pc = "the only masking for short blocks"
        if (gfp.ATHonly)
            pc = "the only masking"
        if (gfp.noATH)
            pc = "not used"
        System.err.printf("\tATH: %s\n", pc)
        System.err.printf("\t ^ type: %d\n", gfp.ATHtype)
        System.err.printf("\t ^ shape: %g%s\n", gfp.ATHcurve,
                " (only for type 4)")
        System.err.printf("\t ^ level adjustement: %g\n", gfp.ATHlower)
        System.err.printf("\t ^ adjust type: %d\n", gfc.ATH.useAdjust)
        System.err.printf("\t ^ adjust sensitivity power: %f\n",
                gfc.ATH.aaSensitivityP)
        System.err.printf("\t ^ adapt threshold type: %d\n",
                gfp.athaa_loudapprox)

        System.err.printf("\texperimental psy tunings by Naoki Shibata\n")
        System.err
                .printf("\t   adjust masking bass=%g dB, alto=%g dB, treble=%g dB, sfb21=%g dB\n",
                        10 * Math.log10(gfc.nsPsy.longfact[0].toDouble()),
                        10 * Math.log10(gfc.nsPsy.longfact[7].toDouble()),
                        10 * Math.log10(gfc.nsPsy.longfact[14].toDouble()),
                        10 * Math.log10(gfc.nsPsy.longfact[21].toDouble()))

        pc = if (gfp.useTemporal) "yes" else "no"
        System.err.printf("\tusing temporal masking effect: %s\n", pc)
        System.err.printf("\tinterchannel masking ratio: %g\n",
                gfp.interChRatio)
        System.err.printf("\t...\n")

        /*
		 * that's all ?
		 */
        System.err.printf("\n")
    }

    /**
     * routine to feed exactly one frame (gfp.framesize) worth of data to the
     * encoding engine. All buffering, resampling, etc, handled by calling
     * program.
     */
    private fun lame_encode_frame(gfp: LameGlobalFlags,
                                  inbuf_l: FloatArray, inbuf_r: FloatArray, mp3buf: ByteArray,
                                  mp3bufPos: Int, mp3buf_size: Int): Int {
        val ret = enc.lame_encode_mp3_frame(gfp, inbuf_l, inbuf_r, mp3buf,
                mp3bufPos, mp3buf_size)
        gfp.frameNum++
        return ret
    }

    private fun update_inbuffer_size(gfc: LameInternalFlags,
                                     nsamples: Int) {
        if (gfc.in_buffer_0 == null || gfc.in_buffer_nsamples < nsamples) {
            gfc.in_buffer_0 = FloatArray(nsamples)
            gfc.in_buffer_1 = FloatArray(nsamples)
            gfc.in_buffer_nsamples = nsamples
        }
    }

    private fun calcNeeded(gfp: LameGlobalFlags): Int {
        var mf_needed = Encoder.BLKSIZE + gfp.framesize - Encoder.FFTOFFSET
        /*
		 * amount needed for FFT
		 */
        mf_needed = Math.max(mf_needed, 512 + gfp.framesize - 32)
        assert(LameInternalFlags.MFSIZE >= mf_needed)

        return mf_needed
    }

    protected class InOut {
        internal var n_in: Int = 0
        internal var n_out: Int = 0
    }

    /**
     * <PRE>
     * THE MAIN LAME ENCODING INTERFACE
     * mt 3/00
     *
     * input pcm data, output (maybe) mp3 frames.
     * This routine handles all buffering, resampling and filtering for you.
     * The required mp3buffer_size can be computed from num_samples,
     * samplerate and encoding rate, but here is a worst case estimate:
     *
     * mp3buffer_size in bytes = 1.25*num_samples + 7200
     *
     * return code = number of bytes output in mp3buffer.  can be 0
     *
     * NOTE: this routine uses LAME's internal PCM data representation,
     * 'sample_t'.  It should not be used by any application.
     * applications should use lame_encode_buffer(),
     * lame_encode_buffer_float()
     * lame_encode_buffer_int()
     * etc... depending on what type of data they are working with.
    </PRE> *
     */
    private fun lame_encode_buffer_sample(gfp: LameGlobalFlags,
                                          buffer_l: FloatArray, buffer_r: FloatArray, nsamples: Int,
                                          mp3buf: ByteArray, mp3bufPos: Int, mp3buf_size: Int): Int {
        var nsamples = nsamples
        var mp3bufPos = mp3bufPos
        val gfc = gfp.internal_flags
        var mp3size = 0
        var ret: Int
        var i: Int
        var ch: Int
        val mf_needed: Int
        val mp3out: Int
        val in_buffer = arrayOfNulls<FloatArray>(2)

        if (gfc.Class_ID != LAME_ID)
            return -3

        if (nsamples == 0)
            return 0

        /* copy out any tags that may have been written into bitstream */
        mp3out = bs.copy_buffer(gfc, mp3buf, mp3bufPos, mp3buf_size, 0)
        if (mp3out < 0)
            return mp3out /* not enough buffer space */
        mp3bufPos += mp3out
        mp3size += mp3out

        in_buffer[0] = buffer_l
        in_buffer[1] = buffer_r

        /* Apply user defined re-scaling */

        /* user selected scaling of the samples */
        val in_buffer_0 = in_buffer[0]!!
        val in_buffer_1 = in_buffer[1]!!
        if (BitStream.NEQ(gfp.scale, 0f) && BitStream.NEQ(gfp.scale, 1.0f)) {
            i = 0
            while (i < nsamples) {
                in_buffer_0[i] *= gfp.scale
                if (gfc.channels_out == 2)
                    in_buffer_1[i] *= gfp.scale
                ++i
            }
        }

        /* user selected scaling of the channel 0 (left) samples */
        if (BitStream.NEQ(gfp.scale_left, 0f) && BitStream.NEQ(gfp.scale_left, 1.0f)) {
            i = 0
            while (i < nsamples) {
                in_buffer_0[i] *= gfp.scale_left
                ++i
            }
        }

        /* user selected scaling of the channel 1 (right) samples */
        if (BitStream.NEQ(gfp.scale_right, 0f) && BitStream.NEQ(gfp.scale_right, 1.0f)) {
            i = 0
            while (i < nsamples) {
                in_buffer_1[i] *= gfp.scale_right
                ++i
            }
        }

        /* Downsample to Mono if 2 channels in and 1 channel out */
        if (gfp.num_channels == 2 && gfc.channels_out == 1) {
            i = 0
            while (i < nsamples) {
                in_buffer_0[i] = 0.5f * (in_buffer_0[i] + in_buffer_1[i])
                in_buffer_1[i] = 0.0f
                ++i
            }
        }

        mf_needed = calcNeeded(gfp)
        val mfbuf = arrayOf<FloatArray>(gfc.mfbuf[0],  gfc.mfbuf[1])

        var in_bufferPos = 0
        while (nsamples > 0) {
            val in_buffer_ptr = arrayOf(in_buffer_0, in_buffer_1)
            var n_in = 0 /* number of input samples processed with fill_buffer */
            var n_out = 0 /* number of samples output with fill_buffer */
            /* n_in <> n_out if we are resampling */

            in_buffer_ptr[0] = in_buffer_0
            in_buffer_ptr[1] = in_buffer_1
            /* copy in new samples into mfbuf, with resampling */
            val inOut = InOut()
            fill_buffer(gfp, mfbuf, in_buffer_ptr, in_bufferPos, nsamples,
                    inOut)
            n_in = inOut.n_in
            n_out = inOut.n_out

            /* compute ReplayGain of resampled input if requested */
            if (gfc.findReplayGain && !gfc.decode_on_the_fly)
                if (ga.AnalyzeSamples(gfc.rgdata, mfbuf[0], gfc.mf_size,
                                mfbuf[1], gfc.mf_size, n_out, gfc.channels_out) == GainAnalysis.GAIN_ANALYSIS_ERROR)
                    return -6

            /* update in_buffer counters */
            nsamples -= n_in
            in_bufferPos += n_in
            if (gfc.channels_out == 2)
            ;// in_bufferPos += n_in;

            /* update mfbuf[] counters */
            gfc.mf_size += n_out
            assert(gfc.mf_size <= LameInternalFlags.MFSIZE)

            /*
			 * lame_encode_flush may have set gfc.mf_sample_to_encode to 0 so we
			 * have to reinitialize it here when that happened.
			 */
            if (gfc.mf_samples_to_encode < 1) {
                gfc.mf_samples_to_encode = Encoder.ENCDELAY + Encoder.POSTDELAY
            }
            gfc.mf_samples_to_encode += n_out

            if (gfc.mf_size >= mf_needed) {
                /* encode the frame. */
                /* mp3buf = pointer to current location in buffer */
                /* mp3buf_size = size of original mp3 output buffer */
                /* = 0 if we should not worry about the */
                /* buffer size because calling program is */
                /* to lazy to compute it */
                /* mp3size = size of data written to buffer so far */
                /* mp3buf_size-mp3size = amount of space avalable */

                var buf_size = mp3buf_size - mp3size
                if (mp3buf_size == 0)
                    buf_size = 0

                ret = lame_encode_frame(gfp, mfbuf[0], mfbuf[1], mp3buf,
                        mp3bufPos, buf_size)

                if (ret < 0)
                    return ret
                mp3bufPos += ret
                mp3size += ret

                /* shift out old samples */
                gfc.mf_size -= gfp.framesize
                gfc.mf_samples_to_encode -= gfp.framesize
                ch = 0
                while (ch < gfc.channels_out) {
//                    {
                        i = 0
                        while (i < gfc.mf_size) {
                            mfbuf[ch][i] = mfbuf[ch][i + gfp.framesize]
                            i++
                        }
//                    }
                    ch++
                }
            }
        }
        assert(nsamples == 0)

        return mp3size
    }

    private fun lame_encode_buffer(gfp: LameGlobalFlags,
                                   buffer_l: ShortArray, buffer_r: ShortArray, nsamples: Int,
                                   mp3buf: ByteArray, mp3bufPos: Int, mp3buf_size: Int): Int {
        val gfc = gfp.internal_flags

        if (gfc.Class_ID != LAME_ID)
            return -3

        if (nsamples == 0)
            return 0

        update_inbuffer_size(gfc, nsamples)
        val in_buffer = arrayOf<FloatArray>( gfc.in_buffer_0, gfc.in_buffer_1)

        /* make a copy of input buffer, changing type to sample_t */
        for (i in 0 until nsamples) {
            in_buffer[0][i] = buffer_l[i].toFloat()
            if (gfc.channels_in > 1)
                in_buffer[1][i] = buffer_r[i].toFloat()
        }

        return lame_encode_buffer_sample(gfp, in_buffer[0], in_buffer[1],
                nsamples, mp3buf, mp3bufPos, mp3buf_size)
    }

    fun lame_encode_buffer_int(gfp: LameGlobalFlags,
                               buffer_l: IntArray, buffer_r: IntArray, nsamples: Int,
                               mp3buf: ByteArray, mp3bufPos: Int, mp3buf_size: Int): Int {
        val gfc = gfp.internal_flags

        if (gfc.Class_ID != LAME_ID)
            return -3

        if (nsamples == 0)
            return 0

        update_inbuffer_size(gfc, nsamples)
        val in_buffer = arrayOf<FloatArray>(gfc.in_buffer_0, gfc.in_buffer_1)

        /* make a copy of input buffer, changing type to sample_t */
        for (i in 0 until nsamples) {
            /* internal code expects +/- 32768.0 */
            in_buffer[0][i] = buffer_l[i].toFloat()
            if (gfc.channels_in > 1)
                in_buffer[1][i] = buffer_r[i].toFloat()
        }

        return lame_encode_buffer_sample(gfp, in_buffer[0], in_buffer[1],
                nsamples, mp3buf, mp3bufPos, mp3buf_size)
    }

    /**
     * Flush mp3 buffer, pad with ancillary data so last frame is complete.
     * Reset reservoir size to 0 but keep all PCM samples and MDCT data in
     * memory This option is used to break a large file into several mp3 files
     * that when concatenated together will decode with no gaps Because we set
     * the reservoir=0, they will also decode seperately with no errors.
     */
    fun lame_encode_flush_nogap(gfp: LameGlobalFlags,
                                mp3buffer: ByteArray, mp3buffer_size: Int): Int {
        val gfc = gfp.internal_flags
        bs.flush_bitstream(gfp)
        return bs.copy_buffer(gfc, mp3buffer, 0, mp3buffer_size, 1)
    }

    /*
	 * called by lame_init_params. You can also call this after flush_nogap if
	 * you want to write new id3v2 and Xing VBR tags into the bitstream
	 */
    fun lame_init_bitstream(gfp: LameGlobalFlags) {
        val gfc = gfp.internal_flags
        gfp.frameNum = 0

        if (gfp.write_id3tag_automatic) {
            id3.id3tag_write_v2(gfp)
        }
        /* initialize histogram data optionally used by frontend */

        gfc.bitrate_stereoMode_Hist = Array(16) { IntArray(4 + 1) }
        gfc.bitrate_blockType_Hist = Array(16) { IntArray(4 + 1 + 1) }

        gfc.PeakSample = 0.0f

        /* Write initial VBR Header to bitstream and init VBR data */
        if (gfp.bWriteVbrTag)
            vbr.InitVbrTag(gfp)
    }

    /**
     * flush internal PCM sample buffers, then mp3 buffers then write id3 v1
     * tags into bitstream.
     */
    fun lame_encode_flush(gfp: LameGlobalFlags,
                          mp3buffer: ByteArray, mp3bufferPos: Int, mp3buffer_size: Int): Int {
        var mp3bufferPos = mp3bufferPos
        val gfc = gfp.internal_flags
        val buffer = Array(2) { ShortArray(1152) }
        var imp3 = 0
        var mp3count: Int
        var mp3buffer_size_remaining: Int

        /*
		 * we always add POSTDELAY=288 padding to make sure granule with real
		 * data can be complety decoded (because of 50% overlap with next
		 * granule
		 */
        var end_padding: Int
        var frames_left: Int
        var samples_to_encode = gfc.mf_samples_to_encode - Encoder.POSTDELAY
        val mf_needed = calcNeeded(gfp)

        /* Was flush already called? */
        if (gfc.mf_samples_to_encode < 1) {
            return 0
        }
        mp3count = 0

        if (gfp.in_samplerate != gfp.out_samplerate) {
            /*
			 * delay due to resampling; needs to be fixed, if resampling code
			 * gets changed
			 */
            samples_to_encode += (16.0 * gfp.out_samplerate / gfp.in_samplerate).toInt()
        }
        end_padding = gfp.framesize - samples_to_encode % gfp.framesize
        if (end_padding < 576)
            end_padding += gfp.framesize
        gfp.encoder_padding = end_padding

        frames_left = (samples_to_encode + end_padding) / gfp.framesize

        /*
		 * send in a frame of 0 padding until all internal sample buffers are
		 * flushed
		 */
        while (frames_left > 0 && imp3 >= 0) {
            var bunch = mf_needed - gfc.mf_size
            val frame_num = gfp.frameNum

            bunch *= gfp.in_samplerate
            bunch /= gfp.out_samplerate
            if (bunch > 1152)
                bunch = 1152
            if (bunch < 1)
                bunch = 1

            mp3buffer_size_remaining = mp3buffer_size - mp3count

            /* if user specifed buffer size = 0, dont check size */
            if (mp3buffer_size == 0)
                mp3buffer_size_remaining = 0

            imp3 = lame_encode_buffer(gfp, buffer[0], buffer[1], bunch,
                    mp3buffer, mp3bufferPos, mp3buffer_size_remaining)

            mp3bufferPos += imp3
            mp3count += imp3
            frames_left -= if (frame_num != gfp.frameNum) 1 else 0
        }
        /*
		 * Set gfc.mf_samples_to_encode to 0, so we may detect and break loops
		 * calling it more than once in a row.
		 */
        gfc.mf_samples_to_encode = 0

        if (imp3 < 0) {
            /* some type of fatal error */
            return imp3
        }

        mp3buffer_size_remaining = mp3buffer_size - mp3count
        /* if user specifed buffer size = 0, dont check size */
        if (mp3buffer_size == 0)
            mp3buffer_size_remaining = 0

        /* mp3 related stuff. bit buffer might still contain some mp3 data */
        bs.flush_bitstream(gfp)
        imp3 = bs.copy_buffer(gfc, mp3buffer, mp3bufferPos,
                mp3buffer_size_remaining, 1)
        if (imp3 < 0) {
            /* some type of fatal error */
            return imp3
        }
        mp3bufferPos += imp3
        mp3count += imp3
        mp3buffer_size_remaining = mp3buffer_size - mp3count
        /* if user specifed buffer size = 0, dont check size */
        if (mp3buffer_size == 0)
            mp3buffer_size_remaining = 0

        if (gfp.write_id3tag_automatic) {
            /* write a id3 tag to the bitstream */
            id3.id3tag_write_v1(gfp)

            imp3 = bs.copy_buffer(gfc, mp3buffer, mp3bufferPos,
                    mp3buffer_size_remaining, 0)

            if (imp3 < 0) {
                return imp3
            }
            mp3count += imp3
        }
        return mp3count
    }

    /**
     * frees internal buffers
     */
    fun lame_close(gfp: LameGlobalFlags?): Int {
        var ret = 0
        if (gfp != null && gfp.class_id == LAME_ID) {
            val gfc = gfp.internal_flags
            gfp.class_id = 0
            if (null == gfc || gfc.Class_ID != LAME_ID) {
                ret = -3
            }
            gfc!!.Class_ID = 0
            gfp.internal_flags = null
            gfp.lame_allocated_gfp = 0
        }
        return ret
    }

    private fun lame_init_old(gfp: LameGlobalFlags): Int {
        val gfc: LameInternalFlags

        gfp.class_id = LAME_ID

        gfp.internal_flags = LameInternalFlags()
        gfc = gfp.internal_flags

        /* Global flags. set defaults here for non-zero values */
        /* see lame.h for description */
        /*
		 * set integer values to -1 to mean that LAME will compute the best
		 * value, UNLESS the calling program as set it (and the value is no
		 * longer -1)
		 */

        gfp.mode = MPEGMode.NOT_SET
        gfp.original = 1
        gfp.in_samplerate = 44100
        gfp.num_channels = 2
        gfp.num_samples = -1

        gfp.bWriteVbrTag = true
        gfp.quality = -1
        gfp.short_blocks = null
        gfc.subblock_gain = -1

        gfp.lowpassfreq = 0
        gfp.highpassfreq = 0
        gfp.lowpasswidth = -1
        gfp.highpasswidth = -1

        gfp.VBR = VbrMode.vbr_off
        gfp.VBR_q = 4
        gfp.ATHcurve = -1f
        gfp.VBR_mean_bitrate_kbps = 128
        gfp.VBR_min_bitrate_kbps = 0
        gfp.VBR_max_bitrate_kbps = 0
        gfp.VBR_hard_min = 0
        gfc.VBR_min_bitrate = 1 /* not 0 ????? */
        gfc.VBR_max_bitrate = 13 /* not 14 ????? */

        gfp.quant_comp = -1
        gfp.quant_comp_short = -1

        gfp.msfix = -1f

        gfc.resample_ratio = 1.0

        gfc.OldValue[0] = 180
        gfc.OldValue[1] = 180
        gfc.CurrentStep[0] = 4
        gfc.CurrentStep[1] = 4
        gfc.masking_lower = 1f
        gfc.nsPsy.attackthre = -1f
        gfc.nsPsy.attackthre_s = -1f

        gfp.scale = -1f

        gfp.athaa_type = -1
        gfp.ATHtype = -1 /* default = -1 = set in lame_init_params */
        gfp.athaa_loudapprox = -1 /* 1 = flat loudness approx. (total energy) */
        /* 2 = equal loudness curve */
        gfp.athaa_sensitivity = 0.0f /* no offset */
        gfp.useTemporal = null
        gfp.interChRatio = -1f

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
        gfc.mf_samples_to_encode = Encoder.ENCDELAY + Encoder.POSTDELAY
        gfp.encoder_padding = 0
        gfc.mf_size = Encoder.ENCDELAY - Encoder.MDCTDELAY
        /*
		 * we pad input with this many 0's
		 */

        gfp.findReplayGain = false
        gfp.decode_on_the_fly = false

        gfc.decode_on_the_fly = false
        gfc.findReplayGain = false
        gfc.findPeakSample = false

        gfc.RadioGain = 0
        gfc.AudiophileGain = 0
        gfc.noclipGainChange = 0
        gfc.noclipScale = -1.0f

        gfp.preset = 0

        gfp.write_id3tag_automatic = true
        return 0
    }

    fun lame_init(): LameGlobalFlags? {
        val gfp = LameGlobalFlags()

        val ret = lame_init_old(gfp)
        if (ret != 0) {
            return null
        }

        gfp.lame_allocated_gfp = 1
        return gfp
    }

    /***********************************************************************
     *
     * some simple statistics
     *
     * Robert Hegemann 2000-10-11
     *
     */

    /**
     * <PRE>
     * histogram of used bitrate indexes:
     * One has to weight them to calculate the average bitrate in kbps
     *
     * bitrate indices:
     * there are 14 possible bitrate indices, 0 has the special meaning
     * "free format" which is not possible to mix with VBR and 15 is forbidden
     * anyway.
     *
     * stereo modes:
     * 0: LR   number of left-right encoded frames
     * 1: LR-I number of left-right and intensity encoded frames
     * 2: MS   number of mid-side encoded frames
     * 3: MS-I number of mid-side and intensity encoded frames
     *
     * 4: number of encoded frames
    </PRE> *
     */
    fun lame_bitrate_kbps(gfp: LameGlobalFlags?,
                          bitrate_kbps: IntArray?) {
        val gfc: LameInternalFlags?

        if (null == bitrate_kbps)
            return
        if (null == gfp)
            return
        gfc = gfp.internal_flags
        if (null == gfc)
            return

        if (gfp.free_format) {
            for (i in 0..13)
                bitrate_kbps[i] = -1
            bitrate_kbps[0] = gfp.brate
        } else {
            for (i in 0..13)
                bitrate_kbps[i] = Tables.bitrate_table[gfp.version][i + 1]
        }
    }

    fun lame_bitrate_hist(gfp: LameGlobalFlags?,
                          bitrate_count: IntArray?) {

        if (null == bitrate_count)
            return
        if (null == gfp)
            return
        val gfc = gfp.internal_flags ?: return

        if (gfp.free_format) {
            for (i in 0..13)
                bitrate_count[i] = 0
            bitrate_count[0] = gfc.bitrate_stereoMode_Hist[0][4]
        } else {
            for (i in 0..13)
                bitrate_count[i] = gfc.bitrate_stereoMode_Hist[i + 1][4]
        }
    }

    fun lame_stereo_mode_hist(gfp: LameGlobalFlags?,
                              stmode_count: IntArray?) {
        if (null == stmode_count)
            return
        if (null == gfp)
            return
        val gfc = gfp.internal_flags ?: return

        for (i in 0..3) {
            stmode_count[i] = gfc.bitrate_stereoMode_Hist[15][i]
        }
    }

    fun lame_bitrate_stereo_mode_hist(gfp: LameGlobalFlags?,
                                      bitrate_stmode_count: Array<IntArray>?) {
        if (null == bitrate_stmode_count)
            return
        if (null == gfp)
            return
        val gfc = gfp.internal_flags ?: return

        if (gfp.free_format) {
            for (j in 0..13)
                for (i in 0..3)
                    bitrate_stmode_count[j][i] = 0
            for (i in 0..3)
                bitrate_stmode_count[0][i] = gfc.bitrate_stereoMode_Hist[0][i]
        } else {
            for (j in 0..13)
                for (i in 0..3)
                    bitrate_stmode_count[j][i] = gfc.bitrate_stereoMode_Hist[j + 1][i]
        }
    }

    fun lame_block_type_hist(gfp: LameGlobalFlags?,
                             btype_count: IntArray?) {
        if (null == btype_count)
            return
        if (null == gfp)
            return
        val gfc = gfp.internal_flags ?: return

        for (i in 0..5) {
            btype_count[i] = gfc.bitrate_blockType_Hist[15][i]
        }
    }

    fun lame_bitrate_block_type_hist(gfp: LameGlobalFlags?,
                                     bitrate_btype_count: Array<IntArray>?) {
        if (null == bitrate_btype_count)
            return
        if (null == gfp)
            return
        val gfc = gfp.internal_flags ?: return

        if (gfp.free_format) {
            for (j in 0..13)
                for (i in 0..5)
                    bitrate_btype_count[j][i] = 0
            for (i in 0..5)
                bitrate_btype_count[0][i] = gfc.bitrate_blockType_Hist[0][i]
        } else {
            for (j in 0..13)
                for (i in 0..5)
                    bitrate_btype_count[j][i] = gfc.bitrate_blockType_Hist[j + 1][i]
        }
    }

    companion object {

        const val LAME_ID: Long = -0x771c5

        /* presets */
        /* values from 8 to 320 should be reserved for abr bitrates */
        /* for abr I'd suggest to directly use the targeted bitrate as a value */

        const val V9 = 410
        const val V8 = 420
        const val V7 = 430
        const val V6 = 440
        const val V5 = 450
        const val V4 = 460
        const val V3 = 470
        const val V2 = 480
        const val V1 = 490
        const val V0 = 500

        /* still there for compatibility */

        const val R3MIX = 1000
        const val STANDARD = 1001
        const val EXTREME = 1002
        const val INSANE = 1003
        const val STANDARD_FAST = 1004
        const val EXTREME_FAST = 1005
        const val MEDIUM = 1006
        const val MEDIUM_FAST = 1007

        /**
         * maximum size of albumart image (128KB), which affects LAME_MAXMP3BUFFER
         * as well since lame_encode_buffer() also returns ID3v2 tag data
         */
        const val LAME_MAXALBUMART = 128 * 1024

        /**
         * maximum size of mp3buffer needed if you encode at most 1152 samples for
         * each call to lame_encode_buffer. see lame_encode_buffer() below
         * (LAME_MAXMP3BUFFER is now obsolete)
         */
        const val LAME_MAXMP3BUFFER = 16384 + LAME_MAXALBUMART

        private val LAME_DEFAULT_QUALITY = 3
    }

}
