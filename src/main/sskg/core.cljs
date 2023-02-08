(ns sskg.core
  (:require ["axios" :as axios]
            ["jsdom" :refer [JSDOM]]
            ["express" :as express]
            [cljs.core.async :refer [go <! >!]]
            [cljs.core.async.interop :refer [<p!]]
						[oops.core :refer [oget ocall ocall!]]
            [clojure.string :as str]))

(def entry-path
  "div.list-group.results div.list-group-item.entry div.entry-content")

(def ul-path "ul")

(def url  "https://www.fran.si/iskanje")

(defn word-definition
  "Returns channel with word definition."
  [word]
  (go
    (try
      (let [res (<p! (axios/get url
																#js{:params #js{:View 1
																								:Query word}}))
            data (.-data res)
            dom (JSDOM. data)
            document (.. dom -window -document)
            uls (-> document
                    (ocall "querySelector" entry-path)
                    (ocall "querySelectorAll" ul-path)
                    (js/Array.from))]
        (str/join " " (map #(oget % "textContent")
													 uls)))
      (catch :default e (js/console.error e)))))


(defn word-handler
  [req res]
  (let [word (oget req "params" "word")]
    (println "word handler called with word: " word)
    (go (let [definition (<! (word-definition word))]
					(println "definition for " word ": " definition)
					(ocall! res "send" definition)))))

(defn logging-middleware
	[req res next!]
	(let [datetime (js/Date.)
				fmt_time (.toISOString datetime)
				method (oget req "method")
				url (oget req "url")
				status (oget res "statusCode")
				log (str "[" fmt_time "]"
								 " " method ":" url
								 " " status)]
		(js/console.log log)
		(next!)))

(defn main [& args]
  (let [app (express)]
    (doto app
			(ocall! "use" logging-middleware)
      (ocall! "get" "/sskj/:word" word-handler)
      (ocall! "listen" 3000))))
