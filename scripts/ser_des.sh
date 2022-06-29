#!/bin/bash

set -eux

JAVA_EXEC=$1
CP="$2 pl.ostrzyciel.superfast_jellyfish.benchmark.RawSerDesBench"
BASE_DATA=$3

JAVA_OPTS="-Xms1G -Xmx16G"

TASKS="ser des"
DATASETS="identica mix wikipedia aemet-1 migr_reschange tour_cap_nuts3 aemet-2 petrol flickr_10m nevada_10m"
JENA_LANGS="jena-proto n3 turtle rdf-xml"
declare -a PROTO_OPTS=(
  ""
  "-Djelly.use-repeat=false"
  "-Djelly.prefix-table-size=0"
  "-Djelly.prefix-table-size=0 -Djelly.name-table-size=256"
)

for dataset in $DATASETS
do
  for task in $TASKS
  do
    # 1. Protobuf serialization
    for proto_opt in "${PROTO_OPTS[@]}"
    do
      $JAVA_EXEC $JAVA_OPTS -Djelly.debug.output-dir=./result/ $proto_opt -cp $CP "$task" protobuf "$BASE_DATA/$dataset.nt.gz"
    done

    # 2. Jena serialization
    for lang in $JENA_LANGS
    do
      $JAVA_EXEC $JAVA_OPTS -Djelly.debug.output-dir=./result/ -cp $CP "$task" "$lang" "$BASE_DATA/$dataset.nt.gz"
    done
  done
done
