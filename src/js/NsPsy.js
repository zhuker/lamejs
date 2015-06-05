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