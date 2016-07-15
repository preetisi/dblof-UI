(ns macarthur-lab.dblof-ui.pd2
  (:require
   clojure.string
   [dmohs.react :as react]
   [macarthur-lab.dblof-ui.utils :as u]
   ))


(defn create-shader [gl str type-name]
  (let [shader (.createShader gl (aget gl type-name))]
    (.shaderSource gl shader str)
    (.compileShader gl shader)
    shader))


(defn create-program [gl vstr fstr]
  (let [program (.createProgram gl)
        vshader (create-shader gl vstr "VERTEX_SHADER")
        fshader (create-shader gl fstr "FRAGMENT_SHADER")]
    (.attachShader gl program vshader)
    (.attachShader gl program fshader)
    (.linkProgram gl program)
    program))


(defn draw [locals gl c]
  (let [{:keys [actions program vertex-pos-buffer scale offset]} @locals]
    (swap! locals assoc-in [:offset 0] (+ (offset 0)
                                          (- (if (contains? actions :panleft) (/ scale 25) 0))
                                          (if (contains? actions :panright) (/ scale 25) 0)))
    (swap! locals assoc-in [:offset 1] (+ (offset 1)
                                          (- (if (contains? actions :pandown) (/ scale 25) 0))
                                          (if (contains? actions :panup) (/ scale 25) 0)))
    (.uniform2f gl (aget program "canvasSizeUniform") (aget c "width") (aget c "height"))
    (.uniform2f gl (aget program "offsetUniform") (offset 0) (offset 1))
    (swap! locals assoc :scale (/ (* scale (if (contains? actions :zoomin) 0.975 1.0))
                                  (if (contains? actions :zoomout) 0.975 1.0)))
    (.uniform1f gl (aget program "scaleUniform")
                (/ (* scale (if (contains? actions :zoomin) 0.975 1.0))
                   (if (contains? actions :zoomout) 0.975 1.0)))
    (.drawArrays gl (aget gl "TRIANGLE_STRIP") 0 (aget vertex-pos-buffer "numItems"))))


(def key-mappings
  {37 :panleft 38 :panup 39 :panright 40 :pandown 90 :zoomin 88 :zoomout})


(react/defc Component
  {:render
   (fn [{:keys [this props state refs locals]}]
     [:div {:style {:backgroundColor "white" :padding "20px 16px"}}
      [:div {:style {:fontWeight "bold"}} "WebGL Canvas"] ; title
      [:div {:style {:marginTop 8 :height 1 :backgroundColor "#959A9E"}}] ; line underneath title
      [:div {:style {:marginTop 8 :backgroundColor "white" :padding "8px"}}]
      [:canvas {:height 900
                :width 900
                :ref "canvas"
                :tabIndex 1
                :onKeyDown (fn [e]
                             (let [kc (aget e "keyCode")
                                   c (@refs "canvas")
                                   gl (.getContext c "experimental-webgl")]
                               (when (get key-mappings kc)
                                 (.preventDefault e)
                                 (swap! locals update-in [:actions] conj (get key-mappings kc))
                                 (if (nil? (get @locals :iv))
                                   (swap! locals assoc :iv
                                          (js/setInterval #(draw locals gl c) 16))))))
                :onKeyUp (fn [e]
                           (let [kc (aget e "keyCode")]
                             (when (get key-mappings kc)
                               (swap! locals update-in [:actions] disj (get key-mappings kc))
                             (dorun (for [j (keys key-mappings)]
                                      (when-not (contains? (get @locals :actions)
                                                           (get key-mappings j))
                                            (js/clearInterval (get @locals :iv))
                                            (swap! locals dissoc :iv)))))))}]])
  :render-webgl
   (fn [{:keys [refs locals]}]
     (let [c (@refs "canvas")
           gl (.getContext c "experimental-webgl")
           vertexPosBuffer (.createBuffer gl)
           vertices [-1 -1 1 -1 -1 1 1 1]
           vs (str "attribute vec2 aVertexPosition;
                    void main() {
                      gl_Position = vec4(aVertexPosition, 0, 1);
                    }")
           fs (str "#ifdef GL_FRAGMENT_PRECISION_HIGH
  		                    precision highp float;
  	                #else
  		                    precision mediump float;
  	                #endif
  		                    precision mediump int;
                          uniform vec2 uCanvasSize;
                          uniform vec2 uOffset;
                          uniform float uScale;

  		                vec4 calc(vec2 texCoord){
  			                   float x = 0.0;
  			                   float y = 0.0;
                           float v = 10000.0;
                           float j = 10000.0;
  			                   for (int i = 0; i <100; ++i){
  			  	                     float xtemp = x*x - y*y + texCoord.x;
  			  	                     y = 2.0*x*y + texCoord.y;
  		   		                     x = xtemp;
                                 v = min(v, abs(x*x + y*y));
                       					 j = min(j, abs(x*y));
  		  		                     if(x*x+y*y >= 8.0){
  			  		                          float d = (float(i) - (log(log(sqrt(x*x + y*y))) / log(2.0))) / 50.0;
                                        v = (1.0 - v) / 2.0;
                            						j = (1.0 - j) / 2.0;
  			  		                          return vec4(d+j, d, d+v, 1);
  			  	                      }
  			                    }
  			            return vec4(0,0, 0, 1);
  		              }
  		                void main() {
  			                   vec2 texCoord = (gl_FragCoord.xy/ uCanvasSize.xy) * 2.0 - vec2(1.0, 1.0);
                           texCoord = texCoord * uScale + uOffset;
  			                   gl_FragColor = calc(texCoord);
  		              }")
           ]
       ;; gl.clear(gl.COLOR_BUFFER_BIT | gl.DEPTH_BUFFER_BIT);
       (.clear gl (bit-or (aget gl "COLOR_BUFFER_BIT") (aget gl "DEPTH_BUFFER_BIT")))

       (.bindBuffer gl (aget gl "ARRAY_BUFFER") vertexPosBuffer)
       (.bufferData gl (aget gl "ARRAY_BUFFER") (js/Float32Array. vertices) (aget gl "STATIC_DRAW"))
       (aset vertexPosBuffer "itemSize" 2)
       (aset vertexPosBuffer "numItems" 4)

       (let [program (create-program gl vs fs)]
         (swap! locals assoc :program program :vertex-pos-buffer vertexPosBuffer)
         (.useProgram gl program)
         (aset program "vertexPosAttrib" (.getAttribLocation gl program "aVertexPosition"))
         (aset program "canvasSizeUniform" (.getUniformLocation gl program "uCanvasSize"))
         (aset program "offsetUniform" (.getUniformLocation gl program "uOffset"))
         (aset program "scaleUniform" (.getUniformLocation gl program "uScale"))
         (.enableVertexAttribArray gl (aget program "vertexPosAttrib"))
         (.vertexAttribPointer
          gl (aget program "vertexPosAttrib") (aget vertexPosBuffer "itemSize") (aget gl "FLOAT")
          false 0 0)
         (draw locals gl c))))
   :component-did-mount
   (fn [{:keys [this locals]}]
     (swap! locals assoc :actions #{} :scale 1.35 :offset [-0.5 0])
     (this :render-webgl))
   :component-will-receive-props
   (fn [{:keys [this]}]
     (this :render-webgl))})
