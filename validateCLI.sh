#!/bin/bash
#
# Copyright (c) 2022, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.
#

# Description:
#
#  The WKO project requires that its code use an env var or constant
#  named "KUBERNETES_CLI" instead of "kubectl" so that
#  users can use env var "KUBERNETES_CLI" to set the cli of their choice.
#
#  It also requires that image builder CLI calls should use the
#  WLSIMG_BUILDER env var or java constant instead of directly using 'docker'.
#
#  This program enforces these restrictions by checking
#  for illegal raw usages of 'kubectl' and 'docker' in the project source.
# 
#  The program is called during a mvn build's 'validate' phase.
#  It can be run stand-alone from the top of the source tree
#  or run by calling "mvn validate".

cd ${1:-.} || exit 1

if [ ! -x "$(command -v git)" ]; then
  echo "WARNING: Skipping '$(basename $0)' validation check." \
       "The 'git' command wasn't found."
  exit 0
fi

if ! git ls-files > /dev/null 2>&1 ; then
  echo "WARNING: Skipping '$(basename $0)' validation check." \
       "It's probable $(pwd) is not in a git tree." \
       "Error from git: $(git ls-files 2>&1 1>/dev/null)"
  exit 0
fi

top_level_file=./THIRD_PARTY_LICENSES.txt
kubectl_exceptions_file=./validateCLI.kubectl.dat
docker_exceptions_file=./validateCLI.docker.dat

for fil in $top_level_file $docker_exceptions_file $kubectl_exceptions_file; do
  if [ ! -f "$fil" ]; then
    echo "ERROR: Validation check '$(basename $0)' failed." \
         "This test expects to be run at the top of the WKO source tree" \
         "but could not find top level file '$fil'." \
         "Current directory = $(pwd)." \
         "Top of git tree = $(git rev-parse --show-toplevel)."
    exit 1
  fi
done

temp_file1=$(mktemp /tmp/$(basename $0).1.XXXXXX.out)
temp_file2=$(mktemp /tmp/$(basename $0).2.XXXXXX.out)
trap "rm -f $temp_file1 $temp_file2" 0 2 3 15

filter_comments() {
  sed 's;^[^:]*:[0-9]*:[[:space:]]*//.*$;;g' \
  | sed 's;^[^:]*:[0-9]*:[[:space:]]*\*.*$;;g' \
  | sed 's;^[^:]*:[0-9]*:[[:space:]]*/\*.*$;;g' \
  | sed 's;^[^:]*:[0-9]*:[[:space:]]*\#.*$;;g'
}

filter_from() {
  local cmd=$(cat $1 | grep -vE "^([[:space:]]*$|#.*)" | sed "s:^\(.*\)$:-e 's;\1;;g':g")
  cmd="sed $cmd"
  eval $cmd
}

for ext in sh py java ; do

  git ls-files \
    | grep "\.${ext}$" \
    | xargs grep -nE "(kubectl|Kubectl|KUBECTL)" \
    | filter_comments \
    | filter_from $kubectl_exceptions_file \
    | grep -E '(kubectl|Kubectl|KUBECTL)'

  git ls-files \
    | grep "\.${ext}$" \
    | xargs grep -nE "(docker|Docker|DOCKER)" \
    | filter_comments \
    | filter_from $docker_exceptions_file \
    | grep -E '[^:]*:.*(docker|Docker|DOCKER)'

done > $temp_file1

exit_code=0
match_myself_regex="^$(basename $0):"
match_myself_count="$(grep -c "$match_myself_regex" $temp_file1)"
match_myself_expected="12"

if [ "$match_myself_count" != "$match_myself_expected" ]; then
  # This script deliberately includes itself in its own docker and
  # kubectl checks as a way to verify that its greps and filters are working
  # correctly. It expects exactly $match_myself_expected occurances
  # of docker and kubectl outside of comments).
  echo "ERROR: The $(basename $0) script did not find exactly $match_myself_expected" \
       "occurrences of itself in its search but instead found $match_myself_count instances:"
  grep "$match_myself_regex" $temp_file1
  exit_code=1
fi

grep -v "$match_myself_regex" $temp_file1 > $temp_file2
mv $temp_file2 $temp_file1

if [ -s $temp_file1 ]; then
  echo "ERROR:" \
       "The '$(basename $0)' in directory '$(pwd -P)'" \
       "detected invalid direct uses of the Kubernetes or image builder CLIs." \
       "Kubernetes CLI calls should use the KUBERNETES_CLI env var or java constant instead of directly using 'kubectl'." \
       "And image builder CLI calls should use the WLSIMG_BUILDER env var or java constant instead of directly using 'docker'." \
       "To add usage exceptions, add a sed regex expression" \
       "in $kubectl_exceptions_file or $docker_exceptions_file for 'kubectl' and 'docker' exceptions respectively." \
       "Please fix the following:"
  cat $temp_file1
  exit_code=2
fi

exit $exit_code
