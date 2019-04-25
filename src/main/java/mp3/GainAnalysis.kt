/*
 *  ReplayGainAnalysis - analyzes input samples and give the recommended dB change
 *  Copyright (C) 2001 David Robinson and Glen Sawyer
 *  Improvements and optimizations added by Frank Klemm, and by Marcel Muller 
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 2.1 of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 *
 *  concept and filter values by David Robinson (David@Robinson.org)
 *    -- blame him if you think the idea is flawed
 *  original coding by Glen Sawyer (mp3gain@hotmail.com)
 *    -- blame him if you think this runs too slowly, or the coding is otherwise flawed
 *
 *  lots of code improvements by Frank Klemm ( http://www.uni-jena.de/~pfk/mpp/ )
 *    -- credit him for all the _good_ programming ;)
 *
 *
 *  For an explanation of the concepts and the basic algorithms involved, go to:
 *    http://www.replaygain.org/
 */

/*
 *  Here's the deal. Call
 *
 *    InitGainAnalysis ( long samplefreq );
 *
 *  to initialize everything. Call
 *
 *    AnalyzeSamples ( const Float_t*  left_samples,
 *                     const Float_t*  right_samples,
 *                     size_t          num_samples,
 *                     int             num_channels );
 *
 *  as many times as you want, with as many or as few samples as you want.
 *  If mono, pass the sample buffer in through left_samples, leave
 *  right_samples NULL, and make sure num_channels = 1.
 *
 *    GetTitleGain()
 *
 *  will return the recommended dB level change for all samples analyzed
 *  SINCE THE LAST TIME you called GetTitleGain() OR InitGainAnalysis().
 *
 *    GetAlbumGain()
 *
 *  will return the recommended dB level change for all samples analyzed
 *  since InitGainAnalysis() was called and finalized with GetTitleGain().
 *
 *  Pseudo-code to process an album:
 *
 *    Float_t       l_samples [4096];
 *    Float_t       r_samples [4096];
 *    size_t        num_samples;
 *    unsigned int  num_songs;
 *    unsigned int  i;
 *
 *    InitGainAnalysis ( 44100 );
 *    for ( i = 1; i <= num_songs; i++ ) {
 *        while ( ( num_samples = getSongSamples ( song[i], left_samples, right_samples ) ) > 0 )
 *            AnalyzeSamples ( left_samples, right_samples, num_samples, 2 );
 *        fprintf ("Recommended dB change for song %2d: %+6.2f dB\n", i, GetTitleGain() );
 *    }
 *    fprintf ("Recommended dB change for whole album: %+6.2f dB\n", GetAlbumGain() );
 */

/*
 *  So here's the main source of potential code confusion:
 *
 *  The filters applied to the incoming samples are IIR filters,
 *  meaning they rely on up to <filter order> number of previous samples
 *  AND up to <filter order> number of previous filtered samples.
 *
 *  I set up the AnalyzeSamples routine to minimize memory usage and interface
 *  complexity. The speed isn't compromised too much (I don't think), but the
 *  internal complexity is higher than it should be for such a relatively
 *  simple routine.
 *
 *  Optimization/clarity suggestions are welcome.
 */
package mp3

import java.util.Arrays

class GainAnalysis {


    /**
     * When calling this procedure, make sure that ip[-order] and op[-order]
     * point to real data
     */
    private fun filterYule(input: FloatArray, inputPos: Int, output: FloatArray,
                           outputPos: Int, nSamples: Int, kernel: FloatArray) {
        var inputPos = inputPos
        var outputPos = outputPos
        var nSamples = nSamples

        while (nSamples-- != 0) {
            /* 1e-10 is a hack to avoid slowdown because of denormals */
            output[outputPos] = 1e-10f + input[inputPos + 0] * kernel[0] - output[outputPos - 1] * kernel[1] + input[inputPos - 1] * kernel[2] - output[outputPos - 2] * kernel[3] + input[inputPos - 2] * kernel[4] - output[outputPos - 3] * kernel[5] + input[inputPos - 3] * kernel[6] - output[outputPos - 4] * kernel[7] + input[inputPos - 4] * kernel[8] - output[outputPos - 5] * kernel[9] + input[inputPos - 5] * kernel[10] - output[outputPos - 6] * kernel[11] + input[inputPos - 6] * kernel[12] - output[outputPos - 7] * kernel[13] + input[inputPos - 7] * kernel[14] - output[outputPos - 8] * kernel[15] + input[inputPos - 8] * kernel[16] - output[outputPos - 9] * kernel[17] + input[inputPos - 9] * kernel[18] - output[outputPos - 10] * kernel[19] + input[inputPos - 10] * kernel[20]
            ++outputPos
            ++inputPos
        }
    }

