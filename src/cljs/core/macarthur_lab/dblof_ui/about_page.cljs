(ns macarthur-lab.dblof-ui.about-page
  (:require
   clojure.string
   [dmohs.react :as react]
   [macarthur-lab.dblof-ui.style :as style]
   [macarthur-lab.dblof-ui.utils :as u]
   ))

(react/defc Component
  {:render
   (fn []
     [:div {:style {:display "flex" :backgroundColor "#e0e0e0" :padding "40px"
                    :justifyContent "space-between"}}
      [:div {:style {:flex "0 0 70%" :backgroundColor "#fafafa" :padding "20px 20px"
                     :fontSize "0.875em"}}
       [:div {:style {:fontWeight "bold" :fontSize "1.875em" :color "#444242" :textAlign "center"}}
        "About dbLoF"]
       [:div {:style {:marginTop 10 :marginBottom 5 :height 1 :backgroundColor "#444242"}}]
       "dbLoF is a database of predicted human loss-of-function (LoF) variants, providing information on the distribution,
          prevalence, and functional validation status of LoF variants discovered from a range of different sequencing studies.
         The goal of dbLoF is to empower the identification, validation, and clinical follow-up of human \"knockout\" individuals
         to improve our understanding of the function of human genes, and to better identify candidate therapeutic drug targets."
        [:div {:style {:paddingTop 10 :fontWeight "bold" :fontSize "1.5em" :color "#444242" :textAlign "left"}}
         "Authors"
         [:div {:style {:marginTop 10 :marginBottom 5 :height 1 :backgroundColor "#444242"}}]
         [:div {:style {:fontWeight "bold" :fontSize 10 :color "#444242" :textAlign "left"}}
          "Daniel MacArthur" [:br]
           "Narasimhan et al (Science 2016):
            Born In Bradford: John Wright Birmingham: Eamonn Maher" [:br]
            "David van Heel, Richard Trembath, Daniel MacArthur, Richard Durbin"]]
         [:div {:style {:paddingTop 10 :fontWeight "bold" :fontSize "1.5em" :color "#444242" :textAlign "left"}}
          "Software Team"
          [:div {:style {:marginTop 10 :marginBottom 5 :height 1 :backgroundColor "#444242"}}]
          [:div {:style {:fontWeight "bold" :fontSize 10 :color "#444242" :textAlign "left"}}
           "Preeti Singh"[:br]
             "David Mohs"[:br]
             "Ben Weisburd"]
          ]]
      [:div {:style {:flex "1 1 30px"}}]
      [:div {:style {:flex "1 1 30%" :height 300 :backgroundColor "#fafafa" :padding "20px 20px"
                     :fontSize "0.875em"}}
       [:div {:style {:fontWeight "bold" :fontSize "1.875em" :color "#444242" :textAlign "center"}}
        "Recent News"]
       [:div {:style {:marginTop 10 :marginBottom 5 :height 1 :backgroundColor "#444242"}}]
       "October 20, 2016" [:br]
       "-Version 0.2 launch at ASHG 2016" [:br]
       [:br]
       "June 1, 2016" [:br]
        "-Version 0.1 launch" [:br]]])})
