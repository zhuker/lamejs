package jdk


fun assert(expect: Boolean) {
    if (!expect) throw  IllegalStateException("assertion failed")
}

inline infix fun Byte.and(i: Int): Int = this.toInt() and i
inline infix fun Byte.xor(i: Int): Int = this.toInt() xor i
inline infix fun Byte.shl(i: Int): Int = this.toInt() shl i
inline infix fun Byte.shr(i: Int): Int = this.toInt() shr i
inline infix fun Short.shr(i: Int): Int = this.toInt() shr i
inline infix fun Short.and(i: Int): Int = this.toInt() and i
