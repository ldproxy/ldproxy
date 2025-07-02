/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.collections.schema.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.collections.schema.domain.SchemaConfiguration;
import de.ii.ogcapi.common.domain.QueryParameterProfile;
import de.ii.ogcapi.foundation.domain.ConformanceClass;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.ExtensionRegistry;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.foundation.domain.ProfileExtension.ResourceType;
import de.ii.ogcapi.foundation.domain.SchemaValidator;
import de.ii.ogcapi.foundation.domain.SpecificationMaturity;
import java.util.List;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * @title profile
 * @endpoints Schema
 * @langEn This query parameter supports requesting variations in the representation of data in the
 *     same format, depending on the intended use of the data. If a format does not support the
 *     requested profile, the best match for the requested profile is used depending on the format.
 *     The negotiated profiles are returned in links with `rel` set to `profile`.
 * @langDe Dieser Abfrageparameter unterstützt die Abfrage von Variationen in der Darstellung von
 *     Daten im gleichen Format, je nach der beabsichtigten Verwendung der Daten. Wenn ein Format
 *     das angeforderte Profil nicht unterstützt, wird je nach Format die beste Übereinstimmung für
 *     das angeforderte Profil verwendet. Die ausgehandelten Profile werden in Links zurückgegeben,
 *     wobei `rel` auf `profile` gesetzt ist.
 * @default []
 */
@Singleton
@AutoBind
public class QueryParameterProfileSchema extends QueryParameterProfile implements ConformanceClass {

  @Inject
  public QueryParameterProfileSchema(
      ExtensionRegistry extensionRegistry, SchemaValidator schemaValidator) {
    super(extensionRegistry, schemaValidator);
  }

  @Override
  public String getId(String collectionId) {
    return "profileSchema_" + collectionId;
  }

  @Override
  public boolean matchesPath(String definitionPath) {
    return definitionPath.equals("/collections/{collectionId}/schema");
  }

  @Override
  public List<String> getConformanceClassUris(OgcApiDataV2 apiData) {
    return List.of("http://www.opengis.net/spec/ogcapi-features-5/0.0/conf/profile-parameter");
  }

  @Override
  public Class<? extends ExtensionConfiguration> getBuildingBlockConfigurationType() {
    return SchemaConfiguration.class;
  }

  @Override
  public ResourceType getResourceType() {
    return ResourceType.SCHEMA;
  }

  @Override
  public int getPriority() {
    return 2;
  }

  @Override
  public Optional<SpecificationMaturity> getSpecificationMaturity() {
    return Optional.of(SpecificationMaturity.DRAFT_OGC);
  }
}
