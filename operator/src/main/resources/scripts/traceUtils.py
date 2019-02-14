# Copyright 2018, Oracle Corporation and/or its affiliates. All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

# Usage: trace('string')
#
# This matches format of bash trace_utils.sh trace, and the operator's log format.
#
# Sample output:   @[2018-09-28T17:23:55.335 UTC][introspectDomain.py:614] Domain introspection complete.
#
# Importing this file when it's not in sys.path of the calling script:
#
#   #Include this script's current directory in the import path
#   tmp_callerframerecord = inspect.stack()[0]    # 0 represents this line # 1 represents line at caller
#   tmp_info = inspect.getframeinfo(tmp_callerframerecord[0])
#   tmp_scriptdir=os.path.dirname(tmp_info[0])
#   sys.path.append(tmp_scriptdir)
#

import sys
import inspect
import os
from datetime import datetime

def trace(object):
  callerframerecord = inspect.stack()[1]    # 0 represents this line
                                            # 1 represents line at caller
  info = inspect.getframeinfo(callerframerecord[0])
  dt=datetime.utcnow()
  filename=os.path.basename(info[0])
  lineno=info[1]
  print("@[%d-%.2d-%.2dT%.2d:%.2d:%.2d.%.3d UTC][%s:%s] %s"
        % (dt.year,dt.month,dt.day,dt.hour,dt.minute,dt.second,dt.microsecond/1000,
           filename,lineno,object))
