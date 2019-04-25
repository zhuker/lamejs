/*
 * id3tag.c -- Write ID3 version 1 and 2 tags.
 *
 * Copyright (C) 2000 Don Melton.
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
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307, USA.
 */

/*
 * HISTORY: This source file is part of LAME (see http://www.mp3dev.org)
 * and was originally adapted by Conrad Sanderson <c.sanderson@me.gu.edu.au>
 * from mp3info by Ricardo Cerqueira <rmc@rccn.net> to write only ID3 version 1
 * tags.  Don Melton <don@blivet.com> COMPLETELY rewrote it to support version
 * 2 tags and be more conformant to other standards while remaining flexible.
 *
 * NOTE: See http://id3.org/ for more information about ID3 tag formats.
 */
package mp3

import jdk.and
import java.nio.charset.Charset
import java.util.Arrays

class ID3Tag {

    internal lateinit var bits: BitStream
    internal lateinit var ver: Version

    fun setModules(bits: BitStream, ver: Version) {
        this.bits = bits
        this.ver = ver
    }

    internal enum class MimeType {
        MIMETYPE_NONE, MIMETYPE_JPEG, MIMETYPE_PNG, MIMETYPE_GIF
    }

    private fun copyV1ToV2(gfp: LameGlobalFlags, frame_id: Int,
                           s: String) {
        val gfc = gfp.internal_flags
        val flags = gfc.tag_spec.flags
        id3v2_add_latin1(gfp, frame_id, null, null, s)
        gfc.tag_spec.flags = flags
    }

    private fun id3v2AddLameVersion(gfp: LameGlobalFlags) {
        val buffer: String
        val b = ver.lameOsBitness
        val v = ver.lameVersion
        val u = ver.lameUrl
        val lenb = b.length

        if (lenb > 0) {
            buffer = String.format("LAME %s version %s (%s)", b, v, u)
        } else {
            buffer = String.format("LAME version %s (%s)", v, u)
        }
        copyV1ToV2(gfp, ID_ENCODER, buffer)
    }

    private fun id3v2AddAudioDuration(gfp: LameGlobalFlags) {
        if (gfp.num_samples != -1) {
            val buffer: String
            val max_ulong = Integer.MAX_VALUE.toDouble()
            var ms = gfp.num_samples.toDouble()
            val playlength_ms: Long

            ms *= 1000.0
            ms /= gfp.in_samplerate.toDouble()
            if (ms > Integer.MAX_VALUE) {
                playlength_ms = max_ulong.toLong()
            } else if (ms < 0) {
                playlength_ms = 0
            } else {
                playlength_ms = ms.toLong()
            }
            buffer = String.format("%d", playlength_ms)
            copyV1ToV2(gfp, ID_PLAYLENGTH, buffer)
        }
    }

    fun id3tag_genre_list(handler: GenreListHandler?) {
        if (handler != null) {
            for (i in genre_names.indices) {
                if (i < genre_alpha_map.size) {
                    val j = genre_alpha_map[i]
                    handler.genre_list_handler(j, genre_names[j])
                }
            }
        }
    }

    fun id3tag_init(gfp: LameGlobalFlags) {
        val gfc = gfp.internal_flags
        gfc.tag_spec = ID3TagSpec()
        gfc.tag_spec.genre_id3v1 = GENRE_NUM_UNKNOWN
        gfc.tag_spec.padding_size = 128
        id3v2AddLameVersion(gfp)
    }

    fun id3tag_add_v2(gfp: LameGlobalFlags) {
        val gfc = gfp.internal_flags
        gfc.tag_spec.flags = gfc.tag_spec.flags and V1_ONLY_FLAG.inv()
        gfc.tag_spec.flags = gfc.tag_spec.flags or ADD_V2_FLAG
    }

    fun id3tag_v1_only(gfp: LameGlobalFlags) {
        val gfc = gfp.internal_flags
        gfc.tag_spec.flags = gfc.tag_spec.flags and (ADD_V2_FLAG or V2_ONLY_FLAG).inv()
        gfc.tag_spec.flags = gfc.tag_spec.flags or V1_ONLY_FLAG
    }

    fun id3tag_v2_only(gfp: LameGlobalFlags) {
        val gfc = gfp.internal_flags
        gfc.tag_spec.flags = gfc.tag_spec.flags and V1_ONLY_FLAG.inv()
        gfc.tag_spec.flags = gfc.tag_spec.flags or V2_ONLY_FLAG
    }

    fun id3tag_space_v1(gfp: LameGlobalFlags) {
        val gfc = gfp.internal_flags
        gfc.tag_spec.flags = gfc.tag_spec.flags and V2_ONLY_FLAG.inv()
        gfc.tag_spec.flags = gfc.tag_spec.flags or SPACE_V1_FLAG
    }

    fun id3tag_pad_v2(gfp: LameGlobalFlags) {
        id3tag_set_pad(gfp, 128)
    }

    fun id3tag_set_pad(gfp: LameGlobalFlags, n: Int) {
        val gfc = gfp.internal_flags
        gfc.tag_spec.flags = gfc.tag_spec.flags and V1_ONLY_FLAG.inv()
        gfc.tag_spec.flags = gfc.tag_spec.flags or PAD_V2_FLAG
        gfc.tag_spec.flags = gfc.tag_spec.flags or ADD_V2_FLAG
        gfc.tag_spec.padding_size = n
    }

    /**
     * <PRE>
     * Some existing options for ID3 tag can be specified by --tv option
     * as follows.
     * --tt <value>, --tv TIT2=value
     * --ta <value>, --tv TPE1=value
     * --tl <value>, --tv TALB=value
     * --ty <value>, --tv TYER=value
     * --tn <value>, --tv TRCK=value
     * --tg <value>, --tv TCON=value
     * (although some are not exactly same)
    </value></value></value></value></value></value></PRE> *
     */

