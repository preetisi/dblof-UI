(ns macarthur-lab.dblof-ui.core
  (:require
    cljsjs.react-select
    clojure.string
    devcards.core
    [dmohs.react :as react]
    [macarthur-lab.dblof-ui.utils :as u])
  (:require-macros [devcards.core :refer [defcard]]))


(def api-url-root "http://api.dblof.broadinstitute.org")


(defonce genes-atom (atom nil))


;if this ajax call fails -> Show error message to user
(when-not @genes-atom
  (u/ajax {:url (str api-url-root "/exec-sql")
           :method :post
           :data (u/->json-string {:sql "select gene from constraint_scores"})
           :on-done (fn [{:keys [get-parsed-response]}]
                      (reset! genes-atom
                              (map clojure.string/lower-case
                                   (map #(get % "gene") (get (get-parsed-response) "rows")))))}))


(defn- get-gene-name-from-window-hash [window-hash]
  (assert (string? window-hash))
  (nth (clojure.string/split window-hash #"/") 1))


(defn- get-window-hash []
  (let [value (.. js/window -location -hash)]
    (when-not (clojure.string/blank? value) value)))


(defn- calculate-score [window-hash cb]
  (u/ajax {:url (str api-url-root "/exec-sql")
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
                    :params (repeat 3 (get-gene-name-from-window-hash window-hash))})
           :on-done (fn [{:keys [get-parsed-response]}]
                      (let [gene-info (first (get (get-parsed-response) "rows"))]
                        (cb gene-info)))}))


(defn- calculate-population-for-gene [gene-name cb]
  (u/ajax {:url (str api-url-root "/exec-sql")
           :method :post
           :data (u/->json-string
                  {:sql (str
                         "select pop as 'each-gene-population',"
                         "pop_frequency as 'each-gene-population-frequency'"
                         "from exac_pop_gene_summary where gene = ? and pop is not NULL")
                   :params (clojure.string/upper-case gene-name)})
           :on-done (fn [{:keys [get-parsed-response]}]
                      (cb (reduce (fn [r, m]
                                    (-> r
                                        (update-in [:exac_each_gene_pop_frequency] conj (get m "each-gene-population-frequency"))
                                        (update-in [:exac_each_gene_population_category] conj (get m "each-gene-population"))))
                                  {:exac_each_gene_pop_frequency [] :exac_each_gene_population_category []}
                                  (get (get-parsed-response) "rows"))))}))

(defn- exac-each-gene-age-calculator [window-hash cb]
  (u/ajax {:url (str api-url-root "/exec-sql")
           :method :post
           :data (u/->json-string
                  {:sql (str
                         "select b as 'each-age-bins', `count(*)` as 'exac-each-gene-age-frequency'"
                         "from exac_age_gene_summary where gene = ?")
                   :params (clojure.string/upper-case (get-gene-name-from-window-hash window-hash))})
           :on-done (fn [{:keys [get-parsed-response]}]
                      (cb (reduce (fn [r, m]
                                    (-> r
                                        (update-in [:exac-each-gene-frequency] conj (get m "exac-each-gene-age-frequency"))
                                        (update-in [:exac-each-gene-age-bins] conj (get m "each-age-bins"))))
                                  {:exac-each-gene-frequency [] :exac-each-gene-age-bins []}
                                  (get (get-parsed-response) "rows"))))}))

(defn- exac-age-calculator [cb]
  (u/ajax {:url (str api-url-root "/exec-sql")
           :method :post
           :data (u/->json-string
                  {:sql (str
                         "select `count(*)` as `exac-age-frequency`,
                            age_exac as `age-bins` from metadata_age
                            where age_exac is not NULL")
                   :params []})
           :on-done (fn [{:keys [get-parsed-response]}]
                      (cb (reduce
                           (fn [r m]
                             (-> r
                                 (update-in [:exac-age-info] conj (get m "exac-age-frequency"))
                                 (update-in [:age-bins] conj (get m "age-bins"))))
                           {:exac-age-info [] :age-bins []}
                           (get (get-parsed-response) "rows"))))}))
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


;New component for displaying the gene details
; this component is rendered when "hash" is not nill (when someone clicks on one of the gene link)
(react/defc GeneInfo
  {:render
   (fn [{:keys [this props state ]}] ;;exac-age-info age-bins
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
      [:div {:ref "plot2" :style {:width 600 :height 300}}]
      [:div {:ref "plot3" :style {:width 600 :height 300}}]

      [:div {:style {:padding "50px" :color "#FF0000"}}
        "Variants"
        [:div {} (map (fn [x]
          [:div {:style {:display "flex"}}
           [:div {:style {:flex "0 0 20%" :padding "10px"
                          :overflow "hidden" :textOverflow "ellipsis"}}
            (get x "variant_id")]
           [:div {:style {:flex "0 0 20%" :padding "10px"}}
                  (get x "chrom")]
           [:div {:style {:flex "0 0 20%"  :padding "10px"}}
                  (get x "pos")]
           [:div {:style {:flex "0 0 20%" :padding "10px"}}
                  (get x "allele_freq")]
          [:div {:style {:flex "0 0 20%" :padding "10px"}}
                  (get x "hom_count")]
          ]) (:variants @state))]]
      [:div {}
       [:a {:href "#"} "< Back"]]])

   :run-age-calculator
   (fn [{:keys [this state refs]} results]
     (swap! state assoc :age-bins (get results :age-bins))
     (swap! state assoc :exac-age-info (get results :exac-age-info))
     (react/call :build-plot this (get results :age-bins) (get results :exac-age-info)))

   :run-each-gene-age-calculator
   (fn [{:keys [this state refs]} results]
     (swap! state assoc :exac-each-gene-age-bins (get results "exac-each-gene-age-bins"))
     (swap! state assoc :exac-each-gene-frequency (get results "exac-each-gene-frequency"))
     (react/call :build-each-gene-age-plot this (get results :exac-each-gene-age-bins) (get results :exac-each-gene-frequency)))

   :run-each-gene-pop-calculator
   (fn [{:keys [this state refs]} results]
     (swap! state assoc :exac_each_gene_population_category (get results "exac_each_gene_population_category"))
     (swap! state assoc :exac_each_gene_pop_frequency (get results "exac_each_gene_pop_frequency"))
     (react/call :build-each-gene-pop-plot this (get results :exac_each_gene_population_category) (get results :exac_each_gene_pop_frequency)))

   :build-plot
   (fn [{:keys [this refs state]} x y]
     #_(.style (.select (.-d3 js/Plotly) "body") "background-color" "")
     (-> js/Plotly .-d3 (.select "body") (.style "background-color" ""))
     (.plot js/Plotly (@refs "plot")
       (clj->js [{:type "bar"
                  :name "age distributin"
                  :x x
                  :y y
                  }])
       (clj->js {:margin {:t 10}})))

   :build-each-gene-age-plot
   (fn [{:keys [this refs state]} x y]
     (.plot js/Plotly (@refs "plot2")
            (clj->js [{:type "bar"
                       :title "age distribution"
                       :x x
                       :y y}])
            (clj->js {:margin {:t 10}})))
   :build-each-gene-pop-plot
   (fn [{:keys [this refs state]} x y]
     (.plot js/Plotly (@refs "plot3")
            (clj->js [{:type "bar"
                       :name "age distributin"
                       :x y
                       :y x
                       :orientation "h"}])
            (clj->js {:margin {:t 10}})))

   :component-did-mount
   (fn [{:keys [this props state refs]}]
     (exac-age-calculator (fn [results]
                               (react/call :run-age-calculator this results)))
                               (exac-each-gene-age-calculator (:hash props) (fn [results]
                                                                (react/call :run-each-gene-age-calculator this results )))
                               (calculate-population-for-gene
                                (get-gene-name-from-window-hash (u/cljslog "hash" (:hash props)))
                                (fn [results]
                                  (react/call :run-each-gene-pop-calculator this results)))
     (react/call :load-variants this))
   :load-variants
   (fn [{:keys [props state]}]
     (let [gene-name (get-gene-name-from-window-hash (get-window-hash))
           gene-name-uc (clojure.string/upper-case gene-name)]
       (u/ajax {:url (str api-url-root "/exec-mongo")
                :method :post
                :data (u/->json-string
                       {:collection-name "genes"
                        :query {:gene_name_upper {:$eq gene-name-uc}}
                        :projection {:gene_id 1}})
                :on-done
                (fn [{:keys [get-parsed-response]}]
                  (let [gene-id (get-in (get-parsed-response) [0 "gene_id"])]
                    (u/ajax {:url (str api-url-root "/exec-mongo")
                             :method :post
                             :data (u/->json-string
                                    {:collection-name "variants"
                                     :query {:genes {:$in [gene-id]}}
                                     :projection {:variant_id 1 :chrom 1
                                                  :pos 1 :allele_count 1
                                                  :hom_count 1 :allele_freq 1 }
                                     :options {:limit 10000}})
                             :on-done
                             (fn [{:keys [get-parsed-response]}]
                               (swap! state assoc :variants (get-parsed-response)))})))})))})


(defn transform-vector-to-gene-label-map [m]
  {:label (get m "gene")
   :value (get m "gene")})

(defn- search-db-handler[search-term cb]
  (u/ajax {:url (str api-url-root "/exec-sql")
           :method :post
           :data (u/->json-string
                   {:sql (str
                           "select gene from constraint_scores where gene like ?"
                           " order by gene limit 20")
                    :params [(str "%" search-term "%")]})
           :on-done (fn [{:keys [get-parsed-response]}]
                        (cb (mapv transform-vector-to-gene-label-map (get (get-parsed-response) "rows"))))}))


;component for displaying auto-select list of genes
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
   ;:exac-wide-age-info
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
                     100)))))))

   })

