# Copyright (c) 2019, 2020, Oracle Corporation and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

#
# Summary:
#
#   This script generates 'dry run' output suitable
#   for cutting and pasting into the sample 
#   documentation. It uses the templates and helper
#   scripts in the sample to generate the following
#   in '/tmp/mii-sample' stripped of any significant
#   references to environment variables, etc:
#
#      - model archives (applications)
#      - model files (wl config)
#      - image build scripts for above model archive/files
#      - model configmap scripts/yaml/source
#      - domain resources
#      - secret scripts/yaml for above model files, model 
#      - configmap, and domain resource
#      - traefik ingress yaml
#
#   Note: If '/tmp/mii-sample' already exists, this
#         script will exit immediately with a non-zero
#         exit code.

SCRIPTDIR="$( cd "$(dirname "$0")" > /dev/null 2>&1 ; pwd -P )"
SRCDIR="$( cd "$SCRIPTDIR/../../../.." > /dev/null 2>&1 ; pwd -P )"
MIISAMPLEDIR="$( cd "$SRCDIR/kubernetes/samples/scripts/create-weblogic-domain/model-in-image" > /dev/null 2>&1 ; pwd -P )"

set -e
set -o pipefail
set -u

export WORKDIR=/tmp/mii-sample

echo "@@ Info: Starting '$(basename $0)'. See target directory '$WORKDIR'."

if [ -d $WORKDIR ] && [ "$(ls $WORKDIR)" ]; then
  echo "@@ Error: Target dir $WORKDIR already exists."
  exit 1
fi

mkdir -p $WORKDIR
cd $WORKDIR

#
# Copy over the sample to WORKDIR
#

