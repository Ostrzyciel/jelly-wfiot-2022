#!/bin/bash

set -eux

JAVA_EXEC=$1
CP="$2 pl.ostrzyciel.superfast_jellyfish.benchmark.FullStreamBench"
BASE_DATA=$3
PORT=$4

JAVA_OPTS="-Xms1G -Xmx16G"

GZIP_OPTS="false true"
DATASETS="identica mix wikipedia aemet-1 migr_reschange tour_cap_nuts3 aemet-2 petrol flickr_10m nevada_10m"
declare -a PROTO_OPTS=(
  ""
  "-Djelly.prefix-table-size=0"
)

for dataset in $DATASETS
do
  for gzip_opt in $GZIP_OPTS
  do
    for proto_opt in "${PROTO_OPTS[@]}"
      do
        $JAVA_EXEC $JAVA_OPTS \
          -Djelly.debug.output-dir=./result/ \
          -Djelly.server.enable-gzip="$gzip_opt" \
          -Djelly.server.port=$PORT \
          -Dakka.grpc.client.jelly-rdf-client.port=$PORT \
          $proto_opt \
          -cp $CP "$BASE_DATA/$dataset.nt.gz"
      done
  done
done