    fun id3tag_set_albumart(gfp: LameGlobalFlags,
                            image: ByteArray, size: Int): Boolean {
        var mimetype = MimeType.MIMETYPE_NONE
        val gfc = gfp.internal_flags

        /* make sure the image size is no larger than the maximum value */
        if (Lame.LAME_MAXALBUMART < size) {
            return false
        }
        /* determine MIME type from the actual image data */
        if (2 < size && image[0] == 0xFF.toByte() && image[1] == 0xD8.toByte()) {
            mimetype = MimeType.MIMETYPE_JPEG
        } else if (4 < size && image[0].toInt() == 0x89
                && String(image, 1, 3, ASCII).startsWith("PNG")) {
            mimetype = MimeType.MIMETYPE_PNG
        } else if (4 < size && String(image, 1, 3, ASCII).startsWith("GIF8")) {
            mimetype = MimeType.MIMETYPE_GIF
        } else {
            return false
        }
        if (gfc.tag_spec.albumart != null) {
            gfc.tag_spec.albumart = null
            gfc.tag_spec.albumart_size = 0
            gfc.tag_spec.albumart_mimetype = MimeType.MIMETYPE_NONE
        }
        if (size < 1) {
            return true
        }
        gfc.tag_spec.albumart = ByteArray(size)
        if (gfc.tag_spec.albumart != null) {
            System.arraycopy(image, 0, gfc.tag_spec.albumart, 0, size)
            gfc.tag_spec.albumart_size = size
            gfc.tag_spec.albumart_mimetype = mimetype
            gfc.tag_spec.flags = gfc.tag_spec.flags or CHANGED_FLAG
            id3tag_add_v2(gfp)
        }
        return true
    }

    private fun set_4_byte_value(bytes: ByteArray, bytesPos: Int,
                                 value: Int): Int {
        var value = value
        var i: Int
        i = 3
        while (i >= 0) {
            bytes[bytesPos + i] = (value and 0xff).toByte()
            value = value shr 8
            --i
        }
        return bytesPos + 4
    }

    private fun toID3v2TagId(s: String?): Int {
        var i: Int
        var x = 0
        if (s == null) {
            return 0
        }
        i = 0
        while (i < 4 && i < s.length) {
            val c = s[i]
            val u = 0x0ff and c.toInt()
            x = x shl 8
            x = x or u
            if (c < 'A' || 'Z' < c) {
                if (c < '0' || '9' < c) {
                    return 0
                }
            }
            ++i
        }
        return x
    }

    private fun isNumericString(frame_id: Int): Boolean {
        return if (frame_id == ID_DATE || frame_id == ID_TIME || frame_id == ID_TPOS
                || frame_id == ID_TRACK || frame_id == ID_YEAR) {
            true
        } else false
    }

    private fun isMultiFrame(frame_id: Int): Boolean {
        return if (frame_id == ID_TXXX || frame_id == ID_WXXX
                || frame_id == ID_COMMENT || frame_id == ID_SYLT
                || frame_id == ID_APIC || frame_id == ID_GEOB
                || frame_id == ID_PCNT || frame_id == ID_AENC
                || frame_id == ID_LINK || frame_id == ID_ENCR
                || frame_id == ID_GRID || frame_id == ID_PRIV) {
            true
        } else false
    }

    private fun hasUcs2ByteOrderMarker(bom: Char): Boolean {
        return if (bom.toInt() == 0xFFFE || bom.toInt() == 0xFEFF) {
            true
        } else false
    }

    private fun findNode(tag: ID3TagSpec, frame_id: Int,
                         last: FrameDataNode?): FrameDataNode? {
        var node: FrameDataNode? = if (last != null) last.nxt else tag.v2_head
        while (node != null) {
            if (node.fid == frame_id) {
                return node
            }
            node = node.nxt
        }
        return null
    }

    private fun appendNode(tag: ID3TagSpec, node: FrameDataNode) {
        if (tag.v2_tail == null || tag.v2_head == null) {
            tag.v2_head = node
            tag.v2_tail = node
        } else {
            tag.v2_tail.nxt = node
            tag.v2_tail = node
        }
    }

    private fun setLang(src: String?): String {
        var i: Int
        if (src == null || src.length == 0) {
            return "XXX"
        } else {
            val dst = StringBuilder()
            if (src != null) {
                dst.append(src.substring(0, 3))
            }
            i = dst.length
            while (i < 3) {
                dst.append(' ')
                ++i
            }
            return dst.toString()
        }
    }

    private fun isSameLang(l1: String, l2: String?): Boolean {
        val d = setLang(l2)
        for (i in 0..2) {
            var a = Character.toLowerCase(l1[i])
            var b = Character.toLowerCase(d[i])
            if (a < ' ')
                a = ' '
            if (b < ' ')
                b = ' '
            if (a != b) {
                return false
            }
        }
        return true
    }

    private fun isSameDescriptor(node: FrameDataNode, dsc: String?): Boolean {
        if (node.dsc.enc == 1 && node.dsc.dim > 0) {
            return false
        }
        for (i in 0 until node.dsc.dim) {
            if (null == dsc || node.dsc.l[i] != dsc[i]) {
                return false
            }
        }
        return true
    }

    private fun isSameDescriptorUcs2(node: FrameDataNode,
                                     dsc: String?): Boolean {
        if (node.dsc.enc != 1 && node.dsc.dim > 0) {
            return false
        }
        for (i in 0 until node.dsc.dim) {
            if (null == dsc || node.dsc.l[i] != dsc[i]) {
                return false
            }
        }
        return true
    }

