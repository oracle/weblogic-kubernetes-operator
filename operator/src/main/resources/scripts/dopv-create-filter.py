# Copyright (c) 2023, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.
#
# ------------
# Description:
# ------------
#  This is a WDT filter for primordial domain creation. It filters out all resources and
#  apps deployments, leaving only the domainInfo and admin server in topology.
#
import os, sys, traceback
import utils


def filter_model(model):
    try:
        if model and 'domainInfo' in model:
            domainInfo = model['domainInfo']
            if 'AppDir' not in domainInfo:
                domain_home = os.environ['DOMAIN_HOME']
                parent_dir = os.path.dirname(domain_home)
                domainInfo['AppDir'] = os.path.join(parent_dir, "applications", model['topology']['Name'])
    except:
        exc_type, exc_obj, exc_tb = sys.exc_info()
        ee_string = traceback.format_exception(exc_type, exc_obj, exc_tb)
        utils.trace('SEVERE', 'Error in applying domain on pv create filter:\n ' + str(ee_string))
        raise
