// Copyright (c) 2019, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.model;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonPatchBuilder;
import jakarta.json.JsonValue;

/**
 * A class which can create JSON patches from the difference of two objects.
 * @param <T> the class of the object to patch
 */
@SuppressWarnings("SameParameterValue")
class ObjectPatch<T> {
  private final List<FieldPatch<T>> fields = new ArrayList<>();
  private Supplier<T> constructor;

  @SuppressWarnings("unused")
  static <S> ObjectPatch<S> createObjectPatch(Class<S> forClass) {
    return new ObjectPatch<>();
  }

  /**
   * Supplies a constructor for the case where a source object is allowed to be null.
   * @param constructor a constructor for a default object of the appropriate type
   * @return this object
   */
  ObjectPatch<T> withConstructor(Supplier<T> constructor) {
    this.constructor = constructor;
    return this;
  }

  ObjectPatch<T> withIntegerField(String fieldName, Function<T,Integer> getter) {
    fields.add(new IntegerField<>(fieldName, getter));
    return this;
  }

  ObjectPatch<T> withBooleanField(String fieldName, Function<T,Boolean> getter) {
    fields.add(new BooleanField<>(fieldName, getter));
    return this;
  }

  ObjectPatch<T> withStringField(String fieldName, Function<T,String> getter) {
    fields.add(new StringField<>(fieldName, getter));
    return this;
  }

  ObjectPatch<T> withDateTimeField(String fieldName, Function<T,OffsetDateTime> getter) {
    fields.add(new DateTimeField<>(fieldName, getter));
    return this;
  }

  <E> ObjectPatch<T> withEnumField(String fieldName, Function<T,E> getter) {
    fields.add(new EnumField<>(fieldName, getter));
    return this;
  }

  <O> ObjectPatch<T> withObjectField(String fieldName, Function<T,O> getter, ObjectPatch<O> objectPatch) {
    fields.add(new ObjectField<>(fieldName, getter, objectPatch));
    return this;
  }

  ObjectPatch<T> withListField(String fieldName, Function<T,List<String>> getter) {
    fields.add(new StringListField<>(fieldName, getter));
    return this;
  }

  <P extends PatchableComponent<P>> ObjectPatch<T> withListField(
        String fieldName, ObjectPatch<P> objectPatch, Function<T,List<P>> getter) {
    fields.add(new ObjectListField<>(fieldName, objectPatch, getter));
    return this;
  }

  void createPatch(JsonPatchBuilder builder, String parentPath, @Nullable T oldValue, T newValue) {
    T startValue = getStartValue(builder, parentPath, oldValue);

    for (FieldPatch<T> field : fields) {
      field.patchField(builder, parentPath, startValue, newValue);
    }
  }

  private T getStartValue(JsonPatchBuilder builder, String parentPath, @Nullable T oldValue) {
    if (oldValue != null) {
      return oldValue;
    }

    if (constructor == null) {
      throw new RuntimeException("No constructor supplied for null object");
    }
    builder.add(parentPath, JsonValue.EMPTY_JSON_OBJECT);
    return constructor.get();
  }

  private void replaceItem(JsonPatchBuilder builder, String parent, T oldItem, T newItem) {
    for (FieldPatch<T> field : fields) {
      field.patchField(builder, parent, oldItem, newItem);
    }
  }

  private void addItem(JsonPatchBuilder builder, String parent, T newItem) {
    builder.add(parent + "/-", createObjectBuilder(newItem).build());
  }

  private JsonObjectBuilder createObjectBuilder(T newItem) {
    JsonObjectBuilder objectBuilder = Json.createObjectBuilder();
    for (FieldPatch<T> field : fields) {
      field.addToObject(objectBuilder, newItem);
    }
    return objectBuilder;
  }

  abstract static class FieldPatch<T> {
    private final String fieldName;

    FieldPatch(String fieldName) {
      this.fieldName = fieldName;
    }

    abstract void addToObject(JsonObjectBuilder builder, T instance);

    abstract void patchField(JsonPatchBuilder builder, String parent, T oldItem, T newItem);

    String getPath(String parent) {
      return parent + "/" + fieldName;
    }
  }

  abstract static class ScalarFieldPatch<T, S> extends FieldPatch<T> {

    private final String name;
    private final Function<T, S> getter;

    ScalarFieldPatch(String name, Function<T, S> getter) {
      super(name);
      this.name = name;
      this.getter = getter;
    }

    @Override
    public void addToObject(JsonObjectBuilder builder, T instance) {
      Optional.ofNullable(getter.apply(instance)).ifPresent(c -> addToObject(builder, name, c));
    }

