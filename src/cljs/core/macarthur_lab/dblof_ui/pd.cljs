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
       [:div {:style {:minHeight "20vh" :position :relative
                      :backgroundColor (when-not (= code :loaded) "#eee")}}
        [:div {:style {:height 1 :backgroundColor "#ccc"
                      :position "absolute" :width "100%" :top "10vh"}}]
        [:div {:style {:position "absolute" :top "7.5vh" :height "5vh" :width "100%"
                       :display "flex"}}
         [:div {:style {:flex "0 0 5px"}}]
         (interpose [:div {:style {:flex "0 0 10px"}}] exons)
         [:div {:style {:flex "0 0 5px"}}]]]))
   :component-did-mount
   (fn [{:keys [props state]}]
     (u/ajax {:url (str (:api-url-root props) "/exec-sql")
              :method :post
              :data (u/->json-string
                     {:sql (str "select exonId `id`, exonStart `start`, size from gene_exon\n"
                                "where symbol=?")
                      :params [(clojure.string/upper-case (:gene-name props))]})
              :on-done (fn [{:keys [get-parsed-response]}]
                         (swap! state assoc :status
                                {:code :loaded :data (get (get-parsed-response) "rows")}))}))})