    private fun id3v2_add_ucs2(gfp: LameGlobalFlags, frame_id: Int,
                               lang: String?, desc: String?, text: String?) {
        val gfc = gfp.internal_flags
        if (gfc != null) {
            var node = findNode(gfc.tag_spec, frame_id, null)
            if (isMultiFrame(frame_id)) {
                while (node != null) {
                    if (isSameLang(node.lng, lang)) {
                        if (isSameDescriptorUcs2(node, desc)) {
                            break
                        }
                    }
                    node = findNode(gfc.tag_spec, frame_id, node)
                }
            }
            if (node == null) {
                node = FrameDataNode()
                appendNode(gfc.tag_spec, node)
            }
            node.fid = frame_id
            node.lng = setLang(lang)
            node.dsc.l = desc
            node.dsc.dim = desc?.length ?: 0
            node.dsc.enc = 1
            node.txt.l = text
            node.txt.dim = text?.length ?: 0
            node.txt.enc = 1
            gfc.tag_spec.flags = gfc.tag_spec.flags or (CHANGED_FLAG or ADD_V2_FLAG)
        }
    }

    private fun id3v2_add_latin1(gfp: LameGlobalFlags,
                                 frame_id: Int, lang: String?, desc: String?,
                                 text: String?) {
        val gfc = gfp.internal_flags
        if (gfc != null) {
            var node: FrameDataNode?
            node = findNode(gfc.tag_spec, frame_id, null)
            if (isMultiFrame(frame_id)) {
                while (node != null) {
                    if (isSameLang(node.lng, lang)) {
                        if (isSameDescriptor(node, desc)) {
                            break
                        }
                    }
                    node = findNode(gfc.tag_spec, frame_id, node)
                }
            }
            if (node == null) {
                node = FrameDataNode()
                appendNode(gfc.tag_spec, node)
            }
            node.fid = frame_id
            node.lng = setLang(lang)
            node.dsc.l = desc
            node.dsc.dim = desc?.length ?: 0
            node.dsc.enc = 0
            node.txt.l = text
            node.txt.dim = text?.length ?: 0
            node.txt.enc = 0
            gfc.tag_spec.flags = gfc.tag_spec.flags or (CHANGED_FLAG or ADD_V2_FLAG)
        }
    }

    fun id3tag_set_textinfo_ucs2(gfp: LameGlobalFlags?,
                                 id: String, text: String?): Int {
        val t_mask = FRAME_ID('T', 0.toChar(), 0.toChar(), 0.toChar())
        val frame_id = toID3v2TagId(id)
        if (frame_id == 0) {
            return -1
        }
        if (frame_id and t_mask == t_mask) {
            if (isNumericString(frame_id)) {
                return -2 /* must be Latin-1 encoded */
            }
            if (text == null) {
                return 0
            }
            if (!hasUcs2ByteOrderMarker(text[0])) {
                return -3 /* BOM missing */
            }
            if (gfp != null) {
                id3v2_add_ucs2(gfp, frame_id, null, null, text)
                return 0
            }
        }
        return -255 /* not supported by now */
    }

    private fun id3tag_set_textinfo_latin1(gfp: LameGlobalFlags?,
                                           id: String, text: String?): Int {
        val t_mask = FRAME_ID('T', 0.toChar(), 0.toChar(), 0.toChar())
        val frame_id = toID3v2TagId(id)
        if (frame_id == 0) {
            return -1
        }
        if (frame_id and t_mask == t_mask) {
            if (text == null) {
                return 0
            }
            if (gfp != null) {
                id3v2_add_latin1(gfp, frame_id, null, null, text)
                return 0
            }
        }
        return -255 /* not supported by now */
    }

    fun id3tag_set_comment(gfp: LameGlobalFlags?,
                           lang: String, desc: String, text: String,
                           textPos: Int): Int {
        if (gfp != null) {
            id3v2_add_latin1(gfp, ID_COMMENT, lang, desc, text)
            return 0
        }
        return -255
    }

    fun id3tag_set_title(gfp: LameGlobalFlags,
                         title: String?) {
        val gfc = gfp.internal_flags
        if (title != null && title.length != 0) {
            gfc.tag_spec.title = title
            gfc.tag_spec.flags = gfc.tag_spec.flags or CHANGED_FLAG
            copyV1ToV2(gfp, ID_TITLE, title)
        }
    }

    fun id3tag_set_artist(gfp: LameGlobalFlags,
                          artist: String?) {
        val gfc = gfp.internal_flags
        if (artist != null && artist.length != 0) {
            gfc.tag_spec.artist = artist
            gfc.tag_spec.flags = gfc.tag_spec.flags or CHANGED_FLAG
            copyV1ToV2(gfp, ID_ARTIST, artist)
        }
    }

    fun id3tag_set_album(gfp: LameGlobalFlags,
                         album: String?) {
        val gfc = gfp.internal_flags
        if (album != null && album.length != 0) {
            gfc.tag_spec.album = album
            gfc.tag_spec.flags = gfc.tag_spec.flags or CHANGED_FLAG
            copyV1ToV2(gfp, ID_ALBUM, album)
        }
    }

    fun id3tag_set_year(gfp: LameGlobalFlags,
                        year: String?) {
        val gfc = gfp.internal_flags
        if (year != null && year.length != 0) {
            var num = Integer.valueOf(year)
            if (num < 0) {
                num = 0
            }
            /* limit a year to 4 digits so it fits in a version 1 tag */
            if (num > 9999) {
                num = 9999
            }
            if (num != 0) {
                gfc.tag_spec.year = num
                gfc.tag_spec.flags = gfc.tag_spec.flags or CHANGED_FLAG
            }
            copyV1ToV2(gfp, ID_YEAR, year)
        }
    }

