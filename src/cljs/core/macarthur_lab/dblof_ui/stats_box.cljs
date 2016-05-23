(ns macarthur-lab.dblof-ui.stats-box
  (:require
   clojure.string
   [dmohs.react :as react]
   [macarthur-lab.dblof-ui.utils :as u]
   ))


(react/defc Component
  {:render
   (fn [{:keys [state]}]
     (let [{:keys [status]} @state
           {:strs [lof-ratio cumulative-af homozygotes-count]} (:data status)]
       [:div {:style {:backgroundColor "white" :padding "20px 16px 16px 16px"}}
        [:div {:style {:display "flex"}}
         [:div {:style {:flex "0 0 33%"}}
          [:div {:style {:fontWeight "bold"}} "Obs/Exp"]]
         [:div {:style {:flex "0 0 33%"}}
          [:div {:style {:fontWeight "bold"}} "Cumulative AF"]]
         [:div {:style {:flex "0 0 33%"}}
          [:div {:style {:fontWeight "bold"}} "Homozygotes"]]]
        [:div {:style {:marginTop 8 :height 1 :backgroundColor "#959A9E"}}]
        [:div {:style {:marginTop 10 :display "flex" :fontWeight 100}}
         [:div {:style {:flex "0 0 33%"}}
          [:div {} (if lof-ratio (str (.toFixed lof-ratio 2) "%") "?")]]
         [:div {:style {:flex "0 0 33%"}}
          [:div {} (if cumulative-af (str (.toFixed (* cumulative-af 100) 2) "%") "?")]]
         [:div {:style {:flex "0 0 33%"}}
          [:div {} (if homozygotes-count homozygotes-count "?")]]]]))
   :component-did-mount
   (fn [{:keys [this props]}] (this :load-data (:gene-name props)))
   :component-will-receive-props
   (fn [{:keys [this props state next-props]}]
     (when-not (apply = (map :gene-name [props next-props]))
       (swap! state dissoc :status)
       (this :load-data (:gene-name next-props))))
   :load-data
   (fn [{:keys [props state]} gene-name]
     (u/ajax {:url (str (:api-url-root props) "/exec-sql")
              :method :post
              :data (u/->json-string
                     {:sql (str
                            "select"
                            " (select (n_lof / exp_lof) * 100 from constraint_scores"
                            " where gene = ?) as `lof-ratio`,"
                            " (select sum(ac_hom) from variant_annotation"
                            " where symbol = ?) as `homozygotes-count`,"
                            " (select sum(ac_adj / an_adj) from variant_annotation"
                            " where symbol = ?) as `cumulative-af`;")
                      :params (repeat 3 gene-name)})
              :on-done (fn [{:keys [get-parsed-response]}]
                         (let [gene-info (first (get (get-parsed-response) "rows"))]
                           (swap! state assoc :status {:code :loaded :data gene-info})))}))})