cp -r $MIISAMPLEDIR/* $WORKDIR

#
# Get commands for downloading WDT and WIT installer zips
#

$SCRIPTDIR/stage-tooling.sh -dry | grep dryrun | sed 's/dryrun://' > $WORKDIR/model-images/download-tooling.sh
chmod +x $WORKDIR/model-images/download-tooling.sh

export MODEL_IMAGE_NAME=model-in-image
export DOMAIN_NAMESPACE=${DOMAIN_NAMESPACE:-sample-domain1-ns}

# these phases match the phases in the MII sample
function known_phase() {
  # An initial domain with admin server, web-app 'v1', and a single cluster 'cluster-1' with 2 replicas.
  [ $domain_num -eq 1 ] \
   && [ "$image_version" = "v1" ] \
   && [ "$configmap" = "false" ] \
   && [ "$archive_version" = "v1" ] && echo "initial"

  # Same as initial, plus a data source targeted to 'cluster-1' which is dynamically supplied using a model configmap. 
  [ $domain_num -eq 1 ] \
   && [ "$image_version" = "v1" ] \
   && [ "$configmap" = "true" ] \
   && [ "$archive_version" = "v1" ] && echo "update1"

  # Same as update1, with a second domain with its own uid 'sample-domain2' that's based on the update1 domain's resource file.
  [ $domain_num -eq 2 ] \
   && [ "$image_version" = "v1" ] \
   && [ "$configmap" = "true" ] \
   && [ "$archive_version" = "v1" ] && echo "update2"

  # Similar to update1, except deploy an updated web-app 'v2' while keeping the original app in the archive.
  [ $domain_num -eq 1 ] \
   && [ "$image_version" = "v2" ] \
   && [ "$configmap" = "true" ] \
   && [ "$archive_version" = "v2" ] && echo "update3"

  # TBD
  ## Same as update2, except with a second cluster 'cluster-2' with 1 replica, and the 'v2' web-app targeted to both clusters. 
  #[ $domain_num -eq 1 ] \
  # && [ "$image_version" = "v3" ] \
  # && [ "$configmap" = "true" ] \
  # && [ "$archive_version" = "v2" ] && echo "update4"
}

#
# Get everything else
#

for domain_num in 1 2; do
for image_version in v1 v2 v3; do
for configmap in true false; do
for archive_version in v1 v2; do
for type in WLS JRF; do
set +e
phase=$(known_phase)
set -e
if [ ! -z "$phase" ]; then

  domain=sample-domain$domain_num

  export WDT_DOMAIN_TYPE=$type
  export MODEL_IMAGE_TAG=$type-$image_version
  model_image=$MODEL_IMAGE_NAME:$MODEL_IMAGE_TAG
  export MODEL_DIR=model-images/${MODEL_IMAGE_NAME}__${MODEL_IMAGE_TAG}
  export ARCHIVE_SOURCEDIR="archives/archive-$archive_version"
  export DOMAIN_UID=$domain
  export INCLUDE_MODEL_CONFIGMAP=$configmap


  sample_root=""
  work_root="$WORKDIR/$ARCHIVE_SOURCEDIR"
  if [ ! "$archive_version" = "v1" ]; then
    if [ ! -d "$work_root" ]; then
      sample_root="$MIISAMPLEDIR/$ARCHIVE_SOURCEDIR.new"
    else
      sample_root="$MIISAMPLEDIR/$ARCHIVE_SOURCEDIR.maybe" 
    fi

    mkdir -p "$work_root"
    cp -r $WORKDIR/archives/archive-v1/* $work_root

    # Rename app directory in app archive to correspond to the app version
    # (We will update model to correspond to this directory a little bit down.)
    mkdir -p $work_root/wlsdeploy/applications/myapp-$archive_version
    cp -r $work_root/wlsdeploy/applications/myapp-v1/* \
             $work_root/wlsdeploy/applications/myapp-$archive_version

    # Update app's index.jsp so it outputs its correct archive/app version.

    sed -i -e "s/'v1'/'$archive_version'/g" \
       $work_root/wlsdeploy/applications/myapp-$archive_version/myapp_war/index.jsp

    if [ ! -d "$sample_root" ]; then
       mkdir -p $sample_root
       cp -r $work_root/* $sample_root
    fi
  fi

  # TBD may differ from documentation - need to manually check

  work_file=$WORKDIR/model-images/build--$model_image.sh
  $SCRIPTDIR/build-model-image.sh -dry | grep dryrun | sed 's/dryrun://' > $work_file
  chmod +x $work_file

  # set up image staging files

  sample_root=""
  work_root="$WORKDIR/$MODEL_DIR"
  if [ ! "$image_version" = "v1" ]; then
    if [ ! -d "$work_root" ]; then
      sample_root="$MIISAMPLEDIR/$MODEL_DIR.new"
    else
      sample_root="$MIISAMPLEDIR/$MODEL_DIR.maybe" 
    fi

    mkdir -p "$work_root"
    # make sure app path in model corresponds to current app/archive version
    cp -r $WORKDIR/model-images/${MODEL_IMAGE_NAME}__$type-v1/* "$work_root"
    sed -i -e "s/myapp-v1/myapp-$archive_version/g" $work_root/model.10.yaml

    if [ ! -d "$sample_root" ]; then
       mkdir -p $sample_root
       cp -r $work_root/* $sample_root
    fi
  fi

  # setup ingresses

  $SCRIPTDIR/stage-and-create-ingresses.sh -nocreate

  # setup domain resource and its associated script for creating secrets & configmap

  domain_path=domain-resources/$type/mii-$phase-d$domain_num-$MODEL_IMAGE_TAG
  if [ "$configmap" = "true" ]; then
    domain_path=$domain_path-ds
  fi

  export DOMAIN_RESOURCE_FILENAME=$domain_path.yaml
  $SCRIPTDIR/stage-domain-resource.sh
  sed -i -e "s/\"domain1\"/\"domain$domain_num\"/g" $WORKDIR/$domain_path.yaml

  #$SCRIPTDIR/create-secrets.sh -dry yaml | grep dryrun | sed 's/dryrun://' > $WORKDIR/$domain_path.secrets.yaml
  $SCRIPTDIR/create-secrets.sh -dry kubectl | grep dryrun | sed 's/dryrun://' > $WORKDIR/$domain_path.secrets.sh
  chmod +x $WORKDIR/$domain_path.secrets.sh
   
  if [ "$configmap" = "true" ]; then
    $SCRIPTDIR/create-model-configmap.sh -dry kubectl | grep dryrun | sed 's/dryrun://' \
     > $WORKDIR/$domain_path.model-configmap.sh
    chmod +x $WORKDIR/$domain_path.model-configmap.sh
  fi
fi
done
done
done
done
done

mkdir -p $MIISAMPLEDIR/domain-resources/JRF
mkdir -p $MIISAMPLEDIR/domain-resources/WLS
cp $WORKDIR/ingresses/*.yaml $MIISAMPLEDIR/ingresses
cp $WORKDIR/domain-resources/JRF/mii*.yaml $MIISAMPLEDIR/domain-resources/JRF
cp $WORKDIR/domain-resources/WLS/mii*.yaml $MIISAMPLEDIR/domain-resources/WLS

echo "@@"
echo "@@ ##############################################"
echo "@@"
echo "@@ Info: Finished '$(basename $0)'! See target directory '$WORKDIR'."
echo "@@"

