;; # 📋 A todo list persisted in datalevin
;; This short [Clerk](https://clerk.vision) notebook shows how to setup [datalevin](https://github.com/juji-io/datalevin?tab=readme-ov-file#datalevin) to work within [application.garden](https://application.garden) projects.
(ns datalevin-example
  (:require [datalevin.core :as d]
            [nextjournal.clerk :as clerk]))

{::clerk/visibility {:code :hide :result :hide}}

(defonce !tasks (atom nil))

(def task-viewer
  {:transform-fn clerk/mark-presented
   :render-fn '(fn [{:as m :task/keys [description completed? id]} _]
                 (println "task" (str id) completed?)
                 [:div.mb-1.flex.bg-amber-200.border.border-amber-400.rounded-md.p-2.justify-between
                  [:div.flex
                   [:input.mt-2.ml-3.cursor-pointer {:type :checkbox :checked (boolean completed?)
                                      :class (str "appearance-none h-4 w-4 rounded bg-amber-300 border border-amber-400 relative"
                                                  "checked:border-amber-700 checked:bg-amber-700 checked:bg-no-repeat checked:bg-contain")
                                      :on-change (fn [e]
                                                   (.then (nextjournal.clerk.render/clerk-eval
                                                           {:recompute? true}
                                                           (list 'update-task (str id) 'assoc :task/completed? (.. e -target -checked)))))}]

                   [:div.text-xl.ml-2.mb-0.font-sans description]]
                  [:button.flex-end.mr-2.text-sm.text-amber-600.font-bold
                   {:on-click #(nextjournal.clerk.render/clerk-eval {:recompute? true} (list 'remove-task (str id)))} "⛌"]])})

(def tasks-viewer
  {:transform-fn (clerk/update-val (comp (partial mapv (partial clerk/with-viewer task-viewer)) deref))
   :render-fn '(fn [coll opts] (into [:div] (nextjournal.clerk.render/inspect-children opts) coll))})

{::clerk/visibility {:code :hide :result :show}}

(clerk/with-viewer
  '(fn [_ _]
     (let [text (nextjournal.clerk.render.hooks/use-state nil)
          ref (nextjournal.clerk.render.hooks/use-ref nil)
          handle-key-press (nextjournal.clerk.render.hooks/use-callback
                            (fn [e]
                              (when (and (= "Enter" (.-key e)) (= (.-target e) @ref) (not-empty @text))
                                (reset! text nil)
                                (nextjournal.clerk.render/clerk-eval {:recompute? true} (list 'add-task @text)))) [text])]

      (nextjournal.clerk.render.hooks/use-effect
       (fn []
         (.addEventListener js/window "keydown" handle-key-press)
         #(.removeEventListener js/window "keydown" handle-key-press)) [handle-key-press])

      [:div.p-1.flex.bg-amber-100.border-amber-200.border.rounded-md.h-10.w-full.pl-8.font-sans.text-xl.mt-2
       [:input.bg-amber-100.focus:outline-none.text-md.w-full
        {:on-change #(reset! text (.. % -target -value))
         :placeholder "Enter some text and press Return…" :ref ref
         :value @text :type "text"}]])) {::clerk/width :wide} nil)

(clerk/with-viewer tasks-viewer {::clerk/width :wide} !tasks)

{::clerk/visibility {:code :show :result :hide}}
;; Start by adding Clerk and datalevin dependencies to your `deps.edn` file
;;
;;```clojure
;;{:paths ["notebooks"]
;; :deps
;; {datalevin/datalevin {:mvn/version "0.8.29"}
;;  io.github.nextjournal/clerk {:git/sha "cbb19fd8f1a9b3b01c9ccb0d43c6dbb4571f3829"}}
;; :aliases
;; {:nextjournal/garden {:exec-fn nextjournal.clerk/serve!
;;                       :exec-args {:index "notebooks/datalevin_example.clj"}
;;                       :jvm-opts ["--add-opens=java.base/java.nio=ALL-UNNAMED"
;;                                  "--add-opens=java.base/sun.nio.ch=ALL-UNNAMED"]}}}
;;```
;;
;; Set up your datalevin connection to use the storage path in `GARDEN_STORAGE` env variable

(def schema
  {:task/description {:db/valueType :db.type/string}
   :task/id {:db/valueType :db.type/uuid
             :db/unique :db.unique/identity}
   :task/completed? {:db/valueType :db.type/boolean}
   :task/category {:db/valueType :db.type/keyword}})

(def conn (d/create-conn (str (System/getenv "GARDEN_STORAGE") "/todo-dtlv")
                         schema
                         {:auto-entity-time? true}))

;; … and the usual [datalog](https://github.com/juji-io/datalevin?tab=readme-ov-file#use-as-a-datalog-store) business for managing entities in a triple store

(defn ->map [m] (into {} (remove (comp #{"db"} namespace key)) m))

(defn tasks []
  (->> (d/q '[:find [?t ...] :where [?t :task/id]]
            (d/db conn))
       (map #(d/entity (d/db conn) %))
       (sort-by :db/created-at >)
       (map ->map)))

(defn add-task [text]
  (d/transact conn [{:task/id (random-uuid)
                     :task/description text
                     :task/category :task.category/CLI}]))

(defn update-task [id f & args]
  (let [ref [:task/id (parse-uuid id)]
        updated-entity (apply f (->map (d/entity (d/db conn) ref)) args)
        {:keys [db-after]} (d/transact! conn [updated-entity])]
    (->map (d/entity db-after ref))))

(defn remove-task [id]
  (d/transact conn [[:db/retractEntity [:task/id (parse-uuid id)]]]))

^{::clerk/visibility {:code :hide} ::clerk/no-cache true}
(reset! !tasks (tasks))
