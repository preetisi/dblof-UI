(ns macarthur-lab.dblof-ui.search-area
  (:require
    clojure.string
    [dmohs.react :as react]
    [macarthur-lab.dblof-ui.utils :as u]
    ))


(react/defc Logo
  {:render
   (fn []
     [:a {:href "#" :style {:float "left" :color "inherit" :textDecoration "none"}}
      [:span {:style {:display "block" :fontSize 40}}
       [:span {:style {:color "#24AFB2" :fontWeight 100}} "db"]
       [:span {:style {}} "LoF"]]
      [:span {:style {:display "block"
                      :fontWeight 100 :fontSize 10 :textTransform "uppercase" :color "#959A9E"}}
       "Loss-of-Function Variants Database"]])})


(react/defc SearchResults
  {:select-next-item
   (fn [{:keys [this state after-update]}]
     (swap! state assoc :selected-index (inc (or (:selected-index @state) -1))))
   :select-prev-item
   (fn [{:keys [this state after-update]}]
     (swap! state assoc :selected-index (dec (:selected-index @state))))
   :report-selection
   (fn [{:keys [props state]}]
     (let [{:keys [on-item-selected]} props
           {:keys [results selected-index]} @state]
       ((:on-empty-results props) (empty? results))
       (when (and (not (empty? results)) on-item-selected)
         (on-item-selected (nth results selected-index)))))
   :render
   (fn [{:keys [this props state]}]
     (let [{:keys [hide? style search-text create-href]} props
           {:keys [results selected-index]} @state]
       [:div {:style (merge {:margin "4px 0 0 0"} (:container style)
                            (when (zero? (count results)) {:display "none"}))}
        (map-indexed
         (fn [i x]
           [:a {:onMouseOver #(swap! state assoc :selected-index i)
                :href (when create-href (create-href x))
                :style {:display "block"
                        :cursor "pointer"
                        :backgroundColor (when (= i selected-index) "rgba(220,245,250,1)")
                        :padding "4px 6px"
                        :textDecoration "none"
                        :color "inherit"}}
            (clojure.string/upper-case x)])
         results)]))
   :component-did-update
   (fn [{:keys [this prev-props props state locals]}]
     (js/clearTimeout (:timeout @locals))
     (let [{:keys [gene-names search-text]} props]
       (when-not (= (:search-text prev-props) search-text)
         (if (clojure.string/blank? search-text)
           (swap! state dissoc :results :selected-index)
           (let [search-text (clojure.string/lower-case search-text)]
             (swap! locals assoc :timeout
                    (js/setTimeout
                     (fn []
                       (let [filtered (filter #(= 0 (.indexOf % search-text)) gene-names)
                             results (take 20 (sort filtered))]
                         (swap! state assoc :results results :selected-index 0)))
                     100)))))))})

(react/defc SearchBox
  {:get-initial-state
   (fn []
     {:empty-search? false})
   :empty-search
   (fn [{:keys [state]} is-search-empty]
     (swap! state assoc :empty-search? is-search-empty))
   :render
   (fn [{:keys [props state refs this]}]
     (let [{:keys [compact?]} props
           {:keys [gene-names search-text suggestion focused? empty-search?]} @state]
       [:div {:onFocus #(swap! state assoc :focused? true)
              :onBlur (fn [e] (js/setTimeout #(swap! state dissoc :focused?) 100))}
        [:div {:style {:fontSize "large"}}
         [:input {:ref "search-box"
                  :value (str search-text (subs (or suggestion "") (count search-text)))
                  :onChange #(swap! state assoc
                                    :suggestion nil
                                    :search-text (.. % -target -value))
                  :onKeyDown (fn [e]
                               (when (= 13 (.-keyCode e))
                                 (react/call :report-selection (@refs "results")))
                               (when (= 38 (.-keyCode e))
                                 (.preventDefault e)
                                 (react/call :select-prev-item (@refs "results")))
                               (when (= 40 (.-keyCode e))
                                 (.preventDefault e)
                                 (react/call :select-next-item (@refs "results"))))
                  :style {:width (if compact? "40ex" "50vw") :height "1.5em"
                          :fontSize "large" :verticalAlign "top"}}]
         [:span {:style {:display "inline-block" :backgroundColor "#24AFB2"
                         :height "1.5em" :width "1.5em" :padding 3}}
          [:span {:style {:display "inline-block" :margin "-4px 0 0 6px"
                          :fontSize "x-large"
                          :WebkitTransform "rotate(-45deg)"}}
           "⚲"]]]
        [:div {:style {:position "relative" :color "initial"}}
         ;; This z-index must be larger than Plotly's or the plot's toolbar will cover it.
         [:div {:style {:position "absolute" :zIndex 1002}}
          [SearchResults
           {:ref "results"
            :gene-names gene-names
            :search-text search-text
            :create-href (fn [item] (str "#genes/" item))
            :on-item-selected (fn [item] (aset js/window "location" "hash" (str "genes/" item)))
            :on-empty-results #(react/call :empty-search this %1)
            :style {:container
                    {:width 310
                     :border "1px solid #ccc"
                     :display (if focused? "inline-block" "none")
                     :textAlign "left"
                     :backgroundColor "white"}}}]]]
        (when-not compact?
          [:div {:style {:marginTop "1em" :fontStyle "italic" :fontSize "small"}}
           "Examples - Gene: "
           [:a {:href "#genes/cd33"
                :style {:color "#CEF4F3" :textDecoration "none" :fontStyle "normal"}}
            "CD33"]])
        (when empty-search?
          [:div {:style {:marginTop "1em" :fontStyle "italic" :fontSize "small"}}
           "Gene not found!"])]))
   :component-did-mount
   (fn [{:keys [props state locals]}]
     (u/ajax {:url (str (:api-url-root props) "/exec-sql")
              :method :post
              :data (u/->json-string {:sql "select gene from constraint_scores"})
              :on-done (fn [{:keys [get-parsed-response]}]
                         (swap! state assoc :gene-names
                                (map
                                 clojure.string/lower-case
                                 (map #(get % "gene") (get (get-parsed-response) "rows")))))}))})

(react/defc Component
  {:render
   (fn [{:keys [props]}]
     (let [{:keys [compact?]} props]
       [:div {:style {:backgroundColor "#343A41" :color "white"
                      :padding 20}}
        [Logo]
        (when-not compact?
          [:div {:style {:marginTop "25vh" :display "flex" :justifyContent "center"}}
           [:div {:style {:textTransform "uppercase" :fontSize "xx-large" :fontWeight 900}}
            "Search Gene/Variant"]])
        [:div {:style (if compact?
                        {:float "right"}
                        {:marginTop 12 :display "flex" :justifyContent "center"})}
         [SearchBox props]]
        [:div {:style {:clear "both"
                       :margin (if compact? "80px -20px -20px -20px" "10vh -20px -20px -20px")
                       :height 4 :backgroundColor "#24AFB2"}}]]))})
