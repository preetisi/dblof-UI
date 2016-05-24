(ns macarthur-lab.dblof-ui.core
  (:require
   clojure.string
   [dmohs.react :as react]
   [macarthur-lab.dblof-ui.pd :as pd]
   [macarthur-lab.dblof-ui.search-area :as search-area]
   [macarthur-lab.dblof-ui.stats-box :as stats-box]
   [macarthur-lab.dblof-ui.utils :as u]
   [macarthur-lab.dblof-ui.variant-table :as variant-table]
   ))


(def api-url-root "http://api.staging.dblof.broadinstitute.org")


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
                           " select"
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


(defn- calculate-exac-group-age [gene-name cb]
  (u/ajax {:url (str api-url-root "/exec-sql")
           :method :post
           :data (u/->json-string
                  {:sql (str
                         "select `count(*)` as `exac-age-frequency`,
                            age_exac as `age-bins` from metadata_age
                            where age_exac is not NULL"
                         )
                   })
           :on-done
           (fn [{:keys [get-parsed-response]}]
           (let [exac-age-frequency-g1 (map (fn [m] (get m "exac-age-frequency"))
                                            (get (get-parsed-response) "rows"))
                 exac-bins-g1 (map (fn [m] (get m "age-bins"))
                                            (get (get-parsed-response) "rows"))]
             (u/ajax {:url (str api-url-root "/exec-sql")
                      :method :post
                      :data (u/->json-string
                        {:sql (str
                               "select b as 'each-age-bins', `count(*)` as 'exac-each-gene-age-frequency'"
                               "from exac_age_gene_summary where gene = ?")
                         :params (clojure.string/upper-case gene-name)
                         } )
                      :on-done
                      (fn [{:keys [get-parsed-response]}]
                        (let [each-gene-age-feq-g2 (map (fn [m] (get m "exac-each-gene-age-frequency"))
                                                         (get (get-parsed-response) "rows"))
                              each-gene-age-bins-g2 (map (fn [m] (get m "each-age-bins"))
                                                               (get (get-parsed-response) "rows"))]
                          (cb exac-age-frequency-g1 exac-bins-g1 each-gene-age-feq-g2 each-gene-age-bins-g2))
                        )})))}))

