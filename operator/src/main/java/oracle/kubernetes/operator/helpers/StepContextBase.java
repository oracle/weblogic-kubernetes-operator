// Copyright 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;

import io.kubernetes.client.models.V1EnvVar;
import io.kubernetes.client.models.V1Pod;
import oracle.kubernetes.operator.Pair;
import oracle.kubernetes.operator.TuningParameters;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.logging.MessageKeys;
import oracle.kubernetes.weblogic.domain.model.Domain;

public abstract class StepContextBase implements StepContextConstants {
  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");

  /**
   * Abstract method to be implemented by subclasses to return a list of configured and additional
   * environment variables to be set up in the pod.
   *
   * @param tuningParameters TuningParameters that can be used when obtaining
   * @return A list of configured and additional environment variables
   */
  abstract List<V1EnvVar> getConfiguredEnvVars(TuningParameters tuningParameters);

  /**
   * Return a list of environment variables to be set up in the pod. This method does some
   * processing of the list of environment variables such as token substitution before returning the
   * list.
   *
   * @param tuningParameters TuningParameters containing parameters that may be used in environment
   *     variables
   * @return A List of environment variables to be set up in the pod
   */
  final List<V1EnvVar> getEnvironmentVariables(TuningParameters tuningParameters) {

    List<V1EnvVar> vars = getConfiguredEnvVars(tuningParameters);

    addDefaultEnvVarIfMissing(
        vars, "USER_MEM_ARGS", "-XX:+UseContainerSupport -Djava.security.egd=file:/dev/./urandom");

    hideAdminUserCredentials(vars);
    doSubstitution(varsToSubVariables(vars), vars);

    return vars;
  }

  protected Map<String, String> varsToSubVariables(List<V1EnvVar> vars) {
    Map<String, String> substitutionVariables = new HashMap<>();
    if (vars != null) {
      for (V1EnvVar envVar : vars) {
        substitutionVariables.put(envVar.getName(), envVar.getValue());
      }
    }

    return substitutionVariables;
  }

  protected void doSubstitution(final Map<String, String> substitutionVariables, List<V1EnvVar> vars) {
    for (V1EnvVar var : vars) {
      var.setValue(translate(substitutionVariables, var.getValue()));
    }
  }

  protected void doDeepSubstitution(final Map<String, String> substitutionVariables, Object obj) {
    if (obj != null) {
      if (obj instanceof List) {
        ListIterator<Object> it = ((List) obj).listIterator();
        while (it.hasNext()) {
          Object member = it.next();
          if (member instanceof String) {
            String trans = translate(substitutionVariables, (String) member);
            if (!member.equals(trans)) {
              it.set(trans);
            }
          } else if (member != null && isModelClass(member.getClass())) {
            doDeepSubstitution(substitutionVariables, member);
          }
        }
      } else {
        try {
          Class cls = obj.getClass();
          if (isModelClass(cls)) {
            List<Method> modelOrListBeans = modelOrListBeans(cls);
            for (Method item : modelOrListBeans) {
              doDeepSubstitution(substitutionVariables, item.invoke(obj));
            }

            List<Pair<Method, Method>> stringBeans = stringBeans(cls);
            for (Pair<Method, Method> item : stringBeans) {
              item.getRight().invoke(obj, translate(substitutionVariables, (String) item.getLeft().invoke(obj)));
            }

            List<Pair<Method, Method>> mapBeans = mapBeans(cls);
            for (Pair<Method, Method> item : mapBeans) {
              item.getRight().invoke(obj, translate(substitutionVariables, (Map) item.getLeft().invoke(obj)));
            }
          }
        } catch (IllegalAccessException | InvocationTargetException e) {
          LOGGER.severe(MessageKeys.EXCEPTION, e);
        }
      }
    }
  }

  private boolean isModelOrListClass(Class cls) {
    return isModelClass(cls) || List.class.isAssignableFrom(cls);
  }

  private static final String MODELS_PACKAGE = V1Pod.class.getPackageName();
  private static final String DOMAIN_MODEL_PACKAGE = Domain.class.getPackageName();

