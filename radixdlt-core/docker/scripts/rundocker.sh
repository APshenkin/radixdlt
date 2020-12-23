#!/bin/bash

# Fail on error
set -e

# Where we are run from
scriptdir=$(dirname "$0")

# Number of validators
validators=${1:-1}

# How to run docker-compose.
# Can use: export DOCKER_COMPOSE_LAUNCH="sudo -E docker-compose"
dockercompose=${DOCKER_COMPOSE_LAUNCH:-docker-compose}

# Check for dockerfile
dockerfile="${scriptdir}/../node-${validators}.yml"
if [ ! -f "${dockerfile}" ]; then
  echo "Can't find ${dockerfile}, aborting."
  exit 1
fi

# Load environment
eval $(${scriptdir}/../../gradlew -q -p "${scriptdir}/../../radixdlt" -P "validators=${validators}" clean generateDevUniverse)

# Launch
${scriptdir}/../../gradlew -p "${scriptdir}/../.." deb4docker && \
  (docker kill $(docker ps -q) || true) 2>/dev/null && \
  ${dockercompose} -f "${dockerfile}" up --build | tee docker.log
