/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.core.domain;

import com.github.azahnen.dagger.annotations.AutoBind;
import com.google.common.collect.ImmutableList;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import java.math.BigDecimal;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
@AutoBind
public class SchemaGeneratorFeatureCollectionOpenApi implements SchemaGeneratorCollectionOpenApi {

  static final Schema<?> GENERIC =
      new ObjectSchema()
          .required(ImmutableList.of("type", "features"))
          .addProperties("type", new StringSchema()._enum(ImmutableList.of("FeatureCollection")))
          .addProperties(
              "features",
              new ArraySchema()
                  .items(
                      new Schema<>()
                          .$ref(
                              "https://raw.githubusercontent.com/opengeospatial/ogcapi-features/master/core/openapi/ogcapi-features-1.yaml#/components/schemas/featureGeoJSON")))
          .addProperties(
              "links", new ArraySchema().items(new Schema<>().$ref("#/components/schemas/Link")))
          .addProperties(
              "timeStamp",
              new Schema<>()
                  .$ref(
                      "https://raw.githubusercontent.com/opengeospatial/ogcapi-features/master/core/openapi/ogcapi-features-1.yaml#/components/schemas/timeStamp"))
          .addProperties(
              "numberMatched",
              new Schema<>()
                  .$ref(
                      "https://raw.githubusercontent.com/opengeospatial/ogcapi-features/master/core/openapi/ogcapi-features-1.yaml#/components/schemas/numberMatched"))
          .addProperties(
              "numberReturned",
              new Schema<>()
                  .$ref(
                      "https://raw.githubusercontent.com/opengeospatial/ogcapi-features/master/core/openapi/ogcapi-features-1.yaml#/components/schemas/numberReturned"));

  private final SchemaGeneratorOpenApi schemaGeneratorFeature;

  @Inject
  public SchemaGeneratorFeatureCollectionOpenApi(SchemaGeneratorOpenApi schemaGeneratorFeature) {
    this.schemaGeneratorFeature = schemaGeneratorFeature;
  }

  @Override
  public String getSchemaReference() {
    return "https://raw.githubusercontent.com/opengeospatial/ogcapi-features/master/core/openapi/ogcapi-features-1.yaml#/components/schemas/featureCollectionGeoJSON";
  }

  @Override
  public Schema<?> getSchema() {
    return GENERIC;
  }

  @Override
  public String getSchemaReference(String collectionId) {
    return "#/components/schemas/featureCollectionGeoJson_" + collectionId;
  }

  @Override
  public Schema<?> getSchema(OgcApiDataV2 apiData, String collectionId) {
    return new ObjectSchema()
        .required(ImmutableList.of("type", "features"))
        .addProperties("type", new StringSchema()._enum(ImmutableList.of("FeatureCollection")))
        .addProperties(
            "features",
            new ArraySchema()
                .items(
                    new Schema<>().$ref(schemaGeneratorFeature.getSchemaReference(collectionId))))
        .addProperties(
            "links", new ArraySchema().items(new Schema<>().$ref("#/components/schemas/Link")))
        .addProperties(
            "timeStamp",
            new StringSchema()
                .description(
                    "This property indicates the time and date when the response was generated.")
                .format("date-time")
                .example("2017-08-17T08:05:32Z"))
        .addProperties(
            "numberMatched",
            new IntegerSchema()
                .description(
                    "The number of features of the feature type that match the selection parameters like `bbox`.")
                .minimum(BigDecimal.ZERO)
                .example(BigDecimal.valueOf(127))
                .addProperties(
                    "numberReturned",
                    new IntegerSchema()
                        .description(
                            """
                    The number of features in the feature collection.

                    A server may omit this information in a response, if the information about the number of features is not known or difficult to compute.

                    If the value is provided, the value shall be identical to the number of items in the "features" array.""")
                        .minimum(BigDecimal.ZERO)
                        .example(BigDecimal.TEN)));
  }

  @Override
  public String getSchemaReferenceForName(String name) {
    return "#/components/schemas/featureCollectionGeoJson_" + name;
  }

  @Override
  public Schema<?> getSchemaForName(String name) {
    return new ObjectSchema()
        .required(ImmutableList.of("type", "features"))
        .addProperties("type", new StringSchema()._enum(ImmutableList.of("FeatureCollection")))
        .addProperties(
            "features",
            new ArraySchema()
                .items(new Schema<>().$ref(schemaGeneratorFeature.getSchemaReference(name)))
                .addProperties(
                    "links",
                    new ArraySchema().items(new Schema<>().$ref("#/components/schemas/Link")))
                .addProperties(
                    "timeStamp",
                    new Schema<>()
                        .$ref(
                            "https://raw.githubusercontent.com/opengeospatial/ogcapi-features/master/core/openapi/ogcapi-features-1.yaml#/components/schemas/timeStamp")))
        .addProperties(
            "numberMatched",
            new Schema<>()
                .$ref(
                    "https://raw.githubusercontent.com/opengeospatial/ogcapi-features/master/core/openapi/ogcapi-features-1.yaml#/components/schemas/numberMatched"))
        .addProperties(
            "numberReturned",
            new Schema<>()
                .$ref(
                    "https://raw.githubusercontent.com/opengeospatial/ogcapi-features/master/core/openapi/ogcapi-features-1.yaml#/components/schemas/numberReturned"));
  }
}
