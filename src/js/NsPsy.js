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

Encoder = require('./Encoder.js');

//package mp3;

/**
 * Variables used for --nspsytune
 *
 * @author Ken
 *
 */
function NsPsy() {
    this.last_en_subshort = new_float_n([4, 9]);
    this.lastAttacks = new_int(4);
    this.pefirbuf = new_float(19);
    this.longfact = new_float(Encoder.SBMAX_l);
    this.shortfact = new_float(Encoder.SBMAX_s);

    /**
     * short block tuning
     */
    this.attackthre = 0.;
    this.attackthre_s = 0.;
}

module.exports = NsPsy;