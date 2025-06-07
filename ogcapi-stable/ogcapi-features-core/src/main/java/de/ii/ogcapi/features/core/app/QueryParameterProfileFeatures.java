/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.core.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import de.ii.ogcapi.common.domain.QueryParameterProfile;
import de.ii.ogcapi.features.core.domain.FeaturesCoreConfiguration;
import de.ii.ogcapi.foundation.domain.ConformanceClass;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.ExtensionRegistry;
import de.ii.ogcapi.foundation.domain.FeatureTypeConfigurationOgcApi;
import de.ii.ogcapi.foundation.domain.OgcApi;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.foundation.domain.Profile;
import de.ii.ogcapi.foundation.domain.ProfileExtension.ResourceType;
import de.ii.ogcapi.foundation.domain.SchemaValidator;
import de.ii.ogcapi.foundation.domain.SpecificationMaturity;
import de.ii.ogcapi.foundation.domain.TypedQueryParameter;
import io.swagger.v3.oas.models.media.Schema;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * @title profile
 * @endpoints Features, Feature
 * @langEn This query parameter supports requesting variations in the representation of data in the
 *     same format, depending on the intended use of the data. The supported profiles depend on the
 *     provider schema of the feature collection. If a format does not support the requested
 *     profile, the best match for the requested profile is used depending on the format. The
 *     negotiated profiles are returned in links with `rel` set to `profile`.
 * @langDe Dieser Abfrageparameter unterstützt die Abfrage von Variationen in der Darstellung von
 *     Daten im gleichen Format, je nach der beabsichtigten Verwendung der Daten. Die unterstützten
 *     Profile hängen vom Provider-Schema der Feature Collection ab. Wenn ein Format das
 *     angeforderte Profil nicht unterstützt, wird je nach Format die beste Übereinstimmung für das
 *     angeforderte Profil verwendet. Die ausgehandelten Profile werden in Links zurückgegeben,
 *     wobei `rel` auf `profile` gesetzt ist.
 * @default []
 */
@Singleton
@AutoBind
public class QueryParameterProfileFeatures extends QueryParameterProfile
    implements TypedQueryParameter<List<Profile>>, ConformanceClass {

  @Inject
  public QueryParameterProfileFeatures(
      ExtensionRegistry extensionRegistry, SchemaValidator schemaValidator) {
    super(extensionRegistry, schemaValidator);
  }

  @Override
  public String getId(String collectionId) {
    return "profileFeatures_" + collectionId;
  }

  @Override
  public boolean matchesPath(String definitionPath) {
    return definitionPath.equals("/collections/{collectionId}/items")
        || definitionPath.equals("/collections/{collectionId}/items/{featureId}");
  }

  @Override
  public boolean isEnabledForApi(OgcApiDataV2 apiData) {
    return super.isEnabledForApi(apiData) && !getProfiles(apiData, ResourceType.FEATURE).isEmpty();
  }

  @Override
  public boolean isEnabledForApi(OgcApiDataV2 apiData, String collectionId) {
    return super.isEnabledForApi(apiData, collectionId)
        && !getProfiles(apiData, collectionId, ResourceType.FEATURE).isEmpty();
  }

  @Override
  public List<String> getConformanceClassUris(OgcApiDataV2 apiData) {
    return List.of("http://www.opengis.net/spec/ogcapi-features-5/0.0/conf/profile-parameter");
  }

  @Override
  public Class<? extends ExtensionConfiguration> getBuildingBlockConfigurationType() {
    return FeaturesCoreConfiguration.class;
  }

  @Override
  public int getPriority() {
    return 2;
  }

  @Override
  public List<Profile> parse(
      String value,
      Map<String, Object> typedValues,
      OgcApi api,
      Optional<FeatureTypeConfigurationOgcApi> optionalCollectionData) {
    if (value == null) {
      return List.of();
    }

    Builder<Profile> builder = ImmutableList.builder();

    List<Profile> allProfiles =
        optionalCollectionData
            .map(cd -> getProfiles(api.getData(), cd.getId(), ResourceType.FEATURE))
            .orElseGet(() -> getProfiles(api.getData(), ResourceType.FEATURE));

    Splitter.on(',')
        .trimResults()
        .split(value)
        .forEach(
            profileId ->
                builder.add(
                    allProfiles.stream()
                        .filter(p -> p.getId().equals(profileId))
                        .findFirst()
                        .orElseThrow(
                            () ->
                                new IllegalArgumentException(
                                    String.format(
                                        "Unknown value for parameter '%s': '%s'. Known values are: [ %s ]",
                                        PROFILE,
                                        profileId,
                                        String.join(
                                            ",",
                                            allProfiles.stream().map(Profile::getId).toList()))))));

    return builder.build();
  }

  @Override
  public Optional<SpecificationMaturity> getSpecificationMaturity() {
    return Optional.of(SpecificationMaturity.DRAFT_OGC);
  }

  @Override
  public Schema<?> getSchema(OgcApiDataV2 apiData) {
    return super.getSchema(apiData, ResourceType.FEATURE);
  }

  @Override
  public Schema<?> getSchema(OgcApiDataV2 apiData, String collectionId) {
    return super.getSchema(apiData, collectionId, ResourceType.FEATURE);
  }
}
