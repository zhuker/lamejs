/*
 *	Get Audio routines source file
 *
 *	Copyright (c) 1999 Albert L Faber
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.	 See the GNU
 * Library General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the
 * Free Software Foundation, Inc., 59 Temple Place - Suite 330,
 * Boston, MA 02111-1307, USA.
 */

/* $Id: GetAudio.java,v 1.26 2011/08/27 18:57:12 kenchis Exp $ */

package mp3

import java.io.BufferedOutputStream
import java.io.DataOutput
import java.io.DataOutputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteOrder
import java.nio.charset.Charset
import java.util.Arrays

import mpg.MPGLib
import jdk.and
import jdk.shl
import jdk.xor

class GetAudio {

    internal lateinit var parse: Parse
    internal lateinit var mpg: MPGLib

    private var count_samples_carefully: Boolean = false
    private var pcmbitwidth: Int = 0
    private var pcmswapbytes: Boolean = false
    private var pcm_is_unsigned_8bit: Boolean = false
    private var num_samples_read: Int = 0
    private var musicin: RandomAccessFile? = null
    private var hip: MPGLib.mpstr_tag? = null

    fun setModules(parse2: Parse, mpg2: MPGLib) {
        parse = parse2
        mpg = mpg2
    }

    enum class sound_file_format {
        sf_unknown, sf_raw, sf_wave, sf_aiff,
        /**
         * MPEG Layer 1, aka mpg
         */
        sf_mp1,
        /**
         * MPEG Layer 2
         */
        sf_mp2,
        /**
         * MPEG Layer 3
         */
        sf_mp3,
        /**
         * MPEG Layer 1,2 or 3; whatever .mp3, .mp2, .mp1 or .mpg contains
         */
        sf_mp123,
        sf_ogg
    }

    protected class BlockAlign {
        internal var offset: Int = 0
        internal var blockSize: Int = 0
    }

    protected class IFF_AIFF {
        internal var numChannels: Short = 0
        internal var numSampleFrames: Int = 0
        internal var sampleSize: Short = 0
        internal var sampleRate: Double = 0.toDouble()
        internal var sampleType: Int = 0
        internal var blkAlgn = BlockAlign()
    }

    fun init_outfile(outPath: String): DataOutput? {
        /* open the output file */
        val outf: DataOutput
        try {
            File(outPath).delete()
            outf = DataOutputStream(BufferedOutputStream(FileOutputStream(outPath), 1 shl 20))
        } catch (e: FileNotFoundException) {
            e.printStackTrace()
            return null
        }

        return outf
    }

    fun init_infile(gfp: LameGlobalFlags,
                    inPath: String, enc: Enc) {
        /* open the input file */
        count_samples_carefully = false
        num_samples_read = 0
        pcmbitwidth = parse.in_bitwidth
        pcmswapbytes = parse.swapbytes
        pcm_is_unsigned_8bit = !parse.in_signed
        musicin = OpenSndFile(gfp, inPath, enc)
    }

    fun close_infile() {
        closeSndFile(parse.input_format, musicin)
    }

    /**
     * reads a frame of audio data from a file to the buffer, aligns the data
     * for future processing, and separates the left and right channels
     */
    fun get_audio(gfp: LameGlobalFlags, buffer: Array<IntArray>): Int {
        return get_audio_common(gfp, buffer, null)
    }

    /**
     * behave as the original get_audio function, with a limited 16 bit per
     * sample output
     */
    fun get_audio16(gfp: LameGlobalFlags,
                    buffer: Array<ShortArray>): Int {
        return get_audio_common(gfp, null, buffer)
    }

