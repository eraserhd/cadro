(ns cadro.ui.input
  (:require
   [cadro.model :as model]
   [clojure.spec.alpha :as s]
   [re-posh.core :as re-posh]
   [re-frame.core :as rf]))

(re-posh/reg-sub
 ::value
 (fn [_ [_ id attr]]
   {:type      :query
    :query     '[:find ?value
                 :in $ ?eid ?attr
                 :where [?eid ?attr ?value]]
    :variables [[::model/id id] attr]}))

(rf/reg-event-fx
 ::set-value
 [(rf/inject-cofx :session)]
 (fn [{:keys [session]} [_ id attr value]]
   {:transact [[:db/add [::model/id id] attr value]]
    :session (model/upsert session id attr value)}))

(s/fdef control-name
  :args (s/cat :id uuid? :attr keyword?))
(defn- control-name
  "Derive the name for a form control from its database attribute."
  [id attr]
  (str (str id)
       "/"
       (namespace attr)
       "/"
       (name attr)))

(defn label
  [{:keys [id attr label]}]
  [:label {:for (control-name id attr)}
   label])

(defn input
  "Input element for an object attribute in the datastore."
  [{:keys [id attr], :as props}]
  (let [value (re-posh/subscribe [::value id attr])]
    [:<>
     (when-let [lbl (:label props)]
       (label {:id id
               :attr attr
               :label lbl}))
     [:input {:id (control-name id attr)
              :default-value @value
              :on-blur (fn [e]
                         (let [value (.. e -target -value)]
                           (rf/dispatch [::set-value id attr value])))}]]))
