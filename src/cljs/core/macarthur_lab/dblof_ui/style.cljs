(ns macarthur-lab.dblof-ui.style
  (:require
   clojure.string
   [dmohs.react :as react]
   [macarthur-lab.dblof-ui.utils :as u]
   ))

;;styling for the line under the title of each graph
(defn create-underlined-title [text]
  [:div {}
   [:div {:style {:fontWeight "bold"}} text]
   [:div {:style {:marginTop 8 :height 1 :backgroundColor "#959A9E"}}]])

(defn put-arrows [arrow-str]
  [:div {}
   [:div {:style {:height 1 :backgroundColor "#ccc" :fontSize "25px" :fontWeight "bolder"
                 :position "absolute" :width "100%" :bottom 30 :left -20}}
    arrow-str]])