    /**
     * central functionality of get_audio* note: either buffer or buffer16 must
     * be allocated upon call
     *
     * @param gfp
     * global flags
     * @param buffer
     * buffer output to the int buffer or 16-bit buffer
     * @param buffer16
     * 16-bit output (if buffer == NULL)
     * @return samples read
     */
    private fun get_audio_common(gfp: LameGlobalFlags,
                                 buffer: Array<IntArray>?, buffer16: Array<ShortArray>?): Int {
        val num_channels = gfp.num_channels
        val insamp = IntArray(2 * 1152)
        val buf_tmp16 = Array(2) { ShortArray(1152) }
        var samples_read: Int
        val framesize: Int
        var samples_to_read: Int
        val remaining: Int
        val tmp_num_samples: Int

        /*
		 * NOTE: LAME can now handle arbritray size input data packets, so there
		 * is no reason to read the input data in chuncks of size "framesize".
		 * EXCEPT: the LAME graphical frame analyzer will get out of sync if we
		 * read more than framesize worth of data.
		 */

        framesize = gfp.framesize
        samples_to_read = framesize
        assert(framesize <= 1152)

        /* get num_samples */
        tmp_num_samples = gfp.num_samples

        /*
		 * if this flag has been set, then we are carefull to read exactly
		 * num_samples and no more. This is useful for .wav and .aiff files
		 * which have id3 or other tags at the end. Note that if you are using
		 * LIBSNDFILE, this is not necessary
		 */
        if (count_samples_carefully) {
            remaining = tmp_num_samples - Math.min(tmp_num_samples, num_samples_read)
            if (remaining < framesize && 0 != tmp_num_samples)
            /*
				 * in case the input is a FIFO (at least it's reproducible with
				 * a FIFO) tmp_num_samples may be 0 and therefore remaining
				 * would be 0, but we need to read some samples, so don't change
				 * samples_to_read to the wrong value in this case
				 */
                samples_to_read = remaining
        }

        if (is_mpeg_file_format(parse.input_format)) {
            if (buffer != null)
                samples_read = read_samples_mp3(gfp, musicin, buf_tmp16)
            else
                samples_read = read_samples_mp3(gfp, musicin, buffer16!!)
            if (samples_read < 0) {
                return samples_read
            }
        } else { /* convert from int; output to 16-bit buffer */
            samples_read = read_samples_pcm(musicin, insamp, num_channels * samples_to_read)
            if (samples_read < 0) {
                return samples_read
            }
            var p = samples_read
            samples_read /= num_channels
            if (buffer != null) { /* output to int buffer */
                if (num_channels == 2) {
                    var i = samples_read
                    while (--i >= 0) {
                        buffer[1][i] = insamp[--p]
                        buffer[0][i] = insamp[--p]
                    }
                } else if (num_channels == 1) {
                    Arrays.fill(buffer[1], 0, samples_read, 0)
                    var i = samples_read
                    while (--i >= 0) {
                        buffer[0][i] = insamp[--p]
                    }
                } else
                    assert(false)
            } else { /* convert from int; output to 16-bit buffer */
                if (num_channels == 2) {
                    var i = samples_read
                    while (--i >= 0) {
                        buffer16!![1][i] = (insamp[--p] shr 16 and 0xffff).toShort()
                        buffer16[0][i] = (insamp[--p] shr 16 and 0xffff).toShort()
                    }
                } else if (num_channels == 1) {
                    Arrays.fill(buffer16!![1], 0, samples_read, 0.toShort())
                    var i = samples_read
                    while (--i >= 0) {
                        buffer16[0][i] = (insamp[--p] shr 16 and 0xffff).toShort()
                    }
                } else
                    assert(false)
            }
        }

        /* LAME mp3 output 16bit - convert to int, if necessary */
        if (is_mpeg_file_format(parse.input_format)) {
            if (buffer != null) {
                run {
                    var i = samples_read
                    while (--i >= 0)
                        buffer[0][i] = buf_tmp16[0][i] and 0xffff shl 16
                }
                if (num_channels == 2) {
                    var i = samples_read
                    while (--i >= 0)
                        buffer[1][i] = buf_tmp16[1][i] and 0xffff shl 16
                } else if (num_channels == 1) {
                    Arrays.fill(buffer[1], 0, samples_read, 0)
                } else
                    assert(false)
            }
        }

        /*
		 * if num_samples = MAX_U_32_NUM, then it is considered infinitely long.
		 * Don't count the samples
		 */
        if (tmp_num_samples != Integer.MAX_VALUE)
            num_samples_read += samples_read

        return samples_read
    }

    internal fun read_samples_mp3(gfp: LameGlobalFlags, musicin: RandomAccessFile?,
                                  mpg123pcm: Array<ShortArray>): Int {
        var out: Int

        out = lame_decode_fromfile(musicin, mpg123pcm[0], mpg123pcm[1],
                parse.mp3input_data)
        /*
		 * out < 0: error, probably EOF out = 0: not possible with
		 * lame_decode_fromfile() ??? out > 0: number of output samples
		 */
        if (out < 0) {
            Arrays.fill(mpg123pcm[0], 0.toShort())
            Arrays.fill(mpg123pcm[1], 0.toShort())
            return 0
        }

        if (gfp.num_channels != parse.mp3input_data.stereo) {
            if (parse.silent < 10) {
                System.err
                        .printf("Error: number of channels has changed in %s - not supported\n",
                                type_name)
            }
            out = -1
        }
        if (gfp.in_samplerate != parse.mp3input_data.samplerate) {
            if (parse.silent < 10) {
                System.err
                        .printf("Error: sample frequency has changed in %s - not supported\n",
                                type_name)
            }
            out = -1
        }
        return out
    }

    fun WriteWaveHeader(fp: DataOutput,
                        pcmbytes: Int, freq: Int, channels: Int,
                        bits: Int): Int {
        try {
            val bytes = (bits + 7) / 8

            /* quick and dirty, but documented */
            fp.writeBytes("RIFF") /* label */
            write32BitsLowHigh(fp, pcmbytes + 44 - 8)
            /* length in bytes without header */
            fp.writeBytes("WAVEfmt ")
            /* 2 labels */
            write32BitsLowHigh(fp, 2 + 2 + 4 + 4 + 2 + 2)
            /* length of PCM format declaration area */
            write16BitsLowHigh(fp, 1)
            /* is PCM? */
            write16BitsLowHigh(fp, channels)
            /* number of channels */
            write32BitsLowHigh(fp, freq)
            /* sample frequency in [Hz] */
            write32BitsLowHigh(fp, freq * channels * bytes)
            /* bytes per second */
            write16BitsLowHigh(fp, channels * bytes)
            /* bytes per sample time */
            write16BitsLowHigh(fp, bits)
            /* bits per sample */
            fp.writeBytes("data")
            /* label */
            write32BitsLowHigh(fp, pcmbytes)
            /* length in bytes of raw PCM data */
        } catch (e: IOException) {
            return -1
        }

        return 0
    }

