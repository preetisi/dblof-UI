(ns macarthur-lab.dblof-ui.variant-table
  (:require
   clojure.string
   [dmohs.react :as react]
   [macarthur-lab.dblof-ui.utils :as u]
   ))


(def columns [{:key :variant_id :label "Variant" :width "20%"
               :format (fn [variant-id]
                         [:a {:href (str "http://exac.broadinstitute.org/variant/" variant-id)
                              :target "_blank"
                              :style {:textDecoration "none"}}
                          variant-id])}
              {:key :chrom :label "Chrom" :width "10%"}
              {:key :pos :label "Position" :width "10%"}
              {:key :filter :label "Filter" :width "10%"}
              {:key :allele_count :label "Allele Count" :width "10%"}
              {:key :allele_num :label "Allele Number" :width "10%"}
              {:key :hom_count :label "Number of Homozygotes" :width "10%"}
              {:key :allele_freq :label "Allele Frequency" :width "20%" :format #(.toFixed % 8)}
            ])


(defn- create-variants-query [gene-id]
  {:genes {:$in [gene-id]}
   :filter "PASS"
   :vep_annotations
   {:$elemMatch
    {:Gene gene-id
     :LoF {:$ne ""}}}})


(def variants-projection
  (reduce (fn [r col] (assoc r (:key col) 1)) {:vep_annotations 1} columns))


(react/defc Component
  {:get-initial-state
   (fn []
     {:sort-column-key :variant_id
      :sort-reversed? true})
   :render
   (fn [{:keys [props state]}]
     (let [{:keys [variants]} props
           {:keys [sort-column-key sort-reversed?]} @state]
       [:div {:style {:paddingTop 10 :fontSize "80%"}}
        [:div {:style {:display "flex" :alignItems "center" :fontWeight "bold"}}
         (map (fn [col]
                [:div {:style {:flex (str "0 0 " (:width col)) :padding 10 :boxSizing "border-box"
                               :cursor "pointer"}
                       :onClick #(swap! state assoc
                                        :sort-column-key (:key col)
                                        :sort-reversed? (not sort-reversed?))}
                 (:label col)
                 (when (= (:key col) sort-column-key)
                   [:span {:style {:marginLeft 6 :fontSize "50%"}}
                    (if sort-reversed? "▲" "▼")])])
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
                            (let [format (or (:format col) identity)]
                              (format (get x (name (:key col)))))])
                         columns)])
                 (sort-by #(get % (name sort-column-key)) variants)))]
          [:div {:style {:padding "1em 0 10em 10px"}}
           "Loading variants data..."])]))})