    fun id3tag_set_comment(gfp: LameGlobalFlags,
                           comment: String?) {
        val gfc = gfp.internal_flags
        if (comment != null && comment.length != 0) {
            gfc.tag_spec.comment = comment
            gfc.tag_spec.flags = gfc.tag_spec.flags or CHANGED_FLAG
            run {
                val flags = gfc.tag_spec.flags
                id3v2_add_latin1(gfp, ID_COMMENT, "XXX", "", comment)
                gfc.tag_spec.flags = flags
            }
        }
    }

    fun id3tag_set_track(gfp: LameGlobalFlags,
                         track: String?): Int {
        val gfc = gfp.internal_flags
        var ret = 0

        if (track != null && track.length != 0) {
            val trackcount = track.indexOf('/')
            var num: Int
            if (trackcount != -1) {
                num = Integer.parseInt(track.substring(0, trackcount))
            } else {
                num = Integer.parseInt(track)
            }
            /* check for valid ID3v1 track number range */
            if (num < 1 || num > 255) {
                num = 0
                ret = -1
                /* track number out of ID3v1 range, ignored for ID3v1 */
                gfc.tag_spec.flags = gfc.tag_spec.flags or (CHANGED_FLAG or ADD_V2_FLAG)
            }
            if (num != 0) {
                gfc.tag_spec.track_id3v1 = num
                gfc.tag_spec.flags = gfc.tag_spec.flags or CHANGED_FLAG
            }
            /* Look for the total track count after a "/", same restrictions */
            if (trackcount != -1) {
                gfc.tag_spec.flags = gfc.tag_spec.flags or (CHANGED_FLAG or ADD_V2_FLAG)
            }
            copyV1ToV2(gfp, ID_TRACK, track)
        }
        return ret
    }

    private fun nextUpperAlpha(p: String, pPos: Int, x: Char): Int {
        var pPos = pPos
        var c = Character.toUpperCase(p[pPos])
        while (pPos < p.length) {
            if ('A' <= c && c <= 'Z') {
                if (c != x) {
                    return pPos
                }
            }
            c = Character
                    .toUpperCase(p[pPos++])
        }
        return pPos
    }

    private fun sloppyCompared(p: String, q: String): Boolean {
        var pPos = nextUpperAlpha(p, 0, 0.toChar())
        var qPos = nextUpperAlpha(q, 0, 0.toChar())
        var cp = if (pPos < p.length) Character.toUpperCase(p[pPos]) else 0.toChar()
        var cq = Character.toUpperCase(q[qPos])
        while (cp == cq) {
            if (cp.toInt() == 0) {
                return true
            }
            if (p[1] == '.') { /* some abbrevation */
                while (qPos < q.length && q[qPos++] != ' ') {
                }
            }
            pPos = nextUpperAlpha(p, pPos, cp)
            qPos = nextUpperAlpha(q, qPos, cq)
            cp = if (pPos < p.length) Character.toUpperCase(p[pPos]) else 0.toChar()
            cq = Character.toUpperCase(q[qPos])
        }
        return false
    }

    private fun sloppySearchGenre(genre: String): Int {
        for (i in genre_names.indices) {
            if (sloppyCompared(genre, genre_names[i])) {
                return i
            }
        }
        return genre_names.size
    }

    private fun searchGenre(genre: String): Int {
        for (i in genre_names.indices) {
            if (genre_names[i] == genre) {
                return i
            }
        }
        return genre_names.size
    }

    fun id3tag_set_genre(gfp: LameGlobalFlags, genre: String?): Int {
        var genre = genre
        val gfc = gfp.internal_flags
        var ret = 0
        if (genre != null && genre.length != 0) {
            var num: Int
            try {
                num = Integer.parseInt(genre)
                if (num < 0 || num >= genre_names.size) {
                    return -1
                }
                genre = genre_names[num]
            } catch (e: NumberFormatException) {
                /* is the input a string or a valid number? */
                num = searchGenre(genre)
                if (num == genre_names.size) {
                    num = sloppySearchGenre(genre)
                }
                if (num == genre_names.size) {
                    num = GENRE_INDEX_OTHER
                    ret = -2
                } else {
                    genre = genre_names[num]
                }
            }

            gfc.tag_spec.genre_id3v1 = num
            gfc.tag_spec.flags = gfc.tag_spec.flags or CHANGED_FLAG
            if (ret != 0) {
                gfc.tag_spec.flags = gfc.tag_spec.flags or ADD_V2_FLAG
            }
            copyV1ToV2(gfp, ID_GENRE, genre.orEmpty())
        }
        return ret
    }

    private fun set_frame_custom(frame: ByteArray, framePos: Int,
                                 fieldvalue: CharArray?): Int {
        var framePos = framePos
        if (fieldvalue != null && fieldvalue[0].toInt() != 0) {
            var value = 5
            var length = String(fieldvalue, value, fieldvalue.size - value).length
            frame[framePos++] = fieldvalue[0].toByte()
            frame[framePos++] = fieldvalue[1].toByte()
            frame[framePos++] = fieldvalue[2].toByte()
            frame[framePos++] = fieldvalue[3].toByte()
            framePos = set_4_byte_value(frame, value, String(fieldvalue,
                    value, fieldvalue.size - value).length + 1)
            /* clear 2-byte header flags */
            frame[framePos++] = 0
            frame[framePos++] = 0
            /* clear 1 encoding descriptor byte to indicate ISO-8859-1 format */
            frame[framePos++] = 0
            while (length-- != 0) {
                frame[framePos++] = fieldvalue[value++].toByte()
            }
        }
        return framePos
    }

