# Copyright (c) 2019, 2021, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.
#
# ------------
# Description:
# ------------
#
#   This code check the diffed model to see what kind of changes are in it.
#   The output is written as csv file /tmp/model_diff_rc  containing the return codes that represent the differences.
#
#   This script is invoked by jython.  See modelInImage.sh diff_model
#
import sys, os, traceback
from java.lang import System
UNSAFE_ONLINE_UPDATE=0
SAFE_ONLINE_UPDATE=1
FATAL_MODEL_CHANGES=2
MODELS_SAME=3
SECURITY_INFO_UPDATED=4
RCU_PASSWORD_CHANGED=5
NOT_FOR_ONLINE_UPDATE=6

class ModelDiffer:

    def is_not_safe_for_online_update(self, model, original_model):
        """
        Is it a safe difference to do online update.
        :param model: diffed model
        return 1 for not safe
            0 for safe
        """
        if os.environ.has_key('MII_USE_ONLINE_UPDATE'):
            if "false" == os.environ['MII_USE_ONLINE_UPDATE']:
                return 0
        else:
            return 0

        if self.in_forbidden_list(model, original_model):
            return 1

        return 0


    def is_safe_diff(self, model, original_model):
        """
        Is it a safe difference for update.
        :param model: diffed model
        return 0 - always return 0 for V1
        """

        # check for phase 1 any security changes in the domainInfo intersection

        if model.has_key('domainInfo'):
            domain_info = model['domainInfo']
            if domain_info.has_key('AdminUserName') or domain_info.has_key('AdminPassword') \
                    or domain_info.has_key('WLSRoles') or domain_info.has_key('WLSUserPasswordCredentialMappings'):
                changed_items.append(SECURITY_INFO_UPDATED)

            if domain_info.has_key('RCUDbInfo'):
                rcu_db_info = domain_info['RCUDbInfo']
                if rcu_db_info.has_key('rcu_schema_password'):
                    changed_items.append(RCU_PASSWORD_CHANGED)

                if rcu_db_info.has_key('rcu_db_conn_string') \
                    or rcu_db_info.has_key('rcu_prefix'):
                    changed_items.append(SECURITY_INFO_UPDATED)

        if model.has_key('topology'):
            if model['topology'].has_key('Security'):
                changed_items.append(SECURITY_INFO_UPDATED)
            if model['topology'].has_key('SecurityConfiguration'):
                changed_items.append(SECURITY_INFO_UPDATED)

        if self.is_not_safe_for_online_update(model, original_model):
            changed_items.append(NOT_FOR_ONLINE_UPDATE)

        return 0


    def in_forbidden_list(self, model, original_model):

        if os.environ.has_key('MII_USE_ONLINE_UPDATE'):
            if "false" == os.environ['MII_USE_ONLINE_UPDATE']:
                return 0
        else:
            return 0

        # Do not allow change ListenAddress, Port, enabled, SSL
        # Allow add
        # Do not allow delete
        _TOPOLOGY = 'topology'
        _NAP = 'NetworkAccessPoint'
        _SSL = 'SSL'
        forbidden_network_attributes = [ 'ListenAddress', 'ListenPort', 'ListenPortEnabled' ]
        if model.has_key(_TOPOLOGY):
            # Do not allow changing topology level attributes
            for key in model[_TOPOLOGY]:
                if not isinstance(model[_TOPOLOGY][key], dict):
                    return 1
            for key in [ 'Server', 'ServerTemplate']:
                # topology.Server|ServerTemplate
                if model[_TOPOLOGY].has_key(key):
                    temp = model[_TOPOLOGY][key]
                    for server in temp:
                        # cannot delete server or template
                        if server.startswith('!'):
                            return 1
                        # ok to add
                        if server not in original_model['topology'][key]:
                            continue
                        for not_this in forbidden_network_attributes:
                            if temp[server].has_key(not_this):
                                return 1
                        if temp[server].has_key(_NAP):
                            nap = temp[server][_NAP]
                            for n in nap:
                                for not_this in forbidden_network_attributes:
                                    if temp[server].has_key(not_this):
                                        return 1
                        # Do not allow any SSL changes
                        if temp[server].has_key(_SSL):
                            return 1

        return 0


class ModelFileDiffer:

    def eval_file(self, file):
        true = True
        false = False
        fh = open(file, 'r')
        content = fh.read()
        return eval(content)

    def compare(self):
        original_model = self.eval_file(sys.argv[1])
        # past_dict = self.eval_file(sys.argv[2])
        obj = ModelDiffer()
        if os.path.exists('/tmp/diffed_model.json'):
            net_diff = self.eval_file('/tmp/diffed_model.json')
        else:
            net_diff = {}
        return obj.is_safe_diff(net_diff, original_model)

def debug(format_string, *arguments):
    if os.environ.has_key('DEBUG_INTROSPECT_JOB'):
        print format_string % (arguments)
    return

def main():
    try:
        obj = ModelFileDiffer()
        obj.compare()
        rcfh = open('/tmp/model_diff_rc', 'w')
        rcfh.write(",".join(map(str,changed_items)))
        rcfh.close()
        System.exit(0)
    except:
        exc_type, exc_obj, exc_tb = sys.exc_info()
        eeString = traceback.format_exception(exc_type, exc_obj, exc_tb)
        print eeString
        System.exit(-1)
if __name__ == "__main__":
    all_changes = []
    all_added = []
    all_removed = []
    changed_items = []
    main()