    private fun filterButter(input: FloatArray, inputPos: Int,
                             output: FloatArray, outputPos: Int, nSamples: Int, kernel: FloatArray) {
        var inputPos = inputPos
        var outputPos = outputPos
        var nSamples = nSamples

        while (nSamples-- != 0) {
            output[outputPos] = input[inputPos + 0] * kernel[0] - output[outputPos - 1] * kernel[1] + input[inputPos - 1] * kernel[2] - output[outputPos - 2] * kernel[3] + input[inputPos - 2] * kernel[4]
            ++outputPos
            ++inputPos
        }
    }

    /**
     * @return INIT_GAIN_ANALYSIS_OK if successful, INIT_GAIN_ANALYSIS_ERROR if
     * not
     */
    private fun ResetSampleFrequency(rgData: ReplayGain,
                                     samplefreq: Long): Int {
        /* zero out initial values */
        for (i in 0 until MAX_ORDER) {
            rgData.routbuf[i] = 0f
            rgData.rstepbuf[i] = rgData.routbuf[i]
            rgData.rinprebuf[i] = rgData.rstepbuf[i]
            rgData.loutbuf[i] = rgData.rinprebuf[i]
            rgData.lstepbuf[i] = rgData.loutbuf[i]
            rgData.linprebuf[i] = rgData.lstepbuf[i]
        }

        when (samplefreq.toInt()) {
            48000 -> rgData.freqindex = 0
            44100 -> rgData.freqindex = 1
            32000 -> rgData.freqindex = 2
            24000 -> rgData.freqindex = 3
            22050 -> rgData.freqindex = 4
            16000 -> rgData.freqindex = 5
            12000 -> rgData.freqindex = 6
            11025 -> rgData.freqindex = 7
            8000 -> rgData.freqindex = 8
            else -> return INIT_GAIN_ANALYSIS_ERROR
        }

        rgData.sampleWindow = ((samplefreq * RMS_WINDOW_TIME_NUMERATOR + RMS_WINDOW_TIME_DENOMINATOR - 1) / RMS_WINDOW_TIME_DENOMINATOR).toInt()

        rgData.lsum = 0.0
        rgData.rsum = 0.0
        rgData.totsamp = 0

        Arrays.fill(rgData.A, 0)

        return INIT_GAIN_ANALYSIS_OK
    }

    fun InitGainAnalysis(rgData: ReplayGain,
                         samplefreq: Long): Int {
        if (ResetSampleFrequency(rgData, samplefreq) != INIT_GAIN_ANALYSIS_OK) {
            return INIT_GAIN_ANALYSIS_ERROR
        }

        rgData.linpre = MAX_ORDER
        rgData.rinpre = MAX_ORDER
        rgData.lstep = MAX_ORDER
        rgData.rstep = MAX_ORDER
        rgData.lout = MAX_ORDER
        rgData.rout = MAX_ORDER

        Arrays.fill(rgData.B, 0)

        return INIT_GAIN_ANALYSIS_OK
    }

    /**
     * square
     */
    private fun fsqr(d: Double): Double {
        return d * d
    }

