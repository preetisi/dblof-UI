(ns macarthur-lab.dblof-ui.variant-table
  (:require
   clojure.string
   [dmohs.react :as react]
   [macarthur-lab.dblof-ui.utils :as u]
   ))

(def columns [{:label "Variant" :width "15%"
               :format (fn [variant]
                         [:a {:href (u/get-exac-variant-page-href
                                     (get variant "Chrom") (get variant "Position")
                                     (get variant "Reference") (get variant "Alternate"))
                              :target "_blank"
                              :style {:textDecoration "none"}}
                          (str (get variant "Chrom") ":" (get variant "Position")
                           " " (get variant "Reference") " / " (get variant "Alternate"))])}
              {:key "Chrom" :label "Chrom" :width "6%"}
              {:key "Position" :label "Position" :width "10%"}
              {:key "Consequence" :label "Consequence" :width "12%"}
              {:key "Annotation" :label "Annotation" :width "10%"}
              {:label "Flags" :width "7%"
               :format (fn [variant]
                         [:span { :style {:backgroundColor "#e62e00" :color "#f2f2f2"
                                          :fontWeight "normal" :paddingTop "2px"
                                          :paddingBottom "2px" :borderRadius "4px"}}
                          (cond
                           (identical? (get variant "Manual Annotation") "no") "Manual"
                           (not (clojure.string/blank? (get variant "Flags"))) "LOFTEE"
                           (not (identical? (get variant "Manual Annotation") "no")) ""
                           (clojure.string/blank? (get variant "Flags")) ""
                           )])}
              {:key "Allele Count" :label "Allele Count" :width "6%"}
              {:key "Allele Number" :label "Allele Number" :width "8%"}
              {:key "Number of Homozygotes" :label "Number of Homozygotes" :width "11%"}
              {:key "Allele Frequency" :label "Allele Frequency" :width "10%"}
              {:key "Manual Annotation" :label "Manual Annotation" :width "10%"}
            ])

(defn create-variants-query [gene-id]
  {:genes {:$in [gene-id]}
   :filter "PASS"
   :vep_annotations
   {:$elemMatch
    {:Gene gene-id
     :LoF {:$ne ""}}}})

(def query-projection
  (reduce (fn [r k] (assoc r k 1))
          {}
          #{:id :pos :allele_count :allele_freq :vep_annotations}))

(react/defc Component
  {:get-initial-state
   (fn []
     {:sort-column-key "Allele Count"
      :sort-reversed? true})
   :render
   (fn [{:keys [props state]}]
     (let [{:keys [variants]} props
           {:keys [sort-column-key sort-reversed?]} @state]
       [:div {:style {:paddingTop 10 :fontSize "80%"}}
        [:div {:style {:display "flex" :alignItems "center" :fontWeight "bold"}}
         (map (fn [col]
                [:div {:style {:flex (str "0 0 " (:width col)) :padding 10 :boxSizing "border-box"
                               :cursor "pointer" :position "relative" :overflow "hidden"}
                       :onClick #(swap! state assoc
                                        :sort-column-key (or (:key col) "Allele Count")
                                        :sort-reversed? (if (= (or (:key col) "Allele Count")
                                                               sort-column-key)
                                                          (not sort-reversed?)
                                                          false))}
                 (:label col)
                 [:span {:style {:position "absolute" :paddingLeft 2
                                 :color (when-not (= (or (:key col) "Allele Count") sort-column-key)
                                          "#ccc")}}
                  (if (= (or (:key col) "Allele Count") sort-column-key)
                    (if sort-reversed? "↑" "↓")
                    "⇅")]])
              columns)]
        [:div {:style {:backgroundColor "#D2D4D8" :height 2 :margin "0 10px"}}]
        (if variants
          [:div {}
           (interpose
            [:div {:style {:backgroundColor "#D2D4D8" :height 1 :margin "0 10px"}}]
            (map (fn [x]
                   [:div {:style {:display "flex" :whiteSpace "nowrap" :fontWeight 100}}
                    (map (fn [col]
                           [:div {:style {:flex (str "0 0 " (:width col))
                                          :padding "6px 10px" :boxSizing "border-box"
                                          :overflow "hidden" :textOverflow "ellipsis"}}
                            (let [value (if (contains? col :key) (get x (:key col)) x)
                                  format (or (:format col) identity)]
                              (format value))])
                         columns)])
                 (let [sorted (sort-by #(get % (name sort-column-key)) variants)]
                   (if sort-reversed? (reverse sorted) sorted))))]
          [:div {:style {:padding "1em 0 10em 10px"}}
           "Loading variants data..."])]))})
