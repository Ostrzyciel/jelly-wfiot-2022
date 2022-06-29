#!/bin/bash

set -eux

JAVA_EXEC=$1
GRPC_CP="$2 pl.ostrzyciel.superfast_jellyfish.benchmark.FullLatencyBench"
KAFKA_CP="$2 pl.ostrzyciel.superfast_jellyfish.benchmark.KafkaLatencyBench"
BASE_DATA=$3
GRPC_PORT=$4
PROD_PORT=$5

JAVA_OPTS="-Xms1G -Xmx16G"

GRPC_GZIP_OPTS="false true"
KAFKA_GZIP_OPTS="0 1"
DATASETS="wikipedia aemet-1 aemet-2 petrol flickr_10m"
declare -a PROTO_OPTS=(
  ""
  "-Djelly.prefix-table-size=0"
)
JENA_LANGS="jena-proto n3"

for dataset in $DATASETS
do
  # gRPC streaming
  for gzip_opt in $GRPC_GZIP_OPTS
  do
    for proto_opt in "${PROTO_OPTS[@]}"
      do
        $JAVA_EXEC $JAVA_OPTS \
          -Djelly.debug.output-dir=./result/ \
          -Djelly.server.enable-gzip="$gzip_opt" \
          -Djelly.server.port=$GRPC_PORT \
          -Dakka.grpc.client.jelly-rdf-client.port=$GRPC_PORT \
          $proto_opt \
          -cp $GRPC_CP "$BASE_DATA/$dataset.nt.gz"
      done
  done

  # Kafka streaming
  for gzip_opt in $KAFKA_GZIP_OPTS
  do
    # Jelly serialization
    for proto_opt in "${PROTO_OPTS[@]}"
    do
      $JAVA_EXEC $JAVA_OPTS \
        -Djelly.debug.output-dir=./result/ \
        -Dakka.kafka.producer.kafka-clients.bootstrap.servers=127.0.0.1:"$PROD_PORT" \
        $proto_opt \
        -cp $KAFKA_CP protobuf "$BASE_DATA/$dataset.nt.gz" "$gzip_opt"
    done

    # Jena serializations
    for lang in $JENA_LANGS
    do
      $JAVA_EXEC $JAVA_OPTS \
        -Djelly.debug.output-dir=./result/ \
        -Dakka.kafka.producer.kafka-clients.bootstrap.servers=127.0.0.1:"$PROD_PORT" \
        -cp $KAFKA_CP "$lang" "$BASE_DATA/$dataset.nt.gz" "$gzip_opt"
    done
  done
done

