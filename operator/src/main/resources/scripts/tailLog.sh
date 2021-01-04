#!/bin/bash

# Copyright (c) 2019, 2021, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

#
# This script is used to tail the server log and is separate from
# startServer.sh so that it is easier to quickly kill the process
# running this script.
#

echo $$ > $2

while true ; do
  if [ -f $1 ]; then
    tail -F -s 0.1 -n +0 $1 || sleep 10
  fi
  sleep 0.1
done

#
# Work around note:
#
#   The forever loop and '[ -f $1 ]' check above work around an
#   unexpected  behavior  from  'tail -F'.  The  '-F'  expected 
#   behavior is meant to handle 'rolling' files by both:
#
#     A- Waiting  until the  file appears  instead of exiting
#        with an error code.
#
#     B- Once the file is found, gracefully handling the file
#        getting deleted or moved  (by pausing while the file
#        is gone,  and starting up again once a new file with
#        the same name appears in its place).
#
#   But 'A' is not working on WL pods and thus the work around.
#   (Strangely and thankfully 'B' works fine.)
#

