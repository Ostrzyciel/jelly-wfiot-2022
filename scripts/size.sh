#!/bin/bash

set -eux

JAVA_EXEC=$1
CP="$2 pl.ostrzyciel.superfast_jellyfish.benchmark.SizeBench"
BASE_DATA=$3

JAVA_OPTS="-Xms1G -Xmx16G"

DATASETS="identica mix wikipedia aemet-1 migr_reschange tour_cap_nuts3 aemet-2 petrol flickr_10m nevada_10m"

for dataset in $DATASETS
do
  $JAVA_EXEC $JAVA_OPTS \
    -Djelly.debug.output-dir=./result/ \
    -cp $CP "$BASE_DATA/$dataset.nt.gz"
done