    fun AnalyzeSamples(rgData: ReplayGain,
                       left_samples: FloatArray, left_samplesPos: Int,
                       right_samples: FloatArray, right_samplesPos: Int, num_samples: Int,
                       num_channels: Int): Int {
        var right_samples = right_samples
        var right_samplesPos = right_samplesPos
        var curleft: Int
        var curleftBase: FloatArray
        var curright: Int
        var currightBase: FloatArray
        var batchsamples: Int
        var cursamples: Int
        var cursamplepos: Int

        if (num_samples == 0)
            return GAIN_ANALYSIS_OK

        cursamplepos = 0
        batchsamples = num_samples

        when (num_channels) {
            1 -> {
                right_samples = left_samples
                right_samplesPos = left_samplesPos
            }
            2 -> {
            }
            else -> return GAIN_ANALYSIS_ERROR
        }

        if (num_samples < MAX_ORDER) {
            System.arraycopy(left_samples, left_samplesPos, rgData.linprebuf,
                    MAX_ORDER, num_samples)
            System.arraycopy(right_samples, right_samplesPos, rgData.rinprebuf,
                    MAX_ORDER, num_samples)
        } else {
            System.arraycopy(left_samples, left_samplesPos, rgData.linprebuf,
                    MAX_ORDER, MAX_ORDER)
            System.arraycopy(right_samples, right_samplesPos, rgData.rinprebuf,
                    MAX_ORDER, MAX_ORDER)
        }

        while (batchsamples > 0) {
            cursamples = if (batchsamples > rgData.sampleWindow - rgData.totsamp)
                rgData.sampleWindow - rgData.totsamp
            else
                batchsamples
            if (cursamplepos < MAX_ORDER) {
                curleft = rgData.linpre + cursamplepos
                curleftBase = rgData.linprebuf
                curright = rgData.rinpre + cursamplepos
                currightBase = rgData.rinprebuf
                if (cursamples > MAX_ORDER - cursamplepos)
                    cursamples = MAX_ORDER - cursamplepos
            } else {
                curleft = left_samplesPos + cursamplepos
                curleftBase = left_samples
                curright = right_samplesPos + cursamplepos
                currightBase = right_samples
            }

            filterYule(curleftBase, curleft, rgData.lstepbuf, rgData.lstep + rgData.totsamp, cursamples, ABYule[rgData.freqindex])
            filterYule(currightBase, curright, rgData.rstepbuf, rgData.rstep + rgData.totsamp, cursamples, ABYule[rgData.freqindex])

            filterButter(rgData.lstepbuf, rgData.lstep + rgData.totsamp,
                    rgData.loutbuf, rgData.lout + rgData.totsamp, cursamples,
                    ABButter[rgData.freqindex])
            filterButter(rgData.rstepbuf, rgData.rstep + rgData.totsamp,
                    rgData.routbuf, rgData.rout + rgData.totsamp, cursamples,
                    ABButter[rgData.freqindex])

            curleft = rgData.lout + rgData.totsamp
            /* Get the squared values */
            curleftBase = rgData.loutbuf
            curright = rgData.rout + rgData.totsamp
            currightBase = rgData.routbuf

            var i = cursamples % 8
            while (i-- != 0) {
                rgData.lsum += fsqr(curleftBase[curleft++].toDouble())
                rgData.rsum += fsqr(currightBase[curright++].toDouble())
            }
            i = cursamples / 8
            while (i-- != 0) {
                rgData.lsum += (fsqr(curleftBase[curleft + 0].toDouble())
                        + fsqr(curleftBase[curleft + 1].toDouble())
                        + fsqr(curleftBase[curleft + 2].toDouble())
                        + fsqr(curleftBase[curleft + 3].toDouble())
                        + fsqr(curleftBase[curleft + 4].toDouble())
                        + fsqr(curleftBase[curleft + 5].toDouble())
                        + fsqr(curleftBase[curleft + 6].toDouble())
                        + fsqr(curleftBase[curleft + 7].toDouble()))
                curleft += 8
                rgData.rsum += (fsqr(currightBase[curright + 0].toDouble())
                        + fsqr(currightBase[curright + 1].toDouble())
                        + fsqr(currightBase[curright + 2].toDouble())
                        + fsqr(currightBase[curright + 3].toDouble())
                        + fsqr(currightBase[curright + 4].toDouble())
                        + fsqr(currightBase[curright + 5].toDouble())
                        + fsqr(currightBase[curright + 6].toDouble())
                        + fsqr(currightBase[curright + 7].toDouble()))
                curright += 8
            }

            batchsamples -= cursamples
            cursamplepos += cursamples
            rgData.totsamp += cursamples
            if (rgData.totsamp == rgData.sampleWindow) {
                /* Get the Root Mean Square (RMS) for this set of samples */
                val `val` = (STEPS_per_dB.toDouble()
                        * 10.0
                        * Math.log10((rgData.lsum + rgData.rsum) / rgData.totsamp * 0.5 + 1e-37))
                var ival = if (`val` <= 0) 0 else `val`.toInt()
                if (ival >= rgData.A.size)
                    ival = rgData.A.size - 1
                rgData.A[ival]++
                rgData.rsum = 0.0
                rgData.lsum = rgData.rsum

                System.arraycopy(rgData.loutbuf, rgData.totsamp,
                        rgData.loutbuf, 0, MAX_ORDER)
                System.arraycopy(rgData.routbuf, rgData.totsamp,
                        rgData.routbuf, 0, MAX_ORDER)
                System.arraycopy(rgData.lstepbuf, rgData.totsamp,
                        rgData.lstepbuf, 0, MAX_ORDER)
                System.arraycopy(rgData.rstepbuf, rgData.totsamp,
                        rgData.rstepbuf, 0, MAX_ORDER)
                rgData.totsamp = 0
            }
            if (rgData.totsamp > rgData.sampleWindow) {
                /*
				 * somehow I really screwed up: Error in programming! Contact
				 * author about totsamp > sampleWindow
				 */
                return GAIN_ANALYSIS_ERROR
            }
        }
        if (num_samples < MAX_ORDER) {
            System.arraycopy(rgData.linprebuf, num_samples, rgData.linprebuf,
                    0, MAX_ORDER - num_samples)
            System.arraycopy(rgData.rinprebuf, num_samples, rgData.rinprebuf,
                    0, MAX_ORDER - num_samples)
            System.arraycopy(left_samples, left_samplesPos, rgData.linprebuf,
                    MAX_ORDER - num_samples, num_samples)
            System.arraycopy(right_samples, right_samplesPos, rgData.rinprebuf,
                    MAX_ORDER - num_samples, num_samples)
        } else {
            System.arraycopy(left_samples, left_samplesPos + num_samples - MAX_ORDER, rgData.linprebuf, 0, MAX_ORDER)
            System.arraycopy(right_samples, right_samplesPos + num_samples - MAX_ORDER, rgData.rinprebuf, 0, MAX_ORDER)
        }

        return GAIN_ANALYSIS_OK
    }

