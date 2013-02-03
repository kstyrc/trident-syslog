trident-syslog
==============

Trident spout implementation for syslog. Available on clojars: https://clojars.org/storm.trident.syslog/trident-syslog

It uses server part of syslog4j lib (http://syslog4j.org/). However, I consider implementing it from scratch using UDP sockets as the implementation uses "forever loop" (boolean var without volatile keyword) inside the listening thread.

You can also utilize Kafka (http://kafka.apache.org/quickstart.html) via KafkaSpout in storm-contrib project (https://github.com/nathanmarz/storm-contrib/tree/master/storm-kafka). Just create a pipe in bash (nc -> kafka-producer):

$> nc -l 514 | bin/kafka-console-producer.sh --zookeeper localhost:2181 --topic syslog-stream

