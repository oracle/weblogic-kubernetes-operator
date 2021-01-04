# Copyright (c) 2019, 2021, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

#
# Summary:
#
#   This script generates 'dry run' output suitable
#   for the MII sample. It uses local templates, helper
#   scripts, and files in the MII sample to generate
#   the following 'complete sample' in '$GENROOTDIR/mii-sample':
#
#      - model image archives (applications)
#      - model image files (wl config)
#      - image build scripts for above model image archive/files
#      - tooling download script
#      - model configmap creation script/source
#      - domain resources
#      - secret creation scripts for above model file, configmap, and domain resource
#      - traefik ingress yaml
#
#   This script exits with a non-zero exit code if:
#
#      - Directory '/$GENROOTDIR/mii-sample' already exists.
#      - A generated domain resource or ingress yaml does
#        not match yaml checked into the MII sample.
#
#   Default for GENROOTDIR is "/tmp".
#
#   TBD add parm to run the 'tooling' script - since that calls curl...
#

SCRIPTDIR="$( cd "$(dirname "$0")" > /dev/null 2>&1 ; pwd -P )"
SRCDIR="$( cd "$SCRIPTDIR/../../../.." > /dev/null 2>&1 ; pwd -P )"
MIISAMPLEDIR="$( cd "$SRCDIR/kubernetes/samples/scripts/create-weblogic-domain/model-in-image" > /dev/null 2>&1 ; pwd -P )"

set -e
set -o pipefail
set -u

# setup globals based on phase, the phases match the phases in the MII sample
function phase_setup() {
  case "$1" in 
    # An initial domain with admin server, web-app 'v1', and a single cluster 'cluster-1' with 2 replicas.
    initial)
      domain_num=1
      image_version=v1
      archive_version=v1
      configmap=false
      ;;
    # Same as initial, plus a data source targeted to 'cluster-1' which is dynamically supplied using a model configmap. 
    update1)
      domain_num=1
      image_version=v1
      archive_version=v1
      configmap=true
      ;;
    # Same as update1, with a second domain with its own uid 'sample-domain2' that's based on the update1 domain's resource file.
    update2)
      domain_num=2
      image_version=v1
      archive_version=v1
      configmap=true
      ;;
    # Similar to update1, except deploy an updated web-app 'v2' while keeping the original app in the archive.
    update3)
      domain_num=1
      image_version=v2
      archive_version=v2
      configmap=true
      ;;
    *)
      echo "Error: Unknown phase $1." 
      ;;
  esac
}

#
# Init WORKDIR
#

export WORKDIR=${GENROOTDIR:-/tmp}/mii-sample

echo "@@ Info: Starting '$(basename $0)'. See target directory '$WORKDIR'."

if [ -d $WORKDIR ] && [ "$(ls $WORKDIR)" ]; then
  echo "@@ Error: Target dir $WORKDIR already exists."
  exit 1
fi

mkdir -p $WORKDIR
cd $WORKDIR

#
# Copy over the sample to WORKDIR, but don't keep the ingress & domain yaml resources (we will regenerate them)
#

