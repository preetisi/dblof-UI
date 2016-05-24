(ns macarthur-lab.dblof-ui.gene-info
  (:require
   cljsjs.markdown
   clojure.string
   [dmohs.react :as react]
   [macarthur-lab.dblof-ui.utils :as u]
   ))


(react/defc Component
  {:render
   (fn [{:keys [props state]}]
     (let [{:keys [status]} @state
           {:keys [code data]} status]
       [:div {:style {:backgroundColor "white" :padding "20px 16px"}}
        [:div {:ref "markdown"}]
        (when-not (= code :loaded)
          (str "No additional gene information for "
               (clojure.string/upper-case (:gene-name props))
               "."))]))
   :component-will-receive-props
   (fn [{:keys [this props state next-props]}]
     (when-not (apply = (map :gene-name [props next-props]))
       (swap! state dissoc :status)
       (this :load-data (:gene-name next-props))))
   :component-did-mount
   (fn [{:keys [this props]}] (this :load-data (:gene-name props)))
   :load-data
   (fn [{:keys [props state refs after-update]} gene-name]
     (u/ajax {:url (str (:api-url-root props) "/get-gene-markdown?symbol="
                        (clojure.string/upper-case gene-name))
              :on-done (fn [{:keys [success? xhr]}]
                         (swap! state assoc :status
                                {:code (if success? :loaded :not-found)
                                 :data (.-responseText xhr)})
                         (after-update
                          (fn []
                            (aset (@refs "markdown") "innerHTML"
                                  (.toHTML js/markdown (.-responseText xhr))))))}))})
