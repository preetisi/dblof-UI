(ns macarthur-lab.dblof-ui.core
  (:require
   cljsjs.react-select
    devcards.core
    [dmohs.react :as react]
    [macarthur-lab.dblof-ui.utils :as u])
  (:require-macros [devcards.core :refer [defcard]]))


#_(def PLOTLYENV "https://<domain>.plot.ly")
;asynchronous function : takes a callback function as parameter and cals that callback function with search results
;cb -> callback function


;This function will parse the url and find the gene clicked
(defn parse-url[current-url]
  (nth(clojure.string/split current-url #"/")1))

;ajax call to display
(defn- get-window-hash []
  (let [value (.. js/window -location -hash)]
    (when-not (clojure.string/blank? value) value)))

;select cs.n_lof/cs.exp_log as a, sum(va.ac_adj/ac.an_adj) as b
;from constraint_scores cs inner join variant_annotation va on cs.gene = va.symbol where cs.gene = ?
;get the cumulative AF -> sum(ac_adj/an_adj) where LoF='HC'

; this function will take the clicked gene as input and query the constraint_score sql for displaying
; obs/exp --> n_lof/exp_lof
;get the number of homozygotes -> sum(ac_hom) where LoF='HC'
(defn- score_calculator[hash cb]
  (u/ajax {:url "http://dblof.broadinstitute.org:30080/exec-sql"
           :method :post
           :data (u/->json-string
                   {:sql (str
                           "select"
                           " (select (n_lof / exp_lof) * 100 from constraint_scores"
                           " where gene = ?) as lof_ratio,"
                           " (select sum(ac_hom) from variant_annotation"
                           " where symbol = ?) as n_homozygotes,"
                           " (select sum(ac_adj / an_adj) from variant_annotation"
                           " where symbol = ?) as cumulative_af;")
                    :params (repeat 3 (parse-url hash))})
           :on-done (fn [{:keys [get-parsed-response]}]
                      (u/cljslog "get-parsed-response-->" (get (get-parsed-response) "rows"))

                      (let [gene-info (first (get (get-parsed-response) "rows"))]
                        (cb gene-info))
                      (u/cljslog (get (get-parsed-response) "rows"))
                      #_(cb ((map #(get % "(n_lof/exp_lof)*100" ) (get (get-parsed-response) "rows")))))}))


; component for navigation bar
(react/defc NavBar
  {
    :render
    (fn [{:keys [this state]}]
      [:div {}
       [:div {:style {:backgroundColor "#000000" :display "inline-block" :position "relative"
                      :padding "10" :width "100%" :fontSize "30px" :color "#ffffff"}} "dbLoF"]
       ])
    })
; New component for displaying the gene details
; this component is rendered when "hash" is not nill (when someone clicks on one of the gene link)
(react/defc GeneInfo
  {:render
   (fn [{:keys [this props]}]
     [:div {}
      [NavBar]
      [:div {}
       [:div {:style {:display "flex"}}
        [:div {:style {:flex "1 1 33%"}}
         "Observed/Expected"
         [:div {} (:lof_ratio props)]]
        [:div {:style {:flex "1 1 33%"}}
         "Cumulative AF"
         [:div {} (:cumulative_af props)]]
        [:div {:style {:flex "1 1 33%"}}
         "n-Homozygotes"
         [:div {} (:n_homozygotes props)]]]]
      [:div {:ref "plot" :style {:width 600 :height 300}}]
      [:div {}
       [:a {:href "#"} "< Back"]]])
   :component-did-mount
   (fn [{:keys [refs]}]
     (.plot js/Plotly (@refs "plot")
            (clj->js [{:x [1, 2, 3, 4, 5],
                       :y [1, 2, 4, 8, 16]}])
            (clj->js {:margin {:t 0}})))})

(defn- search-db-handler[search-term cb]
  (u/ajax {:url "http://dblof.broadinstitute.org:30080/exec-sql"
           :method :post
           :data (u/->json-string
                   {:sql (str
                           "select gene from constraint_scores where gene like ?"
                           " order by gene limit 20")
                    :params [(str "%" search-term "%")]})
           :on-done (fn [{:keys [get-parsed-response]}]
                      (cb (map #(get % "gene") (get (get-parsed-response) "rows"))))}))

; Create a component class. A component implements a render method which returns one single child.
; That child may have an arbitrarily deep child structure
(react/defc SearchBoxAndResults
  ;;render which returns a tree of React components that will eventually render to HTML.
  {
    :get-initial-state
   (fn [] {:hash (get-window-hash)})
   :render
  ;{:keys [this state]} is a map which contains :this :state :props :refs etc
   (fn [{:keys [this state]}]
     ;lof-ratio holds the state for obs/exp
     (let [{:keys [full-page-search? hash lof-ratio cumulative-af n-homozygotes]} @state]
     ;;The <div> tags are not actual DOM nodes; they are instantiations of React div components.
       (if lof-ratio
         [GeneInfo {:lof_ratio lof-ratio :cumulative_af cumulative-af :n_homozygotes n-homozygotes}]
       [:div {}
        [NavBar]
        (when-not full-page-search?
          [:div {:style {:margin "10vh 0 0 30vw" :fontWeight "bold" :fontSize "30px"}}
           "dblof | Database for Loss of Function Variants"])
        [:div {:style {:margin (if full-page-search? "10px 0 0 0" "30vh 0 0 10vw")}}
         [:div {}
          (.createElement
            js/React
            js/Select
            (clj->js
              {:name "our-auto-box" :value "one"
               :options [{:value "one" :label "One"} {:value "two" :label "Two"}]}))]
         [:input {:type "text" :style {:width "70vw" :height "4em" :boxSizing "border-box"}
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
        ])))

   :perform-search
   (fn [{:keys [state refs]}]
     (swap! state assoc :full-page-search? true)
     (search-db-handler
       (.-value (@refs "search-box"))
       (fn [results] ;callback function which takes the results of (search-handler search-term)
         (swap! state assoc :search-results results))))

    ;so there should be one more component-did-mount where you do the ajax call(using gene-details-handler function
    ; and swap the state of gene-details
    ;(swap! state assoc :gene-info {:lof-ratio lof-ratio})
   :component-did-mount
   (fn [{:keys [state locals]}]
     (let [hash-change-listener (fn [e]
                                  (let [hash (get-window-hash)]
                                    (if hash
                                      (do
                                        (score_calculator
                                          hash
                                          (fn [scores]
                                            (u/cljslog "scores:" scores)
                                            (swap! state assoc :lof-ratio (get scores "lof_ratio")
                                                               :cumulative-af (get scores "cumulative_af")
                                                               :n-homozygotes (get scores "n_homozygotes"))
                                            (u/cljslog @state)
                                            ))))
                                      (swap! state dissoc :lof-ratio)
                                      (swap! state dissoc :cumulative-af)
                                      (swap! state dissoc :n-homozygotes)))]
       (swap! locals assoc :hash-change-listener hash-change-listener )
      ;.. makes it window.location.hash
       (js/console.log(get-window-hash))
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
