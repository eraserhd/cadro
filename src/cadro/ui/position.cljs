(ns cadro.ui.position
  (:require
   [cadro.model :as model]
   [clojure.set :as set]))

(defn format-coordinate
  "Formats a coordinate value to 3 decimal places."
  [value]
  (when value
    (.toFixed value 3)))

(defn axis-hiccup [axis-name value]
  [:span.axis
   [:span.name axis-name]
   [:span.value (format-coordinate value)]])

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
               (map #(axis-hiccup % (get axis-name->value %)))))))
