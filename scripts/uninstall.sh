#! /bin/bash
set -x
NAMESPACE=$1

ROOT_DIR=$(dirname $0)/..
NAMESPACE=${NAMESPACE:-examples}

helm  del --purge ${NAMESPACE}-musicreader

kubectl wait --for=delete --timeout=300s FlinkCluster/music-reader-cluster -n ${NAMESPACE}