    /**
     * read and unpack signed low-to-high byte or unsigned single byte input.
     * (used for read_samples function) Output integers are stored in the native
     * byte order (little or big endian). -jd
     *
     * @param swap_order
     * set for high-to-low byte order input stream
     * @param sample_buffer
     * (must be allocated up to samples_to_read upon call)
     * @return number of samples read
     */
    @Throws(IOException::class)
    private fun unpack_read_samples(samples_to_read: Int,
                                    bytes_per_sample: Int, swap_order: Boolean,
                                    sample_buffer: IntArray, pcm_in: RandomAccessFile): Int {
        val bytes = ByteArray(bytes_per_sample * samples_to_read)
        pcm_in.readFully(bytes)

        var op = samples_to_read
        if (!swap_order) {
            if (bytes_per_sample == 1) {
                var i = samples_to_read * bytes_per_sample
                i -= bytes_per_sample
                while (i >= 0) {
                    sample_buffer[--op] = bytes[i] and 0xff shl 24
                    i -= bytes_per_sample
                }
            }
            if (bytes_per_sample == 2) {
                var i = samples_to_read * bytes_per_sample
                i -= bytes_per_sample
                while (i >= 0) {
                    sample_buffer[--op] = bytes[i] and 0xff shl 16 or (bytes[i + 1] and 0xff shl 24)
                    i -= bytes_per_sample
                }
            }
            if (bytes_per_sample == 3) {
                var i = samples_to_read * bytes_per_sample
                i -= bytes_per_sample
                while (i >= 0) {
                    sample_buffer[--op] = (bytes[i] and 0xff shl 8
                            or (bytes[i + 1] and 0xff shl 16)
                            or (bytes[i + 2] and 0xff shl 24))
                    i -= bytes_per_sample
                }
            }
            if (bytes_per_sample == 4) {
                var i = samples_to_read * bytes_per_sample
                i -= bytes_per_sample
                while (i >= 0) {
                    sample_buffer[--op] = (bytes[i] and 0xff
                            or (bytes[i + 1] and 0xff shl 8)
                            or (bytes[i + 2] and 0xff shl 16)
                            or (bytes[i + 3] and 0xff shl 24))
                    i -= bytes_per_sample
                }
            }
        } else {
            if (bytes_per_sample == 1) {
                var i = samples_to_read * bytes_per_sample
                i -= bytes_per_sample
                while (i >= 0) {
                    sample_buffer[--op] = bytes[i] xor 0x80 and 0xff shl 24 or (0x7f shl 16)
                    i -= bytes_per_sample
                }
            } /* convert from unsigned */
            if (bytes_per_sample == 2) {
                var i = samples_to_read * bytes_per_sample
                i -= bytes_per_sample
                while (i >= 0) {
                    sample_buffer[--op] = bytes[i] and 0xff shl 24 or (bytes[i + 1] and 0xff shl 16)
                    i -= bytes_per_sample
                }
            }
            if (bytes_per_sample == 3) {
                var i = samples_to_read * bytes_per_sample
                i -= bytes_per_sample
                while (i >= 0) {
                    sample_buffer[--op] = (bytes[i] and 0xff shl 24
                            or (bytes[i + 1] and 0xff shl 16)
                            or (bytes[i + 2] and 0xff shl 8))
                    i -= bytes_per_sample
                }
            }
            if (bytes_per_sample == 4) {
                var i = samples_to_read * bytes_per_sample
                i -= bytes_per_sample
                while (i >= 0) {
                    sample_buffer[--op] = (bytes[i] and 0xff shl 24
                            or (bytes[i + 1] and 0xff shl 16)
                            or (bytes[i + 2] and 0xff shl 8)
                            or (bytes[i + 3] and 0xff))
                    i -= bytes_per_sample
                }
            }
        }
        return samples_to_read
    }

    /**
     * reads the PCM samples from a file to the buffer
     *
     * SEMANTICS: Reads #samples_read# number of shorts from #musicin#
     * filepointer into #sample_buffer[]#. Returns the number of samples read.
     */
    private fun read_samples_pcm(musicin: RandomAccessFile?,
                                 sample_buffer: IntArray, samples_to_read: Int): Int {
        var samples_read = 0
        var swap_byte_order: Boolean
        /* byte order of input stream */

        try {
            when (pcmbitwidth) {
                32, 24, 16 -> {
                    if (!parse.in_signed) {
                        throw RuntimeException("Unsigned input only supported with bitwidth 8")
                    }
                    run {
                        swap_byte_order = parse.in_endian != ByteOrder.LITTLE_ENDIAN
                        if (pcmswapbytes) {
                            swap_byte_order = !swap_byte_order
                        }
                        samples_read = unpack_read_samples(samples_to_read,
                                pcmbitwidth / 8, swap_byte_order, sample_buffer,
                                musicin!!)

                    }
                }

                8 -> {
                    samples_read = unpack_read_samples(samples_to_read, 1,
                            pcm_is_unsigned_8bit, sample_buffer, musicin!!)
                }

                else -> {
                    throw RuntimeException("Only 8, 16, 24 and 32 bit input files supported")
                }
            }
        } catch (e: IOException) {
            throw RuntimeException("Error reading input file", e)
        }

        return samples_read
    }

