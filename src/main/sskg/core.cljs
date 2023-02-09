(ns sskg.core
  (:require ["axios" :as axios]
            ["jsdom" :refer [JSDOM]]
            ["express" :as express]
            [cljs.core.async :refer [go <! >!]]
            [cljs.core.async.interop :refer [<p!]]
						[oops.core :refer [oget ocall ocall!]]
            [clojure.string :as str]))

#_(def esskj-path
  "div.list-group.results div.list-group-item.entry[data-citation*=\"<em>eSSKJ: Slovar slovenskega knjižnega jezika 2016–2017</em>\"] div.entry-content")

#_(def sskj2-path
  "div.list-group.results div.list-group-item.entry[data-citation*=\"<em>Slovar slovenskega knjižnega jezika, druga, dopolnjena in deloma prenovljena izdaja</em>\"] div.entry-content")

(def entry-content-selector
  "div.list-group.results div.list-group-item.entry div.entry-content")

(def rm-elems-selector
  "style, script, .tooltip_templates, p.entry-citation, span.font_xsmall.color_dark, span.font_xsmall.color_dark + span, span[data-group=\"terminology\"]")

(def url  "https://www.fran.si/iskanje")

(defn entry-content
	[document]
	(ocall document "?querySelector" entry-content-selector))

(defn element-text
  [element]
  (let [elem-clone (ocall element "?cloneNode" true)
        non-text-elems (-> elem-clone
                           (ocall "?querySelectorAll" rm-elems-selector))]
    (ocall! non-text-elems "forEach"
            #(ocall! % "?remove"))
    (-> elem-clone
        (oget "?innerHTML")
        (str/replace #"<" " <")
        (str/replace #">" "> ")
        (JSDOM.)
        (oget "?window.?document.?firstChild.?textContent"))))

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
						document (oget dom "window.document")
						ec (-> document
									 entry-content)
						text (element-text ec)]
        (-> text
						(str/replace #"\s+"
												 " ")))
      (catch :default e (js/console.error e)))))


(defn word-handler
  [req res]
  (let [word (oget req "params" "word")]
    (println "word handler called with word: " word)
    (go (let [definition (<! (word-definition word))
							response (or definition "Not Found")]
					(js/console.log "definition for " word ": " definition)
					(ocall! res "send" response)))))

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
