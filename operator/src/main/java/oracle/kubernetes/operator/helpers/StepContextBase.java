// Copyright (c) 2019, 2023, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1OwnerReference;
import io.kubernetes.client.openapi.models.V1Pod;
import oracle.kubernetes.operator.Pair;
import oracle.kubernetes.weblogic.domain.model.DomainResource;

import static oracle.kubernetes.weblogic.domain.model.DomainResource.TOKEN_END_MARKER;

public abstract class StepContextBase implements StepContextConstants {
  protected final DomainPresenceInfo info;

  StepContextBase(DomainPresenceInfo info) {
    this.info = info;
  }

  <T> T doDeepSubstitution(final Map<String, String> substitutionVariables, T obj) {
    return doDeepSubstitution(substitutionVariables, obj, false);
  }

  @SuppressWarnings("unchecked")
  private <T> T doDeepSubstitution(final Map<String, String> substitutionVariables, T obj, boolean requiresDns1123) {
    if (obj instanceof Enum) {
      return obj;
    } else if (obj instanceof String) {
      return (T) translate(substitutionVariables, (String) obj, requiresDns1123);
    } else if (obj instanceof List<?> list) {
      List<Object> result = new ArrayList<>();
      for (Object o : list) {
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
            item.right()
                .invoke(
                    subObj,
                    doDeepSubstitution(
                        substitutionVariables,
                        item.left().invoke(obj),
                        isDns1123Required(item.left())));
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

  private boolean isDns1123Required(Method method) {
    // value requires to be in DNS1123 if the value is for a name, which is assumed to be
    // a name for a kubernetes object
    return LegalNames.isDns1123Required(method.getName().substring(3));
  }

  private static final String MODELS_PACKAGE = V1Pod.class.getPackageName();
  private static final String DOMAIN_MODEL_PACKAGE = DomainResource.class.getPackageName();

  private boolean isModelClass(Class<?> cls) {
    return cls.getPackageName().startsWith(MODELS_PACKAGE)
        || cls.getPackageName().startsWith(DOMAIN_MODEL_PACKAGE);
  }

  private List<Pair<Method, Method>> typeBeans(Class<?> cls) {
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
            results.add(new Pair<>(m, set));
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
      if (hasToken(result, entry)) {
        result = result.replace(getToken(entry), getReplacement(requiresDns1123, entry));
      }
    }
    return result;
  }

  private boolean hasToken(String result, Entry<String, String> entry) {
    return result != null
        && result.contains(DomainResource.TOKEN_START_MARKER)
        && entry.getValue() != null;
  }

  private String getToken(Entry<String, String> entry) {
    return String.format("%s%s%s", DomainResource.TOKEN_START_MARKER, entry.getKey(), TOKEN_END_MARKER);
  }

  private String getReplacement(boolean requiresDns1123, Entry<String, String> entry) {
    return requiresDns1123 ? LegalNames.toDns1123LegalName(entry.getValue()) : entry.getValue();
  }

  protected V1ObjectMeta updateForOwnerReference(V1ObjectMeta metadata) {
    if (info != null) {
      DomainResource domain = info.getDomain();
      if (domain != null) {
        V1ObjectMeta domainMetadata = domain.getMetadata();
        metadata.addOwnerReferencesItem(
            new V1OwnerReference()
                .apiVersion(domain.getApiVersion())
                .kind(domain.getKind())
                .name(domainMetadata.getName())
                .uid(domainMetadata.getUid())
                .controller(true));
      }
    }
    return metadata;
  }
}
