#! /bin/bash
set -ex
NAMESPACE=$1

ROOT_DIR=$(dirname $0)/..
NAMESPACE=${NAMESPACE:-examples}

echo ${NAMESPACE}
helm upgrade --install --timeout 600 --wait --debug ${NAMESPACE}-musicreader --namespace $NAMESPACE ${ROOT_DIR}/charts