    private fun sizeOfNode(node: FrameDataNode?): Int {
        var n = 0
        if (node != null) {
            n = 10
            /* header size */
            n += 1
            /* text encoding flag */
            when (node.txt.enc) {
                0 -> n += node.txt.dim
                1 -> n += node.txt.dim * 2
                else -> n += node.txt.dim
            }
        }
        return n
    }

    private fun sizeOfCommentNode(node: FrameDataNode?): Int {
        var n = 0
        if (node != null) {
            n = 10
            /* header size */
            n += 1
            /* text encoding flag */
            n += 3
            /* language */
            when (node.dsc.enc) {
                0 -> n += 1 + node.dsc.dim
                1 -> n += 2 + node.dsc.dim * 2
                else -> n += 1 + node.dsc.dim
            }
            when (node.txt.enc) {
                0 -> n += node.txt.dim
                1 -> n += node.txt.dim * 2
                else -> n += node.txt.dim
            }
        }
        return n
    }

    private fun writeChars(frame: ByteArray, framePos: Int, str: String,
                           strPos: Int, n: Int): Int {
        var framePos = framePos
        var strPos = strPos
        var n = n
        while (n-- != 0) {
            frame[framePos++] = str[strPos++].toByte()
        }
        return framePos
    }

    private fun writeUcs2s(frame: ByteArray, framePos: Int, str: String,
                           strPos: Int, n: Int): Int {
        var framePos = framePos
        var strPos = strPos
        var n = n
        while (n-- != 0) {
            frame[framePos++] = (0xff and (str[strPos].toInt() shr 8)).toByte()
            frame[framePos++] = (0xff and str[strPos++].toInt()).toByte()
        }
        return framePos
    }

    private fun set_frame_comment(frame: ByteArray, framePos: Int,
                                  node: FrameDataNode): Int {
        var framePos = framePos
        val n = sizeOfCommentNode(node)
        if (n > 10) {
            framePos = set_4_byte_value(frame, framePos, ID_COMMENT)
            framePos = set_4_byte_value(frame, framePos, n - 10)
            /* clear 2-byte header flags */
            frame[framePos++] = 0
            frame[framePos++] = 0
            /* encoding descriptor byte */
            frame[framePos++] = if (node.txt.enc == 1) 1.toByte() else 0.toByte()
            /* 3 bytes language */
            frame[framePos++] = node.lng[0].toByte()
            frame[framePos++] = node.lng[1].toByte()
            frame[framePos++] = node.lng[2].toByte()
            /* descriptor with zero byte(s) separator */
            if (node.dsc.enc != 1) {
                framePos = writeChars(frame, framePos, node.dsc.l, 0,
                        node.dsc.dim)
                frame[framePos++] = 0
            } else {
                framePos = writeUcs2s(frame, framePos, node.dsc.l, 0,
                        node.dsc.dim)
                frame[framePos++] = 0
                frame[framePos++] = 0
            }
            /* comment full text */
            if (node.txt.enc != 1) {
                framePos = writeChars(frame, framePos, node.txt.l, 0,
                        node.txt.dim)
            } else {
                framePos = writeUcs2s(frame, framePos, node.txt.l, 0,
                        node.txt.dim)
            }
        }
        return framePos
    }

    private fun set_frame_custom2(frame: ByteArray, framePos: Int,
                                  node: FrameDataNode): Int {
        var framePos = framePos
        val n = sizeOfNode(node)
        if (n > 10) {
            framePos = set_4_byte_value(frame, framePos, node.fid)
            framePos = set_4_byte_value(frame, framePos, n - 10)
            /* clear 2-byte header flags */
            frame[framePos++] = 0
            frame[framePos++] = 0
            /* clear 1 encoding descriptor byte to indicate ISO-8859-1 format */
            frame[framePos++] = if (node.txt.enc == 1) 1.toByte() else 0.toByte()
            if (node.txt.enc != 1) {
                framePos = writeChars(frame, framePos, node.txt.l, 0,
                        node.txt.dim)
            } else {
                framePos = writeUcs2s(frame, framePos, node.txt.l, 0,
                        node.txt.dim)
            }
        }
        return framePos
    }

    private fun set_frame_apic(frame: ByteArray, framePos: Int,
                               mimetype: CharArray?, data: ByteArray?, size: Int): Int {
        var framePos = framePos
        var size = size
        /**
         * <PRE>
         * ID3v2.3 standard APIC frame:
         * <Header for></Header>'Attached picture', ID: "APIC">
         * Text encoding    $xx
         * MIME type        <text string> $00
         * Picture type     $xx
         * Description      <text string according to encoding> $00 (00)
         * Picture data     <binary data>
        </binary></text></text></PRE> *
         */
        if (mimetype != null && data != null && size != 0) {
            framePos = set_4_byte_value(frame, framePos,
                    FRAME_ID('A', 'P', 'I', 'C'))
            framePos = set_4_byte_value(frame, framePos,
                    4 + mimetype.size + size)
            /* clear 2-byte header flags */
            frame[framePos++] = 0
            frame[framePos++] = 0
            /* clear 1 encoding descriptor byte to indicate ISO-8859-1 format */
            frame[framePos++] = 0
            /* copy mime_type */
            var mimetypePos = 0
            while (mimetypePos < mimetype.size) {
                frame[framePos++] = mimetype[mimetypePos++].toByte()
            }
            frame[framePos++] = 0
            /* set picture type to 0 */
            frame[framePos++] = 0
            /* empty description field */
            frame[framePos++] = 0
            /* copy the image data */
            var dataPos = 0
            while (size-- != 0) {
                frame[framePos++] = data[dataPos++]
            }
        }
        return framePos
    }

