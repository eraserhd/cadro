(ns cadro.ui.input
  (:require
   [cadro.model :as model]
   [cadro.model.facts :as facts]
   [clara.rules :as clara]
   [net.eraserhead.clara-eql.pull :as pull]
   [clojure.spec.alpha :as s]
   [clojure.string :as str]
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
   {:session (facts/upsert session id attr value)}))

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

(defn- control-class
  [attr]
  (-> (str (namespace attr)
           "_"
           (name attr))
      (str/replace #"[^a-z0-9_]" "-")))

(defn default-lens
  ([x] x)
  ([x v] v))

(defn input
  "Input element for an object attribute in the datastore."
  [{:keys [id attr lens],
    :or {lens default-lens}
    :as props}]
  (let [source-value @(rf/subscribe [::value id attr])]
    [:div.input-field {:class (control-class attr)}
     (when-let [lbl (:label props)]
       (label {:id id
               :attr attr
               :label lbl}))
     (let [props (dissoc props :label :id :attr :lens)]
       [:input (merge {:id (control-name id attr)
                       :default-value (lens source-value)
                       :on-blur (fn [e]
                                  (let [value (lens source-value (.. e -target -value))]
                                    (rf/dispatch [::set-value id attr value])))}
                      props)])]))
