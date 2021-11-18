(ns macarthur-lab.dblof-ui.pdconstitutive
  (:require
   clojure.string
   [dmohs.react :as react]
   [macarthur-lab.dblof-ui.style :as style]
   [macarthur-lab.dblof-ui.utils :as u]
   ))

(defn- create-frequencies [exon-start exon-end variants]
  (let [sizes (reduce
               (fn [r p]
                 (conj r (- p (apply + exon-start r))))
               []
               (map :position variants))
        sizes (conj sizes (- exon-end (apply + exon-start sizes)))]
    [:div {:style {:display "flex"}}
     (interleave
      (map (fn [s] [:div {:style {:flex (str s " " s " auto")}}]) sizes)
      (map
       (fn [v]
         (let [bin (cond
                     (= 1 (:allele-count v)) 1
                     (<= (:allele-count v) 10) 2
                     (<= (:allele-freq v) 0.01) 3
                     :else 4)]
           [:div {:style {:flex "1 1 1" :position "relative"}}
            [:a {:href (u/get-exac-variant-page-href (:chrom v)(:pos v)(:ref v)(:alt v))
                 :target "_blank"
                 :style {:position "absolute" :bottom 4 :height (* bin 20) :width 3
                         :backgroundColor "rgba(36,175,178,0.5)"}}]]))
       variants))]))

(defn c-and-nc->exon-groups [c-a-nc]
  (reduce
   (fn [first-element rest-of-vector]
     (let [current-exon-vector (last first-element)
           element (last current-exon-vector)
           stop (get element "stop")]
       (if (< (get rest-of-vector "start") stop)
         (conj (last first-element) (conj current-exon-vector rest-of-vector))
         (conj first-element [rest-of-vector]))))
   [[(first c-a-nc)]] (rest c-a-nc)))

(defn c-and-nc->exons-with-regions [sorted-exons]
  (map
   (fn [exon]
     {"start" (get (first exon) "start")
      "stop" (get (last exon) "stop")
      "size" (- (get (last exon) "stop") (get (first exon) "start"))
      "regions" exon}) (c-and-nc->exon-groups sorted-exons)))

(defn- create-segments [sorted-exons sorted-variants]
  (let [segments (vec
                  (reduce
                   (fn [r {:strs [start size regions]}]
                     (let [last-stop (if-let [last-segment (last r)]
                                       (+ (:start last-segment) (:size last-segment))
                                       0)
                           intron-start (inc last-stop)
                           intron-size (dec (- start last-stop))]
                       (conj r
                             {:exon? false :start intron-start :size intron-size
                              :variants (filter #(and (>= (:position %) intron-start)
                                                      (<= (:position %)
                                                          (+ intron-start intron-size)))
                                                sorted-variants)}
                             {:exon? true :start start :size size :regions regions
                              :variants (filter #(and (>= (:position %) start)
                                                      (<= (:position %) (+ start size)))
                                                sorted-variants)})))
                   []
                   (c-and-nc->exons-with-regions sorted-exons)))]
    (conj segments
          (let [last-segment (last segments)]
            {:exon? false :start (inc (:start last-segment)) :size 100}))))

(react/defc Component
  {:render
   (fn [{:keys [props state]}]
     (let [{:keys [variants]} props
           {:keys [status]} @state
           {:keys [code data]} status
           variants (map (fn [v]
                           {:id (get v "Variant")
                            :position (get v "Position")
                            :allele-count (get v "Allele Count")
                            :allele-freq (get v "Allele Frequency")
                            :chrom (get v "Chrom")
                            :pos (get v "Position")
                            :ref (get v "Reference")
                            :alt (get v "Alternate")})
                         variants)
           sorted-data (sort-by #(get % "start") data)
           sorted-variants (sort-by #(get % "position") variants)
           segments (when (and data variants)
                      (create-segments sorted-data sorted-variants))
           ;; check if first element of the sorted list is + or -
           reversed? (when (= (get (first sorted-data) "strand") "+")
                       true)]
       [:div {:style {:backgroundColor "white" :padding "20px 16px 20px 32px"}}
        (style/create-underlined-title "Positional distribution")

        [:div {:style {:display "flex" :alignItem "center"}}
         [:div {:style {:flex "0 0 0.9rem" :height "0.75rem" :backgroundColor "#333" :marginTop "0.2rem"}}]
         (style/add-style-for-legends "Constitutive Exon")
         [:div {:style {:flex "0 0 0.9rem" :height "0.75rem" :backgroundColor "#9c9696" :marginTop "0.2rem" :marginLeft "0.4rem"}}]
         (style/add-style-for-legends "Non-Constitutive Exon")]

        [:div {:style {:height 150 :position :relative
                       :backgroundColor (when-not (= code :loaded) "#eee")}}
         [:div {:style {:height 1 :backgroundColor "#ccc"
                        :position "absolute" :width "100%" :bottom 30}}]
         [:div {:style {:position "absolute" :bottom 15 :height 30 :width "100%"
                        :display "flex"}}
          (map
           (fn [{:keys [exon? start size regions variants]}]
             [:div {:style  {:flex (str (if exon? size 30) " " (if exon? size 30) " auto")
                            :backgroundColor (when exon? "#9c9696")}}
              (create-frequencies start (- start size) variants)
              (when exon?
                (let [exon-start start exon-size size]
                  [:div {:style {:display "flex" :height "100%"}}
                   (map
                    (fn [{:strs [start size constitutive]}]
                      [:div {:style {:flex (str size " " size " auto") :minWidth "0.5em"
                                     :backgroundColor (when (= constitutive "c") "#333")}}])
                    regions)]))])
           segments)]]]))
   :component-will-receive-props
   (fn [{:keys [this props state next-props]}]
     (when-not (apply = (map :gene-name [props next-props]))
       (swap! state dissoc :status)
       (this :load-data (:gene-name next-props))))
   :component-did-mount
   (fn [{:keys [this props]}] (this :load-data (:gene-name props)))
   :load-data
   (fn [{:keys [this props state]} gene-name]
     (u/ajax {:url (str (:api-url-root props) "/exec-sql")
              :method :post
              :data (u/->json-string
                     {:sql (str "select start, stop, constitutive, strand, stop-start size from constitutive_nonconstitutive_Exon_2 e\n"
                                "inner join gene_symbols s on e.gene_id=s.gene_id\n"
                                "where s.symbol=?")
                      :params [(clojure.string/upper-case gene-name)]})
              :on-done (fn [{:keys [get-parsed-response]}]
                         (swap! state assoc :status
                                {:code :loaded :data (get (get-parsed-response) "rows")})
                         (get (get-parsed-response) "rows"))}))})
