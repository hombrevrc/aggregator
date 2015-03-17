set terminal png size 800,320 small
set output "aggregator.png"

set datafile separator ","
set xdata time
set timefmt "%Y.%m.%d %H:%M"
unset key
set grid

set multiplot

set origin 0.0,0.25
set size 1,0.75
set lmargin 10
set bmargin 0
set format x ""
set ylabel "Equity (non-floating), $ Risked" offset 0,2
#set ytics 10000,5000
set yrange [0:*] writeback
set mytics 5
set style line 1 lc rgbcolor "blue"
plot "aggregator.csv" using 4:12 with lines ls 1

set style line 2 lc rgbcolor "red"
set yrange restore
plot "aggregator.csv" using 4:13 with lines ls 2

set origin 0.0,0.0
set size 1,0.25
set tmargin 0
set bmargin 2
set format x "%Y/%m"
set ytics 5
set mytics 5
set ylabel ""
set ylabel "Unrated lots" offset -4,0
#set style line linecolor rgb "blue"
set yrange [0:*]
plot "aggregator.csv" using 4:14 with lines

set nomultiplot
