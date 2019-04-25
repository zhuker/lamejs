package mp3

class Version {

    /**
     * A string which describes the version of LAME.
     *
     * @return string which describes the version of LAME
     */
    // primary to write screen reports
    val lameVersion: String
        get() = "$LAME_MAJOR_VERSION.$LAME_MINOR_VERSION.$LAME_PATCH_VERSION"

    /**
     * The short version of the LAME version string.
     *
     * @return short version of the LAME version string
     */
    // Adding date and time to version string makes it harder for output
    // validation
    val lameShortVersion: String
        get() = "$LAME_MAJOR_VERSION.$LAME_MINOR_VERSION.$LAME_PATCH_VERSION"

    /**
     * The shortest version of the LAME version string.
     *
     * @return shortest version of the LAME version string
     */
    // Adding date and time to version string makes it harder for output
    val lameVeryShortVersion: String
        get() = "LAME" + LAME_MAJOR_VERSION + "." + LAME_MINOR_VERSION + "r"

    /**
     * String which describes the version of GPSYCHO
     *
     * @return string which describes the version of GPSYCHO
     */
    val psyVersion: String
        get() = "$PSY_MAJOR_VERSION.$PSY_MINOR_VERSION"

    /**
     * String which is a URL for the LAME website.
     *
     * @return string which is a URL for the LAME website
     */
    val lameUrl: String
        get() = LAME_URL

    /**
     * Quite useless for a java version, however we are compatible ;-)
     *
     * @return "32bits"
     */
    val lameOsBitness: String
        get() = "32bits"

    companion object {

        /**
         * URL for the LAME website.
         */
        private val LAME_URL = "http://www.mp3dev.org/"

        /**
         * Major version number.
         */
        private val LAME_MAJOR_VERSION = 3
        /**
         * Minor version number.
         */
        private val LAME_MINOR_VERSION = 98
        /**
         * Patch level.
         */
        private val LAME_PATCH_VERSION = 4

        /**
         * Major version number.
         */
        private val PSY_MAJOR_VERSION = 0
        /**
         * Minor version number.
         */
        private val PSY_MINOR_VERSION = 93
    }

}
