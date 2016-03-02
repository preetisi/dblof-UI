#!/bin/bash
IFS=$'\n\t'
set -euxo pipefail


CNAME="$USER"-ui-build
CROOT=/mnt/project

if [[ $# -ne 1 ]]; then
  docker cp project.clj "$CNAME:$CROOT"
  docker cp src "$CNAME:$CROOT"
else
  for file_name in "$@"; do
    if [[ "${file_name:0:2}" = './' ]]; then
      file_name=${file_name:2}
    fi
    docker cp "$file_name" "$CNAME:$CROOT"/"$file_name"
  done
fi
