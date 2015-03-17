#! /bin/sh

# This is the wrapper script to invoke AGGREGATOR from Unix CL.

if [ $# -lt 1 ] ; then
	echo "Usage: $0 <confname>"
	echo "<confname> must refer to a file named conf/<confname>.conf"
	exit 1
fi

conf=conf/$1.conf
if [ ! -f $conf ] ; then
	echo "$conf not found!"
	exit 2
fi

# this also generates aggregator.csv:
LANG=C java -cp clojure.jar clojure.main aggregator.clj < $conf > aggregator.txt

# this generates aggregator.png from aggregator.csv:
LANG=C gnuplot aggregator.plt

if [ ! -d reports ] ; then
	mkdir reports
fi

# make a nice report:
mv aggregator.png reports/$1.png
mv aggregator.txt reports/$1.txt
mv aggregator.csv reports/$1.csv

cat > reports/$1.html <<EOD
<html>
<title>$1</title>
<body>
<h2>AGGREGATOR: $1</h2>
<img src="$1.png"><br>
<pre>
EOD

cat reports/$1.txt >> reports/$1.html

cat >> reports/$1.html <<EOD
</pre>
</body>
</html>
EOD
