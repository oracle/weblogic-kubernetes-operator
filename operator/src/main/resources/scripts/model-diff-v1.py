# Copyright (c) 2019, 2021, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.
#
# ------------
# Description:
# ------------
#
#   This code compares python dictionaries.  It is used to compare the new vs the old version.
#   output is written as csv file /tmp/model_diff_rc  containing the return codes that represent the differences.
#   also the actual difference in model as yaml and json /tmp/diffed_model.json /tmp/diffed_model.yaml
#
#   This script is invoked by jython.  See modelInImage.sh diff_model
#
import re
import sets
import sys, os, traceback
from java.lang import System
UNSAFE_ONLINE_UPDATE=0
SAFE_ONLINE_UPDATE=1
FATAL_MODEL_CHANGES=2
MODELS_SAME=3
SECURITY_INFO_UPDATED=4
RCU_PASSWORD_CHANGED=5

# The following class is borrowed directly from the WDT project's yaml_tranlator.py
class PythonToYaml:
    """
    A class that converts a Python dictionary into Yaml and writes the output to a file.
    """
    # 4 spaces
    _indent_unit = '    '
    _requires_quotes_chars_regex = '[:{}\[\],&*#?|<>=!%@`-]'

    def __init__(self):
        return

    def _write_dictionary_to_yaml_file(self, dictionary, writer, indent=''):
        """
        Do the actual heavy lifting of converting a dictionary and writing it to the file.  This method is
        called recursively when a value of the dictionary entry is itself a dictionary.
        :param dictionary: the Python dictionary to convert
        :param writer: the java.io.PrintWriter for the output file
        :param indent: the amount of indent to use (based on the level of recursion)
        :raises: IOException: if an error occurs while writing the output
        """
        if dictionary is None:
            return

        for key, value in dictionary.iteritems():
            quoted_key = self._quotify_string(key)
            if isinstance(value, dict):
                writer.write(indent + quoted_key + ':' + '\n')
                self._write_dictionary_to_yaml_file(value, writer, indent + self._indent_unit)
            else:
                writer.write(indent + quoted_key + ': ' + self._get_value_string(value) + '\n')

        return

    def _get_value_string(self, value):
        """
        Convert the Python value into the proper Yaml value
        :param value: the Python value
        :return: the Yaml value
        """
        if value is None:
            result = 'null'
        elif type(value) is int or type(value) is long or type(value) is float:
            result = str(value)
        elif type(value) is list:
            new_value = '['
            for element in value:
                new_value += ' ' + self._get_value_string(element) + ','
            if len(new_value) > 1:
                new_value = new_value[:-1]
            new_value += ' ]'
            result = str(new_value)
        else:
            result = self._quotify_string(str(value))
        return result

    def _quotify_string(self, text):
        """
        Insert quotes around the string value if it contains Yaml special characters that require it.
        :param text: the input string
        :return: the quoted string, or the original string if no quoting was required
        """
        if bool(re.search(self._requires_quotes_chars_regex, text)):
            result = '\'' + self._quote_embedded_quotes(text) + '\''
        else:
            result = self._quote_embedded_quotes(text)
        return result

    def _quote_embedded_quotes(self, text):
        """
        Replace any embedded quotes with two quotes.
        :param text:  the text to quote
        :return:  the quoted text
        """
        result = text
        if '\'' in text:
            result = result.replace('\'', '\'\'')
        if '"' in text:
            result = result.replace('"', '""')
        return result


