/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.core.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.common.hash.Funnel;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.immutables.value.Value;

@JsonDeserialize(using = JsonSchemaDeserializer.class)
public abstract class JsonSchema {

  @SuppressWarnings("UnstableApiUsage")
  public static final Funnel<JsonSchema> FUNNEL =
      (from, into) -> {
        from.getTitle().ifPresent(s -> into.putString(s, StandardCharsets.UTF_8));
        from.getDescription().ifPresent(s -> into.putString(s, StandardCharsets.UTF_8));
        if (from instanceof JsonSchemaString)
          JsonSchemaString.FUNNEL.funnel((JsonSchemaString) from, into);
        else if (from instanceof JsonSchemaNumber)
          JsonSchemaNumber.FUNNEL.funnel((JsonSchemaNumber) from, into);
        else if (from instanceof JsonSchemaInteger)
          JsonSchemaInteger.FUNNEL.funnel((JsonSchemaInteger) from, into);
        else if (from instanceof JsonSchemaBoolean)
          JsonSchemaBoolean.FUNNEL.funnel((JsonSchemaBoolean) from, into);
        else if (from instanceof JsonSchemaObject)
          JsonSchemaObject.FUNNEL.funnel((JsonSchemaObject) from, into);
        else if (from instanceof JsonSchemaArray)
          JsonSchemaArray.FUNNEL.funnel((JsonSchemaArray) from, into);
        else if (from instanceof JsonSchemaNull)
          JsonSchemaNull.FUNNEL.funnel((JsonSchemaNull) from, into);
        else if (from instanceof JsonSchemaTrue)
          JsonSchemaTrue.FUNNEL.funnel((JsonSchemaTrue) from, into);
        else if (from instanceof JsonSchemaFalse)
          JsonSchemaFalse.FUNNEL.funnel((JsonSchemaFalse) from, into);
        else if (from instanceof JsonSchemaGeometry)
          JsonSchemaGeometry.FUNNEL.funnel((JsonSchemaGeometry) from, into);
        else if (from instanceof JsonSchemaRef)
          JsonSchemaRef.FUNNEL.funnel((JsonSchemaRef) from, into);
        else if (from instanceof JsonSchemaOneOf)
          JsonSchemaOneOf.FUNNEL.funnel((JsonSchemaOneOf) from, into);
        else if (from instanceof JsonSchemaAllOf)
          JsonSchemaAllOf.FUNNEL.funnel((JsonSchemaAllOf) from, into);
      };

  public abstract Optional<String> getTitle();

  public abstract Optional<String> getDescription();

  @JsonProperty("default")
  public abstract Optional<Object> getDefault_();

  public abstract Optional<Boolean> getWriteOnly();

  public abstract Optional<Boolean> getReadOnly();

  @JsonIgnore
  @Value.Derived
  public boolean isWriteOnly() {
    return getWriteOnly().orElse(false);
  }

  @JsonIgnore
  @Value.Derived
  public boolean isReadOnly() {
    return getReadOnly().orElse(false);
  }

  @JsonIgnore
  public abstract Optional<String> getCodelistId();

  @JsonProperty("x-ldproxy-codelistUri")
  public abstract Optional<String> getCodelistUri();

  @JsonProperty("x-ogc-role")
  public abstract Optional<String> getRole();

  @JsonProperty("x-ldproxy-embedded-role")
  public abstract Optional<String> getEmbeddedRole();

  @JsonProperty("x-ogc-collectionId")
  public abstract Optional<String> getRefCollectionId();

  @JsonProperty("x-ogc-uriTemplate")
  public abstract Optional<String> getRefUriTemplate();

  @JsonProperty("x-ogc-propertySeq")
  public abstract Optional<Integer> getPropertySeq();

  @JsonIgnore
  @Value.Auxiliary
  public abstract Optional<String> getName();

  @JsonIgnore
  @Value.Default
  @Value.Auxiliary
  public boolean isRequired() {
    return false;
  }

  public JsonSchema accept(JsonSchemaVisitor visitor) {
    return visitor.visit(this);
  }

  public abstract static class Builder {
    public abstract Builder title(String title);

    public abstract Builder title(Optional<String> title);

    public abstract Builder description(String description);

    public abstract Builder description(Optional<String> description);

    public abstract Builder default_(Object default_);

    public abstract Builder default_(Optional<? extends Object> default_);

    public abstract Builder name(String name);

    public abstract Builder isRequired(boolean isRequired);

    public abstract Builder readOnly(Optional<Boolean> readOnly);

    public abstract Builder writeOnly(Optional<Boolean> writeOnly);

    public abstract Builder propertySeq(int propertySeq);

    public abstract Builder propertySeq(Optional<Integer> propertySeq);

    public abstract JsonSchema build();
  }
}
