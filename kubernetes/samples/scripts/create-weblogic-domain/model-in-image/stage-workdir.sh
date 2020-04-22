#!/bin/bash
# Copyright (c) 2020, Oracle Corporation and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

#
# This script creates a working directory for this sample using the WORKDIR
# environment variable. It also copies './env-custom.sh' if the file isn't
# already in the directory.
#
# Once the working directory is setup, the other sample scripts will
# use this directory for their files. 
#
# To customize sample variables, export the variables prior to running 
# sample scripts, and/or modify 'WORKDIR/env-custom.sh'.
#
# Optional environment variable used by this script:
#
#    WORKDIR 
#      Working directory for the sample with at least 10GB of space.
#      Defaults to '/tmp/$USER/model-in-image-sample-work-dir'.
#

set -eu
set -o pipefail

SCRIPTDIR="$( cd "$(dirname "$0")" > /dev/null 2>&1 ; pwd -P )"

WORKDIR=${WORKDIR:-/tmp/$USER/model-in-image-sample-work-dir}

echo "@@"
echo "@@ ######################################################################"
echo "@@ Info: Running '$(basename "$0")'."
echo "@@ Info: WORKDIR='$WORKDIR'."
echo "@@ Info: SCRIPTDIR='$SCRIPTDIR'."

mkdir -p $WORKDIR

if [ ! -e $WORKDIR/env-custom.sh ]; then
  cp $SCRIPTDIR/env-custom.sh $WORKDIR/env-custom.sh
fi

echo "@@ Info: WORKDIR is ready. This is the directory that this sample will use for its files and that the sample will use to load its environment. To customize sample variables, export the variables and/or modify 'WORKDIR/env-custom.sh' prior to running the other scripts in this sample. WORKDIR='$WORKDIR'."
