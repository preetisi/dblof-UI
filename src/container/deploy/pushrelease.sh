#!/bin/bash
IFS=$'\n\t'
set -euxo pipefail


VERSION="$1"

docker exec "$BUILD_CONTAINER_NAME" rm -rf /tmp/project-deploy
docker exec "$BUILD_CONTAINER_NAME" mkdir /tmp/project-deploy
COPYFILE_DISABLE=1 tar -c lib project.clj src/cljs src/static \
  | docker cp - "$BUILD_CONTAINER_NAME":/tmp/project-deploy
docker exec "$BUILD_CONTAINER_NAME" bash -c \
  'cd /tmp/project-deploy; lein with-profile deploy do resource, cljsbuild once'
docker exec "$BUILD_CONTAINER_NAME" rm -rf /tmp/project-deploy/resources/public/build
docker cp "$BUILD_CONTAINER_NAME":/tmp/project-deploy/resources .
docker build -t gcr.io/"$PROJECT_ID"/ui:"$VERSION" -f src/container/deploy/Dockerfile .
rm -rf resources
gcloud docker push gcr.io/"$PROJECT_ID"/ui:"$VERSION"
