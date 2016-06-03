(ns macarthur-lab.dblof-ui.core
  (:require
   clojure.string
   [dmohs.react :as react]
   [macarthur-lab.dblof-ui.gene-info :as gene-info]
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
                         "select `age-bins`, `exac-age-frequency` from trunctated_norm_age"
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
                                      "select agebins as 'each-age-bins',
                                       `af.agefreq/af_sums.s` as 'exac-each-gene-age-frequency'
                                        from truncated_norm_age_each_gene where gene =?")
                                :params (clojure.string/upper-case gene-name)
                                })
                        :on-done
                        (fn [{:keys [get-parsed-response]}]
                          (let [each-gene-age-feq-g2 (map (fn [m] (get m "exac-each-gene-age-frequency"))
                                                          (get (get-parsed-response) "rows"))
                                each-gene-age-bins-g2 (map (fn [m] (get m "each-age-bins"))
                                                           (get (get-parsed-response) "rows"))]
                            (cb exac-age-frequency-g1 exac-bins-g1 each-gene-age-feq-g2 each-gene-age-bins-g2 gene-name))
                          )})))}))

(defn- create-variants-query [gene-id]
  {:genes {:$in [gene-id]}
   :filter "PASS"
   :vep_annotations
   {:$elemMatch
    {:Gene gene-id
     :LoF {:$ne ""}}}})

(def variants-projection
  (reduce (fn [r col] (assoc r (:key col) 1)) {:vep_annotations 1} variant-table/columns))

; this component is rendered when "hash" is not nill (when someone clicks on one of the gene link)
(react/defc GeneInfo
  {:render
   (fn [{:keys [this props state]}]
     (let [{:keys [gene-name]} props
           {:keys [each-gene-pop? each-gene-age? show-gene-info?]} @state]
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
        [:div {:style {:display "flex" :justifyContent "space-between"}}
         ;; group age plot
         [:div {:ref "group-plot" :style {:flex "1 1 50%" :height 300}}]
         [:div {:style {:flex "1 1 30px"}}]
         [:div {:style {:flex "1 1 50%"}}
          [:div {:style {:paddingLeft 70 :backgroundColor "white"}}
           [:div {:ref "population-plot" :style {:height 300}}]]]]
        [:div {:style {:height 30}}]
        [:div {:style {:display "flex"}}
         [:div {:style {:flex "0 0 50%"
                        :backgroundColor (when-not show-gene-info? "white")
                        :cursor (when show-gene-info? "pointer")
                        :padding "10px 0"
                        :fontWeight "bold" :fontSize "120%" :textAlign "center"}
                :onClick #(swap! state assoc :show-gene-info? false)}
          "Variant Information"]
         [:div {:style {:flex "0 0 50%"
                        :backgroundColor (when show-gene-info? "white")
                        :cursor (when-not show-gene-info? "pointer")
                        :padding "10px 0"
                        :fontWeight "bold" :fontSize "120%" :textAlign "center"}
                :onClick #(swap! state assoc :show-gene-info? true)}
          "Gene Information"]]
        [:div {:style {:backgroundColor "white"}}
         (if show-gene-info?
           [gene-info/Component (merge {:api-url-root api-url-root}
                                       (select-keys props [:gene-name]))]
           [variant-table/Component (merge {:api-url-root api-url-root}
                                           (select-keys props [:gene-name])
                                           (select-keys @state [:variants]))])]
        [:div {:style {:height 50}}]]))
   :component-did-mount
   (fn [{:keys [this props]}]
     (this :render-plots (:gene-name props))
     (this :load-variants-data (:gene-name props)))
   :component-will-receive-props
   (fn [{:keys [this props state next-props]}]
     (when-not (apply = (map :gene-name [props next-props]))
       (this :render-plots (:gene-name next-props))
       (this :load-variants-data (:gene-name next-props))))
   :build-each-gene-pop-plot
   (fn [{:keys [this refs state props]} x y gene-name]
     (.newPlot js/Plotly (@refs "population-plot")
            (clj->js [{:type "bar"
                       :name (str "Population " (clojure.string/upper-case gene-name) )
                       :x y
                       :y x
                       :orientation "h"
                       :marker {:color ["#941494" "#108C44"
                                        "#002F6C" "#ED1E24"
                                        "#6AA5CD" "FF9912"
                                        ]}

                      }])
            (clj->js {:title "Population distribution"
                      :xaxis {:autorange true
                              :showgrid false
                              :title "Frequency" :titlefont {:family "Arial"}}
                      :yaxis {:autorange true
                              :showgrid false
                              :autotick false}
                      })))
   ;;"South Asian"  "Others" "Non-Finnish European" "Finnish" "East Asian" "American" "African"
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
                       :y x2}])
            (clj->js {
                      :xaxis {:autorange true
                               :showgrid false
                               :title "Age" :titlefont {:size 14 :color "black" :family "Arial"}}
                      :yaxis {:autorange true
                               :showgrid false
                               :title "Frequency" :showticklabels false}
                      :legend {:x 0 :y 1.35 :bgcolor "rgba(255, 255, 255, 0)"}


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
           gene-name-uc (clojure.string/upper-case gene-name)]
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
                                     :query (create-variants-query gene-id)
                                     :projection variants-projection
                                     :options {:limit 10000}})
                             :on-done
                             (fn [{:keys [get-parsed-response]}]
                               (swap! state assoc :variants (get-parsed-response)))})))})))})


(defn transform-vector-to-gene-label-map [m]
  {:label (get m "gene")
   :value (get m "gene")})


;component for search box
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
