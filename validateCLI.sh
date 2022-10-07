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
if [ ! -f $top_level_file ]; then
  echo "ERROR: Validation check '$(basename $0)' failed." \
       "This test expects to be run at the top of the WKO source tree" \
       "but could not find top level file '$top_level_file'." \
       "Current directory = $(pwd)." \
       "Top of git tree = $(git rev-parse --show-toplevel)."
  exit 1
fi

temp_file1=$(mktemp /tmp/$(basename $0).1.XXXXXX.out)
temp_file2=$(mktemp /tmp/$(basename $0).2.XXXXXX.out)
trap "rm -f $temp_file1 $temp_file2" 0 2 3 15

for ext in sh py java ; do
  git ls-files \
    | grep "\.${ext}$" \
    | xargs grep "kubectl[^_a-zA-Z0-9]" \
    | grep -vE "[^:]*:[[:space:]]*(//|/\*|\*|#)" \
    | grep -vE "[^:]*:.*(See kubectl|See .kubectl|logger.info)" \
    | grep -vE "[^:]*:.*(KUBERNETES_CLI:-kubectl|KCLI=\"kubectl\")" \
    | grep -vE "[^:]*:.*(.m .kubernetes_cli.)" \
    | grep -vE "[^:]*:.*(Default is .kubectl)" \
    | grep -vE "[^:]*:.*(when using the 'kubectl port-forward' pattern)" \
    | grep -vE "[^:]*:.*(when describing the domain .kubectl)" \
    | grep -vE "[^:]*:.*(__kubernetes_cli=.....-kubectl.)" \
    | grep -vE "DEFAULT = .kubectl." \
    | grep -vE "Verify call .kubectl scale." \
    | grep -vE "annotations.put..kubectl.kubernetes.io.last-applied-configuration" \
    | grep -vE "directly using 'kubectl'"
  git ls-files \
    | grep "\.${ext}$" \
    | xargs grep "docker[^-_a-zA-Z0-9/]" \
    | grep -vE "[^:]*:[[:space:]]*(//|/\*|\*|#)" \
    | grep -vE "[^:]*:.*(logger.info|--secret-docker)" \
    | grep -vE "[^:]*:.*(WLSIMG_BUILDER:-docker)" \
    | grep -vE "DEFAULT = .docker." \
    | grep -vE "directly using 'docker'"
done > $temp_file1

exit_code=0
match_myself_regex="^$(basename $0):"
match_myself_count="$(grep -c "$match_myself_regex" $temp_file1)"
match_myself_expected="2"

if [ "$match_myself_count" != "$match_myself_expected" ]; then
  # This script deliberately includes itself in its own docker & kubectl checks
  # as a way to verify that its checks are working correctly.
  # So the above 'xargs grep' lines are expected to be in $temp_file1
  # (once for docker, and once for kubectl).
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
       "To add usage exceptions, add specifically targeted 'grep -vE' expressions in $(basename $0)." \
       "Please fix the following:"
  cat $temp_file1
  exit_code=2
fi

exit $exit_code
