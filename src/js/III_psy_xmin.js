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

function III_psy_xmin() {
    this.l = new_float(Encoder.SBMAX_l);
    this.s = new_float_n([Encoder.SBMAX_s, 3]);

    var self = this;
    this.assign = function (iii_psy_xmin) {
        System.arraycopy(iii_psy_xmin.l, 0, self.l, 0, Encoder.SBMAX_l);
        for (var i = 0; i < Encoder.SBMAX_s; i++) {
            for (var j = 0; j < 3; j++) {
                self.s[i][j] = iii_psy_xmin.s[i][j];
            }
        }
    }
}

module.exports = III_psy_xmin;