    /**
     * Read Microsoft Wave headers
     *
     * By the time we get here the first 32-bits of the file have already been
     * read, and we're pretty sure that we're looking at a WAV file.
     */
    private fun parse_wave_header(gfp: LameGlobalFlags,
                                  sf: RandomAccessFile): Int {
        var format_tag = 0
        var channels = 0
        var bits_per_sample = 0
        var samples_per_sec = 0

        var is_wav = false
        var data_length = 0
        var subSize = 0
        var loop_sanity = 0

        /* file_length = */Read32BitsHighLow(sf)
        if (Read32BitsHighLow(sf) != WAV_ID_WAVE)
            return -1

        loop_sanity = 0
        while (loop_sanity < 20) {
            val type = Read32BitsHighLow(sf)

            if (type == WAV_ID_FMT) {
                subSize = Read32BitsLowHigh(sf)
                if (subSize < 16) {
                    return -1
                }

                format_tag = Read16BitsLowHigh(sf)
                subSize -= 2
                channels = Read16BitsLowHigh(sf)
                subSize -= 2
                samples_per_sec = Read32BitsLowHigh(sf)
                subSize -= 4
                /* avg_bytes_per_sec = */Read32BitsLowHigh(sf)
                subSize -= 4
                /* block_align = */Read16BitsLowHigh(sf)
                subSize -= 2
                bits_per_sample = Read16BitsLowHigh(sf)
                subSize -= 2

                /* WAVE_FORMAT_EXTENSIBLE support */
                if (subSize > 9 && format_tag == WAVE_FORMAT_EXTENSIBLE.toInt()) {
                    Read16BitsLowHigh(sf) /* cbSize */
                    Read16BitsLowHigh(sf) /* ValidBitsPerSample */
                    Read32BitsLowHigh(sf) /* ChannelMask */
                    /* SubType coincident with format_tag for PCM int or float */
                    format_tag = Read16BitsLowHigh(sf)
                    subSize -= 10
                }

                if (subSize > 0) {
                    try {
                        sf.skipBytes(subSize)
                    } catch (e: IOException) {
                        return -1
                    }

                }

            } else if (type == WAV_ID_DATA) {
                subSize = Read32BitsLowHigh(sf)
                data_length = subSize
                is_wav = true
                /* We've found the audio data. Read no further! */
                break

            } else {
                subSize = Read32BitsLowHigh(sf)
                try {
                    sf.skipBytes(subSize)
                } catch (e: IOException) {
                    return -1
                }

            }
            ++loop_sanity
        }

        if (is_wav) {
            if (format_tag != WAVE_FORMAT_PCM.toInt()) {
                if (parse.silent < 10) {
                    System.err.printf("Unsupported data format: 0x%04X\n",
                            format_tag)
                }
                /* oh no! non-supported format */
                return 0
            }

            /* make sure the header is sane */
            gfp.num_channels = channels
            if (-1 == (gfp.num_channels)) {
                if (parse.silent < 10) {
                    System.err.printf("Unsupported number of channels: %d\n",
                            channels)
                }
                return 0
            }
            gfp.in_samplerate = samples_per_sec
            pcmbitwidth = bits_per_sample
            pcm_is_unsigned_8bit = true
            gfp.num_samples = data_length / (channels * ((bits_per_sample + 7) / 8))
            return 1
        }
        return -1
    }

    /**
     * Checks AIFF header information to make sure it is valid. returns 0 on
     * success, 1 on errors
     */
    private fun aiff_check2(pcm_aiff_data: IFF_AIFF): Int {
        if (pcm_aiff_data.sampleType != IFF_ID_SSND) {
            if (parse.silent < 10) {
                System.err.printf("ERROR: input sound data is not PCM\n")
            }
            return 1
        }
        when (pcm_aiff_data.sampleSize.toInt()) {
            32, 24, 16, 8 -> {
            }
            else -> {
                if (parse.silent < 10) {
                    System.err
                            .printf("ERROR: input sound data is not 8, 16, 24 or 32 bits\n")
                }
                return 1
            }
        }
        if (pcm_aiff_data.numChannels.toInt() != 1 && pcm_aiff_data.numChannels.toInt() != 2) {
            if (parse.silent < 10) {
                System.err
                        .printf("ERROR: input sound data is not mono or stereo\n")
            }
            return 1
        }
        if (pcm_aiff_data.blkAlgn.blockSize != 0) {
            if (parse.silent < 10) {
                System.err
                        .printf("ERROR: block size of input sound data is not 0 bytes\n")
            }
            return 1
        }
        return 0
    }

    private fun make_even_number_of_bytes_in_length(x: Int): Long {
        return if (x and 0x01 != 0) {
            (x + 1).toLong()
        } else x.toLong()
    }

