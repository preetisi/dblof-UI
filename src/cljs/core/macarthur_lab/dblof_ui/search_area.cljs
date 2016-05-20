(ns macarthur-lab.dblof-ui.search-area
  (:require
    clojure.string
    [dmohs.react :as react]
    [macarthur-lab.dblof-ui.utils :as u]
    ))


(react/defc Logo
  {:render
   (fn []
     [:span {:style {:display "inline-block"}}
      [:span {:style {:display "block" :fontSize 40}}
       [:span {:style {:color "#24AFB2" :fontWeight 100}} "db"]
       [:span {:style {}} "LoF"]]
      [:span {:style {:display "block"
                      :fontWeight 100 :fontSize 10 :textTransform "uppercase" :color "#959A9E"}}
       "Loss-of-Function Variants Database"]])})


(react/defc SearchBox
  {:render
   (fn []
     [:div {}
      [:div {:style {:fontSize "large"}}
       [:input {:style {:width "50vw" :height "1.5em" :fontSize "large" :verticalAlign "top"}}]
       [:span {:style {:display "inline-block" :backgroundColor "#24AFB2"
                       :height "1.5em" :width "1.5em" :padding 3}}
        [:span {:style {:display "inline-block" :margin "-4px 0 0 6px"
                        :fontSize "x-large"
                        :WebkitTransform "rotate(-45deg)"}}
         "⚲"]]]
      [:div {:style {:marginTop "1em" :fontStyle "italic" :fontSize "small"}}
       "Examples - Gene: "
       [:a {:href "#genes/pcsk9"
            :style {:color "#CEF4F3" :textDecoration "none" :fontStyle "normal"}}
        "PCSK9"]]])})


(react/defc Component
  {:render
   (fn []
     [:div {:style {:backgroundColor "#343A41" :color "white"
                    :padding "20px 0 20px 20px"}}
      [Logo]
      [:div {:style {:marginTop "14vh" :display "flex" :justifyContent "center"}}
       [:div {:style {:textTransform "uppercase" :fontSize "xx-large" :fontWeight 900}}
        "Search Gene/Variant"]]
      [:div {:style {:marginTop 12 :display "flex" :justifyContent "center"}}
       [SearchBox]]])})