    abstract void addToObject(JsonObjectBuilder builder, String name, S value);

    @Override
    public void patchField(JsonPatchBuilder builder, String parent, T oldItem, T newItem) {
      patchFieldValue(builder, getPath(parent), getter.apply(oldItem), getter.apply(newItem));
    }

    private void patchFieldValue(JsonPatchBuilder builder, String path, S oldValue, S newValue) {
      if (Objects.equals(oldValue, newValue)) {
        return;
      }

      if (oldValue == null) {
        addField(builder, path, newValue);
      } else if (newValue == null) {
        builder.remove(path);
      } else {
        replaceField(builder, path, oldValue, newValue);
      }
    }

    abstract void replaceField(JsonPatchBuilder builder, String path, S oldValue, S newValue);

    abstract void addField(JsonPatchBuilder builder, String path, S newValue);
  }

  static class IntegerField<T> extends ScalarFieldPatch<T,Integer> {

    IntegerField(String name, Function<T, Integer> getter) {
      super(name, getter);
    }

    @Override
    void addToObject(JsonObjectBuilder builder, String name, Integer value) {
      builder.add(name, value);
    }

    @Override
    void replaceField(JsonPatchBuilder builder, String path, Integer oldValue, Integer newValue) {
      builder.replace(path, newValue);
    }

    @Override
    void addField(JsonPatchBuilder builder, String path, Integer newValue) {
      builder.add(path, newValue);
    }
  }

  static class BooleanField<T> extends ScalarFieldPatch<T,Boolean> {

    BooleanField(String name, Function<T, Boolean> getter) {
      super(name, getter);
    }

    @Override
    void addToObject(JsonObjectBuilder builder, String name, Boolean value) {
      builder.add(name, value);
    }

    @Override
    void replaceField(JsonPatchBuilder builder, String path, Boolean oldValue, Boolean newValue) {
      builder.replace(path, newValue);
    }

    @Override
    void addField(JsonPatchBuilder builder, String path, Boolean newValue) {
      builder.add(path, newValue);
    }
  }

  static class StringField<T> extends ScalarFieldPatch<T,String> {

    StringField(String name, Function<T, String> getter) {
      super(name, getter);
    }

    @Override
    void addToObject(JsonObjectBuilder builder, String name, String value) {
      builder.add(name, value);
    }

    @Override
    void replaceField(JsonPatchBuilder builder, String path, String oldValue, String newValue) {
      builder.replace(path, newValue);
    }

    @Override
    void addField(JsonPatchBuilder builder, String path, String newValue) {
      builder.add(path, newValue);
    }
  }

  static class DateTimeField<T> extends StringField<T> {
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    DateTimeField(String name, Function<T, OffsetDateTime> getter) {
      super(name, a -> toString(getter.apply(a)));
    }

    private static String toString(OffsetDateTime dateTime) {
      return Optional.ofNullable(dateTime).map(DATE_FORMAT::format).orElse(null);
    }
  }

  static class EnumField<T,E> extends StringField<T> {
    EnumField(String name, Function<T, E> getter) {
      super(name, a -> getter.apply(a).toString());
    }
  }

  static class ObjectField<T, O> extends ScalarFieldPatch<T,O> {

    private final ObjectPatch<O> objectPatch;

    ObjectField(String fieldName, Function<T, O> getter, ObjectPatch<O> objectPatch) {
      super(fieldName, getter);
      this.objectPatch = objectPatch;
    }

    @Override
    public void addToObject(JsonObjectBuilder builder, String name, O value) {
      builder.add(name, objectPatch.createObjectBuilder(value));
    }

    @Override
    void replaceField(JsonPatchBuilder builder, String path, O oldValue, O newValue) {
      objectPatch.replaceItem(builder, path, oldValue, newValue);
    }

    @Override
    void addField(JsonPatchBuilder builder, String path, O newValue) {
      builder.add(path, objectPatch.createObjectBuilder(newValue).build());
    }
  }

  static class ObjectListField<T, P extends PatchableComponent<P>> extends FieldPatch<T> {

    private final ObjectPatch<P> objectPatch;
    private final Function<T,List<P>> getter;
    private final String fieldName;

    ObjectListField(String fieldName, ObjectPatch<P> objectPatch, Function<T, List<P>> getter) {
      super(fieldName);
      this.fieldName = fieldName;
      this.objectPatch = objectPatch;
      this.getter = getter;
    }

