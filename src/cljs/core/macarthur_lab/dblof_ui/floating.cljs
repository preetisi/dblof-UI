(ns macarthur-lab.dblof-ui.floating
  (:require
   clojure.string
   [dmohs.react :as r]
   [macarthur-lab.dblof-ui.utils :as u]
   ))


(defonce instance nil)
(defn set-instance! [i]
  (set! instance i))


(defn add-float [float]
  (instance :add-float float))


(defn remove-float [float]
  (instance :remove-float float))


(r/defc Component
  {:add-float
   (fn [{:keys [state]} float]
     (swap! state update :floats conj float))
   :remove-float
   (fn [{:keys [state]} float]
     (swap! state update :floats (fn [xs] (vec (remove (partial = float) xs)))))
   :get-initial-state
   (fn []
     {:floats []})
   :render
   (fn [{:keys [state]}]
     [:div {}
      ;; Turning this into a list causes dmohs.react to treat the elements as children.
      (list* (:floats @state))])})
