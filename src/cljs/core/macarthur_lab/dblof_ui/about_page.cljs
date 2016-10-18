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
      [:div {:style {:flex "1 1 70%" :backgroundColor "#fafafa" :padding "20px 20px"
                     :fontSize "0.875em"}}
       [:div {:style {:fontWeight "bold" :fontSize "1.875em" :color "#444242" :textAlign "center"}}
        "About dbLoF"]
       [:div {:style {:marginTop 10 :marginBottom 5 :height 1 :backgroundColor "#444242"}}]
       "dbLoF is a database of predicted human loss-of-function (LoF) variants, providing information on the distribution,
          prevalence, and functional validation status of LoF variants discovered from a range of different sequencing studies.
         The goal of dbLoF is to empower the identification, validation, and clinical follow-up of human \"knockout\" individuals
         to improve our understanding of the function of human genes, and to better identify candidate therapeutic drug targets.
        Authors Narasimhan et al (Science 2016):
        Born In Bradford: John Wright
        Birmingham: Eamonn Maher
        general: David van Heel, Richard Trembath, Daniel MacArthur, Richard Durbin [http://science.sciencemag.org/content/352/6284/474.full]"]
      [:div {:style {:flex "1 1 30px"}}]
      [:div {:style {:flex "1 1 30%" :height 300 :backgroundColor "#fafafa" :padding "20px 20px"
                     :fontSize "0.875em"}}
       [:div {:style {:fontWeight "bold" :fontSize "1.875em" :color "#444242" :textAlign "center"}}
        "Recent News"]
       [:div {:style {:marginTop 10 :marginBottom 5 :height 1 :backgroundColor "#444242"}}]
       "June 1, 2016 -Version 0.1 launch"]])})
