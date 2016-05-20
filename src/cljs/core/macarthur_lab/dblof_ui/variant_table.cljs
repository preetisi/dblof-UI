(ns macarthur-lab.dblof-ui.variant-table
  (:require
   clojure.string
   [dmohs.react :as react]
   [macarthur-lab.dblof-ui.utils :as u]
   ))


(def columns [{:key :variant_id :label "Variant"}
              {:key :chrom :label "Chrom"}
              {:key :pos :label "Position"}
              {:key :allele_freq :label "Allele Frequency"}
              {:key :hom_count :label "Hom Count"}])


(react/defc Component
  {:render
   (fn [{:keys [props state]}]
     [:div {:style {:marginTop 50}}
      [:div {:style {:display "flex"}}
       (map (fn [col]
              [:div {:style {:flex "0 0 20%" :padding 10 :boxSizing "border-box"
                             :overflow "hidden" :textOverflow "ellipsis"}}
               (:label col)])
            columns)]
      [:div {}
       (map (fn [x]
              [:div {:style {:display "flex"}}
               (map (fn [col]
                      [:div {:style {:flex "0 0 20%" :padding 10 :boxSizing "border-box"
                                     :overflow "hidden" :textOverflow "ellipsis"}}
                       (get x (name (:key col)))])
                    columns)])
            (:variants @state))]])
   :component-did-mount
   (fn [{:keys [this]}]
     (this :load-variants))
   :load-variants
   (fn [{:keys [props state]}]
     (let [exec-mongo-url (str (:api-url-root props) "/exec-mongo")
           gene-name-uc (clojure.string/upper-case (:gene-name props))]
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
                                     :query {:genes {:$in [gene-id]}}
                                     :projection {:variant_id 1 :chrom 1
                                                  :pos 1 :allele_count 1
                                                  :hom_count 1 :allele_freq 1}
                                     :options {:limit 10000}})
                             :on-done
                             (fn [{:keys [get-parsed-response]}]
                               (swap! state assoc :variants (get-parsed-response)))})))})))})
