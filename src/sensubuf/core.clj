(ns sensubuf.core
  (:gen-class)
  (:require [clojure.tools.cli :as cli]
            [clojure.java.io :as io]
            [cheshire.core :as json])
  (:import (java.net Socket)
           (java.io RandomAccessFile)
           (java.net DatagramSocket DatagramPacket InetAddress)
           (org.apache.commons.io.input CountingInputStream)))

(def options
  [["-p" "--port" "sensu-client port"
    :default 3030
    :parse-fn #(Integer. %)]
   ["-h" "--host" "sensu-client hostname" :default "localhost"]
   ["-f" "--file" "File to check for updates"]
   ["-o" "--offset-file" "File with offset information"]
   ["-b" "--batch" "Size of batches from FILE" :default 25]
   ["-t" "--tmpl" "JSON template containing --stubtok"]
   ["-v" "--verbose" "Print activity" :flag true :default false]
   ["-s" "--stubtok" "String to substitute with file data"
           :default "%%BATCH%%"]])

(defn write-socket [host port s]
  (let [sock (Socket. host port)
        os (.getOutputStream sock)]
    (.write os (.getBytes s "utf-8"))
    (.flush os)
    (.close sock)))

(defn slurp-offset [f]
  (try
    (.readLong f)
    (catch Exception _
      0)))

(defn spit-offset [f n]
  (.seek f 0)
  (.writeLong f n))

(defmacro with-lock
  "This is only intended for use across JVMs!  Provides no protection
  against other threads."
  [bindings & body]
  `(with-open ~bindings
     (let [lock# (-> ~(first bindings) .getChannel .lock)]
       (try
         ~@body
         (finally
           (.release lock#))))))

(defn spit-udp [sock host port payload]
  (let [addr (InetAddress/getByName host)
        packet (DatagramPacket. (.getBytes payload)
                                (count payload) addr port)]
    (.send sock packet)))

(defn assemble [tmpl stubtok subtxt]
  (.replace tmpl stubtok (.replaceAll subtxt "\\\"" "\\\\\\\"")))

(defn -main [& args]
  (let [[{:keys [host port file offset-file
                 batch verbose tmpl stubtok] :as opts} args help]
        (apply cli/cli args options)]
    (with-lock [raf (RandomAccessFile. offset-file "rw")]
      (let [offset (slurp-offset raf)
            is (CountingInputStream. (io/input-stream file))
            _ (.skip is offset)
            c (atom 0)]
        (with-open [sock (DatagramSocket.)]
          (doseq [b (partition-all batch (line-seq (io/reader is)))]
            (let [payload (assemble tmpl stubtok
                                    (apply str (interpose "\n" b)))]
              (swap! c #(+ % (count b)))
              (prn (-> payload json/decode (get "output") json/decode))
              (spit-udp sock host port payload))))
        (when verbose
          (println @c "lines in" file "since byte" offset)
          (println "now at offset" (.getByteCount is)))
        (spit-offset raf (.getByteCount is))))))
