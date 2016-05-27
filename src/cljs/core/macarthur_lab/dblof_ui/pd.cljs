(ns macarthur-lab.dblof-ui.pd
  (:require
   clojure.string
   [dmohs.react :as react]
   [macarthur-lab.dblof-ui.utils :as u]
   ))


(def columns [{:key :variant_id :label "Variant" :width "20%"}
              {:key :chrom :label "Chrom" :width "10%"}
              {:key :pos :label "Position" :width "10%"}
              {:key :filter :label "Filter" :width "10%"}
              {:key :allele_count :label "Allele Count" :width "10%"}
              {:key :allele_num :label "Allele Number" :width "10%"}
              {:key :hom_count :label "Number of Homozygotes" :width "10%"}
              {:key :allele_freq :label "Allele Frequency" :width "20%" :format #(.toFixed % 8)}])


(defn- create-variants-query [gene-id]
  {:genes {:$in [gene-id]}
   :vep_annotations
   {:$elemMatch
    {:Gene gene-id
     :LoF {:$ne ""}}}})


(def variants-projection
  (reduce (fn [r col] (assoc r (:key col) 1)) {:vep_annotations 1} columns))


(defn- create-frequencies [exon-start exon-end positions]
  (let [sizes (reduce
               (fn [r p]
                 (conj r (- p (apply + exon-start r))))
               []
               positions)
        sizes (conj sizes (- exon-end (apply + exon-start sizes)))]
    [:div {:style {:display "flex"}}
     (interpose
      [:div {:style {:flex "1 1 1" :position "relative"}}
       [:div {:style {:position "absolute" :bottom 4 :height 10 :width 1
                      :backgroundColor "red"}}]]
      (map (fn [s]
             [:div {:style {:flex (str s " " s " auto")}}])
           sizes))]))


(react/defc Component
  {:render
   (fn [{:keys [state]}]
     (let [{:keys [status variants]} @state
           {:keys [code data]} status
           variants (map #(get % "pos") variants)
           exons (when (and data variants)
                   (map
                    (fn [{:strs [start size]}]
                      [:div {:style {:flex (str size " " size " auto") :backgroundColor "#333"}}
                       (let [variants (filter #(and (>= % start) (<= % (+ start size))) variants)]
                         (create-frequencies start (+ start size) variants))])
                    (sort-by #(get % "start") data)))]
       [:div {:style {:backgroundColor "white" :padding "20px 16px"}}
        [:div {:style {:fontWeight "bold"}} "Positional distribution"]
        [:div {:style {:marginTop 8 :height 1 :backgroundColor "#959A9E"}}]
        [:div {:style {:height 100 :position :relative
                       :backgroundColor (when-not (= code :loaded) "#eee")}}
         [:div {:style {:height 1 :backgroundColor "#ccc"
                        :position "absolute" :width "100%" :bottom 30}}]
         [:div {:style {:position "absolute" :bottom 15 :height 30 :width "100%"
                        :display "flex"}}
          [:div {:style {:flex "5 5 auto"}}]
          (interpose [:div {:style {:flex "10 10 auto"}}] exons)
          [:div {:style {:flex "5 5 auto"}}]]]]))
   :component-will-receive-props
   (fn [{:keys [this props state next-props]}]
     (when-not (apply = (map :gene-name [props next-props]))
       (swap! state dissoc :status)
       (this :load-data (:gene-name next-props))))
   :component-did-mount
   (fn [{:keys [this props]}] (this :load-data (:gene-name props)))
   :load-data
   (fn [{:keys [this props state]} gene-name]
     (this :load-variants gene-name)
     (u/ajax {:url (str (:api-url-root props) "/exec-sql")
              :method :post
              :data (u/->json-string
                     {:sql (str "select start, stop-start size from gene_exons_v2 e\n"
                                "inner join gene_symbols s on e.gene_id=s.gene_id\n"
                                "where s.symbol=?")
                      :params [(clojure.string/upper-case gene-name)]})
              :on-done (fn [{:keys [get-parsed-response]}]
                         (swap! state assoc :status
                                {:code :loaded :data (get (get-parsed-response) "rows")}))}))
   :load-variants
   (fn [{:keys [props state]} gene-name]
     (let [exec-mongo-url (str (:api-url-root props) "/exec-mongo")
           gene-name-uc (clojure.string/upper-case gene-name)]
       (u/ajax {:url exec-mongo-url
                :method :post
                :data (u/->json-string
                       {:collection-name "genes"
                        :query {:gene_name_upper {:$eq gene-name-uc}}
                        :projection {:gene_id 1}})
                :on-done
                (fn [{:keys [get-parsed-response]}]
                  (let [gene-id (get-in (get-parsed-response) [0 "gene_id"])]
                    (u/ajax {:url exec-mongo-url
                             :method :post
                             :data (u/->json-string
                                    {:collection-name "variants"
                                     :query (create-variants-query gene-id)
                                     :projection variants-projection
                                     :options {:limit 10000}})
                             :on-done
                             (fn [{:keys [get-parsed-response]}]
                               (swap! state assoc :variants (get-parsed-response)))})))})))})