    fun id3tag_set_fieldvalue(gfp: LameGlobalFlags,
                              fieldvalue: String?): Int {
        val gfc = gfp.internal_flags
        if (fieldvalue != null && fieldvalue.length != 0) {
            val frame_id = toID3v2TagId(fieldvalue)
            if (fieldvalue.length < 5 || fieldvalue[4] != '=') {
                return -1
            }
            if (frame_id != 0) {
                if (id3tag_set_textinfo_latin1(gfp, fieldvalue,
                                fieldvalue.substring(5)) != 0) {
                    gfc.tag_spec.values.add(fieldvalue)
                    gfc.tag_spec.num_values++
                }
            }
            gfc.tag_spec.flags = gfc.tag_spec.flags or CHANGED_FLAG
        }
        id3tag_add_v2(gfp)
        return 0
    }

    fun lame_get_id3v2_tag(gfp: LameGlobalFlags?,
                           buffer: ByteArray?, size: Int): Int {
        val gfc: LameInternalFlags?
        if (gfp == null) {
            return 0
        }
        gfc = gfp.internal_flags
        if (gfc == null) {
            return 0
        }
        if (gfc.tag_spec.flags and V1_ONLY_FLAG != 0) {
            return 0
        }
        run {
            /* calculate length of four fields which may not fit in verion 1 tag */
            val title_length = if (gfc.tag_spec.title != null)
                gfc.tag_spec.title
                        .length
            else
                0
            val artist_length = if (gfc.tag_spec.artist != null)
                gfc.tag_spec.artist
                        .length
            else
                0
            val album_length = if (gfc.tag_spec.album != null)
                gfc.tag_spec.album
                        .length
            else
                0
            val comment_length = if (gfc.tag_spec.comment != null)
                gfc.tag_spec.comment
                        .length
            else
                0
            /* write tag if explicitly requested or if fields overflow */
            if (gfc.tag_spec.flags and (ADD_V2_FLAG or V2_ONLY_FLAG) != 0
                    || title_length > 30 || artist_length > 30
                    || album_length > 30 || comment_length > 30
                    || gfc.tag_spec.track_id3v1 != 0 && comment_length > 28) {
                var tag_size: Int
                var p: Int
                val adjusted_tag_size: Int
                var i: Int
                var albumart_mime: String? = null

                id3v2AddAudioDuration(gfp)

                /* calulate size of tag starting with 10-byte tag header */
                tag_size = 10
                i = 0
                while (i < gfc.tag_spec.num_values) {
                    tag_size += 6 + gfc.tag_spec.values[i].length
                    ++i
                }
                if (gfc.tag_spec.albumart != null && gfc.tag_spec.albumart_size != 0) {
                    when (gfc.tag_spec.albumart_mimetype) {
                        ID3Tag.MimeType.MIMETYPE_JPEG -> albumart_mime = mime_jpeg
                        ID3Tag.MimeType.MIMETYPE_PNG -> albumart_mime = mime_png
                        ID3Tag.MimeType.MIMETYPE_GIF -> albumart_mime = mime_gif
                    }
                    if (albumart_mime != null) {
                        tag_size += (10 + 4 + albumart_mime.length
                                + gfc.tag_spec.albumart_size)
                    }
                }
                run {
                    val tag = gfc.tag_spec
                    if (tag.v2_head != null) {
                        var node: FrameDataNode?
                        node = tag.v2_head
                        while (node != null) {
                            if (node.fid == ID_COMMENT) {
                                tag_size += sizeOfCommentNode(node)
                            } else {
                                tag_size += sizeOfNode(node)
                            }
                            node = node.nxt
                        }
                    }
                }
                if (gfc.tag_spec.flags and PAD_V2_FLAG != 0) {
                    /* add some bytes of padding */
                    tag_size += gfc.tag_spec.padding_size
                }
                if (size < tag_size) {
                    return tag_size
                }
                if (buffer == null) {
                    return 0
                }
                p = 0
                /* set tag header starting with file identifier */
                buffer[p++] = 'I'.toByte()
                buffer[p++] = 'D'.toByte()
                buffer[p++] = '3'.toByte()
                /* set version number word */
                buffer[p++] = 3
                buffer[p++] = 0
                /* clear flags byte */
                buffer[p++] = 0
                /* calculate and set tag size = total size - header size */
                adjusted_tag_size = tag_size - 10
                /*
				 * encode adjusted size into four bytes where most significant
				 * bit is clear in each byte, for 28-bit total
				 */
                buffer[p++] = (adjusted_tag_size shr 21 and 0x7f).toByte()
                buffer[p++] = (adjusted_tag_size shr 14 and 0x7f).toByte()
                buffer[p++] = (adjusted_tag_size shr 7 and 0x7f).toByte()
                buffer[p++] = (adjusted_tag_size and 0x7f).toByte()

                /*
				 * NOTE: The remainder of the tag (frames and padding, if any)
				 * are not "unsynchronized" to prevent false MPEG audio headers
				 * from appearing in the bitstream. Why? Well, most players and
				 * utilities know how to skip the ID3 version 2 tag by now even
				 * if they don't read its contents, and it's actually very
				 * unlikely that such a false "sync" pattern would occur in just
				 * the simple text frames added here.
				 */

                /* set each frame in tag */
                run {
                    val tag = gfc.tag_spec
                    if (tag.v2_head != null) {
                        var node: FrameDataNode?
                        node = tag.v2_head
                        while (node != null) {
                            if (node!!.fid == ID_COMMENT) {
                                p = set_frame_comment(buffer, p, node!!)
                            } else {
                                p = set_frame_custom2(buffer, p, node!!)
                            }
                            node = node!!.nxt
                        }
                    }
                }
                i = 0
                while (i < gfc.tag_spec.num_values) {
                    p = set_frame_custom(buffer, p, gfc.tag_spec.values[i]
                            .toCharArray())
                    ++i
                }
                if (albumart_mime != null) {
                    p = set_frame_apic(buffer, p, albumart_mime.toCharArray(),
                            gfc.tag_spec.albumart, gfc.tag_spec.albumart_size)
                }
                /* clear any padding bytes */
                Arrays.fill(buffer, p, tag_size, 0.toByte())
                return tag_size
            }
        }
        return 0
    }