;New component for displaying the gene details
; this component is rendered when "hash" is not nill (when someone clicks on one of the gene link)
(react/defc GeneInfo
  {:render
   (fn [{:keys [this props state]}]
     (let [{:keys [gene-name]} props
           {:keys [each-gene-pop? each-gene-age?]} @state]
       [:div {:style {:backgroundColor "#E9E9E9"}}
        [:div {:style {:paddingTop 30 :display "flex"}}
         [:div {:style {:flex "1 1 50%"}}
          [:div {:style {:fontSize "180%" :fontWeight 900 :padding "50px"}}
           "Gene: " (clojure.string/upper-case gene-name)]]
         [:div {:style {:flex "1 1 50%"}}
          [stats-box/Component (merge {:api-url-root api-url-root}
                                      (select-keys props [:gene-name]))]]]
        [pd/Component (merge {:api-url-root api-url-root} (select-keys props [:gene-name]))]
      (when-not each-gene-pop?
         [:div {:ref "population-plot" :style {:width 600 :height 300 :padding "50px"}}])
      ;group age plot
      [:div {:ref "group-plot" :style {:width 600 :height 300 :padding "50px"}}]

      [:div {:style {:marginTop 50}}
       [variant-table/Component (merge {:api-url-root api-url-root}
                                       (select-keys props [:gene-name]))]]]))
   :component-did-mount
   (fn [{:keys [this]}]
     (this :render-plots))
   :component-will-receive-props
   (fn [{:keys [this after-update]}]
     (after-update #(this :render-plots)))
   :run-age-calculator
   (fn [{:keys [this state refs]} results]
     (swap! state assoc :age-bins (get results :age-bins))
     (swap! state assoc :exac-age-info (get results :exac-age-info))
     (react/call :build-plot this (get results :age-bins) (get results :exac-age-info)))

   :run-each-gene-age-calculator
   (fn [{:keys [this state refs]} results]
     (u/cljslog "results" results)
     (let [gene-frequency (get results :exac-each-gene-frequency)]
       (u/cljslog "gene-frequency:: " gene-frequency)
       (swap! state assoc :exac-each-gene-age-bins (get results :exac-each-gene-age-bins)
                          :exac-each-gene-frequency gene-frequency
                          :each-gene-age? (empty? gene-frequency)))

     (react/call :build-each-gene-age-plot this
                 (get results :exac-each-gene-age-bins)
                 (get results :exac-each-gene-frequency)))

   :run-each-gene-pop-calculator
   (fn [{:keys [this state refs]} results]
     (let [pop_frequency (get results :exac_each_gene_pop_frequency)]
     (swap! state assoc :exac_each_gene_population_category (get results :exac_each_gene_population_category)
                        :exac_each_gene_pop_frequency pop_frequency
                        :each-gene-pop? (empty? pop_frequency)))

     (react/call :build-each-gene-pop-plot this
                 (get results :exac_each_gene_population_category)
                 (get results :exac_each_gene_pop_frequency)))

   :build-each-gene-pop-plot
   (fn [{:keys [this refs state]} x y]
     (.newPlot js/Plotly (@refs "population-plot")
            (clj->js [{:type "bar"
                       :name "Population distribution of each gene"
                       :title "hello"
                       :x y
                       :y x
                       :orientation "h"}])
            (clj->js {:title "Population distribution" :xaxis {:title "Frequency"} :yaxis {:title "Population"}:margin {:t 100 :l 50 :r 50 :b 100} :width 600 :height 400})))


   :build-group-ages-plot
   (fn [{:keys [this refs state]} x1 y1 x2 y2]
     (.newPlot js/Plotly (@refs "group-plot")
            (clj->js [{:type "bar"
                       :name "Age distributiion over Exac"
                       :color "rgba(204,204,204,1)"
                       :x y1
                       :y x1}
                      {:type "bar"
                       :name "Age distribution of each gene"
                       :x y1
                       :y x2}])
            (clj->js {:title "Age distribution" :xaxis {:title "Age"} :yaxis {:title "Frequency"} :margin {:t 100 :l 50 :r 50 :b 100} :width 600 :height 400})))

   :render-plots
   (fn [{:keys [this props state refs]}]
     (calculate-population-for-gene
      (get-gene-name-from-window-hash (u/cljslog "hash" (:hash props)))
      (fn [results]
        (react/call :run-each-gene-pop-calculator this results)))
     (calculate-exac-group-age (get-gene-name-from-window-hash (u/cljslog "hash" (:hash props)))
      (fn [x1 y1 x2 y2]
         (react/call :build-group-ages-plot this x1 y1 x2 y2))
      ))})


(defn transform-vector-to-gene-label-map [m]
  {:label (get m "gene")
   :value (get m "gene")})


;component for search box
(react/defc SearchBoxAndResults
  {:get-initial-state
   (fn [] {:hash (get-window-hash)})
   :render
   (fn [{:keys [this state refs]}]
     (let [{:keys [hash exac-age-info age-bins]} @state]
       [:div {}
        [search-area/Component {:api-url-root api-url-root :compact? hash}]
        (when hash
          [GeneInfo {:hash hash :gene-name (get-gene-name-from-window-hash hash)}])]))
    ;so there should be one more component-did-mount where you do the ajax call(using gene-details-handler function
    ; and swap the state of gene-details
    ;(swap! state assoc :gene-info {:lof-ratio lof-ratio})
   :component-did-mount
   (fn [{:keys [state locals]}]
     (let [hash-change-listener (fn [e]
                                  (let [hash (get-window-hash)]
                                    (if hash
                                      (swap! state assoc :hash hash)
                                      (swap! state dissoc :hash))))]
       (swap! locals assoc :hash-change-listener hash-change-listener)
       (.addEventListener js/window "hashchange" hash-change-listener)))
   ;remove the event listener
   :component-will-unmount
   (fn [{:keys [locals]}]
     ;locals is a atom that contains a map
     (.removeEventListener js/window "hashchange" (get @locals :hash-change-listener)))})


(defn render-application []
  (react/render
    (react/create-element
      SearchBoxAndResults
      {})
    (.. js/document (getElementById "app"))))