    /**
     * Read Audio Interchange File Format (AIFF) headers.
     *
     * By the time we get here the first 32 bits of the file have already been
     * read, and we're pretty sure that we're looking at an AIFF file.
     */
    private fun parse_aiff_header(gfp: LameGlobalFlags,
                                  sf: RandomAccessFile): Int {
        var subSize = 0
        var dataType = IFF_ID_NONE
        val aiff_info = IFF_AIFF()
        var seen_comm_chunk = 0
        var seen_ssnd_chunk = 0
        var pcm_data_pos: Long = -1

        var chunkSize = Read32BitsHighLow(sf)

        val typeID = Read32BitsHighLow(sf)
        if (typeID != IFF_ID_AIFF && typeID != IFF_ID_AIFC)
            return -1

        while (chunkSize > 0) {
            var ckSize: Long
            val type = Read32BitsHighLow(sf)
            chunkSize -= 4

            /* don't use a switch here to make it easier to use 'break' for SSND */
            if (type == IFF_ID_COMM) {
                seen_comm_chunk = seen_ssnd_chunk + 1
                subSize = Read32BitsHighLow(sf)
                ckSize = make_even_number_of_bytes_in_length(subSize)
                chunkSize -= ckSize.toInt()

                aiff_info.numChannels = Read16BitsHighLow(sf).toShort()
                ckSize -= 2
                aiff_info.numSampleFrames = Read32BitsHighLow(sf)
                ckSize -= 4
                aiff_info.sampleSize = Read16BitsHighLow(sf).toShort()
                ckSize -= 2
                try {
                    aiff_info.sampleRate = readIeeeExtendedHighLow(sf)
                } catch (e1: IOException) {
                    return -1
                }

                ckSize -= 10
                if (typeID == IFF_ID_AIFC) {
                    dataType = Read32BitsHighLow(sf)
                    ckSize -= 4
                }
                try {
                    sf.skipBytes(ckSize.toInt())
                } catch (e: IOException) {
                    return -1
                }

            } else if (type == IFF_ID_SSND) {
                seen_ssnd_chunk = 1
                subSize = Read32BitsHighLow(sf)
                ckSize = make_even_number_of_bytes_in_length(subSize)
                chunkSize -= ckSize.toInt()

                aiff_info.blkAlgn.offset = Read32BitsHighLow(sf)
                ckSize -= 4
                aiff_info.blkAlgn.blockSize = Read32BitsHighLow(sf)
                ckSize -= 4

                aiff_info.sampleType = IFF_ID_SSND

                if (seen_comm_chunk > 0) {
                    try {
                        sf.skipBytes(aiff_info.blkAlgn.offset)
                    } catch (e: IOException) {
                        return -1
                    }

                    /* We've found the audio data. Read no further! */
                    break
                }
                try {
                    pcm_data_pos = sf.filePointer
                } catch (e: IOException) {
                    return -1
                }

                if (pcm_data_pos >= 0) {
                    pcm_data_pos += aiff_info.blkAlgn.offset.toLong()
                }
                try {
                    sf.skipBytes(ckSize.toInt())
                } catch (e: IOException) {
                    return -1
                }

            } else {
                subSize = Read32BitsHighLow(sf)
                ckSize = make_even_number_of_bytes_in_length(subSize)
                chunkSize -= ckSize.toInt()

                try {
                    sf.skipBytes(ckSize.toInt())
                } catch (e: IOException) {
                    return -1
                }

            }
        }
        if (dataType == IFF_ID_2CLE) {
            pcmswapbytes = parse.swapbytes
        } else if (dataType == IFF_ID_2CBE) {
            pcmswapbytes = !parse.swapbytes
        } else if (dataType == IFF_ID_NONE) {
            pcmswapbytes = !parse.swapbytes
        } else {
            return -1
        }

        if (seen_comm_chunk != 0 && (seen_ssnd_chunk > 0 || aiff_info.numSampleFrames == 0)) {
            /* make sure the header is sane */
            if (0 != aiff_check2(aiff_info))
                return 0
            gfp.num_channels = aiff_info.numChannels.toInt()
            if (-1 == (gfp.num_channels)) {
                if (parse.silent < 10) {
                    System.err.printf("Unsupported number of channels: %u\n",
                            aiff_info.numChannels)
                }
                return 0
            }
            gfp.in_samplerate = aiff_info.sampleRate.toInt()
            gfp.num_samples = aiff_info.numSampleFrames
            pcmbitwidth = aiff_info.sampleSize.toInt()
            pcm_is_unsigned_8bit = false

            if (pcm_data_pos >= 0) {
                try {
                    sf.seek(pcm_data_pos)
                } catch (e: IOException) {
                    if (parse.silent < 10) {
                        System.err
                                .printf("Can't rewind stream to audio data position\n")
                    }
                    return 0
                }

            }

            return 1
        }
        return -1
    }

    /**
     * Read the header from a bytestream. Try to determine whether it's a WAV
     * file or AIFF without rewinding, since rewind doesn't work on pipes and
     * there's a good chance we're reading from stdin (otherwise we'd probably
     * be using libsndfile).
     *
     * When this function returns, the file offset will be positioned at the
     * beginning of the sound data.
     */
    private fun parse_file_header(gfp: LameGlobalFlags,
                                  sf: RandomAccessFile): sound_file_format {

        val type = Read32BitsHighLow(sf)
        count_samples_carefully = false
        pcm_is_unsigned_8bit = !parse.in_signed
        /*
		 * input_format = sf_raw; commented out, because it is better to fail
		 * here as to encode some hundreds of input files not supported by LAME
		 * If you know you have RAW PCM data, use the -r switch
		 */

        if (type == WAV_ID_RIFF) {
            /* It's probably a WAV file */
            val ret = parse_wave_header(gfp, sf)
            if (ret > 0) {
                count_samples_carefully = true
                return sound_file_format.sf_wave
            }
            if (ret < 0) {
                if (parse.silent < 10) {
                    System.err
                            .println("Warning: corrupt or unsupported WAVE format")
                }
            }
        } else if (type == IFF_ID_FORM) {
            /* It's probably an AIFF file */
            val ret = parse_aiff_header(gfp, sf)
            if (ret > 0) {
                count_samples_carefully = true
                return sound_file_format.sf_aiff
            }
            if (ret < 0) {
                if (parse.silent < 10) {
                    System.err
                            .printf("Warning: corrupt or unsupported AIFF format\n")
                }
            }
        } else {
            if (parse.silent < 10) {
                System.err.println("Warning: unsupported audio format\n")
            }
        }
        return sound_file_format.sf_unknown
    }

    private fun closeSndFile(input: sound_file_format,
                             musicin: RandomAccessFile?) {
        if (musicin != null) {
            try {
                musicin.close()
            } catch (e: IOException) {
                throw RuntimeException("Could not close sound file", e)
            }

        }
    }

