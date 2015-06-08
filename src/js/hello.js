var a = new Int16Array([42, 43]);
var b = new Int16Array(a);
b[1] = 44;
console.log(a);
console.log(b);