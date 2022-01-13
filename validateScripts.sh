#!/bin/bash
#
# Copyright (c) 2022, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.
#
found_error=0

verify_no_function_keyword() {
  result=$(grep -oE ".*function +(\w+).+{" "$1"  )
  if [ -n "$result" ]; then
   printf "Please remove usages of 'function' keyword from %s:\n%s\n" "$1" "$result"
  fi
}


find . -type f -name '*.sh' -print0 | while IFS= read -r -d '' file; do
    retval=$(verify_no_function_keyword "$file" )
    if [ -n "$retval" ]; then
      echo "$retval"
      exit 1
    fi
done

found_error=$?

exit $found_error

