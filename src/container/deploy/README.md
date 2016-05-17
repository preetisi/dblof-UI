# Deploying

## Staging

Edit core.cljs to point to the staging API server (until this is fixed).

Push the new image:
```bash
./src/container/deploy/pushrelease.sh staging-v<new-version-number>
```

Udpate the deployment to the new version:
```bash
gcloud container clusters get-credentials staging-cluster-2
kubectl edit deployment ui
```

## Production

Make sure core.cljs is pointing at the production API server (until this is fixed).

Push the new image:
```bash
./src/container/deploy/pushrelease.sh prod-v<new-version-number>
```

Udpate the deployment to the new version:
```bash
gcloud container clusters get-credentials prod-cluster-1
kubectl edit deployment ui
```
