(ns macarthur-lab.dblof-ui.three-experiment
  (:require
   clojure.string
   [dmohs.react :as react]
   [macarthur-lab.dblof-ui.utils :as u]
   [macarthur-lab.dblof-ui.style :as style]
   cljsjs.three
   ))

(defn animate-dom [controls]
  (letfn [(animate [ts]
                   ;;updates trackball controls each time it reanimates
                   (.update controls)
                   ;;runs every 60ms to keep movement smooth
                   (.requestAnimationFrame js/window animate))]
    (.requestAnimationFrame js/window animate)))

(defn render-dom [renderer scene camera]
  (.render renderer scene camera)) ;renders the scene

   (react/defc Component
     {:render
      (fn [{:keys []}]
        [:div {:style {:backgroundColor "white" :padding "20px 16px"}}
         [:div {:style {:fontWeight "bold"}} "Three Experiment"]
         [:div {:style {:marginTop 8 :height 1 :backgroundColor "#959A9E"}}]
         [:div {:style {:marginTop 1 :backgroundColor "white" :padding "1px"}}]
         [:canvas {:height 1
                   :width 1
                   :ref "canvas"
                   }]
         [:div {:ref "three"}]])

      :render-webgl
      (fn [{:keys [refs locals]}]
        (let [scene (js/THREE.Scene.) ;creates a scene
              ;;creates a camera
              camera (js/THREE.PerspectiveCamera. 75 (/ (aget js/window "innerWidth")
                                                        (aget js/window "innerHeight")) 0.1 1000)
              ;;defines renderer as the newly created renderer within locals
              renderer (get @locals :renderer)
              ;;sets a material for a line, gives it a color
              lineMat (js/THREE.LineBasicMaterial. (js-obj "color" 0xcccccc))
              ;;gives the line a Geometry
              lineGeo (js/THREE.Geometry.)
              ;;combines the geometry and the material to make the actual line
              line (js/THREE.Line. lineGeo lineMat)
              ;;creates a new TrackballControls object which binds rotation
              ;;panning and zooming to the mouse
              controls (js/THREE.TrackballControls. camera (aget renderer "domElement"))
              ;;function that fires when the event listener registers an event
              func (fn [e] (render-dom renderer scene camera))]
          (if (nil? (get @locals :func)) ;checks to see if there is a function in locals
            ;;if there is none, it is the first render, so add the event listener
            (do (.addEventListener controls "change" func))
            ;;if there is a function, then there is an old event listener we need to remove
            ;;and a new one needs to be added
            (do (.removeEventListener controls "change" (get @locals :func))
              (.addEventListener controls "change" func)))

          ;;sets the size and background color of the renderer
          ;;size is the width of the window and half the height (half was used because
          ;;it takes up less space but still looks good)
          (.setSize renderer (aget js/window "innerWidth") (/ (aget js/window "innerHeight") 2))
          (.setClearColor renderer 0xffffff) ;sets background to white

          ;;gives the line a beginning and ending point defined by the width of the window
          ;;adds the line to the scene
          (.push (aget lineGeo "vertices") (js/THREE.Vector3. (aget js/window "innerWidth") 0 0))
          (.push (aget lineGeo "vertices") (js/THREE.Vector3. (- 0 (aget js/window "innerWidth")) 0 0))
          (.add scene line)

          ;;set camera position's z and x coordinate
          ;;target keeps the camera focused on (-10, 0, 0)
          (aset (aget camera "position") "z" 50)
          (aset (aget camera "position") "x" -1)
          (.set (aget controls "target") -10 0 0)

          ;;make cylinders (exons)
          (dorun (for [x (range 1 10)] ;create the same number of cylinders as elements in list
                        ;;change the length of exon here, 3rd parameter
                   (let [geometry (js/THREE.CylinderGeometry. 1 1 x 15)
                        ;;gives cylinderes a material, color comes from "mesh normal"
                         material (js/THREE.MeshNormalMaterial. (js-obj "wireframe" false))
                         ;;combines geometry and material to make cylinder
                         cylinder (js/THREE.Mesh. geometry material)]
                     (.rotateZ cylinder (/ 3.14 2)) ;puts cylinder sideways
                     (.translateY cylinder (* 6 x)) ;position on the line
                     (.add scene cylinder)))) ;adds cylinder to the scene

          (render-dom renderer scene camera) ;calls render-dom to render the scene with the camera
          (animate-dom controls) ;calls animate-dom to update controls and animate the scene
          ;;updates the function being listened for at the end of the cycle
          (swap! locals assoc :func func)))
      :component-did-mount
      (fn [{:keys [this refs locals]}]
        ;;creates new renderer upon first mount
        (swap! locals assoc :renderer (js/THREE.WebGLRenderer.))
        ;;appends the renderer's domElement to the three div
        (let [three (@refs "three")]
          (.appendChild three (aget (get @locals :renderer) "domElement")))
        (this :render-webgl))
      :component-will-receive-props
      (fn [{:keys [this refs locals]}]
        (let [three (@refs "three")]
          ;;remove old renderer's dom element
          (.removeChild three (aget (get @locals :renderer) "domElement"))
          ;;create new WebGLrenderer upon each rerender to prevent jerking
          (swap! locals assoc :renderer (js/THREE.WebGLRenderer.))
          ;;append the new renderer's dom element to the three div
          (.appendChild three (aget (get @locals :renderer) "domElement")))
        (this :render-webgl))})