    private fun OpenSndFile(gfp: LameGlobalFlags,
                            inPath: String, enc: Enc): RandomAccessFile {

        /* set the defaults from info in case we cannot determine them from file */
        gfp.num_samples = -1

        try {
            musicin = RandomAccessFile(inPath, "r")
        } catch (e: FileNotFoundException) {
            throw RuntimeException(String.format("Could not find \"%s\".", inPath), e)
        }

        if (is_mpeg_file_format(parse.input_format)) {
            if (-1 == lame_decode_initfile(musicin!!, parse.mp3input_data, enc)) {
                throw RuntimeException(String.format("Error reading headers in mp3 input file %s.", inPath))
            }
            gfp.num_channels = parse.mp3input_data.stereo
            gfp.in_samplerate = parse.mp3input_data.samplerate
            gfp.num_samples = parse.mp3input_data.nsamp
        } else if (parse.input_format == sound_file_format.sf_ogg) {
            throw RuntimeException("sorry, vorbis support in LAME is deprecated.")
        } else if (parse.input_format == sound_file_format.sf_raw) {
            /* assume raw PCM */
            if (parse.silent < 10) {
                println("Assuming raw pcm input file")
                if (parse.swapbytes)
                    System.out.printf(" : Forcing byte-swapping\n")
                else
                    System.out.printf("\n")
            }
            pcmswapbytes = parse.swapbytes
        } else {
            parse.input_format = parse_file_header(gfp, musicin!!)
        }
        if (parse.input_format == sound_file_format.sf_unknown) {
            throw RuntimeException("Unknown sound format!")
        }

        if (gfp.num_samples == -1) {

            val flen = File(inPath).length().toDouble()
            /* try to figure out num_samples */
            if (flen >= 0) {
                /* try file size, assume 2 bytes per sample */
                if (is_mpeg_file_format(parse.input_format)) {
                    if (parse.mp3input_data.bitrate > 0) {
                        val totalseconds = flen * 8.0 / (1000.0 * parse.mp3input_data.bitrate)
                        val tmp_num_samples = (totalseconds * gfp.in_samplerate).toInt()

                        gfp.num_samples = tmp_num_samples
                        parse.mp3input_data.nsamp = tmp_num_samples
                    }
                } else {
                    gfp.num_samples = (flen / (2 * gfp.num_channels)).toInt()
                }
            }
        }
        return musicin!!
    }

    private fun check_aid(header: ByteArray): Boolean {
        return String(header, ISO_8859_1).startsWith("AiD\u0001")
    }

    /**
     * Please check this and don't kill me if there's a bug This is a (nearly?)
     * complete header analysis for a MPEG-1/2/2.5 Layer I, II or III data
     * stream
     */
    private fun is_syncword_mp123(headerptr: ByteArray): Boolean {
        val p = 0

        if (headerptr[p + 0] and 0xFF != 0xFF) {
            /* first 8 bits must be '1' */
            return false
        }
        if (headerptr[p + 1] and 0xE0 != 0xE0) {
            /* next 3 bits are also */
            return false
        }
        if (headerptr[p + 1] and 0x18 == 0x08) {
            /* no MPEG-1, -2 or -2.5 */
            return false
        }
        when (headerptr[p + 1] and 0x06) {
            0x00 ->
                /* illegal Layer */
                return false

            0x02 -> {
                /* Layer3 */
                if (parse.input_format != sound_file_format.sf_mp3 && parse.input_format != sound_file_format.sf_mp123) {
                    return false
                }
                parse.input_format = sound_file_format.sf_mp3
            }

            0x04 -> {
                /* Layer2 */
                if (parse.input_format != sound_file_format.sf_mp2 && parse.input_format != sound_file_format.sf_mp123) {
                    return false
                }
                parse.input_format = sound_file_format.sf_mp2
            }

            0x06 -> {
                /* Layer1 */
                if (parse.input_format != sound_file_format.sf_mp1 && parse.input_format != sound_file_format.sf_mp123) {
                    return false
                }
                parse.input_format = sound_file_format.sf_mp1
            }
            else -> return false
        }
        if (headerptr[p + 1] and 0x06 == 0x00) {
            /* no Layer I, II and III */
            return false
        }
        if (headerptr[p + 2] and 0xF0 == 0xF0) {
            /* bad bitrate */
            return false
        }
        if (headerptr[p + 2] and 0x0C == 0x0C) {
            /* no sample frequency with (32,44.1,48)/(1,2,4) */
            return false
        }
        if (headerptr[p + 1] and 0x18 == 0x18
                && headerptr[p + 1] and 0x06 == 0x04
                && abl2[headerptr[p + 2] and 0xff shr 4].toInt() and (1 shl (headerptr[p + 3] and 0xff shr 6)) != 0)
            return false
        return if (headerptr[p + 3] and 3 == 2) {
            /* reserved enphasis mode */
            false
        } else true
    }

