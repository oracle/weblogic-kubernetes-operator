// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.wlsconfig;

/**
 * Substitute macro specified in WLS server template. Behavior of this class should mimic the
 * behavior of the macro substitution logic in WebLogic
 *
 * <p>From WebLogic documentation at
 * https://docs.oracle.com/middleware/12213/wls/DOMCF/server_templates.htm#DOMCF-GUID-EA003F89-C8E4-4CE1-81DF-6FF25F92D21B
 *
 * <p>You can define a macro for any string attribute in a server template. Macros cannot be used
 * for integers or references to other configuration elements. The valid macros available for use in
 * server templates are: <br>
 * ${id}: Instance ID of the dynamically created server; this ID starts at 1 <br>
 * ${serverName}: The name of the server to which this element belongs <br>
 * ${clusterName}: The name of the cluster to which this element belongs <br>
 * ${domainName}: The name of the domain to which this element belongs <br>
 * ${system-property-name}: If this is not one of the predefined macro names listed previously, then
 * it will be evaluated as a system property, and the value will be returned. If the system property
 * does not exist, then an empty string will be substituted. <br>
 * ${machineName}: The name of the machine to which this element belongs (Note: This is missing from
 * the documentation)
 */
public class MacroSubstitutor {

  private static final String START_MACRO = "${";
  private static final String END_MACRO = "}";

  private final int id;
  private final String serverName;
  private final String clusterName;
  private final String domainName;
  private final String machineName;

  /**
   * Creates a MacroSubstitutor with the given macro values that will be used in macro substitution.
   *
   * @param id Value for replacing values in ${id} macro
   * @param serverName Value for replacing values in ${serverName} macro
   * @param clusterName Value for replacing values in ${clusterName} macro
   * @param domainName Value for replacing values in ${domainName} macro
   * @param machineName Value for replacing values in ${machineName} macro
   */
  public MacroSubstitutor(
      int id, String serverName, String clusterName, String domainName, String machineName) {
    this.id = id;
    this.serverName = serverName;
    this.clusterName = clusterName;
    this.domainName = domainName;
    this.machineName = machineName;
  }

  /**
   * Perform macro substitution. Extracts the macro name and resolves its macro value using values.
   * specified in this MacroSubstitutor.
   *
   * @param inputValue String containing macros
   * @return String with values substituted for macros
   */
  String substituteMacro(String inputValue) {

    if (inputValue == null) {
      return inputValue;
    }

    int start = 0;
    int idx = inputValue.indexOf(START_MACRO);
    if (idx == -1) {
      return inputValue;
    }

    StringBuilder retStr = new StringBuilder();

    while (idx != -1) {
      retStr.append(inputValue.substring(start, idx));

      int macroIdx = idx;
      int end = inputValue.indexOf(END_MACRO, macroIdx);
      if (end == -1) {
        start = idx;
        idx = -1;
        continue;
      }

      String macro = inputValue.substring(macroIdx + 2, end);
      String macroVal = resolveMacroValue(macro);

      if (macroVal != null) {
        retStr.append(macroVal);
      }

      start = end + 1;
      idx = inputValue.indexOf(START_MACRO, start);
    }

    retStr.append(inputValue.substring(start));
    return retStr.toString();
  }

  /**
   * Return value for the given macro
   *
   * @param macro Macro to be substituted
   * @return Value for the macro
   */
  private String resolveMacroValue(String macro) {
    if (macro == null || macro.isEmpty()) {
      return "";
    }
    if (macro.equals("domainName")) {
      return domainName;
    } else if (macro.equals("serverName")) {
      return serverName;
    } else if (macro.equals("clusterName")) {
      return clusterName;
    } else if (macro.equals("machineName")) {
      return machineName;
    } else if (macro.equals("id")) {
      return "" + id;
    }

    // Look for macro in ConfigurationProperty or as system property
    return System.getProperty(macro);
  }
}
