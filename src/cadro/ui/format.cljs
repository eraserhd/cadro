(ns cadro.ui.format)

(defn format-coordinate
  "Formats a coordinate value to 3 decimal places."
  [value]
  (when value
    (.toFixed value 3)))
