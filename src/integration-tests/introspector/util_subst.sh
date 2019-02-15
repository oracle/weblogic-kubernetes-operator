# !/bin/sh

# Copyright 2018, Oracle Corporation and/or its affiliates. All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.
# 
# Description:
#
#   This utility substitutes values for macros in a template.  It
#   exits non-zero if the template has unresolved macros unless the
#   line that contains the macro is tagged with 'subst-ignore-missing'.
#
# Usage:
#
#   ./util_subst.sh [-g] sfilename tfilename foo=bar bar=baz ...
#
#     -g          : also use global env for macro substitutions
#     sfilename   : template with '${foo}', etc macros
#                   also supports '${foo:-somevalue}' style defaults
#     tfilename   : target file that must not exist yet
#     foo=bar ... : list of macro substitutions, these occlude
#                   any macro values obtained via '-g'.
#
#   Mark a source file line with 'subst-ignore-missing' to cause subst 
#   to ignore any ${} macro names that have no corresponding macro values
#   and leave the ${} in place.
#

# Setup tracing.

SCRIPTPATH="$( cd "$(dirname "$0")" > /dev/null 2>&1 ; pwd -P )"
SOURCEPATH="`echo $SCRIPTPATH | sed 's/weblogic-kubernetes-operator.*/weblogic-kubernetes-operator/'`"
traceFile=${SOURCEPATH}/operator/src/main/resources/scripts/traceUtils.sh
source ${traceFile}
[ $? -ne 0 ] && echo "Error: missing file ${traceFile}" && exit 1

# Parse command line, and then recursively have the script call itself.

if [ ! "$1" = "SENTINEL" ];  then
  also_use_global_env="false"
  if [ "$1" = "-g" ]; then
    also_use_global_env="true"
    shift
  fi
  sfilename="$1"
  tfilename="$2"
  [ "$sfilename" = "" ] && trace "Error: missing argument" && exit 1
  [ "$tfilename" = "" ] && trace "Error: missing argument" && exit 1
  shift
  shift
  # trace "Debug: Recursively calling self with 'env "$@" ${SCRIPTPATH}/$0 SENTINEL "$sfilename" "$tfilename"'"
  if [ "$also_use_global_env" = "true" ]; then
    env "$@" ${SCRIPTPATH}/util_subst.sh SENTINEL "$sfilename" "$tfilename"
  else
    env - "$@" ${SCRIPTPATH}/util_subst.sh SENTINEL "$sfilename" "$tfilename"
  fi
  exit $?
fi

# If we get this far, the script has recursively called itself.
# Now it's time to do the substitutions.

sfilename="$2"
tfilename="$3"

# trace "Debug: Converting template '${sfilename}' to '${tfilename}'"

if [ ! -e "$sfilename" ]; then
  if [ ! -e "${SCRIPTPATH}/$sfilename" ]; then
    trace "Error: File '${sfilename}' not found in current directory '`pwd`' or in this script's directory '${SCRIPTPATH}'."
    exit 1
  fi
  sfilename="${SCRIPTPATH}/$sfilename"
fi

if [ -e "$tfilename" ]; then
  trace "Error: File '${tfilename}' already exists."
  exit 1
fi

cp $sfilename $tfilename || exit 1

# First, resolve macros that have corresponding env var values

env | awk -F= '{ print $1 }' | sort -r | while read ii; do
  varstr1="\${$ii:-[^}]*}"
  varstr2="\${$ii}"
  newstr="${!ii}"
  # trace "Debug: Substituting '$varstr1' and '$varstr2' with '$newstr'"
  sed -i -e "s;$varstr1;$newstr;g" $tfilename
  sed -i -e "s;$varstr2;$newstr;g" $tfilename
done

# Now handle remaining unresolved macros that have defaults (e.g. ${xxxx:-mydefault})

sed -i -e 's;${[a-zA-Z0-9_][a-zA-Z0-9_]*:-\([^}]*\)};\1;g' $tfilename

# Finally, fail if any unresolved macros are leftover and their
# lines aren't marked with special keyword 'subst-ignore-missing'.
#
# On a failure, helpfully list the problem lines in the input file
# along with the line numbers.

count="`grep -v 'subst-ignore-missing' $tfilename | grep -v 'env:' | grep -v 'secret:' | grep -v '\${id}' | grep -c '\${'`"
if [ $count -gt 0 ]; then
  trace "Error: Found unresolved variables in file '$sfilename':"
  grep -n "\${" $tfilename | sed 's/^/@  line /' | grep -v 'subst-ignore-missing'
  exit 1
fi

exit 0
