#!/bin/bash
IFS=$'\n\t'
set -euxo pipefail

if [ -d "lib/plotly" ]; then
  rm -rf "lib/plotly"
fi
mkdir -p lib lib/plotly
curl -o lib/plotly/plotly.min.js https://cdn.plot.ly/plotly-latest.min.js
