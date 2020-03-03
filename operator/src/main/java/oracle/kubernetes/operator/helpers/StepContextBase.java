// Copyright (c) 2019, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.kubernetes.client.openapi.models.V1Container;
import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodSpec;
import io.kubernetes.client.openapi.models.V1Toleration;
import oracle.kubernetes.operator.Pair;
import oracle.kubernetes.operator.TuningParameters;
import oracle.kubernetes.weblogic.domain.model.Domain;
import oracle.kubernetes.weblogic.domain.model.ServerSpec;

public abstract class StepContextBase implements StepContextConstants {
  abstract ServerSpec getServerSpec();

  abstract String getContainerName();

  abstract List<String> getContainerCommand();

  abstract List<V1Container> getContainers();

  protected V1Container createContainer(TuningParameters tuningParameters) {
    return new V1Container()
        .name(getContainerName())
        .image(getServerSpec().getImage())
        .imagePullPolicy(getServerSpec().getImagePullPolicy())
        .command(getContainerCommand())
        .env(getEnvironmentVariables(tuningParameters))
        .resources(getServerSpec().getResources())
        .securityContext(getServerSpec().getContainerSecurityContext());
  }

  protected V1PodSpec createPodSpec(TuningParameters tuningParameters) {
    return new V1PodSpec()
        .containers(getContainers())
        .addContainersItem(createContainer(tuningParameters))
        .affinity(getServerSpec().getAffinity())
        .nodeSelector(getServerSpec().getNodeSelectors())
        .serviceAccountName(getServerSpec().getServiceAccountName())
        .nodeName(getServerSpec().getNodeName())
        .schedulerName(getServerSpec().getSchedulerName())
        .priorityClassName(getServerSpec().getPriorityClassName())
        .runtimeClassName(getServerSpec().getRuntimeClassName())
        .tolerations(getTolerations())
        .restartPolicy(getServerSpec().getRestartPolicy())
        .securityContext(getServerSpec().getPodSecurityContext())
        .imagePullSecrets(getServerSpec().getImagePullSecrets());
  }

  private List<V1Toleration> getTolerations() {
    List<V1Toleration> tolerations = getServerSpec().getTolerations();
    return tolerations.isEmpty() ? null : tolerations;
  }


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
        vars, "USER_MEM_ARGS", "-Djava.security.egd=file:/dev/./urandom");

    hideAdminUserCredentials(vars);
    return doDeepSubstitution(varsToSubVariables(vars), vars);
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

  protected <T> T doDeepSubstitution(final Map<String, String> substitutionVariables, T obj) {
    return doDeepSubstitution(substitutionVariables, obj, false);
  }

  protected <T> T doDeepSubstitution(final Map<String, String> substitutionVariables, T obj, boolean requiresDns1123) {
    if (obj instanceof String) {
      return (T) translate(substitutionVariables, (String) obj, requiresDns1123);
    } else if (obj instanceof List) {
      List<Object> result = new ArrayList<>();
      for (Object o : (List) obj) {
        result.add(doDeepSubstitution(substitutionVariables, o));
      }
      return (T) result;
    } else if (obj instanceof Map) {
      Map<String, Object> result = new HashMap<>();
      for (Map.Entry<String, Object> entry : ((Map<String, Object>) obj).entrySet()) {
        result.put(
            translate(substitutionVariables, entry.getKey()),
            doDeepSubstitution(substitutionVariables, entry.getValue()));
      }
      return (T) result;
    } else if (obj != null) {
      Class<T> cls = (Class<T>) obj.getClass();
      if (isModelClass(cls)) {
        try {
          Constructor<T> constructor = cls.getConstructor();
          T subObj = constructor.newInstance();

          List<Pair<Method, Method>> typeBeans = typeBeans(cls);
          for (Pair<Method, Method> item : typeBeans) {
            item.getRight()
                .invoke(
                    subObj,
                    doDeepSubstitution(
                        substitutionVariables,
                        item.getLeft().invoke(obj),
                        isDns1123Required(item.getLeft())));
          }
          return subObj;
        } catch (NoSuchMethodException
            | InstantiationException
            | IllegalAccessException
            | InvocationTargetException e) {
          throw new RuntimeException(e);
        }
      }
    }
    return obj;
  }

  boolean isDns1123Required(Method method) {
    // value requires to be in DNS1123 if the value is for a name, which is assumed to be
    // name for a kubernetes object
    return LegalNames.isDns1123Required(method.getName().substring(3));
  }

  private static final String MODELS_PACKAGE = V1Pod.class.getPackageName();
  private static final String DOMAIN_MODEL_PACKAGE = Domain.class.getPackageName();

  private boolean isModelClass(Class cls) {
    return cls.getPackageName().startsWith(MODELS_PACKAGE)
        || cls.getPackageName().startsWith(DOMAIN_MODEL_PACKAGE);
  }

  private List<Pair<Method, Method>> typeBeans(Class cls) {
    List<Pair<Method, Method>> results = new ArrayList<>();
    Method[] methods = cls.getMethods();
    for (Method m : methods) {
      if (m.getParameterCount() == 0) {
        String beanName = null;
        if (m.getName().startsWith("get")) {
          beanName = m.getName().substring(3);
        } else if (m.getName().startsWith("is")) {
          beanName = m.getName().substring(2);
        }
        if (beanName != null) {
          try {
            Method set = cls.getMethod("set" + beanName, m.getReturnType());
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
    return translate(substitutionVariables, rawValue, false);
  }

  private String translate(final Map<String, String> substitutionVariables, String rawValue, boolean requiresDns1123) {
    String result = rawValue;
    for (Map.Entry<String, String> entry : substitutionVariables.entrySet()) {
      if (result != null && entry.getValue() != null) {
        result = result.replace(String.format("$(%s)", entry.getKey()),
            requiresDns1123 ? LegalNames.toDns1123LegalName(entry.getValue()) : entry.getValue());
      }
    }
    return result;
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
