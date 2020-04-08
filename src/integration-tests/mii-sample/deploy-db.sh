# TBD doc/copyright

TESTDIR="$( cd "$(dirname "$0")" > /dev/null 2>&1 ; pwd -P )"
SRCDIR="$( cd "$TESTDIR/../../.." > /dev/null 2>&1 ; pwd -P )"

set -u

RCUDB_IMAGE_TAG="${RCUDB_IMAGE_TAG:-12.2.0.1-slim}"
RCUDB_IMAGE_NAME="${RCUDB_IMAGE_NAME:-container-registry.oracle.com/database/enterprise}"

db_check="$(docker images $RCUDB_IMAGE_NAME | grep $RCUDB_IMAGE_TAG | awk '{ print $2 }')"

if [ ! "$db_check" = "$RCUDB_IMAGE_TAG" ]; then
  echo "@@ Error: Missing DB image '$RCUDB_IMAGE_NAME:$RCUDB_IMAGE_TAG'"
  exit 1
fi

set -eu

cd $SRCDIR/kubernetes/samples/scripts/create-oracle-db-service

start-db-service.sh -n ${RCUDB_NAMESPACE:-default} -i $RCUDB_IMAGE_NAME:$RCUDB_IMAGE_TAG

# TBD - need some way to turn off node port, otherwise can't do multi-namespace DB since all will share same NP
