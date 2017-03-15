(ns macarthur-lab.dblof-ui.variant-table
  (:require
   clojure.string
   [dmohs.react :as react]
   [macarthur-lab.dblof-ui.floating :as floating]
   [macarthur-lab.dblof-ui.style :as style]
   [macarthur-lab.dblof-ui.utils :as u]
   ))


(defn- create-float [left top text]
  (react/create-element
   [:div {:style {:position "fixed" :left left :top top}}
    [:div {:style {:backgroundColor "#555" :borderRadius 2
                   :padding "2px 4px" :color "#eee" :fontWeight 100}}
     text]]))

(react/defc FlagComponent
  {:render
   (fn [{:keys [props state refs locals]}]
     [:span {:ref "root"
             :onMouseOver (fn []
                             (let [rect (.getBoundingClientRect (@refs "root"))
                                   float (create-float (+ (.-right rect) 4) (.-top rect)
                                                       (:popup-text props))]
                               (swap! locals assoc :float float)
                               (floating/add-float float)))
             :onMouseOut #(floating/remove-float (:float @locals))}
      (:child props)])})

(def columns [{:label "Variant" :width "10%"
               :format (fn [variant]
                         [:a {:href (u/get-exac-variant-page-href
                                     (get variant "Chrom") (get variant "Position")
                                     (get variant "Reference") (get variant "Alternate"))
                              :target "_blank"
                              :style {:textDecoration "none"}}
                               (str (get variant "Chrom") ":" (get variant "Position")
                               " " (get variant "Reference") " / " (get variant "Alternate"))])}
              {:key "Consequence" :label "Consequence" :width "10%"}
              {:key "Annotation" :label "Annotation" :width "10%"}
              {:label "LoF" :width "10%"
               :format (fn [variant]
                         (let [[child popup-text]
                               (cond
                                 (not (clojure.string/blank? (get variant "Flags")))
                                 [(style/create-box "LOFTEE" "#e62e00") "Filtered by LOFTEE pipeline"])]
                           (when child [FlagComponent {:child child :popup-text popup-text}])))}
              {:label "Source" :width "10%"
               :format (fn [variant]
                         (let [[child popup-text]
                               (cond
                                 (identical? (get variant "in_vanheel") 1)
                                 [(style/create-box "BiB" "green")
                                  [:span {}
                                   "data from the Born in Bradford cohort," [:br]
                                   "published in Narasimhan et al. Science 2016."]]
                                 (identical? (get variant "in_exac") 1)
                                 [(style/create-box "ExAC" "green")
                                  [:span {}
                                   "Data from ExAC browser"]]
                                 :else nil)]
                            (when child [FlagComponent {:child child :popup-text popup-text}])))}
              {:label "Flags" :width "10%"
               :format (fn [variant]
                         (let [[child popup-text]
                               (cond
                                 (= (get variant "Manual Annotation") "no")
                                 [(style/create-box "Manual" "#e62e00")
                                  [:span {}
                                   "Manual curation of read and annotation data" [:br]
                                   "suggests variant is not LoF."]]

                                 (= (get variant "Manual Annotation") "yes")
                                 [[:span {:style {:color "green"}} "✓"] "Curated LoF"]
                                 (= (get variant "Manual Annotation") "no")
                                 [[:span {:style {:color "red"}} "x"] "Curated LoF"]

                                 :else nil)]
                           (when child [FlagComponent {:child child :popup-text popup-text}])))}
              {:key "Allele Count" :label "Allele Count" :width "10%"}
              {:key "Allele Number" :label "Allele Number" :width "10%"}
              {:key "Number of Homozygotes" :label "Number of Homozygotes" :width "10%"}
              {:key "Allele Frequency" :label "Allele Frequency" :width "10%"}])

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
                               :cursor "pointer" :position "relative" :overflowX "hidden"}
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
                                          :overflowX "hidden" :textOverflow "ellipsis"}}
                            (let [value (if (contains? col :key) (get x (:key col)) x)
                                  format (or (:format col) identity)]
                              (format value))])
                         columns)])
                 (let [sorted (sort-by #(get % (name sort-column-key)) variants)]
                   (if sort-reversed? (reverse sorted) sorted))))]
          [:div {:style {:padding "1em 0 10em 10px"}}
           "Loading variants data..."])]))})