    private fun analyzeResult(Array: IntArray, len: Int): Float {
        var i: Int

        var elems = 0
        i = 0
        while (i < len) {
            elems += Array[i]
            i++
        }
        if (elems == 0)
            return GAIN_NOT_ENOUGH_SAMPLES.toFloat()

        var upper = Math.ceil(elems * (1.0 - RMS_PERCENTILE)).toInt()
        i = len
        while (i-- > 0) {
            upper -= Array[i]
            if ((upper) <= 0)
                break
        }

        return PINK_REF - i.toFloat() / STEPS_per_dB
    }

    fun GetTitleGain(rgData: ReplayGain): Float {
        val retval = analyzeResult(rgData.A, rgData.A.size)

        for (i in rgData.A.indices) {
            rgData.B[i] += rgData.A[i]
            rgData.A[i] = 0
        }

        for (i in 0 until MAX_ORDER) {
            rgData.routbuf[i] = 0f
            rgData.rstepbuf[i] = rgData.routbuf[i]
            rgData.rinprebuf[i] = rgData.rstepbuf[i]
            rgData.loutbuf[i] = rgData.rinprebuf[i]
            rgData.lstepbuf[i] = rgData.loutbuf[i]
            rgData.linprebuf[i] = rgData.lstepbuf[i]
        }

        rgData.totsamp = 0
        rgData.rsum = 0.0
        rgData.lsum = rgData.rsum
        return retval
    }

