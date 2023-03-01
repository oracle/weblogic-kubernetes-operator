# Copyright (c) 2018, 2023, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.
#
# ------------
# Description:
# ------------
#  This is a WDT filter for primordial domain creation. It filters out all resources and
#  apps deployments, leaving only the domainInfo and admin server in topology.
#
import sys, traceback
import utils


def filter_model(model):
  try:
    if model and 'topology' in model:
      topology = model['topology']
      if 'AdminServerName' in model['topology']:
        admin_server = topology['AdminServerName']
      else:
        # weblogic default
        if 'Server' in topology and 'adminserver' in topology['Server']:
          raise ValueError('Your model does not have AdminServerName set in the topology section but have a server named "adminserver" in topology/Server section, this is not supported.  Please set the AdminServerName attribute in the topology section to the actual administration server name')
        admin_server = 'AdminServer'
      model['topology'] = {}
      model['topology']['AdminServerName'] = admin_server
      model['topology']['Server'] = {}

      # cover the odd case that the model doesn't have any server!
      if 'Server' in topology and admin_server in topology['Server']:
        model['topology']['Server'][admin_server] = topology['Server'][admin_server]
      else:
        if 'Server' not in topology:
          model['topology']['Server'] = {}
        model['topology']['Server'][admin_server] = {}

      if 'Name' in topology:
        model['topology']['Name'] = topology['Name']

      if 'Security' in topology:
        model['topology']['Security'] = topology['Security']

    if model and 'appDeployments' in model:
      model['appDeployments'] = {}

    if model and 'resources' in model:
      model['resources'] = {}

  except:
    exc_type, exc_obj, exc_tb = sys.exc_info()
    ee_string = traceback.format_exception(exc_type, exc_obj, exc_tb)
    utils.trace('SEVERE', 'Error in applying MII filter:\n ' + str(ee_string))
    raise
