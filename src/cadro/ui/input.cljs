(ns cadro.ui.input
  (:require
   [cadro.model :as model]
   [re-posh.core :as re-posh]
   [re-frame.core :as rf]))

(re-posh/reg-sub
 ::value
 (fn [_ [_ eid attr]]
   {:type      :query
    :query     '[:find ?value
                 :in $ ?eid ?attr
                 :where [?eid ?attr ?value]]
    :variables [eid attr]}))

(rf/reg-event-fx
 ::set-value
 (fn [_ [_ eid attr value]]
   {:transact [[:db/add eid attr value]]}))

(defn control-name
  "Derive the name for a form control from its database attribute."
  [eid attr]
  (str (str eid)
       "/"
       (namespace attr)
       "/"
       (name attr)))

(defn label
  [{:keys [id attr label]}]
  [:label {:for (control-name [::model/id id] attr)}
   label])

(defn input
  "Input element for an object attribute in the datastore."
  [{:keys [id attr], :as props}]
  (let [value (re-posh/subscribe [::value [::model/id id] attr])]
    [:<>
     (when-let [lbl (:label props)]
       (label {:id id
               :attr attr
               :label lbl}))
     [:input {:id (control-name [::model/id id] attr)
              :default-value @value
              :on-blur (fn [e]
                         (let [value (.. e -target -value)]
                           (rf/dispatch [::set-value [::model/id id] attr value])))}]]))
