(
echo 'function lamejs() {'
browserify --list src/js/lametest.js  | grep lamejs | xargs cat | grep -v -e 'common\..*;' -e 'require(' -e 'module.exports = .*;$' | sed 's/^module.exports = {/var module_exports = {/';
echo 'this.Mp3Encoder = Mp3Encoder;'
echo '}'
)>lame.all.js

#cc=closure-compiler
cc="java -jar ~/java/compiler.jar"
$cc lame.all.js --language_in ECMASCRIPT5 --js_output_file lame.min.js
