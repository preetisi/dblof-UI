(ns macarthur-lab.dblof-ui.stats-box
  (:require
   clojure.string
   [dmohs.react :as react]
   [macarthur-lab.dblof-ui.utils :as u]
   ))

(react/defc Component
  {:render
   (fn [{:keys [state]}]
     (let [{:keys [status]} @state
           {:strs [lof-ratio cumulative-af homozygotes-count pli]} (:data status)]
       [:div {:style {:backgroundColor "white" :padding "20px 16px 16px 16px"}}
        [:div {:style {:display "flex"}}
         [:div {:style {:flex "0 0 25%"}}
          [:div {:style {:fontWeight "bold"}} "Obs/Exp"]]
         [:div {:style {:flex "0 0 25%"}}
          [:div {:style {:fontWeight "bold"}} "Cumulative AF"]]
         [:div {:style {:flex "0 0 25%"}}
          [:div {:style {:fontWeight "bold"}} "Homozygotes"]]
          [:div {:style {:flex "0 0 25%"}}
           [:div {:style {:fontWeight "bold"}} "pLI"]]]
        [:div {:style {:marginTop 8 :height 1 :backgroundColor "#959A9E"}}]
        [:div {:style {:marginTop 10 :display "flex" :fontWeight 100}}
         [:div {:style {:flex "0 0 25%"}}
          [:div {} (if lof-ratio (str (.toFixed lof-ratio 2) "%") "loading")]]
         [:div {:style {:flex "0 0 25%"}}
          [:div {} (if cumulative-af (str (.toFixed (* cumulative-af 100) 2) "%") "loading")]]
         [:div {:style {:flex "0 0 25%"}}
          [:div {} (if homozygotes-count homozygotes-count "loading")]]
          [:div {:style {:flex "0 0 25%"}}
           [:div {} (if pli (.toFixed pli 2)"loading")]]]]))
   :component-did-mount
   (fn [{:keys [this props]}] (this :load-data (:gene-name props)))
   :component-will-receive-props
   (fn [{:keys [this props state next-props]}]
     (when-not (apply = (map :gene-name [props next-props]))
       (swap! state dissoc :status)
       (this :load-data (:gene-name next-props))))
   :load-data
   (fn [{:keys [props state]} gene-name]
     (u/ajax {:url (str (:api-url-root props) "/exec-sql")
              :method :post
              :data (u/->json-string
                     {:sql (str
                            "select\n"
                            "(select (n_lof / exp_lof) * 100 from constraint_scores\n"
                            "where gene = ?) as `lof-ratio`,\n"
                            "(select cast(pli as decimal(10,2)) from constraint_scores where gene = ?) as `pli`,\n"
                            "(select sum(`num_hom`) from (select sum(`Number of Homozygotes`) as `num_hom`\n"
                            "from variants v inner join gene_symbols gs on v.gene_id = gs.gene_id\n"
                            "where gs.symbol = ? and Annotation in ('splice acceptor', 'stop gained','splice donor', 'frameshift')\n"
                            "union select sum(`num_hom`) from vanheel_variants_new vv inner join gene_symbols gs on vv.gene_id = gs.gene_id\n"
                            "where gs.symbol = ? and Annotation in ('splice_acceptor_variant', 'stop_gained', 'splice_donor_variant', 'frameshift_variant')) G)\n"
                            "as `homozygotes-count`,\n"
                            "(select sum(`Allele Frequency`) from variants v inner join gene_symbols\n"
                            "gs on v.gene_id = gs.gene_id where gs.symbol = ? and \n"
                            "Annotation in ('splice acceptor', 'stop gained', 'splice donor', 'frameshift')) as `cumulative-af`;")
                      :params (repeat 5 gene-name)})
              :on-done (fn [{:keys [get-parsed-response]}]
                         (let [gene-info (first (get (get-parsed-response) "rows"))]
                           (swap! state assoc :status {:code :loaded :data gene-info})))}))})
