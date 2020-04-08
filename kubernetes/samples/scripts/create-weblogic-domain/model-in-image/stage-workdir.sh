#!/bin/bash
# Copyright (c) 2020, Oracle Corporation and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

#
# This script sets up a working directory for this sample. It copies ./env.sh
# to this directory. It fails with a non-zero exit code if it cannot
# create the directory when the directory doesn't already exist, or if 
# it cannot copy env.sh to the directory if env.sh isn't already copied to
# the directory.
#
# Once the working directory is setup, the other sample scripts will
# use this directory for their files. To customize sample variables,
# export the variables and/or modify WORKDIR/env.sh prior to running
# other scripts from this sample.
#
# Optional environment variable used by this script:
#
#    WORKDIR 
#      Working directory for the sample with at least 10g of space
#      defaults to /tmp/$USER/model-in-image-sample-work-dir
#

set -o pipefail
set -eu
SCRIPTDIR="$( cd "$(dirname "$0")" > /dev/null 2>&1 ; pwd -P )"
echo "@@ Info: Running '$(basename "$0")'."

WORKDIR=${WORKDIR:-/tmp/$USER/model-in-image-sample-work-dir}

mkdir -p $WORKDIR

if [ ! -e $WORKDIR/env.sh ]; then
  cp $SCRIPTDIR/env.sh $WORKDIR/env.sh
fi

echo "@@ Info: WORKDIR is ready. This is the directory that this sample will use for its files and that the sample will use to load its environment. To customize sample variables, export the variables and/or modify WORKDIR/env.sh prior to running the other scripts in this sample. WORKDIR='$WORKDIR'."
