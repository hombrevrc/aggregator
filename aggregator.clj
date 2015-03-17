;;;
;;;  AGGREGATOR
;;;  MT4 backtest report data aggregator & optimizer for multi-strategy, multi-currency backtesting.
;;;  (C) Tom Szilagyi 2011.
;;;

(import '(java.io BufferedReader BufferedWriter FileReader FileWriter IOException))
(import '(java.text SimpleDateFormat FieldPosition))
(import '(java.util Date Calendar GregorianCalendar))

(defmacro log[& args]
  `(doto *out*
     (.write (format ~@args))
     (.flush)))

(defmacro nodiv0[& body]
  `(try ~@body (catch ArithmeticException _# 0.0)))

(def dateformatter (SimpleDateFormat. "yyyy.MM.dd HH:mm"))

(defn format-nil[fstr blank val]
  (if (or (nil? val)
	  (and (number? val)
	       (= 0 val)))
    blank
    (format fstr val)))

(defn format-date[datetime]
  (if (nil? datetime)
    "                   "
    (. (. dateformatter format datetime (StringBuffer.) (FieldPosition. 0)) toString)))

(defn print-report-table[pos]
  (log "%17s %7s %6d  %s %13s %6d %6.2f %9.4f %s %s %s %s %8.2f %8.2f\n"
       (:strategy pos)
       (:currency pos)
       (:tr-no pos)
       (format-date (:datetime pos))
       (:type pos)
       (:op-no pos)
       (:lots pos)
       (:price pos)
       (format-nil "%9.4f" "         " (:sl pos))
       (format-nil "%9.4f" "         " (:tp pos))
       (format-nil "%+9.2f" "         " (:profit pos ))
       (format-nil "%9.2f" "        " (:balance pos))
       (:risk pos)
       (:open-lots pos)))

(defn list-report-table[report-table]
  (log "         Strategy  C.Pair  Tr.no         Date/Time          Type  Op.no   Lots     Price     SLoss   TProfit    Profit   Balance   Risked OpenLots\n")
  (log "==================================================================================================================================================\n")
  (loop [pos (first report-table)
	 posrem (rest report-table)]
    (print-report-table pos)
    (if (not (empty? posrem))
      (recur (first posrem) (rest posrem)))))

(defn save-report-csv[report-table]
  (let [out-stream (BufferedWriter. (FileWriter. "aggregator.csv"))]
    (loop [pos (first report-table)
	   posrem (rest report-table)]
      (. out-stream write (format "%s,%s,%d,%s,%s,%d,%.2f,%.4f,%.4f,%.4f,%.2f,%.2f,%.2f,%.2f\n"
				  (:strategy pos)
				  (:currency pos)
				  (:tr-no pos)
				  (format-date (:datetime pos))
				  (:type pos)
				  (:op-no pos)
				  (:lots pos)
				  (:price pos)
				  (:sl pos)
				  (:tp pos)
				  (:profit pos)
				  (:balance pos)
				  (:risk pos)
				  (:open-lots pos)))
      (if (not (empty? posrem))
	(recur (first posrem) (rest posrem))
	(. out-stream close)))))

(def report-table (ref '()))

(defn push-report[strategy currency
		  tr-no datetime type op-no lots price sl tp profit balance]
  ;(log "%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s\n" strategy currency
  ;     tr-no datetime type op-no lots price sl tp profit balance)
  (dosync (ref-set report-table (conj @report-table
				      {:strategy strategy
				       :currency currency
				       :tr-no tr-no
				       :datetime datetime
				       :type type
				       :op-no op-no
				       :lots lots
				       :price price
				       :sl sl
				       :tp tp
				       :profit profit
				       :balance balance}))))

(defn read-report[strategy currency long-lots short-lots]
  (let [in-stream (BufferedReader. (FileReader. (str "data/" strategy "/" currency ".csv")))]
    (loop [c (try
	       (. in-stream readLine)
	       (catch Exception _ nil))
	   opno-map {}]
      (if (not (nil? c))
	; format of input line: tr-no datetime type op-no lots price sl tp profit balance
	(let [p1 (. c indexOf "," 0)
	      p2 (. c indexOf "," (+ 1 p1))
	      p3 (. c indexOf "," (+ 1 p2))
	      p4 (. c indexOf "," (+ 1 p3))
	      p5 (. c indexOf "," (+ 1 p4))
	      p6 (. c indexOf "," (+ 1 p5))
	      p7 (. c indexOf "," (+ 1 p6))
	      p8 (. c indexOf "," (+ 1 p7))
	      p9 (. c indexOf "," (+ 1 p8))
	      
	      s1 (. c substring 0 p1)
	      s2 (. c substring (+ 1 p1) p2)
	      s3 (. c substring (+ 1 p2) p3)
	      s4 (. c substring (+ 1 p3) p4)
	      s5 (. c substring (+ 1 p4) p5)
	      s6 (. c substring (+ 1 p5) p6)
	      s7 (. c substring (+ 1 p6) p7)
	      s8 (. c substring (+ 1 p7) p8)
	      s9 (. c substring (+ 1 p8) p9)
	      s10 (. c substring (+ 1 p9))
	      
	      tr-no (Integer. s1)
	      datetime (. dateformatter parse s2)
	      type s3
	      op-no (Integer. s4)
	      lots (Double. s5)
	      price (Double. s6)
	      sl (Double. s7)
	      tp (Double. s8)
	      profit (Double. s9)
	      balance (Double. s10)

	      ; compute & store lots multiplier for each op-no on trade open
	      new-opno-map (if (or (. type startsWith "buy") (. type startsWith "sell"))
			     (assoc opno-map op-no
				    (if (. type startsWith "buy")
				      (/ long-lots lots)
				      (/ short-lots lots)))
			     opno-map)
	      multiplier (get new-opno-map op-no)]

	  (push-report strategy currency
		       tr-no datetime type op-no (* lots multiplier) price sl tp (* profit multiplier) balance)
	  (recur (. in-stream readLine) new-opno-map))))
    (. in-stream close)))

(def config-map (ref '{}))
(defn push-config[strategy currency long-lots short-lots]
  (log "%17s %7s  %6.2f %6.2f\n" strategy currency long-lots short-lots)
  (if (nil? (get @config-map strategy))
    (dosync (ref-set config-map (assoc @config-map strategy (list currency))))
    (dosync (ref-set config-map (assoc @config-map strategy (conj (get @config-map strategy) currency)))))
  (read-report strategy currency long-lots short-lots))

(def lots-multiplier (ref 1.0))
(defn read-config[]
  (let [in-stream (BufferedReader. *in*)]
    (loop [c (try
	       (. in-stream readLine)
	       (catch Exception _ nil))]
      (if (not (nil? c))
	(do
	  (if (not (. c startsWith "#"))
	    ; any line may be a lots-multiplier to change the global multiplier to use hereafter
	    (if (. c startsWith "lots-multiplier")
	      (let [p1 (. c indexOf " " 0)
		    s1 (. c substring (+ 1 p1))
		    l-m (Double. s1)]
		;(log "lots-multiplier = %f\n" l-m)
		(dosync (ref-set lots-multiplier l-m)))
	      ; format of regular input line: strategy currency long-lots short-lots
	      (let [p1 (. c indexOf " " 0)
		    p2 (. c indexOf " " (+ 1 p1))
		    p3 (. c indexOf " " (+ 1 p2))
		    
		    s1 (. c substring 0 p1)
		    s2 (. c substring (+ 1 p1) p2)
		    s3 (. c substring (+ 1 p2) p3)
		    s4 (. c substring (+ 1 p3))
		    
		    strategy s1
		    currency s2
		    long-lots (Double. s3)
		    short-lots (Double. s4)]
		
		(push-config strategy currency
			     (* long-lots @lots-multiplier)
			     (* short-lots @lots-multiplier)))))
	  (recur (. in-stream readLine)))))
    (. in-stream close)))

(defn acc-profits[report-table]
  (loop [r (first report-table)
	 rrem (rest report-table)
	 balance (:balance r)
	 new-report-table '()]
    (let [new-balance (+ balance (:profit r))
	  updated-report-table (conj new-report-table (assoc r :balance new-balance))]
      (if (not (empty? rrem))
	(recur (first rrem) (rest rrem) new-balance updated-report-table)
	(reverse updated-report-table)))))

(defn fix-trnos[report-table]
  (loop [r (first report-table)
	 rrem (rest report-table)
	 trno (:tr-no r)
	 new-report-table '()]
    (let [updated-report-table (conj new-report-table (assoc r :tr-no trno))]
    (if (not (empty? rrem))
      (recur (first rrem) (rest rrem) (+ 1 trno) updated-report-table)
      (reverse updated-report-table)))))

(def opno-subst-list (ref '()))
(defn opno-lookup[r]
  (loop [sub (first @opno-subst-list)
	 rsub (rest @opno-subst-list)]
    (if (and (= (:strategy sub) (:strategy r))
	     (= (:currency sub) (:currency r))
	     (= (:op-no-orig sub) (:op-no r)))
      (:op-no-new sub)
      (if (not (empty? rsub))
	(recur (first rsub) (rest rsub))
	nil))))

(defn fix-opnos[report-table]
  (loop [r (first report-table)
	 rrem (rest report-table)
	 op-no 0
	 new-report-table '()]
    (if (not (nil? r))
      (if (or (= (:type r) "buy stop")
	      (= (:type r) "sell stop")
	      (= (:type r) "buy limit")
	      (= (:type r) "sell limit")
	      (and (= (:type r) "buy") (nil? (opno-lookup r)))
	      (and (= (:type r) "sell") (nil? (opno-lookup r))))
	(do ; in case of a new op, op-no is incremented
	    ; also noted is the strategy,currency,op-no for which new op-no is to be substituted from now on.
	  (dosync (ref-set opno-subst-list (conj @opno-subst-list {:strategy (:strategy r)
								   :currency (:currency r)
								   :op-no-orig (:op-no r)
								   :op-no-new (+ 1 op-no)})))
	  (recur (first rrem) (rest rrem) (+ 1 op-no) (conj new-report-table (assoc r :op-no (+ 1 op-no)))))
	; in other cases we should already have a new op-no to substitute
	(recur (first rrem) (rest rrem) op-no (conj new-report-table (assoc r :op-no (opno-lookup r)))))
      (reverse new-report-table))))

(defn calc-risk[price sl lots]
  (let [point (if (> price 10.0)
		  0.01
		  0.0001)
	pmul (/ 1 point)]
  (* pmul lots (Math/abs (- price sl)))))

(defn calc-openlots[report-table]
  (loop [r (first report-table)
	 rrem (rest report-table)
	 risk-map {} ; map of positions with rated risk
	 open-map {} ; map of positions with unrated risk, contains lot sizes instead
	 new-report-table '()]
    (if (not (nil? r))
      (let [new-risk-map (if (or (= (:type r) "buy")
				 (= (:type r) "sell")
				 (= (:type r) "modify"))
			   (if (not (= (:sl r) 0.0))
			     (assoc risk-map (:op-no r) (calc-risk (:price r) (:sl r) (:lots r)))
			     (dissoc risk-map (:op-no r)))
			   (if (or (. (:type r) startsWith "close")
				   (= (:type r) "t/p")
				   (= (:type r) "s/l"))
			     (dissoc risk-map (:op-no r))
			     risk-map))
	    new-open-map (if (or (= (:type r) "buy")
				 (= (:type r) "sell")
				 (= (:type r) "modify"))
			   (if (= (:sl r) 0.0)
			     (assoc open-map (:op-no r) (:lots r))
			     (dissoc open-map (:op-no r)))
			   (if (or (. (:type r) startsWith "close")
				   (= (:type r) "t/p")
				   (= (:type r) "s/l"))
			     (dissoc open-map (:op-no r))
			     open-map))
	    risk (float (reduce + (vals new-risk-map)))
	    open-lots (float (reduce + (vals new-open-map)))]
	
	(recur (first rrem) (rest rrem) new-risk-map new-open-map
	       (conj new-report-table (assoc r :risk risk :open-lots open-lots))))
      (reverse new-report-table))))

(def kelly-stats (ref '()))
(defn get-type-by-opno[op-no report-table]
  (loop [r (first report-table)
	 rrem (rest report-table)]
    (if (= (:op-no r) op-no)
      (:type r)
      (if (not (empty? rrem))
	(recur (first rrem) (rest rrem))))))
  
(defn collect-kelly-stats[report-table]
  (loop [r (first report-table)
	 rrem (rest report-table)
	 kelly-stats '()]
    (if (not (nil? r))
      (let [strategy (:strategy r)
	    currency (:currency r)
	    profit (:profit r)]
	(if (or (. (:type r) startsWith "close")
		(= (:type r) "t/p")
		(= (:type r) "s/l"))
	  (let [type (get-type-by-opno (:op-no r) report-table)]
	    (recur (first rrem) (rest rrem) (conj kelly-stats (list strategy currency type profit))))
	  (recur (first rrem) (rest rrem) kelly-stats)))
      (reverse kelly-stats))))

(defn print-kelly-stats[kelly-stats strategy currency]
  (let [longs (filter #(and (= (first %1) strategy)
			    (= (second %1) currency)
			    (. (nth %1 2) startsWith "buy")) kelly-stats)
	long-winners (filter #(> (nth %1 3) 0) longs)
	long-losers (filter #(<= (nth %1 3) 0) longs)
	long-win-avg (nodiv0 (/ (reduce + (map #(nth %1 3) long-winners)) (count long-winners)))
	long-lose-avg (nodiv0 (/ (reduce + (map #(nth %1 3) long-losers)) (count long-losers)))
	b-long (nodiv0 (/ long-win-avg (* -1 long-lose-avg)))
	p-long (nodiv0 (/ (count long-winners) (count longs)))
	k-long (nodiv0 (/ (- (* b-long p-long) (- 1 p-long)) b-long))
	
	shorts (filter #(and (= (first %1) strategy)
			    (= (second %1) currency)
			    (. (nth %1 2) startsWith "sell")) kelly-stats)
	short-winners (filter #(> (nth %1 3) 0) shorts)
	short-losers (filter #(<= (nth %1 3) 0) shorts)
	short-win-avg (nodiv0 (/ (reduce + (map #(nth %1 3) short-winners)) (count short-winners)))
	short-lose-avg (nodiv0 (/ (reduce + (map #(nth %1 3) short-losers)) (count short-losers)))
	b-short (nodiv0 (/ short-win-avg (* -1 short-lose-avg)))
	p-short (nodiv0 (/ (count short-winners) (count shorts)))
	k-short (nodiv0 (/ (- (* b-short p-short) (- 1 p-short)) b-short))]
    
    (log "%17s %7s | %4d %4d %6.2f%% %8.2f %9.2f %7.4f | %4d %4d %6.2f%% %8.2f %9.2f %7.4f\n"
	 strategy currency
	 (count long-winners) (count longs) (* (/ (count long-winners) (count longs)) 100.0)
	 long-win-avg long-lose-avg (float k-long)
	 (count short-winners) (count shorts) (* (/ (count short-winners) (count shorts)) 100.0)
	 short-win-avg short-lose-avg (float k-short))))

(defn print-kelly-report[config-map kelly-stats]
  (loop [s (first (reverse (keys config-map)))
	 sr (rest (reverse (keys config-map)))]
    (loop [c (first (reverse (get config-map s)))
	   cr (rest (reverse (get config-map s)))]
      (print-kelly-stats kelly-stats s c)
      (if (not (empty? cr))
	(recur (first cr) (rest cr))))
    (if (not (empty? sr))
      (recur (first sr) (rest sr)))))

(log "AGGREGATOR\n")
(log "MT4 backtest report data aggregator & optimizer for multi-strategy, multi-currency backtesting.\n")
(log "(C) Tom Szilagyi 2011.\n")
(log "\nCONFIGURATION TABLE:\n\n")
(log "         Strategy  C.Pair   LLots  SLots\n")
(log "========================================\n")
(read-config)
(dosync (ref-set report-table (sort-by #(vec (map % [:datetime :strategy :currency :tr-no])) @report-table)))
(dosync (ref-set report-table (acc-profits @report-table)))
(dosync (ref-set report-table (fix-trnos @report-table)))
(dosync (ref-set report-table (fix-opnos @report-table)))
(dosync (ref-set report-table (calc-openlots @report-table)))
(dosync (ref-set kelly-stats (collect-kelly-stats @report-table)))
(log "\n\nKELLY REPORT:\n")
(log "                          |                     LONG                     |                     SHORT                   \n")
(log "         Strategy  C.Pair | NWin NTot  PctWin   AvgWin   AvgLose   Kelly | NWin NTot  PctWin   AvgWin   AvgLose   Kelly\n")
(log "==========================|==============================================|=============================================\n")
(print-kelly-report @config-map @kelly-stats)
(log "\n\nTRADE REPORT:\n\n")
(list-report-table @report-table)
(save-report-csv @report-table)
(log "\n\nEND OF REPORT\n")