  private boolean isModelClass(Class cls) {
    return cls.getPackageName().startsWith(MODELS_PACKAGE)
        || cls.getPackageName().startsWith(DOMAIN_MODEL_PACKAGE);
  }

  private List<Method> modelOrListBeans(Class cls) {
    List<Method> results = new ArrayList<>();
    Method[] methods = cls.getMethods();
    if (methods != null) {
      for (Method m : methods) {
        if (m.getName().startsWith("get")
            && isModelOrListClass(m.getReturnType())
            && m.getParameterCount() == 0) {
          results.add(m);
        }
      }
    }
    return results;
  }

  private List<Pair<Method, Method>> stringBeans(Class cls) {
    return typeBeans(cls, String.class);
  }

  private List<Pair<Method, Method>> mapBeans(Class cls) {
    return typeBeans(cls, Map.class);
  }

  private List<Pair<Method, Method>> typeBeans(Class cls, Class type) {
    List<Pair<Method, Method>> results = new ArrayList<>();
    Method[] methods = cls.getMethods();
    if (methods != null) {
      for (Method m : methods) {
        if (m.getName().startsWith("get")
            && m.getReturnType().equals(type)
            && m.getParameterCount() == 0) {
          try {
            Method set = cls.getMethod("set" + m.getName().substring(3), type);
            if (set != null) {
              results.add(new Pair<>(m, set));
            }
          } catch (NoSuchMethodException nsme) {
            // no-op
          }
        }
      }
    }
    return results;
  }

  private String translate(final Map<String, String> substitutionVariables, String rawValue) {
    String result = rawValue;
    for (Map.Entry<String, String> entry : substitutionVariables.entrySet()) {
      if (result != null && entry.getValue() != null) {
        result = result.replace(String.format("$(%s)", entry.getKey()), entry.getValue());
      }
    }
    return result;
  }

  private Map<String, Object> translate(final Map<String, String> substitutionVariables, Map<String, Object> rawValue) {
    if (rawValue == null)
      return null;

    Map<String, Object> trans = new HashMap<>();
    for (Map.Entry<String, ?> entry : rawValue.entrySet()) {
      Object value = entry.getValue();
      if (value instanceof String) {
        value = translate(substitutionVariables, (String) value);
      } else if (value instanceof Map) {
        value = translate(substitutionVariables, (Map) value);
      } else {
        doDeepSubstitution(substitutionVariables, value);
      }
      trans.put(translate(substitutionVariables, entry.getKey()), value);
    }

    return trans;
  }

  protected void addEnvVar(List<V1EnvVar> vars, String name, String value) {
    vars.add(new V1EnvVar().name(name).value(value));
  }

  protected boolean hasEnvVar(List<V1EnvVar> vars, String name) {
    for (V1EnvVar var : vars) {
      if (name.equals(var.getName())) {
        return true;
      }
    }
    return false;
  }

  protected void addDefaultEnvVarIfMissing(List<V1EnvVar> vars, String name, String value) {
    if (!hasEnvVar(vars, name)) {
      addEnvVar(vars, name, value);
    }
  }

  protected V1EnvVar findEnvVar(List<V1EnvVar> vars, String name) {
    for (V1EnvVar var : vars) {
      if (name.equals(var.getName())) {
        return var;
      }
    }
    return null;
  }

  protected void addOrReplaceEnvVar(List<V1EnvVar> vars, String name, String value) {
    V1EnvVar var = findEnvVar(vars, name);
    if (var != null) {
      var.value(value);
    } else {
      addEnvVar(vars, name, value);
    }
  }

  // Hide the admin account's user name and password.
  // Note: need to use null v.s. "" since if you upload a "" to kubectl then download it,
  // it comes back as a null and V1EnvVar.equals returns false even though it's supposed to
  // be the same value.
  // Regardless, the pod ends up with an empty string as the value (v.s. thinking that
  // the environment variable hasn't been set), so it honors the value (instead of using
  // the default, e.g. 'weblogic' for the user name).
  protected void hideAdminUserCredentials(List<V1EnvVar> vars) {
    addEnvVar(vars, "ADMIN_USERNAME", null);
    addEnvVar(vars, "ADMIN_PASSWORD", null);
  }
}
