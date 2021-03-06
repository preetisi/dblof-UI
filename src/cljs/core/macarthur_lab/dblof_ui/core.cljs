(ns macarthur-lab.dblof-ui.core
  (:require
   clojure.string
   [dmohs.react :as react]
   [macarthur-lab.dblof-ui.about-page :as about-page]
   [macarthur-lab.dblof-ui.floating :as floating]
   [macarthur-lab.dblof-ui.literature :as literature]
   [macarthur-lab.dblof-ui.pd2 :as pd2]
   [macarthur-lab.dblof-ui.pdconstitutive :as pdconstitutive]
   [macarthur-lab.dblof-ui.three-experiment :as three-experiment]
   [macarthur-lab.dblof-ui.search-area :as search-area]
   [macarthur-lab.dblof-ui.stats-box :as stats-box]
   [macarthur-lab.dblof-ui.style :as style]
   [macarthur-lab.dblof-ui.utils :as u]
   [macarthur-lab.dblof-ui.variant-table :as variant-table]
   ))

(def api-url-root "http://api.dblof.broadinstitute.org")

(defonce genes-atom (atom nil))

;; TODO: if this ajax call fails -> Show error message to user
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

(defn change-label[xs]
  (map (fn [x]
         (case x 15 "<20" (str x)
               x 85 ">80" (str x))) xs))

