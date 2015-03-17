#!/bin/bash
for i in conf/*.conf; do
	echo aggregator: $i
	./aggregator.sh `echo $i | sed 's/conf\///' | sed 's/.conf//'`
done
