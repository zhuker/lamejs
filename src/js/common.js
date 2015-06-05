function new_int(count) {
    var a = new Array(count);
    for (var i = 0; i < count; i++) {
        a[i] = 0;
    }
    return a;
}
function new_float(count) {
    count = 0 | count;
    var a = new Array(count);
    for (var i = 0; i < count; i++) {
        a[i] = 0.;
    }
    return a;
}
function new_double(count) {
    return new_float(count);
}
function new_float_n(args) {
    if (args.length == 1) {
        return new_float(args[0]);
    }
    var sz = args[0];
    args = args.slice(1);
    var A = [];
    for (var i = 0; i < sz; i++) {
        A.push(new_float_n(args));
    }
    return A;
}
function new_int_n(args) {
    if (args.length == 1) {
        return new_int(args[0]);
    }
    var sz = args[0];
    args = args.slice(1);
    var A = [];
    for (var i = 0; i < sz; i++) {
        A.push(new_int_n(args));
    }
    return A;
}


function new_byte(count) {
    return new_int(count);
}

Arrays = {};
Arrays.fill_byte = function () {
    throw 'not implemented';
};

System = {};


System.arraycopy = function(src, srcPos, dest, destPos, length) {
    var srcEnd = srcPos + length;
    while (srcPos < srcEnd)
        dest[destPos++] = src[srcPos++];
};