(ns macarthur-lab.dblof-ui.core
  (:require
    cljsjs.react-select
    clojure.string
    devcards.core
    [dmohs.react :as react]
    [macarthur-lab.dblof-ui.utils :as u])
  (:require-macros [devcards.core :refer [defcard]]))


(def api-url-root "http://dblof.broadinstitute.org:30080")


(defonce genes-atom (atom nil))
(when-not @genes-atom
  (u/ajax {:url (str api-url-root "/exec-sql")
           :method :post
           :data (u/->json-string {:sql "select gene from constraint_scores"})
           :on-done (fn [{:keys [get-parsed-response]}]
                      (reset! genes-atom
                              (map clojure.string/lower-case
                                   (map #(get % "gene") (get (get-parsed-response) "rows")))))}))


;asynchronous function : takes a callback function as parameter and cals that callback function with search results
;cb -> callback function


;This function will parse the url and find the gene clicked
(defn get-gene-name-from-window-hash[current-url]
  (assert (string? current-url))
  (nth(clojure.string/split current-url #"/")1))


(u/cljslog "parse-url" (clojure.string/split "http://dblof.broadinstitute.org:10080/arf5" #"/"))

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
                    :params (repeat 3 (get-gene-name-from-window-hash hash))})
           :on-done (fn [{:keys [get-parsed-response]}]
                      #_(u/cljslog "get-parsed-response-->" (get (get-parsed-response) "rows"))

                      (let [gene-info (first (get (get-parsed-response) "rows"))]
                        (cb gene-info)))}))




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
        [:div {:style {:flex "1 1 33%" :padding "30px" :textAlign "center"}}
         "Observed/Expected"
         [:div {} (:lof_ratio props)]]
        [:div {:style {:flex "1 1 33%":padding "30px" :textAlign "center" }}
         "Cumulative AF"
         [:div {} (:cumulative_af props)]]
        [:div {:style {:flex "1 1 33%" :padding "30px" :textAlign "center"}}
         "n-Homozygotes"
         [:div {} (:n_homozygotes props)]]]]
      [:div {:ref "plot" :style {:width 600 :height 300}}]
      [:div {}
       [:a {:href "#"} "< Back"]]])
   :component-did-mount
   (fn [{:keys [refs]}]
     (.plot js/Plotly (@refs "plot")
            (clj->js [{:type "bar"
                       :name "age distributin"
                       :x ["10-15", "1-"],
                       :y [1, 2]}])
            (clj->js {:margin {:t 10}})))})

(defn transform-vector-to-gene-label-map [m]
  {:label (get m "gene")
   :value (get m "gene")})

(defn- search-db-handler[search-term cb]
  (u/ajax {:url "http://dblof.broadinstitute.org:30080/exec-sql"
           :method :post
           :data (u/->json-string
                   {:sql (str
                           "select gene from constraint_scores where gene like ?"
                           " order by gene limit 20")
                    :params [(str "%" search-term "%")]})
           :on-done (fn [{:keys [get-parsed-response]}]
                      #_(u/cljslog "parse-value-->" (get (get-parsed-response) "rows"))
                        (u/cljslog (mapv transform-vector-to-gene-label-map (get (get-parsed-response) "rows")))
                       #_ (u/cljslog "transformed vector" (map transform-vector-to-gene-label-map [str "rows"]))
                        (cb (mapv transform-vector-to-gene-label-map (get (get-parsed-response) "rows"))))}))

(defn get-age-vectors [m]
  [])

(defn- exac-age-calculator []
  (u/ajax {:url "http://dblof.broadinstitute.org:30080/exec-sql"
           :method :post
           :data (u/->json-string
                   {:sql (str
                           "select `count(*)` as `exac-age-frequency`,
                            age_exac as `age-bins` from metadata_age
                            where age_exac is not NULL")
                    :params []})
           :on-done (fn [{:keys [get-parsed-response]}]
                      (u/cljslog "get-age-data-->" (mapv get-age-vectors (get (get-parsed-response) "rows")))
                      (let [exac-age-info (first (get (get-parsed-response) "rows"))]))}))

(exac-age-calculator)

(react/defc SearchResults
  {:select-next-item
   (fn [{:keys [this state after-update]}]
     (swap! state assoc :selected-index (inc (or (:selected-index @state) -1))))
   :select-prev-item
   (fn [{:keys [this state after-update]}]
     (swap! state assoc :selected-index (dec (:selected-index @state))))
   :report-selection
   (fn [{:keys [props state]}]
     (let [{:keys [on-item-selected]} props
           {:keys [results selected-index]} @state]
       (when on-item-selected
         (on-item-selected (nth results selected-index)))))
   :render
   (fn [{:keys [this props state]}]
     (let [{:keys [style search-text]} props
           {:keys [results selected-index]} @state]
       [:div {:style (merge {:margin "4px 0 0 0"} (:container style))}
        (map-indexed
         (fn [i x]
           [:div {:onMouseOver #(swap! state assoc :selected-index i)
                  :onClick #(react/call :report-selection this)
                  :style {:cursor "pointer"
                          :backgroundColor (when (= i selected-index) "rgba(220,245,250,1)")
                          :padding "0 6px"}}
            (clojure.string/upper-case x)])
         results)]))
   :component-did-update
   (fn [{:keys [this prev-props props state locals]}]
     (js/clearTimeout (:timeout @locals))
     (let [{:keys [search-text]} props]
       (when-not (= (:search-text prev-props) search-text)
         (if (clojure.string/blank? search-text)
           (swap! state dissoc :results :selected-index)
           (let [search-text (clojure.string/lower-case search-text)]
             (swap! locals assoc :timeout
                    (js/setTimeout
                     (fn []
                       (let [filtered (filter #(= 0 (.indexOf % search-text)) @genes-atom)
                             results (take 20 (sort filtered))]
                         (swap! state assoc :results results :selected-index 0)))
                     100)))))))})

; Create a component class. A component implements a render method which returns one single child.
; That child may have an arbitrarily deep child structure
(react/defc SearchBoxAndResults
  ;;render which returns a tree of React components that will eventually render to HTML.
  {
    :get-initial-state
   (fn [] {:hash (get-window-hash)})
   :render
  ;{:keys [this state]} is a map which contains :this :state :props :refs etc
   (fn [{:keys [this state refs]}]
     ;lof-ratio holds the state for obs/exp
     (let [{:keys [full-page-search? hash lof-ratio cumulative-af n-homozygotes search-text suggestion]} @state]
     ;;The <div> tags are not actual DOM nodes; they are instantiations of React div components.
       [:div {}
        (when lof-ratio
          [GeneInfo {:lof_ratio lof-ratio :cumulative_af cumulative-af :n_homozygotes n-homozygotes}])
        [:div {:style {:display (when lof-ratio "none")}}
         [NavBar]
         (when-not full-page-search?
           [:div {:style {:margin "10vh 0 0 30vw" :fontWeight "bold" :fontSize "30px"}}
            "dblof | Database for Loss of Function Variants"])
         [:div {:style {:margin (if full-page-search? "10px 0 0 0" "10px 0 0 0")
                        :textAlign "center"}}
          [:input {:ref "search-box"
                   :value (str search-text (subs (or suggestion "") (count search-text)))
                   :onChange #(swap! state assoc :suggestion nil :search-text (.. % -target -value))
                   :onKeyDown (fn [e]
                                (when (= 13 (.-keyCode e))
                                  (react/call :report-selection (@refs "results")))
                                (when (= 38 (.-keyCode e))
                                  (.preventDefault e)
                                  (react/call :select-prev-item (@refs "results")))
                                (when (= 40 (.-keyCode e))
                                  (.preventDefault e)
                                  (react/call :select-next-item (@refs "results"))))
                   :style {:fontSize "medium" :width 200}}]
          [:br]
          [SearchResults {:ref "results"
                          :search-text search-text
                          :on-item-selected (fn [item]
                                              (aset js/window "location" "hash"
                                                    (str "genes/" item)))
                          :style {:container {:width 210 :display "inline-block" :textAlign "left"}}}]

         ]]]))

   :perform-search
   (fn [{:keys [state refs]}]
     (swap! state assoc :full-page-search? true)

       (search-db-handler
       (.-value (@refs "search-box"))
       (fn [results] ;callback function which takes the results of (search-handler search-term)
         (swap! state assoc :search-results results)))
     )

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
