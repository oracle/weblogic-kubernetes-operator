# TBD doc/copyright

# gets md5s of
#  - all files in WORKDIR/model directory after recursively expanding archives to a temporary directory
#  - current base image
#  - current model image

set -eu

TESTDIR="$( cd "$(dirname "$0")" > /dev/null 2>&1 ; pwd -P )"
WORKDIR=${WORKDIR:-/tmp/$USER/model-in-image-sample-work-dir}
VERBOSE="${VERBOSE:-}"

source $TESTDIR/util-misc.sh

if [ "$VERBOSE" ]; then
  trace "Info: Running '$(basename "$0")'."
  trace "Info: WORKDIR='$WORKDIR'."
  trace "Info: Output File='WORKDIR/$1'."
fi

WDT_DOMAIN_TYPE=${WDT_DOMAIN_TYPE:-WLS}

case "$WDT_DOMAIN_TYPE" in
  WLS)
    BASE_IMAGE_NAME="${BASE_IMAGE_NAME:-container-registry.oracle.com/middleware/weblogic}" ;;
  JRF|RestrictedJRF)
    BASE_IMAGE_NAME="${BASE_IMAGE_NAME:-container-registry.oracle.com/middleware/fmw-infrastructure}" ;;
  *)
    trace "Error: Invalid domain type WDT_DOMAIN_TYPE '$WDT_DOMAIN_TYPE': expected 'WLS', 'JRF', or 'RestrictedJRF'." && exit 1 ;;
esac
BASE_IMAGE_TAG=${BASE_IMAGE_TAG:-12.2.1.4}
BASE_IMAGE="${BASE_IMAGE_NAME}:${BASE_IMAGE_TAG}"
MODEL_IMAGE_NAME=${MODEL_IMAGE_NAME:-model-in-image}
MODEL_IMAGE_TAG=${MODEL_IMAGE_TAG:-v1}
MODEL_IMAGE="${MODEL_IMAGE_NAME}:${MODEL_IMAGE_TAG}"

changed=true
CURPID=$(bash -c "echo \$PPID")
tmpdir="/tmp/expander.$CURPID.$PPID"
mkdir $tmpdir
cp $WORKDIR/model/* $tmpdir
cd $tmpdir

while [ "$changed" = "true" ]; do
  changed=false
  find . -name "*.zip" | sort | while read line; do
    cd `dirname $line`
    unzip -q `basename $line`
    cd $tmpdir
    rm $line
    changed=true
  done
  find . -name "*.ear" | sort | while read line; do
    cd `dirname $line`
    jar xf `basename $line`
    cd $tmpdir
    rm $line
    changed=true
  done
  find . -name "*.jar" | sort | while read line; do
    cd `dirname $line`
    jar xf $line 
    cd $tmpdir
    rm $line
    changed=true
  done
  find . -name "*.war" | sort | while read line; do
    cd `dirname $line`
    jar xf $line 
    cd $tmpdir
    rm $line
    changed=true
  done
  find . -name "*.gz" | sort | while read line; do
    cd `dirname $line`
    gunzip `basename $line`
    cd $tmpdir
    changed=true
  done
  find . -name "*.tar" | sort | while read line; do
    cd `dirname $line`
    tar xf `basename $line`
    cd $tmpdir
    rm $line
    changed=true
  done
done

function modelImageCkSum {
  cd $tmpdir
  find . -type f | sort | xargs md5sum 
  cd $WORKDIR
  md5sum weblogic-image-tool.zip  
  md5sum weblogic-deploy-tooling.zip
  echo "Base docker image: $BASE_IMAGE $(docker images -q $BASE_IMAGE)"
  echo "Model docker image: $MODEL_IMAGE $(docker images -q $MODEL_IMAGE)"
}

modelImageCkSum > $WORKDIR/$1

rm -fr "/tmp/expander.$CURPID.$PPID"

if [ "$VERBOSE" ]; then
  trace "Info: Done. MD5s for WORKDIR './model', image $BASE_IMAGE, image $MODEL_IMAGE, and WORKDIR tooling zips are in 'WORKDIR/$1'."
fi

