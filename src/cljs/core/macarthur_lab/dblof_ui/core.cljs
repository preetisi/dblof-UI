(ns macarthur-lab.dblof-ui.core
  (:require devcards.core
            [dmohs.react :as react]
            [macarthur-lab.dblof-ui.utils :as u])
  (:require-macros [devcards.core :refer [defcard]]))

;synchronous function
(defn- search-handler [search-term]
  (let [search-term (clojure.string/lower-case search-term)]
    (filter
      (fn [x]
        (clojure.string/index-of x search-term))
      (map clojure.string/lower-case ["gene1" "gene1" "gene2" "gene3" "gene4" "GENE5" "pcsk9" "ENSG00000169174"]))))


;asynchronous function : takes a callback function as parameter and cals that callback function with search results
;cb -> callback function
(defn- search-db-handler[search-term cb]
  (u/ajax {:url "http://dblof.broadinstitute.org:30080/exec-sql"
           :method :post
           :data (u/->json-string {:sql "select * from constraint_scores where gene like ?" :params [(str "%" search-term "%")]})
           :on-done (fn [{:keys [get-parsed-response]}]
                      (cb (map #(get % "gene") (get (get-parsed-response) "rows"))))}))


(defn- gene-page-handler[search-term] search-term)


(defn- get-window-hash []
  (let [value (.. js/window -location -hash)]
    (when-not (clojure.string/blank? value) value)))


; Create a component class. A component implements a render method which returns one single child.
; That child may have an arbitrarily deep child structure
(react/defc SearchBoxAndResults
  ;;render which returns a tree of React components that will eventually render to HTML.
  {:get-initial-state
   (fn [] {:hash (get-window-hash)})
   :render
  ;{:keys [this state]} is a map which contains :this :state :props :refs etc
   (fn [{:keys [this state]}]
     (let [{:keys [full-page-search? gene-page? hash]} @state]
     ;;The <div> tags are not actual DOM nodes; they are instantiations of React div components.
       (if hash
         [:div {} "Gene page: " hash]
       [:div {}
        [:div {:style {:backgroundColor "#000000" :display "inline-block" :position "relative" :padding "10" :width "100%" :font-size "30px" :color "#ffffff"}} "dbLoF"]
        (when-not full-page-search?
          [:div {:style {:margin "10vh 0 0 30vw" :font-weight "bold" :font-size "30px"}}
           "dblof | Database for Loss of Function Variants"])
        [:div {:style {:margin (if full-page-search? "10px 0 0 0" "30vh 0 0 10vw")}}
         [:input {:type "text" :style {:width "70vw" :height "4em" :box-sizing "border-box"}
                  :defaultValue "gene1"
                  :placeholder "Enter Gene"
                  :ref "search-box"
                  :onKeyDown (fn [e]
                               (when (= 13 (.-keyCode e))
                                 (react/call :perform-search this)))}]
         [:button {:style {:marginLeft 4 :height "4em"}
                   :onClick #(react/call :perform-search this)}
          "Search"]]
        [:div {:style {:marginBottom "1em"}}]
        [:div {}
         "Results: "
         [:br]
         ;interpose Returns a lazy seq of the elements of coll separated by sep (here it is br)
         ;@form ⇒ (deref form)
         (interpose [:br] (map (fn [s] [:a {:style {:padding 5 :display "inline-block"}
                                            :href (str "#genes/" s)}
                                        s])
                            (:search-results @state)))]
        (when gene-page?
          [:div {:type "text" :style {:font-size "10px" }}]
          (:gene-page-click @state))
        ])))
   :perform-search
   (fn [{:keys [state refs]}]
     (swap! state assoc :full-page-search? true)
     (search-db-handler
       (.-value (@refs "search-box"))
       (fn [results] ;callback function which takes the results of (search-handler search-term)
         (swap! state assoc :search-results results))))
   :component-did-mount
   (fn [{:keys [state locals]}]
     (let [hash-change-listener (fn [e] (swap! state assoc :hash (get-window-hash)))]
       (swap! locals assoc :hash-change-listener hash-change-listener )
      ;.. makes it window.location.hash
      (.addEventListener js/window "hashchange" hash-change-listener)))
   ;remove the event listener
   :component-will-unmount
   (fn [{:keys [locals]}]
     ;locals is a atom that contains a map
     (.removeEventListener js/window "hashchange" (get @locals :hash-change-listener) ))})



(defn render-application [& [hot-reload?]]
  (react/render
    (react/create-element
      SearchBoxAndResults
      {})
    (.. js/document (getElementById "app"))
    nil
    hot-reload?))

