#!/bin/bash
# Copyright (c) 2017, 2019, Oracle Corporation and/or its affiliates. All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

#
# archive.sh <source_dir> <target_dir>
#   - internal helper method
#   - archives directory ${1} into ${2}/IntSuite.TIMESTAMP.jar
#   - deletes all but the 10 newest archives
#   - this method doesn't have any configurable env vars
#

# trace <message>
function trace {
  #Date reported in same format as oper-log for easier correlation.  01-22-2018T21:49:01
  echo "[`date '+%m-%d-%YT%H:%M:%S'`] [secs=$SECONDS] [test=archive] [fn=archive]: ""$@"
}

function fail {
  trace "Error: ""$@"
  exit 1
}

function archive {
  local SOURCE_DIR="${1?}"
  local ARCHIVE_DIR="${2?}"
  local ARCHIVE_FILE="IntSuite.${IT_CLASS}.TMP.`date '+%Y%m%d%H%M%S'`.jar"
  local ARCHIVE="$ARCHIVE_DIR/$ARCHIVE_FILE"
  local OUTFILE="/tmp/$ARCHIVE_FILE"

  trace About to archive \'$SOURCE_DIR\'.

  [ ! -d "$SOURCE_DIR" ] && fail Could not archive, could not find source directory \'$SOURCE_DIR\'.

  mkdir -p $ARCHIVE_DIR || fail Could not archive, could not create target directory \'$ARCHIVE_DIR\'.

  $JAVA_HOME/bin/jar cf $ARCHIVE $SOURCE_DIR > $OUTFILE 2>&1
  [ $? -eq 0 ] || fail "Could not archive, 'jar cf $ARCHIVE $SOURCE_DIR' command failed: `cat $OUTFILE`"
  rm -f $OUTFILE

  # Jenkins log cleanup is managed on Jenkins job config
  if [ ! "$JENKINS" = "true" ]; then
	  find $ARCHIVE_DIR -maxdepth 1 -name "IntSuite.${IT_CLASS}.PV.*jar" | sort -r | awk '{ if (NR>5) print $NF }' | xargs rm -f
    find $ARCHIVE_DIR -maxdepth 1 -name "IntSuite.${IT_CLASS}.TMP.*jar" | sort -r | awk '{ if (NR>5) print $NF }' | xargs rm -f
  fi
 
   
  trace Archived to \'$ARCHIVE\'.
}

archive "$1" "$2"
