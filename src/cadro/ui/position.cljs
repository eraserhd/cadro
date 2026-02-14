(ns cadro.ui.position
  (:require
   [cadro.model :as model]
   [clojure.set :as set]))

(defn format-coordinate
  "Formats a coordinate value to 3 decimal places.
   In the future, this can be made configurable."
  [value]
  (when value
    (.toFixed value 3)))

(defn position-hiccup
  "Renders a position (map of axis-id to value) as hiccup markup.
   Takes a position map and spans (collection of axis info)."
  [position spans]
  (let [axes-names       (into {}
                               (map (juxt ::model/id ::model/displays-as))
                               spans)
        axis-name->value (set/rename-keys position axes-names)]
    (into [:span.distance]
          (->> (sort (keys axis-name->value))
               (map (fn [axis-name]
                      [:span.axis
                       [:span.name axis-name]
                       [:span.value (format-coordinate (get axis-name->value axis-name))]]))))))
