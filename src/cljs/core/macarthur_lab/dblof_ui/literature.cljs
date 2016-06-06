(ns macarthur-lab.dblof-ui.literature
  (:require
   clojure.string
   [dmohs.react :as react]
   [macarthur-lab.dblof-ui.utils :as u]
   ))


(react/defc Component
  {:render
   (fn [{:keys [props state]}]
     (let [{:keys [status]} @state
           {:keys [code data]} status]
       [:div {:style {:minHeight 200}}
        (case code
          nil [:div {:style {:textAlign "center" :paddingTop 45 :marginBottom -40}}
               (str "Loading additional information for "
                    (clojure.string/upper-case (:gene-name props))
                    "...")]
          :not-found [:div {:style {:textAlign "center" :paddingTop 45 :marginBottom -40}}
                      (str "No additional gene information for "
                           (clojure.string/upper-case (:gene-name props))
                           ".")]
          nil)
        [:div {:ref "markdown" :className "markdown-body"}]]))
   :component-will-receive-props
   (fn [{:keys [this props state next-props]}]
     (when-not (apply = (map :gene-name [props next-props]))
       (swap! state dissoc :status)
       (this :load-data (:gene-name next-props))))
   :component-did-mount
   (fn [{:keys [this props]}] (this :load-data (:gene-name props)))
   :load-data
   (fn [{:keys [this props state refs after-update]} gene-name]
     (u/ajax {:url (str (:api-url-root props) "/get-gene-markdown?symbol="
                        (clojure.string/upper-case gene-name))
              :on-done (fn [{:keys [success? xhr]}]
                         (if success?
                           (this :render-markdown (.-responseText xhr))
                           (do
                             (swap! state assoc :status {:code :not-found})
                             (aset (@refs "markdown") "innerHTML" ""))))}))
   :render-markdown
   (fn [{:keys [props state refs]} md]
     (u/ajax {:url (str (:api-url-root props) "/render-github-markdown")
              :method :post
              :data md
              :on-done (fn [{:keys [success? xhr]}]
                         (swap! state assoc :status {:code (if success? :loaded :error)})
                         (if success?
                           (aset (@refs "markdown") "innerHTML" (.-responseText xhr))
                           (aset (@refs "markdown") "innerHTML" "")))}))})
