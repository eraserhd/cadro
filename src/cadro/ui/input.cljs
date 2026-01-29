(ns cadro.ui.input
  (:require
   [cadro.model :as model]
   [clara.rules :as clara]
   [net.eraserhead.clara-eql.pull :as pull]
   [clojure.spec.alpha :as s]
   [re-frame.core :as rf]))

;; FIXME: if we subscribe to eav, narrow to e then to a then to v?
(rf/reg-sub
 ::value
 :<- [:session]
 (fn [session [_ id attr]]
   (let [eav-map (:?eav-map (first (clara/query session pull/eav-map)))]
     (first (get-in eav-map [id attr])))))

(rf/reg-event-fx
 ::set-value
 [(rf/inject-cofx :session)]
 (fn [{:keys [session]} [_ id attr value]]
   {:session (model/upsert session id attr value)}))

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
  (let [value (rf/subscribe [::value id attr])]
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
