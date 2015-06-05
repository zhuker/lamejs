//package mp3;

/**
 * Layer III side information.
 *
 * @author Ken
 *
 */
function ScaleFac(arrL, arrS, arr21, arr12) {

    this.l = new_int(1 + Encoder.SBMAX_l);
    this.s = new_int(1 + Encoder.SBMAX_s);
    this.psfb21 = new_int(1 + Encoder.PSFB21);
    this.psfb12 = new_int(1 + Encoder.PSFB12);
    var l = this.l;
    var s = this.s;
    var psfb21 = this.psfb21;
    var psfb12 = this.psfb12;

    if (arguments.length == 4) {
        //public ScaleFac(final int[] arrL, final int[] arrS, final int[] arr21,
        //    final int[] arr12) {
        this.arrL = arguments[0];
        this.arrS = arguments[1];
        this.arr21 = arguments[2];
        this.arr12 = arguments[3];
        var arrL = this.arrL;
        var arrS = this.arrS;
        var arr21 = this.arr21;
        var arr12 = this.arr12;

        System.arraycopy(arrL, 0, l, 0, Math.min(arrL.length, l.length));
        System.arraycopy(arrS, 0, s, 0, Math.min(arrS.length, s.length));
        System.arraycopy(arr21, 0, psfb21, 0,
            Math.min(arr21.length, psfb21.length));
        System.arraycopy(arr12, 0, psfb12, 0,
            Math.min(arr12.length, psfb12.length));
    }
}