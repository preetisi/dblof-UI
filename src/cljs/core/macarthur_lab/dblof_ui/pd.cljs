(ns macarthur-lab.dblof-ui.pd
  (:require
   clojure.string
   [dmohs.react :as react]
   [macarthur-lab.dblof-ui.utils :as u]
   ))


(react/defc Component
  {:render
   (fn [{:keys [state]}]
     (let [{:keys [status]} @state
           {:keys [code data]} status
           exons (map
                  (fn [{:strs [size]}]
                    [:div {:style {:flex (str size " " size " auto") :backgroundColor "#333"}}])
                  (sort-by #(get % "start") data))]
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
   (fn [{:keys [props state]} gene-name]
     (u/ajax {:url (str (:api-url-root props) "/exec-sql")
              :method :post
              :data (u/->json-string
                     {:sql (str "select start, stop-start size from gene_exons_v2 e\n"
                                "inner join gene_symbols s on e.gene_id=s.gene_id\n"
                                "where s.symbol=?")
                      :params [(clojure.string/upper-case gene-name)]})
              :on-done (fn [{:keys [get-parsed-response]}]
                         (swap! state assoc :status
                                {:code :loaded :data (get (get-parsed-response) "rows")}))}))})
