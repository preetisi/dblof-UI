#!/bin/bash
IFS=$'\n\t'
set -euxo pipefail


docker create --name "$USER"-ui-build -it \
  -w /mnt/project \
  -p "$UI_BUILD_PORT":3449 \
  -e FIGWHEEL_HOST=dblof.broadinstitute.org:"$UI_BUILD_PORT" \
  -v jars:/root/.m2 \
  clojure \
  rlfe lein with-profile +figwheel do clean, resource, figwheel