    fun id3tag_write_v2(gfp: LameGlobalFlags): Int {
        val gfc = gfp.internal_flags
        if (gfc.tag_spec.flags and CHANGED_FLAG != 0 && 0 == gfc.tag_spec.flags and V1_ONLY_FLAG) {
            var tag: ByteArray? = null
            val tag_size: Int
            val n: Int

            n = lame_get_id3v2_tag(gfp, null, 0)
            tag = ByteArray(n)
            tag_size = lame_get_id3v2_tag(gfp, tag, n)
            if (tag_size > n) {
                return -1
            } else {
                /* write tag directly into bitstream at current position */
                for (i in 0 until tag_size) {
                    bits.add_dummy_byte(gfp, tag[i] and 0xff, 1)
                }
            }
            return tag_size
            /* ok, tag should not exceed 2GB */
        }
        return 0
    }

    private fun set_text_field(field: ByteArray, fieldPos: Int,
                               text: String?, size: Int, pad: Int): Int {
        var fieldPos = fieldPos
        var size = size
        var textPos = 0
        while (size-- != 0) {
            if (text != null && textPos < text.length) {
                field[fieldPos++] = text[textPos++].toByte()
            } else {
                field[fieldPos++] = pad.toByte()
            }
        }
        return fieldPos
    }

    fun lame_get_id3v1_tag(gfp: LameGlobalFlags?,
                           buffer: ByteArray?, size: Int): Int {
        val tag_size = 128
        val gfc: LameInternalFlags?

        if (gfp == null) {
            return 0
        }
        if (size < tag_size) {
            return tag_size
        }
        gfc = gfp.internal_flags
        if (gfc == null) {
            return 0
        }
        if (buffer == null) {
            return 0
        }
        if (gfc.tag_spec.flags and CHANGED_FLAG != 0 && 0 == gfc.tag_spec.flags and V2_ONLY_FLAG) {
            var p = 0
            val pad = (if (gfc.tag_spec.flags and SPACE_V1_FLAG != 0) ' ' else 0.toChar()).toInt()
            val year: String

            /* set tag identifier */
            buffer[p++] = 'T'.toByte()
            buffer[p++] = 'A'.toByte()
            buffer[p++] = 'G'.toByte()
            /* set each field in tag */
            p = set_text_field(buffer, p, gfc.tag_spec.title, 30, pad)
            p = set_text_field(buffer, p, gfc.tag_spec.artist, 30, pad)
            p = set_text_field(buffer, p, gfc.tag_spec.album, 30, pad)
            year = String.format("%d", Integer.valueOf(gfc.tag_spec.year))
            p = set_text_field(buffer, p, if (gfc.tag_spec.year != 0) year else null,
                    4, pad)
            /* limit comment field to 28 bytes if a track is specified */
            p = set_text_field(buffer, p, gfc.tag_spec.comment,
                    if (gfc.tag_spec.track_id3v1 != 0) 28 else 30, pad)
            if (gfc.tag_spec.track_id3v1 != 0) {
                /* clear the next byte to indicate a version 1.1 tag */
                buffer[p++] = 0
                buffer[p++] = gfc.tag_spec.track_id3v1.toByte()
            }
            buffer[p++] = gfc.tag_spec.genre_id3v1.toByte()
            return tag_size
        }
        return 0
    }

    fun id3tag_write_v1(gfp: LameGlobalFlags): Int {
        val tag = ByteArray(128)

        val m = tag.size
        val n = lame_get_id3v1_tag(gfp, tag, m)
        if (n > m) {
            return 0
        }
        /* write tag directly into bitstream at current position */
        for (i in 0 until n) {
            bits.add_dummy_byte(gfp, tag[i] and 0xff, 1)
        }
        return n /* ok, tag has fixed size of 128 bytes, well below 2GB */
    }

