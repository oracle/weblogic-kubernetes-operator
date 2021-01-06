# Copyright (c) 2018, 2021, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

# Usage: trace('string')
#        trace(logLevel,'string')
#
# Valid values for logLevel are SEVERE|WARNING|ERROR|INFO|CONFIG|FINE|FINER|FINEST
#    'ERROR' is converted to 'SEVERE' 
#    Unknown logLevels are converted to 'FINE'.
#
# This matches format of bash utils.sh trace, and rougly matches the operator's log format.
#
# Sample output:   @[2018-09-28T17:23:55.335 UTC][introspectDomain.py:614][FINE] Domain introspection complete.
#
# Importing this file when it's not in sys.path of the calling script:
#
#   #Include this script's current directory in the import path
#   tmp_callerframerecord = inspect.stack()[0]    # 0 represents this line # 1 represents line at caller
#   tmp_info = inspect.getframeinfo(tmp_callerframerecord[0])
#   tmp_scriptdir=os.path.dirname(tmp_info[0])
#   sys.path.append(tmp_scriptdir)
#
#   from utils import *
#

import sys
import inspect
import os
from datetime import datetime

# NOTE: This may be parsed by the operator. Do not change the date or log format without 
#       also updating the parser.

def traceInner(logLevel,object):
  callerframerecord = inspect.stack()[2]    # 0 represents this line
                                            # 1 represents line at caller
                                            # 2 represents line at caller's caller
  info = inspect.getframeinfo(callerframerecord[0])
  dt=datetime.utcnow()
  filename=os.path.basename(info[0])
  lineno=info[1]
  # convert ERROR to SEVERE as operator has no ERROR level
  switcher = {
    'SEVERE'  : 'SEVERE',
    'ERROR'   : 'SEVERE',
    'WARNING' : 'WARNING',
    'INFO'    : 'INFO',
    'CONFIG'  : 'CONFIG',
    'FINE'    : 'FINE',
    'FINER'   : 'FINER',
    'FINEST'  : 'FINEST',
  }
  # use FINE as logLevel if logLevel is not a known type
  logLevel=switcher.get(logLevel.upper(),'FINE')
  print("@[%d-%.2d-%.2dT%.2d:%.2d:%.2d.%.3d UTC][%s:%s][%s] %s"
        % (dt.year,dt.month,dt.day,dt.hour,dt.minute,dt.second,dt.microsecond/1000,
           filename,lineno,logLevel,object))

def trace(arg1,arg2='SENTINEL'):
  if arg2 == 'SENTINEL': 
    traceInner('FINE',arg1)
  else:
    traceInner(arg1,arg2)