    private fun lame_decode_initfile(fd: RandomAccessFile,
                                     mp3data: MP3Data, enc: Enc): Int {
        val buf = ByteArray(100)
        val pcm_l = ShortArray(1152)
        val pcm_r = ShortArray(1152)
        var freeformat = false

        if (hip != null) {
            mpg.hip_decode_exit(hip)
        }
        hip = mpg.hip_decode_init()

        var len = 4
        try {
            fd.readFully(buf, 0, len)
        } catch (e: IOException) {
            e.printStackTrace()
            return -1 /* failed */
        }

        if (buf[0] == 'I'.toByte() && buf[1] == 'D'.toByte() && buf[2] == '3'.toByte()) {
            if (parse.silent < 10) {
                println("ID3v2 found. " + "Be aware that the ID3 tag is currently lost when transcoding.")
            }
            len = 6
            try {
                fd.readFully(buf, 0, len)
            } catch (e: IOException) {
                e.printStackTrace()
                return -1 /* failed */
            }

            buf[2] = (buf[2] and 127).toByte()
            buf[3] = (buf[3] and 127).toByte()
            buf[4] = (buf[4] and 127).toByte()
            buf[5] = (buf[5] and 127).toByte()
            len = (((buf[2] shl 7) + buf[3] shl 7) + buf[4] shl 7) + buf[5]
            try {
                fd.skipBytes(len)
            } catch (e: IOException) {
                e.printStackTrace()
                return -1 /* failed */
            }

            len = 4
            try {
                fd.readFully(buf, 0, len)
            } catch (e: IOException) {
                e.printStackTrace()
                return -1 /* failed */
            }

        }
        if (check_aid(buf)) {
            try {
                fd.readFully(buf, 0, 2)
            } catch (e: IOException) {
                e.printStackTrace()
                return -1 /* failed */
            }

            val aid_header = (buf[0] and 0xff) + 256 * (buf[1] and 0xff)
            if (parse.silent < 10) {
                System.out.printf("Album ID found.  length=%d \n", aid_header)
            }
            /* skip rest of AID, except for 6 bytes we have already read */
            try {
                fd.skipBytes(aid_header - 6)
            } catch (e: IOException) {
                e.printStackTrace()
                return -1 /* failed */
            }

            /* read 4 more bytes to set up buffer for MP3 header check */
            try {
                fd.readFully(buf, 0, len)
            } catch (e: IOException) {
                e.printStackTrace()
                return -1 /* failed */
            }

        }
        len = 4
        while (!is_syncword_mp123(buf)) {
            var i: Int
            i = 0
            while (i < len - 1) {
                buf[i] = buf[i + 1]
                i++
            }
            try {
                fd.readFully(buf, len - 1, 1)
            } catch (e: IOException) {
                e.printStackTrace()
                return -1 /* failed */
            }

        }

        if (buf[2] and 0xf0 == 0) {
            if (parse.silent < 10) {
                println("Input file is freeformat.")
            }
            freeformat = true
        }
        /* now parse the current buffer looking for MP3 headers. */
        /* (as of 11/00: mpglib modified so that for the first frame where */
        /* headers are parsed, no data will be decoded. */
        /* However, for freeformat, we need to decode an entire frame, */
        /* so mp3data->bitrate will be 0 until we have decoded the first */
        /* frame. Cannot decode first frame here because we are not */
        /* yet prepared to handle the output. */
        var ret = mpg.hip_decode1_headersB(hip, buf, len, pcm_l, pcm_r,
                mp3data, enc)
        if (-1 == ret)
            return -1

        /* repeat until we decode a valid mp3 header. */
        while (!mp3data.header_parsed) {
            try {
                fd.readFully(buf)
            } catch (e: IOException) {
                e.printStackTrace()
                return -1 /* failed */
            }

            ret = mpg.hip_decode1_headersB(hip, buf, buf.size, pcm_l, pcm_r,
                    mp3data, enc)
            if (-1 == ret)
                return -1
        }

        if (mp3data.bitrate == 0 && !freeformat) {
            if (parse.silent < 10) {
                System.err.println("fail to sync...")
            }
            return lame_decode_initfile(fd, mp3data, enc)
        }

        if (mp3data.totalframes > 0) {
            /* mpglib found a Xing VBR header and computed nsamp & totalframes */
        } else {
            /*
			 * set as unknown. Later, we will take a guess based on file size
			 * ant bitrate
			 */
            mp3data.nsamp = -1
        }

        return 0
    }

    /**
     * @return -1 error n number of samples output. either 576 or 1152 depending
     * on MP3 file.
     */
    private fun lame_decode_fromfile(fd: RandomAccessFile?,
                                     pcm_l: ShortArray, pcm_r: ShortArray, mp3data: MP3Data): Int {
        var ret = 0
        var len = 0
        val buf = ByteArray(1024)

        /* first see if we still have data buffered in the decoder: */
        ret = -1
        ret = mpg.hip_decode1_headers(hip, buf, len, pcm_l, pcm_r, mp3data)
        if (ret != 0)
            return ret

        /* read until we get a valid output frame */
        while (true) {
            try {
                len = fd!!.read(buf, 0, 1024)
            } catch (e: IOException) {
                e.printStackTrace()
                return -1
            }

            if (len <= 0) {
                /* we are done reading the file, but check for buffered data */
                ret = mpg.hip_decode1_headers(hip, buf, 0, pcm_l, pcm_r,
                        mp3data)
                if (ret <= 0) {
                    mpg.hip_decode_exit(hip)
                    /* release mp3decoder memory */
                    hip = null
                    return -1 /* done with file */
                }
                break
            }

            ret = mpg.hip_decode1_headers(hip, buf, len, pcm_l, pcm_r, mp3data)
            if (ret == -1) {
                mpg.hip_decode_exit(hip)
                /* release mp3decoder memory */
                hip = null
                return -1
            }
            if (ret > 0)
                break
        }
        return ret
    }

    private fun is_mpeg_file_format(
            input_file_format: sound_file_format): Boolean {
        when (input_file_format) {
            GetAudio.sound_file_format.sf_mp1, GetAudio.sound_file_format.sf_mp2, GetAudio.sound_file_format.sf_mp3, GetAudio.sound_file_format.sf_mp123 -> return true
            else -> {
            }
        }
        return false
    }

    // Rest of portableio.c:

