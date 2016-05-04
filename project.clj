(defproject macarthur-lab/dblof-ui "0.0.1"
  :dependencies
  [
   [cljsjs/react-select "1.0.0-beta12-0"]
   [dmohs/react "0.2.12"]
   [org.clojure/clojure "1.7.0"]
   [org.clojure/clojurescript "1.7.228"]
   ]
  :plugins [[lein-cljsbuild "1.1.2"] [lein-resource "15.10.2"]]
  :profiles {:dev {:plugins [[lein-figwheel "0.5.0" :exclusions [org.clojure/clojure]]]
                   :dependencies [[binaryage/devtools "0.5.2"]
                                  [devcards "0.2.1" :exclusions [cljsjs/react]]]
                   :cljsbuild
                   {:builds {:client {:source-paths ["src/cljs/devtools"]
                                      :compiler
                                      {:optimizations :none
                                       :source-map true
                                       :source-map-timestamp true}
                                      :figwheel {:websocket-host :js-client-host}}}}}
             :figwheel {:cljsbuild
                        {:builds
                         {:client {:source-paths ["src/cljs/figwheel"]
                                   :compiler {:main "macarthur-lab.dblof-ui.main"}}}}}
             :deploy {:cljsbuild
                      {:builds {:client {:source-paths ["src/cljs/deploy"]
                                         :compiler
                                         {:main "macarthur-lab.dblof-ui.main"
                                          :optimizations :simple
                                          :pretty-print false}}}}}}
  :target-path "resources/public"
  :clean-targets ^{:protect false} ["resources"]
  :cljsbuild {:builds {:client {:source-paths ["src/cljs/core"]
                                :compiler {:output-dir "resources/public/build"
                                           :asset-path "build"
                                           :output-to "resources/public/compiled.js"}}}}
  :resource {:resource-paths ["src/static" "lib/plotly"] :skip-stencil [#".*"]})
