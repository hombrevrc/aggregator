#! /bin/bash

# Convert all MT4 reports (*.htm) under data/ to csv

for i in `find data/ -name *.htm`; do
	dest=`echo $i | sed s/htm/csv/`
	echo "report2csv: $i -> $dest"
	awk -f report2csv.awk < $i > $dest
done
