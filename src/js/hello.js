function ololo(args) {
    if (args.length == 1) {
        return new Array(args[0]);
    }
    var sz = args[0];
    args = args.slice(1);
    var A = [];
    for (var i = 0; i < sz; i++) {
        A.push(ololo(args));
    }
    return A;
}

var a = ololo([2, 3, 1]);
console.log(a);