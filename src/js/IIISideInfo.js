//package mp3;
GrInfo = require('./GrInfo.js');

function IIISideInfo() {
    this.tt = [[null, null], [null, null]];
    this.main_data_begin = 0;
    this.private_bits = 0;
    this.resvDrain_pre = 0;
    this.resvDrain_post = 0;
    this.scfsi = [new_int(4), new_int(4)];

    for (var gr = 0; gr < 2; gr++) {
        for (var ch = 0; ch < 2; ch++) {
            this.tt[gr][ch] = new GrInfo();
        }
    }
}

module.exports = IIISideInfo;