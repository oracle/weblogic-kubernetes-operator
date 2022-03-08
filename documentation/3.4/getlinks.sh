#!/bin/bash

# Copyright (c) 2019, 2021, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

function usage() {

cat << EOF

 This is a helper script for getting a best effort inventory
 of the header links in a hugo markup file. It can be used
 to help create a table of contents for a particular file,
 or as a tool for discovering the 'relref' syntax for each file.

 Usage #1:
   Pass the location of a markup file as a parameter. For example:

   ./getlinks.sh security/service-accounts.md
   [Service accounts](({{< relref security/service-accounts.md >}}))
      - [WebLogic Kubernetes Operator service account](#weblogic-kubernetes-operator-service-account)
      - [Additional reading](#additional-reading)

 Usage #2:
   Get links for all files by passing 'all'. For example:

   ./getlinks.sh all > links.txt

 Notes:
   - The script must be run within the 'content' directory.

   - The script is 'best effort'. For example, it may mistakenly
     assume certain text is a title, etc. 

   - The script works by looking for lines starting with '##'. 
     It ignores '# ' as that's little used in the WKO documentation
     and sometimes has other meanings than 'title'.

EOF
}

# Generate internal refs for each heading in a markup file
# For example "#Any questions?" becomes "[Any questions?](#any-questions)"
function internalRef() {
  # Ignore "^# " as a heuristic - we tend not to use the
  # largest heading style, and "^#" might be a comment in
  # a ``` block instead of a heading.
  grep "^##" $1 | while read contents; do
    line="$(echo $contents | sed 's/^#*//g' | sed 's/^ *//' | sed 's/ *$//')"
    spaces="$(echo $contents | sed 's/\(^#*\).*/\1/g' | sed 's/#/  /g')"
    ref=$(echo $line | tr '[:upper:]' '[:lower:]' | sed 's/ /-/g' | sed "s/[(),!?'\.\`\"]//g")
    # echo "$spaces - [$line](#$ref) or `relRef $1 \"$line\" \"$ref\"`"
    echo "$spaces - [$line](#$ref)"
  done
}

# Generate a relref for a markup file, using the file's title for the relref text
# For example, this can generate: [Prepare for a domain]({{< relref "/quickstart/prepare.md" >}})
function relRef() {
  title=$(grep "^title *[:=]" $1 | sed 's/^[^"]*"\(.*\)"/\1/g' | sed 's/^ *//' | sed 's/ *$//')
  ref=$(echo $1 | sed 's/^\.//')
  if [ "$2$3" = "" ]; then
    echo "[$title]({{< relref \"$ref\" >}})"
  else
    echo "[$2]({{< relref \"$ref#$3\" >}})"
  fi
}

if [ "$1" = "all" ]; then
  cd ../content

  find . -name "*.md" | while read line; do
    echo
    relRef $line
    internalRef $line
  done

elif [ -f ../content/$1 ]; then
  cd ../content

  relRef $1
  internalRef $1

elif [ "$1" = "-help" ] || [ "$1" = "-?" ] || [ -z "$1" ]; then

  usage

else

  echo "Error: File '../content/$1' not found.  Pass -? for usage."

fi