cp -r $MIISAMPLEDIR/* $WORKDIR
rm -f $WORKDIR/domain-resources/WLS/*.yaml
rm -f $WORKDIR/domain-resources/JRF/*.yaml
rm -f $WORKDIR/ingresses/*.yaml

#
# Stage commands for downloading WDT and WIT installer zips
#

$SCRIPTDIR/stage-tooling.sh -dry | grep dryrun | sed 's/dryrun://' > $WORKDIR/model-images/download-tooling.sh
chmod +x $WORKDIR/model-images/download-tooling.sh

#
# Stage everything else
#

for phase in initial update1 update2 update3; do

  phase_setup $phase

  export DOMAIN_NAMESPACE=sample-domain1-ns
  export DOMAIN_UID=sample-domain$domain_num
  export ARCHIVE_SOURCEDIR="archives/archive-$archive_version"
  export INCLUDE_MODEL_CONFIGMAP=$configmap
  export CUSTOM_DOMAIN_NAME=domain$domain_num
  export MODEL_IMAGE_NAME=model-in-image
  export INTROSPECTOR_DEADLINE_SECONDS=600
  export IMAGE_PULL_SECRET_NAME=""

  # setup ingress yaml files
  $SCRIPTDIR/stage-and-create-ingresses.sh -dry

  for type in WLS JRF; do

  export WDT_DOMAIN_TYPE=$type
  export MODEL_IMAGE_TAG=$type-$image_version
  export MODEL_DIR=model-images/${MODEL_IMAGE_NAME}__${MODEL_IMAGE_TAG}

  # setup image build scripts

  if [ -d $WORKDIR/$MODEL_DIR ]; then
    $SCRIPTDIR/build-model-image.sh -dry \
      | grep dryrun | sed 's/dryrun://' \
      > $WORKDIR/$MODEL_DIR/build-image.sh
    chmod +x $WORKDIR/$MODEL_DIR/build-image.sh
  fi

  # setup domain resource 

  domain_path=domain-resources/$type/mii-$phase-d$domain_num-$MODEL_IMAGE_TAG
  if [ "$configmap" = "true" ]; then
    domain_path=$domain_path-ds
  fi
  export DOMAIN_RESOURCE_FILENAME=$domain_path.yaml
  $SCRIPTDIR/stage-domain-resource.sh

  # setup secret script for the domain resource

  $SCRIPTDIR/create-secrets.sh -dry kubectl | grep dryrun | sed 's/dryrun://' > $WORKDIR/$domain_path.secrets.sh
  chmod +x $WORKDIR/$domain_path.secrets.sh
   
  # setup script for the configmap

  if [ "$configmap" = "true" ]; then
    $WORKDIR/utils/create-configmap.sh \
      -c ${DOMAIN_UID}-wdt-config-map \
      -f ${WORKDIR}/model-configmaps/datasource \
      -d $DOMAIN_UID \
      -n $DOMAIN_NAMESPACE \
      -dry kubectl | grep dryrun | sed 's/dryrun://' \
      > $WORKDIR/$domain_path.model-configmap.sh
    chmod +x $WORKDIR/$domain_path.model-configmap.sh
  fi

  done
done

echo "@@"
echo "@@ ##############################################"
echo "@@"

#
# Assert that generated ingress and domain resource
# yaml files match the files in the sample's git check-in.
#

yaml_error=false

function yaml_compare() {
  set -e
  cd $1
  for line in $(find . -name "*.yaml"); do
    if [ ! -f "$2/$line" ]; then
      echo "@@ Error: file '$1/$line' exists, but file '$2/$line' does not."
      yaml_error=true
    else
      set +e 
      diff $line $2/$line > /dev/null 2>&1
      if [ $? -ne 0 ]; then
        echo "@@ Error: file '$1/$line' differs from '$2/$line':"
        diff $line $2/$line
        yaml_error=true
      fi
      set -e
    fi
  done
}

yaml_compare $WORKDIR $MIISAMPLEDIR
yaml_compare $MIISAMPLEDIR $WORKDIR

if [ "$yaml_error" = "true" ]; then
  echo "@@ Error: Files in '$MIISAMPLEDIR' don't match the files generated by '$SCRIPTDIR/$(basename $0)'."
  echo "@@ Error: The mismatch could be due to a direct edit of a generated file in '$MIISAMPLEDIR' (which is not supported)."
  echo "@@ Error: The mismatch could be due to an edit of a domain resource template in '$SCRIPTDIR/*template*' "
  echo "@@        or an edit of an ingress template in '$SCRIPTDIR/stage-and-create-ingresses.sh', where the generated "
  echo "@@        result from this script '$0' was not checked into git at '$MIISAMPLEDIR'."
  echo "@@"
  exit 1
else
  echo "@@ Info: Confirmed that files in '$MIISAMPLEDIR' match the files generated by '$SCRIPTDIR/$(basename $0)'."
  echo "@@ Info: Finished '$(basename $0)'! See target directory '$WORKDIR'."
  echo "@@"
fi

