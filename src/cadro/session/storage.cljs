(ns cadro.session.storage
  "Protocol for session storage backends.")

(defprotocol SessionStorage
  "Protocol for session storage backends."
  (save! [this data] "Save session data string. Returns a promise.")
  (load! [this] "Load session data string. Returns a promise that resolves to data or nil."))