class ModelDiffer:

    def __init__(self, current_dict, past_dict):

        self.final_changed_model=dict()
        self.current_dict = current_dict
        self.past_dict = past_dict
        self.set_current = sets.Set()
        self.set_past = sets.Set()
        for item in self.current_dict.keys():
            self.set_current.add(item)
        for item in self.past_dict.keys():
            self.set_past.add(item)
        self.intersect = self.set_current.intersection(self.set_past)

    def added(self):
        return self.set_current - self.intersect

    def removed(self):
        return self.set_past - self.intersect

    def changed(self):
        result = sets.Set()
        for o in self.intersect:
            if self.past_dict[o] != self.current_dict[o]:
                result.add(o)
        return result

    def unchanged(self):
        result = sets.Set()
        for o in self.intersect:
            if self.past_dict[o] == self.current_dict[o]:
                result.add(o)
        return result

    def print_diff(self,s, category):
        print category
        if len(s) > 0:
            print s

    def recursive_changed_detail(self, key, token, root):
        debug("DEBUG: Entering recursive_changed_detail key=%s token=%s root=%s", key, token, root)
        a=ModelDiffer(self.current_dict[key], self.past_dict[key])
        diff=a.changed()
        added=a.added()
        removed=a.removed()
        saved_token=token
        debug('DEBUG: In recursive changed detail %s', diff)
        debug('DEBUG: In recursive added detail %s', added)
        if len(diff) > 0:
            for o in diff:
                token=saved_token
                # The token is a | separated string that is used to parse and rebuilt the structure later
                debug('DEBUG: in recursive changed detail walking down 1 %s', o)
                token=token+'|'+o
                if a.is_dict(o):
                    debug('DEBUG: in recursive changed detail walking down 2 %s', token)
                    a.recursive_changed_detail(o,token, root)
                    last=token.rfind('|')
                    token=root
                else:
                    all_changes.append(token)
                    last=token.rfind('|')
                    token=root


        # already out of recursive calls, add all entries from current dictionary
        # resources.JDBCSubsystemResources.* (note it may not have the lower level nodes
        added_token=token
        debug('DEBUG: current added token %s' , added_token)
        if len(added) > 0:
            for item in added:
                token=saved_token
                debug('DEBUG: recursive added token %s item %s ', token, item)
                all_added.append(token + '|' + item)

        # We don't really care about this, just put something here is enough

        if len(removed) > 0:
            for item in removed:
                debug('DEBUG: removed %s', item)
                all_removed.append(token + '|' + item)
        debug('DEBUG: Exiting recursive_changed_detail')

    def is_dict(self,key):
        if isinstance(self.current_dict[key],dict):
            return 1
        else:
            return 0

    def calculate_changed_model(self):
        """
        Calculate the changed model.
        """
        result = dict()
        changed=self.changed()

        for s in changed:
            token=s
            self.recursive_changed_detail(s, token, s)
            self._add_results(all_changes)
            self._add_results(all_added)
            # TODO:  delete needs more work, not simply added to the results
            #self._add_results(all_removed)


    def _add_results(self, ar_changes):

        # The ar_changes is the keys of changes in the dotted format
        #  'resources|JDBCSystemResource|Generic2|JdbcResource|JDBCConnectionPoolParams|TestConnectionsOnReserve
        #
        #  Now change it to python dictionrary
        for item in ar_changes:
            debug('DEBUG: add_results %s', item)

            splitted=item.split('|',1)
            n=len(splitted)
            result=dict()
            walked=[]

            while n > 1:
                tmp=dict()
                tmp[splitted[0]]=dict()
                if len(result) > 0:
                    # traverse to the leaf
                    leaf=result
                    for k in walked:
                        leaf = leaf[k]
                    leaf[splitted[0]]=dict()
                    walked.append(splitted[0])
                else:
                    result=tmp
                    walked.append(splitted[0])
                splitted=splitted[1].split('|',1)
                n=len(splitted)
            #
            # result is the dictionary format
            #
            leaf=result
            value_tree=self.current_dict
            for k in walked:
                leaf = leaf[k]
                value_tree=value_tree[k]

            # walk the current dictionary and set the value
            # doesn't work in delete case
            #
            leaf[splitted[0]] = value_tree[splitted[0]]
            self.merge_dictionaries(self.final_changed_model, result)


    def merge_dictionaries(self, dictionary, new_dictionary):
        """
         Merge the values from the new dictionary to the existing one.
        :param dictionary: the existing dictionary
        :param new_dictionary: the new dictionary to be merged
        """
        for key in new_dictionary:
            new_value = new_dictionary[key]
            if key not in dictionary:
                dictionary[key] = new_value
            else:
                value = dictionary[key]
                if isinstance(value, dict) and isinstance(new_value, dict):
                    self.merge_dictionaries(value, new_value)
                else:
                    dictionary[key] = new_value

    def is_safe_diff(self, model):
        """
        Is it a safe difference for update.
        :param model: diffed model
        return 0 - always return 0 for V1
        """

        # check for phase 1 any security changes in the domainInfo intersection

        if model.has_key('domainInfo'):
            domain_info = model['domainInfo']
            if domain_info.has_key('AdminUserName') or domain_info.has_key('AdminPassword') \
                    or domain_info.has_key('WLSRoles'):
                changed_items.append(SECURITY_INFO_UPDATED)

            if domain_info.has_key('RCUDbInfo'):
                rcu_db_info = domain_info['RCUDbInfo']
                if rcu_db_info.has_key('rcu_schema_password'):
                    changed_items.append(RCU_PASSWORD_CHANGED)

                if rcu_db_info.has_key('rcu_db_conn_string') \
                        or rcu_db_info.has_key('rcu_prefix'):
                    changed_items.append(SECURITY_INFO_UPDATED)

        if model.has_key('topology'):
            if model['topology'].has_key('Security') or model['topology'].has_key('SecurityConfiguration'):
                changed_items.append(SECURITY_INFO_UPDATED)

        return 0

    def _is_safe_addition(self, items):
        """
        check the items in all_added to see if can be used for online update
        return 0 false ;
            1 true ;
            2 for fatal
        """
        # allows add attribute to existing entity

        found_in_past_dictionary = 1
        has_topology=0
        for itm in items:
            if itm.find('topology.') == 0:
                has_topology = 1

            debug('DEBUG: is_safe_addition %s', itm)
            found_in_past_dictionary = self._in_model(self.past_dict, itm)
            debug('DBUEG: found_in_past_dictionary %s', found_in_past_dictionary)
            if not found_in_past_dictionary:
                break
            else:
                # check whether it is in the forbidden list
                if self.in_forbidden_list(itm):
                    print 'Found changes not supported for update: %s. Exiting' % (itm)
                    return FATAL_MODEL_CHANGES


        # if there is a shape change
        # return 2 ?
        if has_topology and not found_in_past_dictionary:
            print 'Found changes not supported for update: %s. Exiting' % (itm)
            return FATAL_MODEL_CHANGES

        if found_in_past_dictionary:
            return SAFE_ONLINE_UPDATE

        # allow new additions for anything ??
        return SAFE_ONLINE_UPDATE

    def _in_model(self, dictionary, keylist):
        """
        check whether the keys is in the dictionary
        :param dictionary dictonary to check
        :param keylist  dot separted key list
        return 1 if it is in model
               0 if it is not in model
        """
        debug('DBEUG: in model keylist=%s dictionary %s', keylist, dictionary)

        splitted=keylist.split('|')
        n=len(splitted)
        i=0
        root_key = splitted[0]

        # loop through the keys and use it to walk the dictionary
        # if it can walk down 3 levels, safely assume it is in the
        # dictionary, otherwise it is a total new addition

        for i in range(0, n):
            if dictionary.has_key(splitted[i]):
                if isinstance(dictionary[splitted[i]], dict):
                    dictionary = dictionary[splitted[i]]
                continue
            else:
                break

        if i > 2:
            return 1

        return 0

    def in_forbidden_list(self, itm):
        forbidden_list = [ '.ListenPort', '.ListenAddress' ]
        for forbidden in forbidden_list:
            if itm.endswith(forbidden):
                return 1
        return 0

    def get_final_changed_model(self):
        """
        Return the changed model.
        """
        return self.final_changed_model



