#!/bin/bash

set -eux

JAVA_EXEC=$1
CP="$2 pl.ostrzyciel.superfast_jellyfish.benchmark.KafkaFullStreamBench"
BASE_DATA=$3
PROD_PORT=$4

JAVA_OPTS="-Xms1G -Xmx16G"

GZIP_OPTS="0 1"
DATASETS="identica mix wikipedia aemet-1 migr_reschange tour_cap_nuts3 aemet-2 petrol flickr_10m nevada_10m"
JENA_LANGS="jena-proto n3"
declare -a PROTO_OPTS=(
  ""
  "-Djelly.prefix-table-size=0"
)

for dataset in $DATASETS
do
  for gzip_opt in $GZIP_OPTS
  do
    # Jelly serialization
    for proto_opt in "${PROTO_OPTS[@]}"
    do
      $JAVA_EXEC $JAVA_OPTS \
        -Djelly.debug.output-dir=./result/ \
        -Dakka.kafka.producer.kafka-clients.bootstrap.servers=127.0.0.1:"$PROD_PORT" \
        $proto_opt \
        -cp $CP protobuf "$BASE_DATA/$dataset.nt.gz" "$gzip_opt"
    done

    # Jena serializations
    for lang in $JENA_LANGS
    do
      $JAVA_EXEC $JAVA_OPTS \
        -Djelly.debug.output-dir=./result/ \
        -Dakka.kafka.producer.kafka-clients.bootstrap.servers=127.0.0.1:"$PROD_PORT" \
        -cp $CP "$lang" "$BASE_DATA/$dataset.nt.gz" "$gzip_opt"
    done
  done
done
