#! /usr/bin/awk -f

# Convert MT4 report html page to csv
# Tom Szilagyi, 2011-06-23.

BEGIN {
	balance = "10000"
	enable = 0
}

/^<img/ {
	enable = 1
}

/^<tr/ {
	if (enable == 1) {
		enable = 2
	} else if (enable > 1) {
		#print $0
		field = 0
		outline = ""
		while (length($0) > 0) {
			where = match($0, ">[a-zA-Z0-9\\.:/\\- ]*</td>")
			if (where) {
				str = substr($0, RSTART+1, RLENGTH-6)
				#print "field", field, " = ", str

				if (field == 9) {
					balance = str
					#print "new balance = ", balance
				}

				if ((field == 8) && (str == "")) {
					outline = sprintf("%s,0,%s", outline, balance)
				} else if (field > 0) {
					outline = sprintf("%s,%s", outline, str)
				} else {
					outline = sprintf("%s", str)
				}
	
				$0 = substr($0, RSTART+RLENGTH);
				field++
			} else {
				break
			}
		}
		print outline
	}
}