    companion object {
        /**
         * calibration value for 89dB
         */
        private const  val PINK_REF = 64.82f

        private const  val YULE_ORDER = 10
        /**
         * percentile which is louder than the proposed level
         */
        private const  val RMS_PERCENTILE = 0.95f
        /**
         * maximum allowed sample frequency [Hz]
         */
        private const  val MAX_SAMP_FREQ = 48000
        private const  val RMS_WINDOW_TIME_NUMERATOR = 1
        /**
         * numerator / denominator = time slice size [s]
         */
        private const val RMS_WINDOW_TIME_DENOMINATOR = 20
        /**
         * Table entries per dB
         */
        internal const val STEPS_per_dB = 100f
        /**
         * Table entries for 0...MAX_dB (normal max. values are 70...80 dB)
         */
        internal const val MAX_dB = 120f

        const val GAIN_NOT_ENOUGH_SAMPLES = -24601
        const val GAIN_ANALYSIS_ERROR = 0
        const val GAIN_ANALYSIS_OK = 1
        const val INIT_GAIN_ANALYSIS_ERROR = 0
        const val INIT_GAIN_ANALYSIS_OK = 1

        internal const val MAX_ORDER = YULE_ORDER
        /**
         * max. Samples per Time slice
         */
        internal const val MAX_SAMPLES_PER_WINDOW = MAX_SAMP_FREQ * RMS_WINDOW_TIME_NUMERATOR / RMS_WINDOW_TIME_DENOMINATOR + 1

        private val ABYule = arrayOf(floatArrayOf(0.03857599435200f, -3.84664617118067f, -0.02160367184185f, 7.81501653005538f, -0.00123395316851f, -11.34170355132042f, -0.00009291677959f, 13.05504219327545f, -0.01655260341619f, -12.28759895145294f, 0.02161526843274f, 9.48293806319790f, -0.02074045215285f, -5.87257861775999f, 0.00594298065125f, 2.75465861874613f, 0.00306428023191f, -0.86984376593551f, 0.00012025322027f, 0.13919314567432f, 0.00288463683916f), floatArrayOf(0.05418656406430f, -3.47845948550071f, -0.02911007808948f, 6.36317777566148f, -0.00848709379851f, -8.54751527471874f, -0.00851165645469f, 9.47693607801280f, -0.00834990904936f, -8.81498681370155f, 0.02245293253339f, 6.85401540936998f, -0.02596338512915f, -4.39470996079559f, 0.01624864962975f, 2.19611684890774f, -0.00240879051584f, -0.75104302451432f, 0.00674613682247f, 0.13149317958808f, -0.00187763777362f), floatArrayOf(0.15457299681924f, -2.37898834973084f, -0.09331049056315f, 2.84868151156327f, -0.06247880153653f, -2.64577170229825f, 0.02163541888798f, 2.23697657451713f, -0.05588393329856f, -1.67148153367602f, 0.04781476674921f, 1.00595954808547f, 0.00222312597743f, -0.45953458054983f, 0.03174092540049f, 0.16378164858596f, -0.01390589421898f, -0.05032077717131f, 0.00651420667831f, 0.02347897407020f, -0.00881362733839f), floatArrayOf(0.30296907319327f, -1.61273165137247f, -0.22613988682123f, 1.07977492259970f, -0.08587323730772f, -0.25656257754070f, 0.03282930172664f, -0.16276719120440f, -0.00915702933434f, -0.22638893773906f, -0.02364141202522f, 0.39120800788284f, -0.00584456039913f, -0.22138138954925f, 0.06276101321749f, 0.04500235387352f, -0.00000828086748f, 0.02005851806501f, 0.00205861885564f, 0.00302439095741f, -0.02950134983287f), floatArrayOf(0.33642304856132f, -1.49858979367799f, -0.25572241425570f, 0.87350271418188f, -0.11828570177555f, 0.12205022308084f, 0.11921148675203f, -0.80774944671438f, -0.07834489609479f, 0.47854794562326f, -0.00469977914380f, -0.12453458140019f, -0.00589500224440f, -0.04067510197014f, 0.05724228140351f, 0.08333755284107f, 0.00832043980773f, -0.04237348025746f, -0.01635381384540f, 0.02977207319925f, -0.01760176568150f), floatArrayOf(0.44915256608450f, -0.62820619233671f, -0.14351757464547f, 0.29661783706366f, -0.22784394429749f, -0.37256372942400f, -0.01419140100551f, 0.00213767857124f, 0.04078262797139f, -0.42029820170918f, -0.12398163381748f, 0.22199650564824f, 0.04097565135648f, 0.00613424350682f, 0.10478503600251f, 0.06747620744683f, -0.01863887810927f, 0.05784820375801f, -0.03193428438915f, 0.03222754072173f, 0.00541907748707f), floatArrayOf(0.56619470757641f, -1.04800335126349f, -0.75464456939302f, 0.29156311971249f, 0.16242137742230f, -0.26806001042947f, 0.16744243493672f, 0.00819999645858f, -0.18901604199609f, 0.45054734505008f, 0.30931782841830f, -0.33032403314006f, -0.27562961986224f, 0.06739368333110f, 0.00647310677246f, -0.04784254229033f, 0.08647503780351f, 0.01639907836189f, -0.03788984554840f, 0.01807364323573f, -0.00588215443421f), floatArrayOf(0.58100494960553f, -0.51035327095184f, -0.53174909058578f, -0.31863563325245f, -0.14289799034253f, -0.20256413484477f, 0.17520704835522f, 0.14728154134330f, 0.02377945217615f, 0.38952639978999f, 0.15558449135573f, -0.23313271880868f, -0.25344790059353f, -0.05246019024463f, 0.01628462406333f, -0.02505961724053f, 0.06920467763959f, 0.02442357316099f, -0.03721611395801f, 0.01818801111503f, -0.00749618797172f), floatArrayOf(0.53648789255105f, -0.25049871956020f, -0.42163034350696f, -0.43193942311114f, -0.00275953611929f, -0.03424681017675f, 0.04267842219415f, -0.04678328784242f, -0.10214864179676f, 0.26408300200955f, 0.14590772289388f, 0.15113130533216f, -0.02459864859345f, -0.17556493366449f, -0.11202315195388f, -0.18823009262115f, -0.04060034127000f, 0.05477720428674f, 0.04788665548180f, 0.04704409688120f, -0.02217936801134f))

        private val ABButter = arrayOf(floatArrayOf(0.98621192462708f, -1.97223372919527f, -1.97242384925416f, 0.97261396931306f, 0.98621192462708f), floatArrayOf(0.98500175787242f, -1.96977855582618f, -1.97000351574484f, 0.97022847566350f, 0.98500175787242f), floatArrayOf(0.97938932735214f, -1.95835380975398f, -1.95877865470428f, 0.95920349965459f, 0.97938932735214f), floatArrayOf(0.97531843204928f, -1.95002759149878f, -1.95063686409857f, 0.95124613669835f, 0.97531843204928f), floatArrayOf(0.97316523498161f, -1.94561023566527f, -1.94633046996323f, 0.94705070426118f, 0.97316523498161f), floatArrayOf(0.96454515552826f, -1.92783286977036f, -1.92909031105652f, 0.93034775234268f, 0.96454515552826f), floatArrayOf(0.96009142950541f, -1.91858953033784f, -1.92018285901082f, 0.92177618768381f, 0.96009142950541f), floatArrayOf(0.95856916599601f, -1.91542108074780f, -1.91713833199203f, 0.91885558323625f, 0.95856916599601f), floatArrayOf(0.94597685600279f, -1.88903307939452f, -1.89195371200558f, 0.89487434461664f, 0.94597685600279f))
    }

}
