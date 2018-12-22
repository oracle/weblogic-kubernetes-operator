# !/bin/sh

# Copyright 2018, Oracle Corporation and/or its affiliates. All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

# 
# Description:
# ------------
#
# This helper utility parses $1 into multiple files.
# 
# It looks for marker lines ">>> some_path" and ">>> EOF" in $1 to delimit
# files, and then copies delimited files to directory $2 with file name
# "`basename some_path`".  
#
# It ignores lines that aren't delimited.  E.g. it ignores all
# lines up to the first ">>> some_path", lines between ">>> EOF"
# and ">>> some_other_path", etc.
#
# If a ">>> EOF" is missing, then the next ">>> some_path" line is 
# assumed to mark the end of the current file.
#
# Usage:
# ------
# 
# ./util_fsplit.sh input_file_name output_dir
#

SCRIPTPATH="$( cd "$(dirname "$0")" > /dev/null 2>&1 ; pwd -P )"
SOURCEPATH="`echo $SCRIPTPATH | sed 's/weblogic-kubernetes-operator.*/weblogic-kubernetes-operator/'`"
traceFile=${SOURCEPATH}/operator/src/main/resources/scripts/traceUtils.sh
source ${traceFile}
[ $? -ne 0 ] && echo "Error: missing file ${traceFile}" && exit 1

sfile="$1"
tdir="$2"

[ ! -f "$sfile" ] && trace "Error: file '$sfile' doesn't exist." && exit 1
[ -e "$tdir" ] && trace "Error: directory '$tdir' already exists." && exit 1

mkdir -p $tdir || exit 1

trace "Info: Parsing files out of '$sfile' into directory '$tdir'."

curname=""

{
# IFS='' causes read line to preserve leading spaces
IFS=''

# -r cause read to treat backslashes as-is, e.g. '\n' --> '\n'
cat $sfile | while read -r line; do
  fname="`echo "$line" | grep '^>>>' | sed 's/^>>> *\([^ ]*\).*/\1/'`"
  if [ "$fname" = "EOF" ]; then
    # trace "Debug: Done with file '$curname'"
    curname=""
  elif [ ! "$fname" = "" ]; then
    curname="$tdir/`basename $fname`"
    trace "Info: Creating file '$curname'"
    rm -f $curname
  elif [ "$curname" = "" ];  then
    # trace "Debug: Ignoring line '$line' (no current file)."
    [ 1 -eq 1 ] #no-op
  else
    echo "$line" >> $curname
  fi
done

}