    @Override
    public void addToObject(JsonObjectBuilder builder, T newItem) {
      List<P> contents = getter.apply(newItem);
      if (contents.isEmpty()) {
        return;
      }
      
      JsonArrayBuilder arrayBuilder = Json.createArrayBuilder();
      contents.stream().map(objectPatch::createObjectBuilder).forEach(arrayBuilder::add);
      builder.add(fieldName, arrayBuilder.build());
    }

    @Override
    public void patchField(JsonPatchBuilder builder, String parent, T oldItem, T newItem) {
      P[] oldItems = getListField(oldItem);
      P[] newItems = getListField(newItem);
      List<Disposition> disposition
            = Arrays.stream(oldItems).map(c -> getDisposition(c, newItems)).collect(Collectors.toList());

      for (int i = 0; i < oldItems.length; i++) {
        if (disposition.get(i).type == DispositionType.UPDATE) {
          objectPatch.replaceItem(
              builder, getPath(parent) + "/" + i, oldItems[i], newItems[disposition.get(i).newIndex]);
        }
      }

      for (int i = disposition.size() - 1; i >= 0; i--) {
        if (disposition.get(i).type == DispositionType.REMOVE) {
          removePatch(builder, getPath(parent), i);
        }
      }

      if (oldItems.length == 0 && newItems.length != 0) {
        builder.add(getPath(parent), JsonValue.EMPTY_JSON_ARRAY);
      }
      
      for (int j = 0; j < newItems.length; j++) {
        if (Disposition.shouldAdd(disposition, j)) {
          objectPatch.addItem(builder, getPath(parent), newItems[j]);
        }
      }
    }

    @SuppressWarnings("unchecked")
    private P[] getListField(T item) {
      return (P[]) getter.apply(item).toArray(new PatchableComponent[0]);
    }

    Disposition getDisposition(P oldItem, P[] newItems) {
      for (int i = 0; i < newItems.length; i++) {
        if (oldItem.equals(newItems[i])) {
          return Disposition.retain(i);
        } else if (newItems[i].isPatchableFrom(oldItem)) {
          return Disposition.update(i);
        }
      }

      return Disposition.remove();
    }

    void removePatch(JsonPatchBuilder builder, String parent, int index) {
      builder.remove(parent + "/" + index);
    }

  }

  enum DispositionType {
    REMOVE, UPDATE, EXISTS
  }

  static class Disposition {
    private final DispositionType type;
    private final int newIndex;

    Disposition(DispositionType type, int newIndex) {
      this.type = type;
      this.newIndex = newIndex;
    }

    static Disposition remove() {
      return new Disposition(DispositionType.REMOVE, Integer.MIN_VALUE);
    }

    static Disposition update(int newIndex) {
      return new Disposition(DispositionType.UPDATE, newIndex);
    }

    static Disposition retain(int newIndex) {
      return new Disposition(DispositionType.EXISTS, newIndex);
    }

    static boolean shouldAdd(List<Disposition> dispositions, int newIndex) {
      return dispositions.stream().noneMatch(d -> d.newIndex == newIndex);
    }
  }

  static class StringListField<T> extends FieldPatch<T> {

    private final Function<T,List<String>> getter;
    private final String fieldName;

    StringListField(String fieldName, Function<T, List<String>> getter) {
      super(fieldName);
      this.fieldName = fieldName;
      this.getter = getter;
    }

    @Override
    public void addToObject(JsonObjectBuilder builder, T newItem) {
      List<String> contents = getter.apply(newItem);
      if (contents.isEmpty()) {
        return;
      }

      JsonArrayBuilder arrayBuilder = Json.createArrayBuilder();
      contents.forEach(arrayBuilder::add);
      builder.add(fieldName, arrayBuilder.build());
    }

    @Override
    public void patchField(JsonPatchBuilder builder, String parent, T oldItem, T newItem) {
      List<String> oldItems = getter.apply(oldItem);
      List<String> newItems = getter.apply(newItem);
      List<String> addedItems = getDifference(newItems, oldItems);
      List<String> removedItems = getDifference(oldItems, newItems);

      removedItems.forEach(e -> removeFromList(builder, parent, oldItems.indexOf(e)));
      addedItems.forEach(e -> addToList(builder, parent, e));
    }

    List<String> getDifference(List<String> start, List<String> toRemove) {
      ArrayList<String> difference = new ArrayList<>(start);
      difference.removeAll(toRemove);
      return difference;
    }

    private void removeFromList(JsonPatchBuilder builder, String parent, int index) {
      builder.remove(getPath(parent) + "/" + index);
    }

    private void addToList(JsonPatchBuilder builder, String parent, String entry) {
      builder.add(getPath(parent) + "/-", entry);
    }

  }
}