    companion object {

        private val CHANGED_FLAG = 1 shl 0
        private val ADD_V2_FLAG = 1 shl 1
        private val V1_ONLY_FLAG = 1 shl 2
        private val V2_ONLY_FLAG = 1 shl 3
        private val SPACE_V1_FLAG = 1 shl 4
        private val PAD_V2_FLAG = 1 shl 5

        private val genre_names = arrayOf(
                /*
	 * NOTE: The spelling of these genre names is identical to those found in
	 * Winamp and mp3info.
	 */
                "Blues", "Classic Rock", "Country", "Dance", "Disco", "Funk", "Grunge", "Hip-Hop", "Jazz", "Metal", "New Age", "Oldies", "Other", "Pop", "R&B", "Rap", "Reggae", "Rock", "Techno", "Industrial", "Alternative", "Ska", "Death Metal", "Pranks", "Soundtrack", "Euro-Techno", "Ambient", "Trip-Hop", "Vocal", "Jazz+Funk", "Fusion", "Trance", "Classical", "Instrumental", "Acid", "House", "Game", "Sound Clip", "Gospel", "Noise", "Alternative Rock", "Bass", "Soul", "Punk", "Space", "Meditative", "Instrumental Pop", "Instrumental Rock", "Ethnic", "Gothic", "Darkwave", "Techno-Industrial", "Electronic", "Pop-Folk", "Eurodance", "Dream", "Southern Rock", "Comedy", "Cult", "Gangsta", "Top 40", "Christian Rap", "Pop/Funk", "Jungle", "Native US", "Cabaret", "New Wave", "Psychedelic", "Rave", "Showtunes", "Trailer", "Lo-Fi", "Tribal", "Acid Punk", "Acid Jazz", "Polka", "Retro", "Musical", "Rock & Roll", "Hard Rock", "Folk", "Folk-Rock", "National Folk", "Swing", "Fast Fusion", "Bebob", "Latin", "Revival", "Celtic", "Bluegrass", "Avantgarde", "Gothic Rock", "Progressive Rock", "Psychedelic Rock", "Symphonic Rock", "Slow Rock", "Big Band", "Chorus", "Easy Listening", "Acoustic", "Humour", "Speech", "Chanson", "Opera", "Chamber Music", "Sonata", "Symphony", "Booty Bass", "Primus", "Porn Groove", "Satire", "Slow Jam", "Club", "Tango", "Samba", "Folklore", "Ballad", "Power Ballad", "Rhythmic Soul", "Freestyle", "Duet", "Punk Rock", "Drum Solo", "A Cappella", "Euro-House", "Dance Hall", "Goa", "Drum & Bass", "Club-House", "Hardcore", "Terror", "Indie", "BritPop", "Negerpunk", "Polsk Punk", "Beat", "Christian Gangsta", "Heavy Metal", "Black Metal", "Crossover", "Contemporary Christian", "Christian Rock", "Merengue", "Salsa", "Thrash Metal", "Anime", "JPop", "SynthPop")

        private val genre_alpha_map = intArrayOf(123, 34, 74, 73, 99, 20, 40, 26, 145, 90, 116, 41, 135, 85, 96, 138, 89, 0, 107, 132, 65, 88, 104, 102, 97, 136, 61, 141, 32, 1, 112, 128, 57, 140, 2, 139, 58, 3, 125, 50, 22, 4, 55, 127, 122, 120, 98, 52, 48, 54, 124, 25, 84, 80, 115, 81, 119, 5, 30, 36, 59, 126, 38, 49, 91, 6, 129, 79, 137, 7, 35, 100, 131, 19, 33, 46, 47, 8, 29, 146, 63, 86, 71, 45, 142, 9, 77, 82, 64, 133, 10, 66, 39, 11, 103, 12, 75, 134, 13, 53, 62, 109, 117, 23, 108, 92, 67, 93, 43, 121, 15, 68, 14, 16, 76, 87, 118, 17, 78, 143, 114, 110, 69, 21, 111, 95, 105, 42, 37, 24, 56, 44, 101, 83, 94, 106, 147, 113, 18, 51, 130, 144, 60, 70, 31, 72, 27, 28)

        private val GENRE_INDEX_OTHER = 12

        private fun FRAME_ID(a: Char, b: Char, c: Char, d: Char): Int {
            return (a.toInt() and 0xff shl 24 or (b.toInt() and 0xff shl 16) or (c.toInt() and 0xff shl 8)
                    or (d.toInt() and 0xff shl 0))
        }

        private val ID_TITLE = FRAME_ID('T', 'I', 'T', '2')
        private val ID_ARTIST = FRAME_ID('T', 'P', 'E', '1')
        private val ID_ALBUM = FRAME_ID('T', 'A', 'L', 'B')
        private val ID_GENRE = FRAME_ID('T', 'C', 'O', 'N')
        private val ID_ENCODER = FRAME_ID('T', 'S', 'S', 'E')
        private val ID_PLAYLENGTH = FRAME_ID('T', 'L', 'E', 'N')
        private val ID_COMMENT = FRAME_ID('C', 'O', 'M', 'M')

        /**
         * "ddMM"
         */
        private val ID_DATE = FRAME_ID('T', 'D', 'A', 'T')
        /**
         * "hhmm"
         */
        private val ID_TIME = FRAME_ID('T', 'I', 'M', 'E')
        /**
         * '0'-'9' and '/' allowed
         */
        private val ID_TPOS = FRAME_ID('T', 'P', 'O', 'S')
        /**
         * '0'-'9' and '/' allowed
         */
        private val ID_TRACK = FRAME_ID('T', 'R', 'C', 'K')
        /**
         * "yyyy"
         */
        private val ID_YEAR = FRAME_ID('T', 'Y', 'E', 'R')

        private val ID_TXXX = FRAME_ID('T', 'X', 'X', 'X')
        private val ID_WXXX = FRAME_ID('W', 'X', 'X', 'X')
        private val ID_SYLT = FRAME_ID('S', 'Y', 'L', 'T')
        private val ID_APIC = FRAME_ID('A', 'P', 'I', 'C')
        private val ID_GEOB = FRAME_ID('G', 'E', 'O', 'B')
        private val ID_PCNT = FRAME_ID('P', 'C', 'N', 'T')
        private val ID_AENC = FRAME_ID('A', 'E', 'N', 'C')
        private val ID_LINK = FRAME_ID('L', 'I', 'N', 'K')
        private val ID_ENCR = FRAME_ID('E', 'N', 'C', 'R')
        private val ID_GRID = FRAME_ID('G', 'R', 'I', 'D')
        private val ID_PRIV = FRAME_ID('P', 'R', 'I', 'V')

        private val GENRE_NUM_UNKNOWN = 255
        private val ASCII = Charset.forName("US-ASCII")

        private val mime_jpeg = "image/jpeg"
        private val mime_png = "image/png"
        private val mime_gif = "image/gif"
    }

}
