/*
 *      quantize_pvt source file
 *
 *      Copyright (c) 1999-2002 Takehiro Tominaga
 *      Copyright (c) 2000-2002 Robert Hegemann
 *      Copyright (c) 2001 Naoki Shibata
 *      Copyright (c) 2002-2005 Gabriel Bouvigne
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

/* $Id: QuantizePVT.js,v 1.24 2011/05/24 20:48:06 kenchis Exp $ */
package mp3


class QuantizePVT {

    @JvmField
    internal var tak: Takehiro? = null
    @JvmField
    internal var rv: Reservoir? = null
    @JvmField
    internal var psy: PsyModel? = null

    /**
     * The following table is used to implement the scalefactor partitioning for
     * MPEG2 as described in section 2.4.3.2 of the IS. The indexing corresponds
     * to the way the tables are presented in the IS:
     *
     * [table_number][row_in_table][column of nr_of_sfb]
     */
    @JvmField
    val nr_of_sfb_block = arrayOf(arrayOf(intArrayOf(6, 5, 5, 5), intArrayOf(9, 9, 9, 9), intArrayOf(6, 9, 9, 9)), arrayOf(intArrayOf(6, 5, 7, 3), intArrayOf(9, 9, 12, 6), intArrayOf(6, 9, 12, 6)), arrayOf(intArrayOf(11, 10, 0, 0), intArrayOf(18, 18, 0, 0), intArrayOf(15, 18, 0, 0)), arrayOf(intArrayOf(7, 7, 7, 0), intArrayOf(12, 12, 12, 0), intArrayOf(6, 15, 12, 0)), arrayOf(intArrayOf(6, 6, 6, 3), intArrayOf(12, 9, 9, 6), intArrayOf(6, 12, 9, 6)), arrayOf(intArrayOf(8, 8, 5, 0), intArrayOf(15, 12, 9, 0), intArrayOf(6, 18, 9, 0)))