; Create a component class. A component implements a render method which returns one single child.
; That child may have an arbitrarily deep child structure
(react/defc SearchBoxAndResults
  ;;render which returns a tree of React components that will eventually render to HTML.
  {
    :get-initial-state
   ;returns str or nil if empty
   (fn [] (u/cljslog "{:hash (get-window-hash)}" {:hash (get-window-hash)}))

   :render
  ;{:keys [this state]} is a map which contains :this :state :props :refs etc
   (fn [{:keys [this state refs]}]
     ;lof-ratio holds the state for obs/exp
     (let [{:keys [full-page-search? hash lof-ratio cumulative-af n-homozygotes search-text suggestion exac-age-info age-bins]} @state]
     ;;The <div> tags are not actual DOM nodes; they are instantiations of React div components.
       [:div {}
        (when lof-ratio
          [GeneInfo {:lof_ratio lof-ratio :cumulative_af cumulative-af :n_homozygotes n-homozygotes :hash hash}])
        [:div {:style {:display (when lof-ratio "none")}}
         [NavBar]
         (when-not full-page-search?
           [:div {:style {:margin "10vh 0 0 30vw" :fontWeight "bold" :fontSize "30px"}}
            "dbLoF | Database for Loss of Function Variants"])
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
                                        (calculate-score
                                          hash
                                          (fn [scores]
                                            (u/cljslog "scores:" scores)
                                            (swap! state assoc :hash hash
                                                   :lof-ratio (get scores "lof_ratio")
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