    private fun Read32BitsLowHigh(fp: RandomAccessFile): Int {
        val first = 0xffff and Read16BitsLowHigh(fp)
        val second = 0xffff and Read16BitsLowHigh(fp)

        return (second shl 16) + first
    }

    private fun Read16BitsLowHigh(fp: RandomAccessFile): Int {
        try {
            val first = 0xff and fp.read()
            val second = 0xff and fp.read()

            return (second shl 8) + first
        } catch (e: IOException) {
            e.printStackTrace()
            return 0
        }

    }

    private fun Read16BitsHighLow(fp: RandomAccessFile): Int {
        try {
            val high = fp.readUnsignedByte()
            val low = fp.readUnsignedByte()

            return high shl 8 or low
        } catch (e: IOException) {
            e.printStackTrace()
            return 0
        }

    }

    private fun Read32BitsHighLow(fp: RandomAccessFile): Int {
        val first = 0xffff and Read16BitsHighLow(fp)
        val second = 0xffff and Read16BitsHighLow(fp)

        return (first shl 16) + second
    }

    private fun unsignedToFloat(u: Double): Double {
        return (u - 2147483647.0 - 1.0).toLong().toDouble() + 2147483648.0
    }

    private fun ldexp(x: Double, exp: Double): Double {
        return x * Math.pow(2.0, exp)
    }

    /**
     * Extended precision IEEE floating-point conversion routines
     */
    private fun convertFromIeeeExtended(bytes: ByteArray): Double {
        var f: Double
        var expon = (bytes[0] and 0x7F shl 8 or (bytes[1] and 0xFF)).toLong()
        val hiMant = ((bytes[2] and 0xFF).toLong() shl 24
                or ((bytes[3] and 0xFF).toLong() shl 16)
                or ((bytes[4] and 0xFF).toLong() shl 8) or (bytes[5] and 0xFF).toLong())
        val loMant = ((bytes[6] and 0xFF).toLong() shl 24
                or ((bytes[7] and 0xFF).toLong() shl 16)
                or ((bytes[8] and 0xFF).toLong() shl 8) or (bytes[9] and 0xFF).toLong())

        /*
		 * This case should also be called if the number is below the smallest
		 * positive double variable
		 */
        if (expon == 0L && hiMant == 0L && loMant == 0L) {
            f = 0.0
        } else {
            /*
			 * This case should also be called if the number is too large to fit
			 * into a double variable
			 */

            if (expon == 0x7FFFL) { /* Infinity or NaN */
                f = java.lang.Double.POSITIVE_INFINITY
            } else {
                expon -= 16383

                expon -= 31
                f = ldexp(unsignedToFloat(hiMant.toDouble()), (expon).toInt().toDouble())
                expon -= 32
                f += ldexp(unsignedToFloat(loMant.toDouble()), (expon).toInt().toDouble())
            }
        }

        return if (bytes[0] and 0x80 != 0)
            -f
        else
            f
    }

    @Throws(IOException::class)
    private fun readIeeeExtendedHighLow(fp: RandomAccessFile): Double {
        val bytes = ByteArray(10)

        fp.readFully(bytes)
        return convertFromIeeeExtended(bytes)
    }

    @Throws(IOException::class)
    private fun write32BitsLowHigh(fp: DataOutput, i: Int) {
        write16BitsLowHigh(fp, (i.toLong() and 0xffffL).toInt())
        write16BitsLowHigh(fp, ((i shr 16).toLong() and 0xffffL).toInt())
    }

    @Throws(IOException::class)
    fun write16BitsLowHigh(fp: DataOutput, i: Int) {
        fp.write(i and 0xff)
        fp.write(i shr 8 and 0xff)
    }

    companion object {

        private val type_name = "MP3 file"

        /* AIFF Definitions */

        /**
         * "FORM"
         */
        private val IFF_ID_FORM = 0x464f524d
        /**
         * "AIFF"
         */
        private val IFF_ID_AIFF = 0x41494646
        /**
         * "AIFC"
         */
        private val IFF_ID_AIFC = 0x41494643
        /**
         * "COMM"
         */
        private val IFF_ID_COMM = 0x434f4d4d
        /**
         * "SSND"
         */
        private val IFF_ID_SSND = 0x53534e44

        /**
         * "NONE" AIFF-C data format
         */
        private val IFF_ID_NONE = 0x4e4f4e45
        /**
         * "twos" AIFF-C data format
         */
        private val IFF_ID_2CBE = 0x74776f73
        /**
         * "sowt" AIFF-C data format
         */
        private val IFF_ID_2CLE = 0x736f7774

        /**
         * "RIFF"
         */
        private val WAV_ID_RIFF = 0x52494646
        /**
         * "WAVE"
         */
        private val WAV_ID_WAVE = 0x57415645
        /**
         * "fmt "
         */
        private val WAV_ID_FMT = 0x666d7420
        /**
         * "data"
         */
        private val WAV_ID_DATA = 0x64617461

        private val WAVE_FORMAT_PCM: Short = 0x0001
        private val WAVE_FORMAT_EXTENSIBLE = 0xFFFE.toShort()

        private val ISO_8859_1 = Charset.forName("ISO-8859-1")

        private val abl2 = charArrayOf(0.toChar(), 7.toChar(), 7.toChar(), 7.toChar(), 0.toChar(), 7.toChar(), 0.toChar(), 0.toChar(), 0.toChar(), 0.toChar(), 0.toChar(), 8.toChar(), 8.toChar(), 8.toChar(), 8.toChar(), 8.toChar())
    }
}