    /**
     * Table B.6: layer3 preemphasis
     */
    @JvmField
    val pretab = intArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 2, 2, 3, 3, 3, 2, 0)

    /**
     * Here are MPEG1 Table B.8 and MPEG2 Table B.1 -- Layer III scalefactor
     * bands. <BR></BR>
     * Index into this using a method such as:<BR></BR>
     * idx = fr_ps.header.sampling_frequency + (fr_ps.header.version * 3)
     */
    val sfBandIndex = arrayOf(
            // Table B.2.b: 22.05 kHz
            ScaleFac(intArrayOf(0, 6, 12, 18, 24, 30, 36, 44, 54, 66, 80, 96, 116, 140, 168, 200, 238, 284, 336, 396, 464, 522, 576),
                    intArrayOf(0, 4, 8, 12, 18, 24, 32, 42, 56, 74, 100, 132, 174, 192), intArrayOf(0, 0, 0, 0, 0, 0, 0) //  sfb21 pseudo sub bands
                    , intArrayOf(0, 0, 0, 0, 0, 0, 0) //  sfb12 pseudo sub bands
            ),
            /* Table B.2.c: 24 kHz */ /* docs: 332. mpg123(broken): 330 */
            ScaleFac(intArrayOf(0, 6, 12, 18, 24, 30, 36, 44, 54, 66, 80, 96, 114, 136, 162, 194, 232, 278, 332, 394, 464, 540, 576),
                    intArrayOf(0, 4, 8, 12, 18, 26, 36, 48, 62, 80, 104, 136, 180, 192), intArrayOf(0, 0, 0, 0, 0, 0, 0) /*  sfb21 pseudo sub bands */, intArrayOf(0, 0, 0, 0, 0, 0, 0) /*  sfb12 pseudo sub bands */
            ),
            /* Table B.2.a: 16 kHz */
            ScaleFac(intArrayOf(0, 6, 12, 18, 24, 30, 36, 44, 54, 66, 80, 96, 116, 140, 168, 200, 238, 284, 336, 396, 464, 522, 576),
                    intArrayOf(0, 4, 8, 12, 18, 26, 36, 48, 62, 80, 104, 134, 174, 192), intArrayOf(0, 0, 0, 0, 0, 0, 0) /*  sfb21 pseudo sub bands */, intArrayOf(0, 0, 0, 0, 0, 0, 0) /*  sfb12 pseudo sub bands */
            ),
            /* Table B.8.b: 44.1 kHz */
            ScaleFac(intArrayOf(0, 4, 8, 12, 16, 20, 24, 30, 36, 44, 52, 62, 74, 90, 110, 134, 162, 196, 238, 288, 342, 418, 576),
                    intArrayOf(0, 4, 8, 12, 16, 22, 30, 40, 52, 66, 84, 106, 136, 192), intArrayOf(0, 0, 0, 0, 0, 0, 0) /*  sfb21 pseudo sub bands */, intArrayOf(0, 0, 0, 0, 0, 0, 0) /*  sfb12 pseudo sub bands */
            ),
            /* Table B.8.c: 48 kHz */
            ScaleFac(intArrayOf(0, 4, 8, 12, 16, 20, 24, 30, 36, 42, 50, 60, 72, 88, 106, 128, 156, 190, 230, 276, 330, 384, 576),
                    intArrayOf(0, 4, 8, 12, 16, 22, 28, 38, 50, 64, 80, 100, 126, 192), intArrayOf(0, 0, 0, 0, 0, 0, 0) /*  sfb21 pseudo sub bands */, intArrayOf(0, 0, 0, 0, 0, 0, 0) /*  sfb12 pseudo sub bands */
            ),
            /* Table B.8.a: 32 kHz */
            ScaleFac(intArrayOf(0, 4, 8, 12, 16, 20, 24, 30, 36, 44, 54, 66, 82, 102, 126, 156, 194, 240, 296, 364, 448, 550, 576),
                    intArrayOf(0, 4, 8, 12, 16, 22, 30, 42, 58, 78, 104, 138, 180, 192), intArrayOf(0, 0, 0, 0, 0, 0, 0) /*  sfb21 pseudo sub bands */, intArrayOf(0, 0, 0, 0, 0, 0, 0) /*  sfb12 pseudo sub bands */
            ),
            /* MPEG-2.5 11.025 kHz */
            ScaleFac(intArrayOf(0, 6, 12, 18, 24, 30, 36, 44, 54, 66, 80, 96, 116, 140, 168, 200, 238, 284, 336, 396, 464, 522, 576),
                    intArrayOf(0 / 3, 12 / 3, 24 / 3, 36 / 3, 54 / 3, 78 / 3, 108 / 3, 144 / 3, 186 / 3, 240 / 3, 312 / 3, 402 / 3, 522 / 3, 576 / 3), intArrayOf(0, 0, 0, 0, 0, 0, 0) /*  sfb21 pseudo sub bands */, intArrayOf(0, 0, 0, 0, 0, 0, 0) /*  sfb12 pseudo sub bands */
            ),
            /* MPEG-2.5 12 kHz */
            ScaleFac(intArrayOf(0, 6, 12, 18, 24, 30, 36, 44, 54, 66, 80, 96, 116, 140, 168, 200, 238, 284, 336, 396, 464, 522, 576),
                    intArrayOf(0 / 3, 12 / 3, 24 / 3, 36 / 3, 54 / 3, 78 / 3, 108 / 3, 144 / 3, 186 / 3, 240 / 3, 312 / 3, 402 / 3, 522 / 3, 576 / 3), intArrayOf(0, 0, 0, 0, 0, 0, 0) /*  sfb21 pseudo sub bands */, intArrayOf(0, 0, 0, 0, 0, 0, 0) /*  sfb12 pseudo sub bands */
            ),
            /* MPEG-2.5 8 kHz */
            ScaleFac(intArrayOf(0, 12, 24, 36, 48, 60, 72, 88, 108, 132, 160, 192, 232, 280, 336, 400, 476, 566, 568, 570, 572, 574, 576),
                    intArrayOf(0 / 3, 24 / 3, 48 / 3, 72 / 3, 108 / 3, 156 / 3, 216 / 3, 288 / 3, 372 / 3, 480 / 3, 486 / 3, 492 / 3, 498 / 3, 576 / 3), intArrayOf(0, 0, 0, 0, 0, 0, 0) /*  sfb21 pseudo sub bands */, intArrayOf(0, 0, 0, 0, 0, 0, 0) /*  sfb12 pseudo sub bands */
            ))

    @JvmField
    var pow20 = FloatArray(Q_MAX + Q_MAX2 + 1)
    @JvmField
    var ipow20 = FloatArray(Q_MAX)
    @JvmField
    var pow43 = FloatArray(PRECALC_SIZE)

    @JvmField
    var adj43 = FloatArray(PRECALC_SIZE)

    fun setModules(tk: Takehiro, rv: Reservoir, psy: PsyModel) {
        this.tak = tk
        this.rv = rv
        this.psy = psy
    }

    fun POW20(x: Int): Float {
        assert(0 <= x + QuantizePVT.Q_MAX2 && x < QuantizePVT.Q_MAX)
        return pow20[x + QuantizePVT.Q_MAX2]
    }

    fun IPOW20(x: Int): Float {
        assert(0 <= x && x < QuantizePVT.Q_MAX)
        return ipow20[x]
    }

    /**
     * <PRE>
     * compute the ATH for each scalefactor band cd range: 0..96db
     *
     * Input: 3.3kHz signal 32767 amplitude (3.3kHz is where ATH is smallest =
     * -5db) longblocks: sfb=12 en0/bw=-11db max_en0 = 1.3db shortblocks: sfb=5
     * -9db 0db
     *
     * Input: 1 1 1 1 1 1 1 -1 -1 -1 -1 -1 -1 -1 (repeated) longblocks: amp=1
     * sfb=12 en0/bw=-103 db max_en0 = -92db amp=32767 sfb=12 -12 db -1.4db
     *
     * Input: 1 1 1 1 1 1 1 -1 -1 -1 -1 -1 -1 -1 (repeated) shortblocks: amp=1
     * sfb=5 en0/bw= -99 -86 amp=32767 sfb=5 -9 db 4db
     *
     *
     * MAX energy of largest wave at 3.3kHz = 1db AVE energy of largest wave at
     * 3.3kHz = -11db Let's take AVE: -11db = maximum signal in sfb=12. Dynamic
     * range of CD: 96db. Therefor energy of smallest audible wave in sfb=12 =
     * -11 - 96 = -107db = ATH at 3.3kHz.
     *
     * ATH formula for this wave: -5db. To adjust to LAME scaling, we need ATH =
     * ATH_formula - 103 (db) ATH = ATH * 2.5e-10 (ener)
    </PRE> *
     */
    private fun ATHmdct(gfp: LameGlobalFlags, f: Float): Float {
        var ath = psy!!.ATHformula(f, gfp)

        ath -= NSATHSCALE.toFloat()

        /* modify the MDCT scaling for the ATH and convert to energy */
        ath = Math.pow(10.0, ath / 10.0 + gfp.ATHlower).toFloat()
        return ath
    }

    private fun compute_ath(gfp: LameGlobalFlags) {
        val ATH_l = gfp.internal_flags.ATH.l
        val ATH_psfb21 = gfp.internal_flags.ATH.psfb21
        val ATH_s = gfp.internal_flags.ATH.s
        val ATH_psfb12 = gfp.internal_flags.ATH.psfb12
        val gfc = gfp.internal_flags
        val samp_freq = gfp.out_samplerate.toFloat()

        for (sfb in 0 until Encoder.SBMAX_l) {
            val start = gfc.scalefac_band.l[sfb]
            val end = gfc.scalefac_band.l[sfb + 1]
            ATH_l[sfb] = java.lang.Float.MAX_VALUE
            for (i in start until end) {
                val freq = i * samp_freq / (2 * 576)
                val ATH_f = ATHmdct(gfp, freq) /* freq in kHz */
                ATH_l[sfb] = Math.min(ATH_l[sfb], ATH_f)
            }
        }

        for (sfb in 0 until Encoder.PSFB21) {
            val start = gfc.scalefac_band.psfb21[sfb]
            val end = gfc.scalefac_band.psfb21[sfb + 1]
            ATH_psfb21[sfb] = java.lang.Float.MAX_VALUE
            for (i in start until end) {
                val freq = i * samp_freq / (2 * 576)
                val ATH_f = ATHmdct(gfp, freq) /* freq in kHz */
                ATH_psfb21[sfb] = Math.min(ATH_psfb21[sfb], ATH_f)
            }
        }

        for (sfb in 0 until Encoder.SBMAX_s) {
            val start = gfc.scalefac_band.s[sfb]
            val end = gfc.scalefac_band.s[sfb + 1]
            ATH_s[sfb] = java.lang.Float.MAX_VALUE
            for (i in start until end) {
                val freq = i * samp_freq / (2 * 192)
                val ATH_f = ATHmdct(gfp, freq) /* freq in kHz */
                ATH_s[sfb] = Math.min(ATH_s[sfb], ATH_f)
            }
            ATH_s[sfb] *= (gfc.scalefac_band.s[sfb + 1] - gfc.scalefac_band.s[sfb]).toFloat()
        }

        for (sfb in 0 until Encoder.PSFB12) {
            val start = gfc.scalefac_band.psfb12[sfb]
            val end = gfc.scalefac_band.psfb12[sfb + 1]
            ATH_psfb12[sfb] = java.lang.Float.MAX_VALUE
            for (i in start until end) {
                val freq = i * samp_freq / (2 * 192)
                val ATH_f = ATHmdct(gfp, freq) /* freq in kHz */
                ATH_psfb12[sfb] = Math.min(ATH_psfb12[sfb], ATH_f)
            }
            /* not sure about the following */
            ATH_psfb12[sfb] *= (gfc.scalefac_band.s[13] - gfc.scalefac_band.s[12]).toFloat()
        }

        /*
		 * no-ATH mode: reduce ATH to -200 dB
		 */
        if (gfp.noATH) {
            for (sfb in 0 until Encoder.SBMAX_l) {
                ATH_l[sfb] = 1E-20f
            }
            for (sfb in 0 until Encoder.PSFB21) {
                ATH_psfb21[sfb] = 1E-20f
            }
            for (sfb in 0 until Encoder.SBMAX_s) {
                ATH_s[sfb] = 1E-20f
            }
            for (sfb in 0 until Encoder.PSFB12) {
                ATH_psfb12[sfb] = 1E-20f
            }
        }

        /*
		 * work in progress, don't rely on it too much
		 */
        gfc.ATH.floor = 10f * Math.log10(ATHmdct(gfp, -1f).toDouble()).toFloat()
    }

    /**
     * initialization for iteration_loop
     */
    fun iteration_init(gfp: LameGlobalFlags) {
        val gfc = gfp.internal_flags
        val l3_side = gfc.l3_side
        var i: Int

        if (gfc.iteration_init_init == 0) {
            gfc.iteration_init_init = 1

            l3_side.main_data_begin = 0
            compute_ath(gfp)

            pow43[0] = 0.0f
            i = 1
            while (i < PRECALC_SIZE) {
                pow43[i] = Math.pow(i.toFloat().toDouble(), 4.0 / 3.0).toFloat()
                i++
            }

            i = 0
            while (i < PRECALC_SIZE - 1) {
                adj43[i] = (i + 1 - Math.pow(
                        0.5 * (pow43[i] + pow43[i + 1]), 0.75)).toFloat()
                i++
            }
            adj43[i] = 0.5f

            i = 0
            while (i < Q_MAX) {
                ipow20[i] = Math.pow(2.0, (i - 210) * -0.1875).toFloat()
                i++
            }
            i = 0
            while (i <= Q_MAX + Q_MAX2) {
                pow20[i] = Math.pow(2.0, (i - 210 - Q_MAX2) * 0.25).toFloat()
                i++
            }

            tak!!.huffman_init(gfc)

            run {
                val bass: Float
                val alto: Float
                val treble: Float
                val sfb21: Float

                i = gfp.exp_nspsytune shr 2 and 63
                if (i >= 32)
                    i -= 64
                bass = Math.pow(10.0, i.toDouble() / 4.0 / 10.0).toFloat()

                i = gfp.exp_nspsytune shr 8 and 63
                if (i >= 32)
                    i -= 64
                alto = Math.pow(10.0, i.toDouble() / 4.0 / 10.0).toFloat()

                i = gfp.exp_nspsytune shr 14 and 63
                if (i >= 32)
                    i -= 64
                treble = Math.pow(10.0, i.toDouble() / 4.0 / 10.0).toFloat()

                /*
				 * to be compatible with Naoki's original code, the next 6 bits
				 * define only the amount of changing treble for sfb21
				 */
                i = gfp.exp_nspsytune shr 20 and 63
                if (i >= 32)
                    i -= 64
                sfb21 = treble * Math.pow(10.0, i.toDouble() / 4.0 / 10.0).toFloat()
                i = 0
                while (i < Encoder.SBMAX_l) {
                    val f: Float
                    if (i <= 6)
                        f = bass
                    else if (i <= 13)
                        f = alto
                    else if (i <= 20)
                        f = treble
                    else
                        f = sfb21

                    gfc.nsPsy.longfact[i] = f
                    i++
                }
                i = 0
                while (i < Encoder.SBMAX_s) {
                    val f: Float
                    if (i <= 5)
                        f = bass
                    else if (i <= 10)
                        f = alto
                    else if (i <= 11)
                        f = treble
                    else
                        f = sfb21

                    gfc.nsPsy.shortfact[i] = f
                    i++
                }
            }
        }
    }

    /**
     * allocate bits among 2 channels based on PE<BR></BR>
     * mt 6/99<BR></BR>
     * bugfixes rh 8/01: often allocated more than the allowed 4095 bits
     */
    fun on_pe(gfp: LameGlobalFlags, pe: Array<FloatArray>,
              targ_bits: IntArray, mean_bits: Int, gr: Int, cbr: Int): Int {
        val gfc = gfp.internal_flags
        var tbits = 0
        var bits: Int
        val add_bits = IntArray(2)
        var ch: Int

        /* allocate targ_bits for granule */
        val mb = MeanBits(tbits)
        var extra_bits = rv!!.ResvMaxBits(gfp, mean_bits, mb, cbr)
        tbits = mb.bits
        /* maximum allowed bits for this granule */
        var max_bits = tbits + extra_bits
        if (max_bits > LameInternalFlags.MAX_BITS_PER_GRANULE) {
            // hard limit per granule
            max_bits = LameInternalFlags.MAX_BITS_PER_GRANULE
        }
        bits = 0
        ch = 0
        while (ch < gfc.channels_out) {
            /******************************************************************
             * allocate bits for each channel
             */
            targ_bits[ch] = Math.min(LameInternalFlags.MAX_BITS_PER_CHANNEL,
                    tbits / gfc.channels_out)

            add_bits[ch] = (targ_bits[ch] * pe[gr][ch] / 700.0 - targ_bits[ch]).toInt()

            /* at most increase bits by 1.5*average */
            if (add_bits[ch] > mean_bits * 3 / 4)
                add_bits[ch] = mean_bits * 3 / 4
            if (add_bits[ch] < 0)
                add_bits[ch] = 0

            if (add_bits[ch] + targ_bits[ch] > LameInternalFlags.MAX_BITS_PER_CHANNEL)
                add_bits[ch] = Math.max(0,
                        LameInternalFlags.MAX_BITS_PER_CHANNEL - targ_bits[ch])

            bits += add_bits[ch]
            ++ch
        }
        if (bits > extra_bits) {
            ch = 0
            while (ch < gfc.channels_out) {
                add_bits[ch] = extra_bits * add_bits[ch] / bits
                ++ch
            }
        }

        ch = 0
        while (ch < gfc.channels_out) {
            targ_bits[ch] += add_bits[ch]
            extra_bits -= add_bits[ch]
            ++ch
        }

        bits = 0
        ch = 0
        while (ch < gfc.channels_out) {
            bits += targ_bits[ch]
            ++ch
        }
        if (bits > LameInternalFlags.MAX_BITS_PER_GRANULE) {
            var sum = 0
            ch = 0
            while (ch < gfc.channels_out) {
                targ_bits[ch] *= LameInternalFlags.MAX_BITS_PER_GRANULE
                targ_bits[ch] /= bits
                sum += targ_bits[ch]
                ++ch
            }
            assert(sum <= LameInternalFlags.MAX_BITS_PER_GRANULE)
        }

        return max_bits
    }

    fun reduce_side(targ_bits: IntArray,
                    ms_ener_ratio: Float, mean_bits: Int, max_bits: Int) {
        assert(max_bits <= LameInternalFlags.MAX_BITS_PER_GRANULE)
        assert(targ_bits[0] + targ_bits[1] <= LameInternalFlags.MAX_BITS_PER_GRANULE)

        /*
		 * ms_ener_ratio = 0: allocate 66/33 mid/side fac=.33 ms_ener_ratio =.5:
		 * allocate 50/50 mid/side fac= 0
		 */
        /* 75/25 split is fac=.5 */
        var fac = .33f * (.5f - ms_ener_ratio) / .5f
        if (fac < 0)
            fac = 0f
        if (fac > .5)
            fac = .5f

        /* number of bits to move from side channel to mid channel */
        /* move_bits = fac*targ_bits[1]; */
        var move_bits = (fac.toDouble() * .5 * (targ_bits[0] + targ_bits[1]).toDouble()).toInt()

        if (move_bits > LameInternalFlags.MAX_BITS_PER_CHANNEL - targ_bits[0]) {
            move_bits = LameInternalFlags.MAX_BITS_PER_CHANNEL - targ_bits[0]
        }
        if (move_bits < 0)
            move_bits = 0

        if (targ_bits[1] >= 125) {
            /* dont reduce side channel below 125 bits */
            if (targ_bits[1] - move_bits > 125) {

                /* if mid channel already has 2x more than average, dont bother */
                /* mean_bits = bits per granule (for both channels) */
                if (targ_bits[0] < mean_bits)
                    targ_bits[0] += move_bits
                targ_bits[1] -= move_bits
            } else {
                targ_bits[0] += targ_bits[1] - 125
                targ_bits[1] = 125
            }
        }

        move_bits = targ_bits[0] + targ_bits[1]
        if (move_bits > max_bits) {
            targ_bits[0] = max_bits * targ_bits[0] / move_bits
            targ_bits[1] = max_bits * targ_bits[1] / move_bits
        }
        assert(targ_bits[0] <= LameInternalFlags.MAX_BITS_PER_CHANNEL)
        assert(targ_bits[1] <= LameInternalFlags.MAX_BITS_PER_CHANNEL)
        assert(targ_bits[0] + targ_bits[1] <= LameInternalFlags.MAX_BITS_PER_GRANULE)
    }

    /**
     * Robert Hegemann 2001-04-27:
     * this adjusts the ATH, keeping the original noise floor
     * affects the higher frequencies more than the lower ones
     */
    fun athAdjust(a: Float, x: Float,
                  athFloor: Float): Float {
        /*
		 * work in progress
		 */
        val o = 90.30873362f
        val p = 94.82444863f
        var u = Util.FAST_LOG10_X(x, 10.0f)
        val v = a * a
        var w = 0.0f
        u -= athFloor /* undo scaling */
        if (v > 1E-20)
            w = 1f + Util.FAST_LOG10_X(v, 10.0f / o)
        if (w < 0)
            w = 0f
        u *= w
        u += athFloor + o - p /* redo scaling */

        return Math.pow(10.0, 0.1 * u).toFloat()
    }

    /**
     * Calculate the allowed distortion for each scalefactor band, as determined
     * by the psychoacoustic model. xmin(sb) = ratio(sb) * en(sb) / bw(sb)
     *
     * returns number of sfb's with energy > ATH
     */
    fun calc_xmin(gfp: LameGlobalFlags,
                  ratio: III_psy_ratio, cod_info: GrInfo,
                  pxmin: FloatArray): Int {
        var pxminPos = 0
        val gfc = gfp.internal_flags
        var gsfb: Int
        var j = 0
        var ath_over = 0
        val ATH = gfc.ATH
        val xr = cod_info.xr
        val enable_athaa_fix = if (gfp.VBR == VbrMode.vbr_mtrh) 1 else 0
        var masking_lower = gfc.masking_lower

        if (gfp.VBR == VbrMode.vbr_mtrh || gfp.VBR == VbrMode.vbr_mt) {
            /* was already done in PSY-Model */
            masking_lower = 1.0f
        }

        gsfb = 0
        while (gsfb < cod_info.psy_lmax) {
            var en0: Float
            var xmin: Float
            val rh1: Float
            var rh2: Float
            val width: Int
            var l: Int

            if (gfp.VBR == VbrMode.vbr_rh || gfp.VBR == VbrMode.vbr_mtrh)
                xmin = athAdjust(ATH.adjust, ATH.l[gsfb], ATH.floor)
            else
                xmin = ATH.adjust * ATH.l[gsfb]

            width = cod_info.width[gsfb]
            rh1 = xmin / width
            rh2 = DBL_EPSILON
            l = width shr 1
            en0 = 0.0f
            do {
                val xa: Float
                val xb: Float
                xa = xr[j] * xr[j]
                en0 += xa
                rh2 += if (xa < rh1) xa else rh1
                j++
                xb = xr[j] * xr[j]
                en0 += xb
                rh2 += if (xb < rh1) xb else rh1
                j++
            } while (--l > 0)
            if (en0 > xmin)
                ath_over++

            if (gsfb == Encoder.SBPSY_l) {
                val x = xmin * gfc.nsPsy.longfact[gsfb]
                if (rh2 < x) {
                    rh2 = x
                }
            }
            if (enable_athaa_fix != 0) {
                xmin = rh2
            }
            if (!gfp.ATHonly) {
                val e = ratio.en.l[gsfb]
                if (e > 0.0f) {
                    var x: Float
                    x = en0 * ratio.thm.l[gsfb] * masking_lower / e
                    if (enable_athaa_fix != 0)
                        x *= gfc.nsPsy.longfact[gsfb]
                    if (xmin < x)
                        xmin = x
                }
            }
            if (enable_athaa_fix != 0)
                pxmin[pxminPos++] = xmin
            else
                pxmin[pxminPos++] = xmin * gfc.nsPsy.longfact[gsfb]
            gsfb++
        } /* end of long block loop */

        /* use this function to determine the highest non-zero coeff */
        var max_nonzero = 575
        if (cod_info.block_type != Encoder.SHORT_TYPE) {
            // NORM, START or STOP type, but not SHORT
            var k = 576
            while (k-- != 0 && BitStream.EQ(xr[k], 0f)) {
                max_nonzero = k
            }
        }
        cod_info.max_nonzero_coeff = max_nonzero

        var sfb = cod_info.sfb_smin
        while (gsfb < cod_info.psymax) {
            val width: Int
            var b: Int
            val tmpATH: Float
            if (gfp.VBR == VbrMode.vbr_rh || gfp.VBR == VbrMode.vbr_mtrh)
                tmpATH = athAdjust(ATH.adjust, ATH.s[sfb], ATH.floor)
            else
                tmpATH = ATH.adjust * ATH.s[sfb]

            width = cod_info.width[gsfb]
            b = 0
            while (b < 3) {
                var en0 = 0.0f
                var xmin: Float
                val rh1: Float
                var rh2: Float
                var l = width shr 1

                rh1 = tmpATH / width
                rh2 = DBL_EPSILON
                do {
                    val xa: Float
                    val xb: Float
                    xa = xr[j] * xr[j]
                    en0 += xa
                    rh2 += if (xa < rh1) xa else rh1
                    j++
                    xb = xr[j] * xr[j]
                    en0 += xb
                    rh2 += if (xb < rh1) xb else rh1
                    j++
                } while (--l > 0)
                if (en0 > tmpATH)
                    ath_over++
                if (sfb == Encoder.SBPSY_s) {
                    val x = tmpATH * gfc.nsPsy.shortfact[sfb]
                    if (rh2 < x) {
                        rh2 = x
                    }
                }
                if (enable_athaa_fix != 0)
                    xmin = rh2
                else
                    xmin = tmpATH

                if (!gfp.ATHonly && !gfp.ATHshort) {
                    val e = ratio.en.s[sfb][b]
                    if (e > 0.0f) {
                        var x: Float
                        x = en0 * ratio.thm.s[sfb][b] * masking_lower / e
                        if (enable_athaa_fix != 0)
                            x *= gfc.nsPsy.shortfact[sfb]
                        if (xmin < x)
                            xmin = x
                    }
                }
                if (enable_athaa_fix != 0)
                    pxmin[pxminPos++] = xmin
                else
                    pxmin[pxminPos++] = xmin * gfc.nsPsy.shortfact[sfb]
                b++
            } /* b */
            if (gfp.useTemporal) {
                if (pxmin[pxminPos - 3] > pxmin[pxminPos - 3 + 1])
                    pxmin[pxminPos - 3 + 1] += (pxmin[pxminPos - 3] - pxmin[pxminPos - 3 + 1]) * gfc.decay
                if (pxmin[pxminPos - 3 + 1] > pxmin[pxminPos - 3 + 2])
                    pxmin[pxminPos - 3 + 2] += (pxmin[pxminPos - 3 + 1] - pxmin[pxminPos - 3 + 2]) * gfc.decay
            }
            sfb++
            gsfb += 3
        } /* end of short block sfb loop */

        return ath_over
    }

    class StartLine(@JvmField var s: Int)

    private fun calc_noise_core(cod_info: GrInfo,
                                startline: StartLine, l: Int, step: Float): Float {
        var l = l
        var noise = 0f
        var j = startline.s
        val ix = cod_info.l3_enc

        if (j > cod_info.count1) {
            while (l-- != 0) {
                var temp: Float
                temp = cod_info.xr[j]
                j++
                noise += temp * temp
                temp = cod_info.xr[j]
                j++
                noise += temp * temp
            }
        } else if (j > cod_info.big_values) {
            val ix01 = FloatArray(2)
            ix01[0] = 0f
            ix01[1] = step
            while (l-- != 0) {
                var temp: Float
                temp = Math.abs(cod_info.xr[j]) - ix01[ix[j]]
                j++
                noise += temp * temp
                temp = Math.abs(cod_info.xr[j]) - ix01[ix[j]]
                j++
                noise += temp * temp
            }
        } else {
            while (l-- != 0) {
                var temp: Float
                temp = Math.abs(cod_info.xr[j]) - pow43[ix[j]] * step
                j++
                noise += temp * temp
                temp = Math.abs(cod_info.xr[j]) - pow43[ix[j]] * step
                j++
                noise += temp * temp
            }
        }

        startline.s = j
        return noise
    }

    /**
     * <PRE>
     * -oo dB  =>  -1.00
     * - 6 dB  =>  -0.97
     * - 3 dB  =>  -0.80
     * - 2 dB  =>  -0.64
     * - 1 dB  =>  -0.38
     * 0 dB  =>   0.00
     * + 1 dB  =>  +0.49
     * + 2 dB  =>  +1.06
     * + 3 dB  =>  +1.68
     * + 6 dB  =>  +3.69
     * +10 dB  =>  +6.45
    </PRE> *
     */
    fun calc_noise(cod_info: GrInfo,
                   l3_xmin: FloatArray, distort: FloatArray,
                   res: CalcNoiseResult, prev_noise: CalcNoiseData?): Int {
        var distortPos = 0
        var l3_xminPos = 0
        var sfb: Int
        var l: Int
        var over = 0
        var over_noise_db = 0f
        /* 0 dB relative to masking */
        var tot_noise_db = 0f
        /* -200 dB relative to masking */
        var max_noise = -20.0f
        var j = 0
        val scalefac = cod_info.scalefac
        var scalefacPos = 0

        res.over_SSD = 0

        sfb = 0
        while (sfb < cod_info.psymax) {
            val x = if (cod_info.preflag != 0) this.pretab[sfb] else 0
            val s = (cod_info.global_gain
                    - (scalefac[scalefacPos++] + x shl cod_info.scalefac_scale + 1)
                    - cod_info.subblock_gain[cod_info.window[sfb]] * 8)
            var noise = 0.0f

            if (prev_noise != null && prev_noise.step[sfb] == s) {

                /* use previously computed values */
                noise = prev_noise.noise[sfb]
                j += cod_info.width[sfb]
                distort[distortPos++] = noise / l3_xmin[l3_xminPos++]

                noise = prev_noise.noise_log[sfb]

            } else {
                val step = POW20(s)
                l = cod_info.width[sfb] shr 1

                if (j + cod_info.width[sfb] > cod_info.max_nonzero_coeff) {
                    val usefullsize: Int
                    usefullsize = cod_info.max_nonzero_coeff - j + 1

                    if (usefullsize > 0)
                        l = usefullsize shr 1
                    else
                        l = 0
                }

                val sl = StartLine(j)
                noise = calc_noise_core(cod_info, sl, l, step)
                j = sl.s

                if (prev_noise != null) {
                    /* save noise values */
                    prev_noise.step[sfb] = s
                    prev_noise.noise[sfb] = noise
                }

                val n1 = noise / l3_xmin[l3_xminPos++]
                distort[distortPos++] = n1
                noise = n1

                /* multiplying here is adding in dB, but can overflow */
                noise = Util.FAST_LOG10(Math.max(noise.toDouble(), 1E-20).toFloat())

                if (prev_noise != null) {
                    /* save noise values */
                    prev_noise.noise_log[sfb] = noise
                }
            }

            if (prev_noise != null) {
                /* save noise values */
                prev_noise.global_gain = cod_info.global_gain
            }

            tot_noise_db += noise

            if (noise > 0.0) {
                val tmp: Int

                tmp = Math.max((noise * 10 + .5).toInt(), 1)
                res.over_SSD += tmp * tmp

                over++
                /* multiplying here is adding in dB -but can overflow */
                /* over_noise *= noise; */
                over_noise_db += noise
            }
            max_noise = Math.max(max_noise, noise)
            sfb++

        }

        res.over_count = over
        res.tot_noise = tot_noise_db
        res.over_noise = over_noise_db
        res.max_noise = max_noise

        return over
    }

    /**
     * updates plotting data
     *
     * Mark Taylor 2000-??-??
     *
     * Robert Hegemann: moved noise/distortion calc into it
     */
    private fun set_pinfo(gfp: LameGlobalFlags,
                          cod_info: GrInfo, ratio: III_psy_ratio,
                          gr: Int, ch: Int) {
        val gfc = gfp.internal_flags
        var sfb: Int
        var sfb2: Int
        var l: Int
        var en0: Float
        var en1: Float
        val ifqstep = if (cod_info.scalefac_scale == 0) .5f else 1.0f
        val scalefac = cod_info.scalefac

        val l3_xmin = FloatArray(L3Side.SFBMAX)
        val xfsf = FloatArray(L3Side.SFBMAX)
        val noise = CalcNoiseResult()

        calc_xmin(gfp, ratio, cod_info, l3_xmin)
        calc_noise(cod_info, l3_xmin, xfsf, noise, null)

        var j = 0
        sfb2 = cod_info.sfb_lmax
        if (cod_info.block_type != Encoder.SHORT_TYPE && 0 == cod_info.mixed_block_flag)
            sfb2 = 22
        sfb = 0
        while (sfb < sfb2) {
            val start = gfc.scalefac_band.l[sfb]
            val end = gfc.scalefac_band.l[sfb + 1]
            val bw = end - start
            en0 = 0.0f
            while (j < end) {
                en0 += cod_info.xr[j] * cod_info.xr[j]
                j++
            }
            en0 /= bw.toFloat()
            /* convert to MDCT units */
            /* scaling so it shows up on FFT plot */
            en1 = 1e15f
            gfc.pinfo.en[gr][ch][sfb] = (en1 * en0).toDouble()
            gfc.pinfo.xfsf[gr][ch][sfb] = (en1 * l3_xmin[sfb] * xfsf[sfb] / bw).toDouble()

            if (ratio.en.l[sfb] > 0 && !gfp.ATHonly)
                en0 = en0 / ratio.en.l[sfb]
            else
                en0 = 0.0f

            gfc.pinfo.thr[gr][ch][sfb] = (en1 * Math.max(en0 * ratio.thm.l[sfb], gfc.ATH.l[sfb])).toDouble()

            /* there is no scalefactor bands >= SBPSY_l */
            gfc.pinfo.LAMEsfb[gr][ch][sfb] = 0.0
            if (cod_info.preflag != 0 && sfb >= 11)
                gfc.pinfo.LAMEsfb[gr][ch][sfb] = (-ifqstep * pretab[sfb]).toDouble()

            if (sfb < Encoder.SBPSY_l) {
                /* scfsi should be decoded by caller side */
                assert(scalefac[sfb] >= 0)
                gfc.pinfo.LAMEsfb[gr][ch][sfb] -= (ifqstep * scalefac[sfb]).toDouble()
            }
            sfb++
        } /* for sfb */

        if (cod_info.block_type == Encoder.SHORT_TYPE) {
            sfb2 = sfb
            sfb = cod_info.sfb_smin
            while (sfb < Encoder.SBMAX_s) {
                val start = gfc.scalefac_band.s[sfb]
                val end = gfc.scalefac_band.s[sfb + 1]
                val bw = end - start
                for (i in 0..2) {
                    en0 = 0.0f
                    l = start
                    while (l < end) {
                        en0 += cod_info.xr[j] * cod_info.xr[j]
                        j++
                        l++
                    }
                    en0 = Math.max((en0 / bw).toDouble(), 1e-20).toFloat()
                    /* convert to MDCT units */
                    /* scaling so it shows up on FFT plot */
                    en1 = 1e15f

                    gfc.pinfo.en_s[gr][ch][3 * sfb + i] = (en1 * en0).toDouble()
                    gfc.pinfo.xfsf_s[gr][ch][3 * sfb + i] = ((en1 * l3_xmin[sfb2]
                            * xfsf[sfb2]) / bw).toDouble()
                    if (ratio.en.s[sfb][i] > 0)
                        en0 = en0 / ratio.en.s[sfb][i]
                    else
                        en0 = 0.0f
                    if (gfp.ATHonly || gfp.ATHshort)
                        en0 = 0f

                    gfc.pinfo.thr_s[gr][ch][3 * sfb + i] = (en1 * Math.max(en0 * ratio.thm.s[sfb][i],
                            gfc.ATH.s[sfb])).toDouble()

                    /* there is no scalefactor bands >= SBPSY_s */
                    gfc.pinfo.LAMEsfb_s[gr][ch][3 * sfb + i] = -2.0 * cod_info.subblock_gain[i]
                    if (sfb < Encoder.SBPSY_s) {
                        gfc.pinfo.LAMEsfb_s[gr][ch][3 * sfb + i] -= (ifqstep * scalefac[sfb2]).toDouble()
                    }
                    sfb2++
                }
                sfb++
            }
        } /* block type short */
        gfc.pinfo.LAMEqss[gr][ch] = cod_info.global_gain
        gfc.pinfo.LAMEmainbits[gr][ch] = cod_info.part2_3_length + cod_info.part2_length
        gfc.pinfo.LAMEsfbits[gr][ch] = cod_info.part2_length

        gfc.pinfo.over[gr][ch] = noise.over_count
        gfc.pinfo.max_noise[gr][ch] = noise.max_noise * 10.0
        gfc.pinfo.over_noise[gr][ch] = noise.over_noise * 10.0
        gfc.pinfo.tot_noise[gr][ch] = noise.tot_noise * 10.0
        gfc.pinfo.over_SSD[gr][ch] = noise.over_SSD
    }

    /**
     * updates plotting data for a whole frame
     *
     * Robert Hegemann 2000-10-21
     */
    fun set_frame_pinfo(gfp: LameGlobalFlags,
                        ratio: Array<Array<III_psy_ratio>>) {
        val gfc = gfp.internal_flags

        gfc.masking_lower = 1.0f

        /*
		 * for every granule and channel patch l3_enc and set info
		 */
        for (gr in 0 until gfc.mode_gr) {
            for (ch in 0 until gfc.channels_out) {
                val cod_info = gfc.l3_side.tt[gr][ch]
                val scalefac_sav = IntArray(L3Side.SFBMAX)
                System.arraycopy(cod_info.scalefac, 0, scalefac_sav, 0,
                        scalefac_sav.size)

                /*
				 * reconstruct the scalefactors in case SCFSI was used
				 */
                if (gr == 1) {
                    var sfb: Int
                    sfb = 0
                    while (sfb < cod_info.sfb_lmax) {
                        if (cod_info.scalefac[sfb] < 0)
                        /* scfsi */
                            cod_info.scalefac[sfb] = gfc.l3_side.tt[0][ch].scalefac[sfb]
                        sfb++
                    }
                }

                set_pinfo(gfp, cod_info, ratio[gr][ch], gr, ch)
                System.arraycopy(scalefac_sav, 0, cod_info.scalefac, 0,
                        scalefac_sav.size)
            } /* for ch */
        } /* for gr */
    }

    companion object {

        /**
         * smallest such that 1.0+DBL_EPSILON != 1.0
         */
        private val DBL_EPSILON = 2.2204460492503131e-016f

        /**
         * ix always <= 8191+15. see count_bits()
         */
        const val IXMAX_VAL = 8206

        private val PRECALC_SIZE = IXMAX_VAL + 2

        private val Q_MAX = 256 + 1

        /**
         * <CODE>
         * minimum possible number of
         * -cod_info.global_gain + ((scalefac[] + (cod_info.preflag ? pretab[sfb] : 0))
         * << (cod_info.scalefac_scale + 1)) + cod_info.subblock_gain[cod_info.window[sfb]] * 8;
         *
         * for long block, 0+((15+3)<<2) = 18*4 = 72
         * for short block, 0+(15<<2)+7*8 = 15*4+56 = 116
        </CODE> *
         */
        const val Q_MAX2 = 116

        const val LARGE_BITS = 100000

        /**
         * Assuming dynamic range=96dB, this value should be 92
         */
        private val NSATHSCALE = 100
    }

}
