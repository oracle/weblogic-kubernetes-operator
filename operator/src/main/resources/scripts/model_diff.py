import sets
import sys


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
        # print 'Entering recursive changed detail key=' + str(key) + ' token=' + str(token) + ' root=' + str(root)
        a=ModelDiffer(self.current_dict[key], self.past_dict[key])
        diff=a.changed()
        added=a.added()
        removed=a.removed()
        saved_token=token

        # print 'DEBUG: In recursive changed detail ' + str(diff)
        # print 'DEBUG: In recursive added detail: ' + str(a.added())
        if len(diff) > 0:
            for o in diff:
                token = saved_token
                # The token is a dotted string that is used to parse and rebuilt the structure later
                # print 'DEBUG: in recursive changed detail walking down1 ' + str(o)
                token=token+'.'+o
                if a.is_dict(o):
                    # print 'DEBUG: in recursive changed detail walking down2 ' + str(token)
                    a.recursive_changed_detail(o,token, root)
                    last=token.rfind('.')
                    token=root
                else:
                    all_changes.append(token)
                    last=token.rfind('.')
                    token=root


        # already out of recursive calls, add all entries from current dictionary
        # resources.JDBCSubsystemResources.* (note it may not have the lower level nodes

        if len(added) > 0:
            for item in added:
                token = saved_token
                all_added.append(token + '.' + item)

        # We don't really care about this, just put something here is enough

        if len(removed) > 0:
            for item in removed:
                token = saved_token
                all_removed.append(token + '.' + item)
        # print 'Exiting recursive_changed_detail'

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

    def _add_results(self, ar_changes):
        for item in ar_changes:
            splitted=item.split('.',1)
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
                splitted=splitted[1].split('.',1)
                n=len(splitted)

            leaf=result
            value_tree=self.current_dict
            for k in walked:
                leaf = leaf[k]
                value_tree=value_tree[k]

            leaf[splitted[0]] =value_tree[splitted[0]]
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
        Is it a safe difference to do online update.
        :param model: diffed model
        return 0 false 1 true
        """
        if model.has_key('appDeployments'):
            return 0

        # if nothing changed
        if not model:
            return 0

        if len(all_removed) > 0:
            return 0

        if len(all_added) > 0:
            return self._is_safe_addition()

        return 1

    def _is_safe_addition(self):
        """
        Check to see if the additions are safe to use for online update
        Note:  all the additions are in a list.  The entire list is check and the condition is all or nothing
        :return: 1 if ok to apply all the additions for online update
                 0 if not ok to apply all the additions for online update
        """
        # filter out topology
        if model.has_key('appDeployments'):
            return 0

        if model.has_key('topology'):
            return 0

        # allows add attribute to existing entity
        found_in_past_dictionary = 1

        for itm in all_added:
            found_in_past_dictionary = self._in_model(self.past_dict, itm)
            if found_in_past_dictionary == 0:
                break

        if found_in_past_dictionary == 1:
            return 1

        return 0

    def _in_model(self, dictionary, keylist):
        """
        Check whether the keylist is in the dictionary

        :param dictionary: model dictionary
        :param keylist: dot separated keys
        :return:
        """
        splitted=keylist.split('.')
        n=len(splitted)
        i=0
        for i in range(0, n-1):
            if i > 3:
                break
            if dictionary.has_key(splitted[i]):
                if isinstance(dictionary[splitted[i]], dict):
                    dictionary = dictionary[splitted[i]]
                continue
            else:
                break
        if i > 3:
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
        _start_dict = '{'
        _end_dict = '}'

        if dictionary is None:
            return
        end_line = ''
        writer.write(_start_dict)
        end_indent = indent

        indent += ' '
        for key, value in dictionary.iteritems():
            writer.write(end_line)
            end_line = ','
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
        if type(value) == bool or (type(value) == str and (value == 'true' or value == 'false')):
            if value:
                v = "true"
            else:
                v = "false"
            builder.append(v)
        elif type(value) == str:
            builder.append('"').append(self.quote_embedded_quotes(value)).append('"')
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
        return obj.is_safe_diff(net_diff)


def main():
    obj = ModelFileDiffer(sys.argv[1], sys.argv[2])
    if not obj.compare():
        exit(exitcode=1)
    else:
        exit(exitcode=0)


if __name__ == "main":
    all_changes = []
    all_added = []
    all_removed = []
    main()

