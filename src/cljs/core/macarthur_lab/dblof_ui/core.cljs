(ns macarthur-lab.dblof-ui.core
  (:require
   clojure.string
   [dmohs.react :as react]
   [macarthur-lab.dblof-ui.literature :as literature]
   [macarthur-lab.dblof-ui.pd :as pd]
   [macarthur-lab.dblof-ui.pd2 :as pd2]
   [macarthur-lab.dblof-ui.three-experiment :as three-experiment]
   [macarthur-lab.dblof-ui.search-area :as search-area]
   [macarthur-lab.dblof-ui.stats-box :as stats-box]
   [macarthur-lab.dblof-ui.style :as style]
   [macarthur-lab.dblof-ui.utils :as u]
   [macarthur-lab.dblof-ui.variant-table :as variant-table]
   ))


(def api-url-root "http://api.staging.dblof.broadinstitute.org")


(defonce genes-atom (atom nil))


;;if this ajax call fails -> Show error message to user
(when-not @genes-atom
  (u/ajax {:url (str api-url-root "/exec-sql")
           :method :post
           :data (u/->json-string {:sql "select gene from constraint_scores"})
           :on-done (fn [{:keys [get-parsed-response]}]
                      (reset! genes-atom
                              (map clojure.string/lower-case
                                   (map #(get % "gene")
                                        (get (get-parsed-response) "rows")))))}))


(defn- get-gene-name-from-window-hash [window-hash]
  (assert (string? window-hash))
  (nth (clojure.string/split window-hash #"/") 1))


(defn- get-window-hash []
  (let [value (.. js/window -location -hash)]
    (when-not (clojure.string/blank? value) value)))

(def default-map1
  {"South Asian" 0
   "Non-Finnish European" 0
   "Latino" 0
   "Finnish" 0
   "East Asian" 0
   "African" 0})

(defn- calculate-population-for-gene [gene-name cb]
  (u/ajax {:url (str api-url-root "/exec-sql")
           :method :post
           :data (u/->json-string
                  {:sql (str
                         "select afr_caf as `African`,eas_caf as `East Asian`,
                          fin_caf as `Finnish`, amr_caf as `Latino`,
                          nfe_caf as `Non-Finnish European`, sas_caf as `South Asian`
                          from gene_CAF where symbol= ?")
                   :params (clojure.string/upper-case gene-name)})
           :on-done (fn [{:keys [get-parsed-response]}]
                      (let [population_frequencies (keys (merge default-map1 (nth (get (get-parsed-response) "rows") 0)))
                            population_category (vals (merge default-map1 (nth (get (get-parsed-response) "rows") 0)))]
                        (cb population_frequencies population_category)))}))


(defn- calculate-exac-group-age [gene-name cb]
  (u/ajax {:url (str api-url-root "/exec-sql")
           :method :post
           :data (u/->json-string
                  {:sql (str
                         "select `age-bins`, `exac-age-frequency` from "
                         "trunctated_norm_age order by `age-bins`")})
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
                                      "select nadh.bin_ls_20 as `15`, nadh.bin_20 as `20`,
                                       nadh.bin_25 as `25`, nadh.bin_30 as `30`, nadh.bin_35 as `35`,
                                       nadh.bin_40 as `40`, nadh.bin_45 as `45`,nadh.bin_50 as `50`,
                                       nadh.bin_55 as `55`, nadh.bin_60 as `60`, nadh.bin_65 as `65`,
                                       nadh.bin_70 as `70`, nadh.bin_75 as `75`, nadh.bin_80 as `80`,
                                       nadh.bin_85 as `85` from normalised_age_new_histogram nadh
                                       inner join gene_symbols gs on nadh.gene = gs.gene_id where gs.symbol = ?;")
                                :params (clojure.string/upper-case gene-name)
                                })
                        :on-done
                        (fn [{:keys [get-parsed-response]}]
                          (let [age_bins_each_gene (map (fn [s] (js/parseInt s))
                                                        (keys (nth (get (get-parsed-response) "rows") 0)))
                                age_frequencies_each_gene (vals (nth (get (get-parsed-response) "rows") 0))]
                            (cb exac-age-frequency-g1 exac-bins-g1 age_frequencies_each_gene age_bins_each_gene gene-name))
                          )})))}))

