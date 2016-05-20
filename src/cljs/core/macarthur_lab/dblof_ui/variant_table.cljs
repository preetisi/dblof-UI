(ns macarthur-lab.dblof-ui.variant-table
  (:require
   clojure.string
   [dmohs.react :as react]
   [macarthur-lab.dblof-ui.utils :as u]
   ))


(react/defc Component
  {:render
   (fn [{:keys [props]}]
     [:div {:style {:marginTop 50}}
      [:div {:style {:display "flex"}}
       [:div {:style {:flex "0 0 20%" :padding "10px"
                      :overflow "hidden" :textOverflow "ellipsis"}}
        "variant_id"]
       [:div {:style {:flex "0 0 20%" :padding "10px"}}
        "chrom"]
       [:div {:style {:flex "0 0 20%"  :padding "10px"}}
        "pos"]
       [:div {:style {:flex "0 0 20%" :padding "10px"}}
        "allele_freq"]
       [:div {:style {:flex "0 0 20%" :padding "10px"}}
        "hom_count"]]
      [:div {}
       (map (fn [x]
              [:div {:style {:display "flex"}}
               [:div {:style {:flex "0 0 20%" :padding "10px" :boxSizing "border-box"
                              :overflow "hidden" :textOverflow "ellipsis"}}
                (get x "variant_id")]
               [:div {:style {:flex "0 0 20%" :padding "10px"}}
                (get x "chrom")]
               [:div {:style {:flex "0 0 20%"  :padding "10px"}}
                (get x "pos")]
               [:div {:style {:flex "0 0 20%" :padding "10px"}}
                (get x "allele_freq")]
               [:div {:style {:flex "0 0 20%" :padding "10px"}}
                (get x "hom_count")]])
            (:variants props))]])})
