# Copyright (c) 2019, 2020, Oracle Corporation and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

#
# Summary:
#
#   This script generates 'dry run' output suitable
#   for cutting and pasting into the sample 
#   documentation. It uses the templates and helper
#   scripts in the sample to generate the following
#   in '/tmp/miisample' stripped of any significant
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
# WARNING!
#   
#   This script is destructive! It deletes
#   anything that's already in '/tmp/miisample'
#

SCRIPTDIR="$( cd "$(dirname "$0")" > /dev/null 2>&1 ; pwd -P )"
SRCDIR="$( cd "$SCRIPTDIR/../../../.." > /dev/null 2>&1 ; pwd -P )"
MIISAMPLEDIR="$( cd "$SRCDIR/kubernetes/samples/scripts/create-weblogic-domain/model-in-image" > /dev/null 2>&1 ; pwd -P )"

set -e
set -o pipefail
set -u

export WORKDIR=/tmp/miisample

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

#
# Get commands and yaml for creating the model configmap
#

export MODEL_IMAGE_NAME=model-in-image
export DOMAIN_NAMESPACE=${DOMAIN_NAMESPACE:-sample-domain1-ns}

for domain in sample-domain1 sample-domain2; do
  export DOMAIN_UID=$domain
  $SCRIPTDIR/stage-model-configmap.sh
  $SCRIPTDIR/create-model-configmap.sh -dry kubectl | grep dryrun | sed 's/dryrun://' > $WORKDIR/model-configmaps/model-configmap-uid-$DOMAIN_UID.sh
  chmod +x $WORKDIR/model-configmaps/model-configmap-uid-$DOMAIN_UID.sh
done


#
# Get everything else
#

for type in WLS JRF
do
  export WDT_DOMAIN_TYPE=$type
  for version in v1 v2
  do
    export MODEL_IMAGE_TAG=$type-$version
    model_image=$MODEL_IMAGE_NAME:$MODEL_IMAGE_TAG

    export MODEL_DIR=model-images/$model_image

    export ARCHIVE_SOURCEDIR="archives/archive-$version"

    if [ ! -d "$WORKDIR/$ARCHIVE_SOURCEDIR" ]; then
      mkdir -p "$WORKDIR/$ARCHIVE_SOURCEDIR"
      cp -r $WORKDIR/archives/archive-v1/* $ARCHIVE_SOURCEDIR

      # Rename app directory in app archive to correspond to the app version
      # (We will update model to correspond to this directory a little bit down.)
      mv $WORKDIR/$ARCHIVE_SOURCEDIR/wlsdeploy/applications/myapp-v1 \
         $WORKDIR/$ARCHIVE_SOURCEDIR/wlsdeploy/applications/myapp-$version

      # Update app's index.jsp so it outputs its archive/app version.
      sed -i -e "s/'v1'/'$version'/g" \
         $WORKDIR/$ARCHIVE_SOURCEDIR/wlsdeploy/applications/myapp-$version/myapp_war/index.jsp
    fi

    $SCRIPTDIR/build-model-image.sh -dry | grep dryrun | sed 's/dryrun://' \
      > $WORKDIR/model-images/build--$model_image.sh
    chmod +x $WORKDIR/model-images/build--$model_image.sh

    if [ ! -d "$WORKDIR/$MODEL_DIR" ]; then
      mkdir -p "$WORKDIR/$MODEL_DIR"
      cp -r $WORKDIR/model-images/$MODEL_IMAGE_NAME:$type-v1/* "$WORKDIR/$MODEL_DIR"
      sed -i -e "s/myapp-v1/myapp-$version/g" $MODEL_DIR/model.10.yaml
    fi

    for domain in sample-domain1 sample-domain2; do

      export DOMAIN_UID=$domain
      $SCRIPTDIR/stage-and-create-ingresses.sh -nocreate

      for configmap in true false; do
        export INCLUDE_MODEL_CONFIGMAP=$configmap

        if [ "$configmap" = "true" ]; then
          domain_root=domain-resources/$type/uid-$DOMAIN_UID/imagetag-$MODEL_IMAGE_TAG/model-configmap-yes
        else
          domain_root=domain-resources/$type/uid-$DOMAIN_UID/imagetag-$MODEL_IMAGE_TAG/model-configmap-no
        fi

        export DOMAIN_RESOURCE_FILENAME=$domain_root/mii-domain.yaml

        $SCRIPTDIR/stage-domain-resource.sh

        $SCRIPTDIR/create-secrets.sh -dry kubectl | grep dryrun | sed 's/dryrun://' > $WORKDIR/$domain_root/secrets.sh
        $SCRIPTDIR/create-secrets.sh -dry yaml | grep dryrun | sed 's/dryrun://' > $WORKDIR/$domain_root/secrets.yaml
        chmod +x $WORKDIR/$domain_root/secrets.sh
   
        if [ "$configmap" = "true" ]; then
          $SCRIPTDIR/create-model-configmap.sh -dry kubectl | grep dryrun | sed 's/dryrun://' \
            > $WORKDIR/$domain_root/model-configmap.sh
          chmod +x $WORKDIR/$domain_root/model-configmap.sh
        fi

      done
    done
  done
done

echo "@@"
echo "@@ ##############################################"
echo "@@"
echo "@@ Info: Finished '$(basename $0)'! See target directory '$WORKDIR'."
echo "@@"

