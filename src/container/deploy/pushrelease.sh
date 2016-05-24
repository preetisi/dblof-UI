#!/bin/bash
IFS=$'\n\t'
set -euxo pipefail


VERSION="${1:-not specified}"

if [[ "$VERSION" = "not specified" ]]; then
  docker pull -a gcr.io/"$PROJECT_ID"/ui
  docker images gcr.io/"$PROJECT_ID"/ui
  echo "Specifiy a new version."
  exit 1
fi

CNAME="$(
  docker create -w /work -v deploy-jars:/root/.m2 \
  clojure \
  lein with-profile deploy do resource, cljsbuild once
)"
COPYFILE_DISABLE=1 tar -c lib project.clj src/cljs src/static \
  | docker cp - "$CNAME":/work
docker start --attach "$CNAME"
docker cp "$CNAME":/work/resources .
docker rm "$CNAME"
rm -rf resources/public/build
docker build -t gcr.io/"$PROJECT_ID"/ui:"$VERSION" -f src/container/deploy/Dockerfile .
rm -rf resources
gcloud docker push gcr.io/"$PROJECT_ID"/ui:"$VERSION"