(defn- plot [title ref-name]
  [:div { :style {:flex "1 1 50%" :backgroundColor "white" :padding "20px 16px 0 16px"}}
   (style/create-underlined-title title)
   [:div {:style {:paddingLeft 60}}
    [:div {:ref ref-name
           :style {:height 300 :paddingTop 0}}]]])

;;this component is rendered when "hash" is not nill (when someone clicks on one of the gene link)
(react/defc GeneInfo
  {:get-initial-state
   (fn []
     {:auto-select-variants? true
      ;; setting this.state to what was stored in sessionStorage so it maintains with refresh
      :show-canvas? (if (= (.getItem (aget js/window "sessionStorage") "show-canvas?") "true")
                      true
                      false)
      :show-three? (if (= (.getItem (aget js/window "sessionStorage") "show-three?") "true")
                      true
                      false)})
   :render
   (fn [{:keys [this props state]}]
     (let [{:keys [gene-name]} props
           {:keys [show-variants? auto-select-variants?]} @state]
       [:div {:style {:backgroundColor "#E9E9E9"}}
        [:div {:style {:paddingTop 30 :display "flex"}}
         [:div {:style {:flex "1 1 50%"}}
          [:div {:style {:fontSize "180%" :fontWeight 900}}
           "Gene: " (clojure.string/upper-case gene-name)]]
         [:div {:style {:flex "1 1 50%"}}
          [stats-box/Component (merge {:api-url-root api-url-root}
                                      (select-keys props [:gene-name]))]]]
        [:div {:style {:height 30}}]
        [pd/Component (merge {:api-url-root api-url-root}
                             (select-keys props [:gene-name])
                             (select-keys @state [:variants]))]
        [:div {:style {:height 30}}]
        (when (get @state :show-canvas?) ; if :show-canvas? is true, show pd2 div
          [:div {}
           [pd2/Component (merge {:api-url-root api-url-root}
                                 (select-keys props [:gene-name])
                                 (select-keys @state [:variants]))]
           [:div {:style {:height 30}}]])
        (when (get @state :show-three?) ;if :show-three? is true, show three-experiment div
          [:div {} [three-experiment/Component (merge {:api-url-root api-url-root}
                                                       (select-keys props [:gene-name])
                                                       (select-keys @state [:variants]))]
           [:div {:style {:height 30}}]])
        [:div {:style {:display "flex" :justifyContent "space-between"}}
         (plot "Age distribution" "group-plot")
         [:div {:style {:flex "1 1 30px"}}]
         (plot "Population distribution" "population-plot")]
        [:div {:style {:height 30}}]
        [:div {:style {:display "flex"}}
         [:div {:style {:flex "0 0 50%"
                        :backgroundColor (when-not show-variants? "white")
                        :cursor (when show-variants? "pointer")
                        :padding "10px 0"
                        :fontWeight "bold" :fontSize "120%" :textAlign "center"}
                :onClick #(swap! state assoc :show-variants? false :auto-select-variants? false)}
          "Gene Information"]
         [:div {:style {:flex "0 0 50%"
                        :backgroundColor (when show-variants? "white")
                        :cursor (when-not show-variants? "pointer")
                        :padding "10px 0"
                        :fontWeight "bold" :fontSize "120%" :textAlign "center"}
                :onClick #(swap! state assoc :show-variants? true)}
          "Variant Information"]]
        [:div {:style {:backgroundColor "white"}}
         (if show-variants?
           [variant-table/Component (merge {:api-url-root api-url-root}
                                           (select-keys props [:gene-name])
                                           (select-keys @state [:variants :variants-v2]))]
           [literature/Component
            (merge {:api-url-root api-url-root
                    :on-loaded (fn [has-literature?]
                                 (when (:auto-select-variants? @state)
                                   (swap! state assoc :auto-select-variants? false)
                                   (when-not has-literature?
                                     (swap! state assoc :show-variants? true))))}
                   (select-keys props [:gene-name]))])]
        [:div {:style {:height 50}}]]))
   :component-did-mount
   (fn [{:keys [this state props]}]
     (this :render-plots (:gene-name props))
     (this :load-variants-data (:gene-name props))
     ;;define the feature flag functions
     (aset js/window "showCanvas" (fn [] (swap! state assoc :show-canvas? true)))
     (aset js/window "hideCanvas" (fn [] (swap! state assoc :show-canvas? false)))
     (aset js/window "showThree" (fn [] (swap! state assoc :show-three? true)))
     (aset js/window "hideThree" (fn [] (swap! state assoc :show-three? false))))
   :component-will-receive-props
   (fn [{:keys [this props state next-props]}]
     (when-not (apply = (map :gene-name [props next-props]))
       (swap! state dissoc :show-variants?)
       (swap! state assoc :auto-select-variants? true)
       (this :render-plots (:gene-name next-props))
       (this :load-variants-data (:gene-name next-props))))
   :component-did-update
   (fn [{:keys [state]}]
     ;; persist the value of :show-canvas? into "show-canvas?" key of session storage
     (.setItem (aget js/window "sessionStorage") "show-canvas?" (str (get @state :show-canvas?)))
     (.setItem (aget js/window "sessionStorage") "show-three?" (str (get @state :show-three?))))
   :build-each-gene-pop-plot
   (fn [{:keys [this refs state props]} x y gene-name]
     (.newPlot js/Plotly (@refs "population-plot")
            (clj->js [{:type "bar"
                       :x y
                       :y x
                       :orientation "h"
                       :marker {:color ["FF9912" "#6AA5CD"
                                        "#ED1E24" "#002F6C"
                                        "#108C44" "#941494"
                                        ]}}])
            (clj->js {
                      :xaxis {:autorange true
                              :showgrid false
                              :showticklabels false
                              :title "Frequency" :titlefont {:size 14}}
                      :yaxis {:autorange true
                              :showgrid false
                              :autotick false}
                      })
           (clj->js {
                     :displayModeBar false
                    })))
   :build-group-ages-plot
   (fn [{:keys [this refs state props]} x1 y1 x2 y2 gene-name]
     (.newPlot js/Plotly (@refs "group-plot")
               (clj->js [{:type "bar"
                          :name "All ExAC individuals"
                          :x y1
                          :y x1
                          :marker {:color "47cccc"}}
                         {:type "bar"
                          :name  (str "LoF carriers in " (clojure.string/upper-case gene-name) )
                          :x y2
                          :y x2
                          :displayModeBar false}])
               (clj->js {
                         :xaxis {:autorange true
                                 :showgrid false
                                 :title "Age" :titlefont {:size 14}}
                         :yaxis {:autorange true
                                 :showgrid false
                                 :title "Frequency" :showticklabels false :titlefont {:size 14}}
                         :legend {:x 0 :y 1.35 :bgcolor "rgba(255, 255, 255, 0)"}
                         :displayModeBar false})
               (clj->js {
                         :displayModeBar false
                         })))
   :render-plots
   (fn [{:keys [this props state refs]} gene-name]
     (calculate-population-for-gene
      gene-name
      (fn [x y]
        (react/call :build-each-gene-pop-plot this x y gene-name)))
     (calculate-exac-group-age
      gene-name
      (fn [x1 y1 x2 y2 gene-name]
        (react/call :build-group-ages-plot this x1 y1 x2 y2 gene-name))))
   :load-variants-data
   (fn [{:keys [props state]} gene-name]
     (let [exec-mongo-url (str api-url-root "/exec-mongo")
           exec-sql-url (str api-url-root "/exec-sql")
           gene-name-uc (clojure.string/upper-case gene-name)]
       (u/ajax {:url exec-sql-url
                :method :post
                :data (u/->json-string
                       {:sql (str
                              "select * from variants v\n"
                              "inner join gene_symbols gs on v.gene_id = gs.gene_id\n"
                              "where gs.symbol = ? and Annotation in "
                              "('splice acceptor', 'stop gained', 'splice donor', 'frameshift')")
                        :params [gene-name-uc]})
                :on-done (fn [{:keys [get-parsed-response]}]
                           (swap! state assoc
                                  :variants-v2
                                  (map (fn [v]
                                         (assoc v
                                                "Variant"
                                                (str (v "Chrom") ":" (v "Position")
                                                     " " (v "Reference") " / " (v "Alternate"))
                                                "Allele Count" (js/parseInt (v "Allele Count"))
                                                "Position" (js/parseInt (v "Position"))
                                                "Allele Number" (js/parseInt (v "Allele Number"))
                                                "Number of Homozygotes"
                                                (js/parseInt (v "Number of Homozygotes"))))
                                       (get (get-parsed-response) "rows"))))})
       (u/ajax {:url exec-mongo-url
                :method :post
                :data (u/->json-string
                       {:collection-name "genes"
                        :query {:gene_name_upper {:$eq gene-name-uc}}
                        :projection {:gene_id 1}})
                :on-done
                (fn [{:keys [get-parsed-response]}]
                  (let [gene-id (get-in (get-parsed-response) [0 "gene_id"])]
                    (u/ajax {:url exec-mongo-url
                             :method :post
                             :data (u/->json-string
                                    {:collection-name "variants"
                                     :query (variant-table/create-variants-query gene-id)
                                     :projection variant-table/query-projection
                                     :options {:limit 10000}})
                             :on-done
                             (fn [{:keys [get-parsed-response]}]
                               (swap! state assoc :variants (get-parsed-response)))})))})))})

;;component for search box
(react/defc App
  {:get-initial-state
   (fn [] {:hash (get-window-hash)})
   :render
   (fn [{:keys [this state refs]}]
     (let [{:keys [hash exac-age-info age-bins logged-in?]} @state]
       (if logged-in?
         [:div {}
          [search-area/Component {:api-url-root api-url-root :compact? hash}]
          (when hash
            [GeneInfo {:hash hash :gene-name (get-gene-name-from-window-hash hash)}])]
         (let [{:keys [values]} @state
               {:keys [username password]} values]
           [:div {:style {:backgroundColor "#343A41" :color "white"
                          :padding 20}}
            [search-area/Logo]
            [:div {:style {:marginTop "15vh" :display "flex" :justifyContent "center"}}
             [:div {:style {:textTransform "uppercase" :fontSize "xx-large" :fontWeight 900}}
              "Log-In"]]
            [:div {:style {:marginTop 12 :display "flex" :justifyContent "center"}}
             [:input {:placeholder "Username"
                      :value (or username "")
                      :onChange #(swap! state assoc-in [:values :username] (-> % .-target .-value))
                      :style {:width "20ex" :height "1.5em"
                              :fontSize "large" :verticalAlign "top"}}]]
            [:div {:style {:marginTop 12 :display "flex" :justifyContent "center"}}
             [:input {:type "password"
                      :placeholder "Password"
                      :value (or password "")
                      :onChange #(swap! state assoc-in [:values :password] (-> % .-target .-value))
                      :style {:width "20ex" :height "1.5em"
                              :fontSize "large" :verticalAlign "top"}}]]
            [:div {:style {:marginTop 12 :display "flex" :justifyContent "center"}}
             [:button {:onClick (fn [e]
                                  (when (and (= username "dblofpreview") (= password "qY9CW7MDdY"))
                                    (swap! state assoc :logged-in? true)))
                       :style {:width "10ex" :height "1.5em"
                               :fontSize "large" :verticalAlign "top"}}
              "Log-In"]]
            [:div {:style {:clear "both"
                           :margin "10vh -20px -20px -20px"
                           :height 4 :backgroundColor "#24AFB2"}}]]))))
   :component-did-mount
   (fn [{:keys [state locals]}]
     (let [hash-change-listener (fn [e]
                                  (let [hash (get-window-hash)]
                                    (if hash
                                      (swap! state assoc :hash hash)
                                      (swap! state dissoc :hash))))]
       (swap! locals assoc :hash-change-listener hash-change-listener)
       (.addEventListener js/window "hashchange" hash-change-listener)))
   :component-will-unmount
   (fn [{:keys [locals]}]
     (.removeEventListener js/window "hashchange" (get @locals :hash-change-listener)))})


(defn render-application []
  (react/render
    (react/create-element
      App
      {})
    (.. js/document (getElementById "app"))))
