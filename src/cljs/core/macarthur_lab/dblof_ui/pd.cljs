(ns macarthur-lab.dblof-ui.pd
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
            [:a {:href (u/get-exac-variant-page-href (:id v))
                 :target "_blank"
                 :style {:position "absolute" :bottom 4 :height (* bin 20) :width 3
                         :backgroundColor "rgba(36,175,178,0.5)"}}]]))
       variants))]))


(defn- create-segments [sorted-exons sorted-variants]
  (let [segments (reduce
                  (fn [r {:strs [start size]}]
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
                            {:exon? true :start start :size size
                             :variants (filter #(and (>= (:position %) start)
                                                     (<= (:position %) (+ start size)))
                                               sorted-variants)})))
                  []
                  sorted-exons)]
    (conj
     segments
     (let [last-segment (last segments)]
       {:exon? false :start (inc (:start last-segment)) :size 100}))))

(react/defc Component
  {:render
   (fn [{:keys [props state]}]
     (let [{:keys [variants]} props
           {:keys [status]} @state
           {:keys [code data]} status
           variants (map (fn [v]
                           {:id (get v "variant_id")
                            :position (get v "pos")
                            :allele-count (get v "allele_count")
                            :allele-freq (get v "allele_freq")})
                         variants)
           segments (when (and data variants)
                      (create-segments (sort-by #(get % "start") data)
                                       (sort-by :position variants))
                      )
           reversed? (when (< (get (nth (sort-by #(get % "start") data) 1) "exon_number")
                              (get (last (sort-by #(get % "start") data)) "exon_number"))
                       true)
           ]
       (u/cljslog (get (nth (sort-by #(get % "start") data) 1) "exon_number"))
       (u/cljslog (get (last (sort-by #(get % "start") data)) "exon_number"))
       [:div {:style {:backgroundColor "white" :padding "20px 16px 20px 32px"}}
        (style/create-underlined-title "Positional distribution")
        [:div {:style {:height 150 :position :relative
                       :backgroundColor (when-not (= code :loaded) "#eee")}}
         (if reversed?
         [:div {:style {:height 1 :backgroundColor "#ccc" :fontSize "25px" :fontWeight "bolder"
                      :textShadow "2px 0"  
                      :position "absolute" :width "100%" :bottom 30 :left -20}} "←"]
         [:div {:style {:height 1 :backgroundColor "#ccc" :fontSize "20px" :fontWeight "bolder"
                        :position "absolute" :width "100%" :bottom 30 :left -20}} "→"]
           )

         [:div {:style {:position "absolute" :bottom 15 :height 30 :width "100%"
                        :display "flex"}}
          (map
           (fn [{:keys [exon? start size variants]}]
             [:div {:style {:flex (str (if exon? size 10) " " (if exon? size 10) " auto")
                            :backgroundColor (when exon? "#333")}}
              (create-frequencies start (+ start size) variants)])
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
                     {:sql (str "select start, stop-start size, exon_number from gene_exons_v2 e\n"
                                "inner join gene_symbols s on e.gene_id=s.gene_id\n"
                                "where s.symbol=?")
                      :params [(clojure.string/upper-case gene-name)]})
              :on-done (fn [{:keys [get-parsed-response]}]
                         (swap! state assoc :status
                                {:code :loaded :data (get (get-parsed-response) "rows")}))}))})