class ModelFileDiffer:

    def __init__(self, current_dict, past_dict):

        self.current_dict_file = current_dict
        self.past_dict_file = past_dict

    def eval_file(self, file):
        true = True
        false = False
        fh = open(file, 'r')
        content = fh.read()
        return eval(content)


    def write_dictionary_to_json_file(self, dictionary, writer, indent=''):
        """
        Write the python dictionary in json syntax using the provided writer stream.
        :param dictionary: python dictionary to convert to json syntax
        :param writer: where to write the dictionary into json syntax
        :param indent: current string indention of the json syntax. If not provided, indent is an empty string
        """
        _start_dict = "{\n"
        _end_dict = "}\n"

        if dictionary is None:
            return
        end_line = ''
        writer.write(_start_dict)
        end_indent = indent

        indent += ' '
        for key, value in dictionary.iteritems():
            writer.write(end_line)
            end_line = ",\n"
            writer.write(indent + '"' + self.quote_embedded_quotes(key) + '" : ')
            if isinstance(value, dict):
                self.write_dictionary_to_json_file(value, writer, indent)
            else:
                writer.write(self.format_json_value(value))
        writer.write(str(end_indent + _end_dict))

        return

    def quote_embedded_quotes(self, text):
        """
        Quote all embedded double quotes in a string with a backslash.
        :param text: the text to quote
        :return: the quotes result
        """
        result = text
        if type(text) is str and '"' in text:
            result = text.replace('"', '\\"')
        return result

    def format_json_value(self, value):
        """
        Format the value as a JSON snippet.
        :param value: the value
        :return: the JSON snippet
        """
        import java.lang.StringBuilder as StringBuilder
        builder = StringBuilder()
        debug("DEBUG: value %s TYPE %s", value, type(value))
        if type(value) == bool or (type(value) == str and (value == 'true' or value == 'false')):
            if value:
                v = "true"
            else:
                v = "false"
            builder.append(v)
        elif type(value) == str:
            builder.append('"').append(self.quote_embedded_quotes(value)).append('"')
        elif type(value) == list:
            builder.append("[ ")
            ind = 0
            for list_item in value:
                if ind > 0:
                    builder.append(", ")
                builder.append('"').append(list_item).append('"')
                ind = ind+1

            builder.append(" ]")
        else:
            builder.append(value)
        return builder.toString()

    def compare(self):
        current_dict = self.eval_file(sys.argv[1])
        past_dict = self.eval_file(sys.argv[2])
        obj = ModelDiffer(current_dict, past_dict)
        obj.calculate_changed_model()
        net_diff = obj.get_final_changed_model()
        fh = open('/tmp/diffed_model.json', 'w')
        self.write_dictionary_to_json_file(net_diff, fh)
        #print all_added
        fh.close()
        fh = open('/tmp/diffed_model.yaml', 'w')
        pty = PythonToYaml()
        pty._write_dictionary_to_yaml_file(net_diff, fh)
        fh.close()
        return obj.is_safe_diff(net_diff)

def debug(format_string, *arguments):
    if os.environ.has_key('DEBUG_INTROSPECT_JOB'):
        print format_string % (arguments)
    return

def main():
    try:
        obj = ModelFileDiffer(sys.argv[1], sys.argv[2])
        rc=obj.compare()
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

