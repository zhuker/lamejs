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

function CalcNoiseData() {
    this.global_gain = 0;
    this.sfb_count1 = 0;
    this.step = new_int(39);
    this.noise = new_float(39);
    this.noise_log = new_float(39);
}

module.exports = CalcNoiseData;