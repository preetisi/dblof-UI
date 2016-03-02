(ns macarthur-lab.dblof-ui.core
  (:require
   devcards.core
   [dmohs.react :as react]
   [macarthur-lab.dblof-ui.utils :as u])
  (:require-macros
   [devcards.core :refer [defcard]]))


(react/defc ClickyDiv
  {:get-initial-state
   (fn []
     {:click-count 0})
   :render
   (fn [{:keys [props state]}]
     [:div {}
       "Hi"
      [:div {:style {:float "right" :color "#666"}}"dbLoF"]
      [:h1 {:style {:color "#666" :fontStyle "italic"}} "PCSK9"]
      [:div {:style {:color "white" :backgroundColor "#666" :padding "4px 10px"}} "Exac data"]
      [:div {}[:div {} "obs/exp"][:div {} "95%"]]
      [:div {}[:div {} "Cumulative AF"][:div {} "0.07%"]]





      [:div {} [:button {:onClick (fn [e] (swap! state update-in [:click-count] inc))} "Clicky"]]
      [:div {} "The above button has been clicked " (:click-count @state) " times."]
      [:div {} "Today's magic word is: \"" (:word props) "\""]
      [:dev {} [:button {:onClick (fn [e] ((:on-word-request props)))} "Request New Word"]]])})


(defn- get-random-word []
  (rand-nth ["filthy" "morose" "flippant" "silly" "thorn"]))


(defcard clicky-div
  (react/wrap-devcard-fn
   (fn [data-atom owner devcard-props]
     [ClickyDiv (merge devcard-props
                       {:name "nice person"
                        :word (:word @data-atom)
                        :on-word-request #(swap! data-atom assoc :word (get-random-word))})]))
  {:word (get-random-word)}
  {:inspect-data true})


(def word-database ["happy" "sad" "scared" "angry" "elephant" "cat" "giraffe" "lion" "tiger"])


(defn render-application [& [hot-reload?]]
  (react/render
   (react/create-element
    ClickyDiv
    {:name "Application"
     :word (rand-nth word-database)
     :on-word-request #(js/alert "Not yet implemented.")})
   (.. js/document (getElementById "app"))
   nil
   hot-reload?))
