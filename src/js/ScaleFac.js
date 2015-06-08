//package mp3;

/**
 * Layer III side information.
 *
 * @author Ken
 *
 */

common = require('./common.js');
System = common.System;
VbrMode = common.VbrMode;
Float = common.Float;
ShortBlock = common.ShortBlock;
Util = common.Util;
Arrays = common.Arrays;
new_array_n = common.new_array_n;
new_byte = common.new_byte;
new_double = common.new_double;
new_float = common.new_float;
new_float_n = common.new_float_n;
new_int = common.new_int;
new_int_n = common.new_int_n;

function ScaleFac(arrL, arrS, arr21, arr12) {

    this.l = new_int(1 + Encoder.SBMAX_l);
    this.s = new_int(1 + Encoder.SBMAX_s);
    this.psfb21 = new_int(1 + Encoder.PSFB21);
    this.psfb12 = new_int(1 + Encoder.PSFB12);
    var l = this.l;
    var s = this.s;

    if (arguments.length == 4) {
        //public ScaleFac(final int[] arrL, final int[] arrS, final int[] arr21,
        //    final int[] arr12) {
        this.arrL = arguments[0];
        this.arrS = arguments[1];
        this.arr21 = arguments[2];
        this.arr12 = arguments[3];

        System.arraycopy(this.arrL, 0, l, 0, Math.min(this.arrL.length, this.l.length));
        System.arraycopy(this.arrS, 0, s, 0, Math.min(this.arrS.length, this.s.length));
        System.arraycopy(this.arr21, 0, this.psfb21, 0, Math.min(this.arr21.length, this.psfb21.length));
        System.arraycopy(this.arr12, 0, this.psfb12, 0, Math.min(this.arr12.length, this.psfb12.length));
    }
}

module.exports = ScaleFac;