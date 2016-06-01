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
     (let [{:keys [variants sort-column-key sort-reversed?]} @state]
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
               (sort-by #(get % (name sort-column-key)) variants)))]]))
   :component-did-mount
   (fn [{:keys [this props]}]
     (this :load-variants (:gene-name props)))
   :component-will-receive-props
   (fn [{:keys [this props state next-props]}]
     (when-not (apply = (map :gene-name [props next-props]))
       (this :load-variants (:gene-name next-props))))
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