(defn put-labels[xs]
  (map (fn [x] (case x "<20" 15 ">80" 85 (int x))) xs))

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
                                       inner join gene_symbols gs on nadh.gene = gs.gene_id where gs.symbol = ?")
                                :params (clojure.string/upper-case gene-name)})
                        :on-done
                        (fn [{:keys [get-parsed-response]}]
                          (let [age_bins_each_gene
                                (map (fn [s] (js/parseInt s))
                                     (keys (nth (get (get-parsed-response) "rows") 0)))
                                age_frequencies_each_gene
                                (vals (nth (get (get-parsed-response) "rows") 0))]
                            (cb exac-age-frequency-g1 (change-label exac-bins-g1) age_frequencies_each_gene
                                (change-label age_bins_each_gene) gene-name)))})))}))

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
        [pdconstitutive/Component (merge {:api-url-root api-url-root}
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
                                           (select-keys @state [:variants]))]
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
                                        "#108C44" "#941494"]}}])
            (clj->js {:xaxis {:autorange true
                              :showgrid false
                              :showticklabels false
                              :title "Frequency" :titlefont {:size 14}}
                      :yaxis {:autorange true
                              :showgrid false
                              :autotick false}})
           (clj->js {:displayModeBar false})))
   :build-group-ages-plot
   (fn [{:keys [this refs state props]} x1 y1 x2 y2 gene-name]
     (.newPlot js/Plotly (@refs "group-plot")
               (clj->js [{:type "bar"
                          :name "All ExAC individuals"
                          :x (put-labels y1)
                          :y x1
                          :marker {:color "47cccc"}}
                         {:type "bar"
                          :name  (str "LoF carriers in " (clojure.string/upper-case gene-name) )
                          :x (put-labels y2)
                          :y x2
                          :displayModeBar false}])
               (clj->js {:xaxis {:showgrid false
                                 :title "Age"
                                 :titlefont {:size 14}
                                 :ticktext y1
                                 :tickvals (put-labels y1)}
                         :yaxis {:showgrid false
                                 :title "Frequency"
                                 :showticklabels false
                                 :titlefont {:size 14}}
                         :legend {:x 0 :y 1.35 :bgcolor "rgba(255, 255, 255, 0)"}
                         :displayModeBar false})
               (clj->js {:displayModeBar false})))
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
     (let [exec-sql-url (str api-url-root "/exec-sql")
           gene-name-uc (clojure.string/upper-case gene-name)]
       (u/ajax {:url exec-sql-url
                :method :post
                :data (u/->json-string
                        {:sql (str
                          "select vv.gene_id, vv.chrom as Chrom, vv.position,\n"
                           "vv.reference as Reference, vv.alternate as Alternate,vv.Consequence,vv.Filter,\n"
                           "vv.annotation as Annotation, vv.flags as Flags, vv.`allele_count` as allele_count,\n"
                           "vv.`allele_number` as allele_number, vv.`num_hom` as num_hom,vv.`allele_freq` as `Allele Frequency`,\n"
                           "vv.in_vanheel, vv.in_exac, m.`description`,m.annotation as `Manual Annotation`, m.`author`,\n"
                           "m.date,gs.symbol from vanheel_variants_new as vv \n"
                           "inner join gene_symbols gs on vv.gene_id=gs.gene_id and vv.annotation in \n"
                           "('splice_acceptor_variant', 'stop_gained', 'splice_donor_variant', 'frameshift_variant') \n"
                           "left join  mannually_curated_variants as m on vv.chrom=m.chrom and  vv.reference=m.ref \n"
                           "and vv.alternate=m.alt and vv.position=m.pos where gs.symbol= ? \n"
                           "union select v.gene_id, v.chrom as Chrom, v.position, v.reference as Reference, \n"
                           "v.alternate as Alternate,v.Consequence,v.Filter, v.annotation as Annotation, \n"
                           "v.flags as Flags, v.`allele count` as allele_count,v.`allele number` as allele_number, \n"
                           "v.`number of homozygotes` as num_hom,v.`allele frequency` as `Allele Frequency` , \n"
                           "v.in_vanheel as `in_vanheel`, v.in_exac as `in_exac`, m.`description`,m.annotation as `Manual Annotation`, \n"
                           "m.`author`, m.date, gs.symbol from variants_backup_2016_09_07 as v inner join gene_symbols gs \n"
                           "on v.gene_id=gs.gene_id and v.annotation in ('splice acceptor', 'stop gained', 'splice donor', 'frameshift') \n"
                           "left join mannually_curated_variants as m on v.chrom=m.chrom and  v.reference=m.ref and v.alternate=m.alt \n"
                           "and v.position=m.pos where gs.symbol= ?")
                         :params [gene-name-uc gene-name-uc]}
                       )
                :on-done (fn [{:keys [get-parsed-response]}]
                           (swap! state assoc
                                  :variants
                                  (map (fn [v]
                                         (assoc v
                                                "Variant"
                                                (str (v "Chrom") ":" (v "position")
                                                     " " (v "Reference") " / " (v "Alternate"))
                                                "Allele Count" (js/parseInt (v "allele_count"))
                                                "Position" (js/parseInt (v "position"))
                                                "Allele Number" (js/parseInt (v "allele_number"))
                                                "Number of Homozygotes"
                                                (js/parseInt (v "num_hom"))))
                                       (get (get-parsed-response) "rows"))))})))})

(react/defc SearchBoxAndResults
  {:get-initial-state
   (fn [] {:hash (get-window-hash)})
   :render
   (fn [{:keys [this state refs]}]
     (let [{:keys [hash exac-age-info age-bins]} @state]
       [:div {}
        [search-area/Component {:api-url-root api-url-root :compact? hash}]
        ; if gene-info returns true then dont show the about page
        (if-not hash
          [about-page/Component]
          [GeneInfo {:hash hash :gene-name (get-gene-name-from-window-hash hash)}])
        [floating/Component {:ref "floats"}]]))
   :component-did-mount
   (fn [{:keys [state refs locals]}]
     (let [hash-change-listener (fn [e]
                                  (let [hash (get-window-hash)]
                                    (if hash
                                      (swap! state assoc :hash hash)
                                      (swap! state dissoc :hash))))]
       (swap! locals assoc :hash-change-listener hash-change-listener)
       (.addEventListener js/window "hashchange" hash-change-listener))
     (floating/set-instance! (@refs "floats")))
   :component-will-unmount
   (fn [{:keys [locals]}]
     (.removeEventListener js/window "hashchange" (get @locals :hash-change-listener)))})

(defn render-application []
  (react/render
    (react/create-element
      SearchBoxAndResults
      {})
    (.. js/document (getElementById "app"))))
