#!/bin/bash
IFS=$'\n\t'
set -euxo pipefail


docker create --name ui-builder -it \
  -w /project \
  -p 80:3449 \
  clojure \
  bash
