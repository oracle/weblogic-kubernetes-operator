# Copyright (c) 2022, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

import sys
import traceback

def connect_to_adminserver():
  try:
    if connected == 'false':
      print 'connecting to admin server'
      connect(admin_username, admin_password, 't3://' + admin_host + ':' + admin_port)
  except NameError, e:
    print('Apparently properties not set.')
    print('Please check the property: ', sys.exc_info()[0], sys.exc_info()[1])
    exit(exitcode=1)
  except:
    print 'Connecting to admin server failed'
    print dumpStack()
    apply(traceback.print_exception, sys.exc_info())
    exit(exitcode=1)

connect_to_adminserver()
exit()
