/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.html.domain;

import com.google.common.collect.ImmutableList;
import de.ii.ogcapi.features.html.app.FeatureHtml;
import de.ii.ogcapi.features.html.app.ImmutableFeatureCollectionDetailsView;
import de.ii.ogcapi.features.html.app.ModifiableFeatureHtml;
import de.ii.ogcapi.features.html.app.ModifiablePropertyHtml;
import de.ii.ogcapi.features.html.app.PropertyHtml;
import de.ii.ogcapi.foundation.domain.I18n;
import de.ii.ogcapi.html.domain.NavigationDTO;
import de.ii.xtraplatform.features.domain.FeatureObjectEncoder;
import de.ii.xtraplatform.features.domain.PropertyBase;
import de.ii.xtraplatform.features.domain.SchemaBase.Type;
import de.ii.xtraplatform.streams.domain.OutputStreamToByteConsumer;
import de.ii.xtraplatform.strings.domain.StringTemplateFilters;
import de.ii.xtraplatform.web.domain.MustacheRenderer;
import io.dropwizard.views.common.View;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FeatureEncoderHtml extends FeatureObjectEncoder<PropertyHtml, FeatureHtml> {

  private static final Logger LOGGER = LoggerFactory.getLogger(FeatureEncoderHtml.class);

  private final FeatureTransformationContextHtml transformationContext;

  public FeatureEncoderHtml(FeatureTransformationContextHtml transformationContext) {
    this.transformationContext = transformationContext;
  }

  @Override
  public FeatureHtml createFeature() {
    return ModifiableFeatureHtml.create();
  }

  @Override
  public PropertyHtml createProperty() {
    return ModifiablePropertyHtml.create();
  }

  @Override
  public void onStart(ModifiableContext context) {
    if (transformationContext.isFeatureCollection()
        && context.metadata().getNumberReturned().isPresent()) {
      long returned = context.metadata().getNumberReturned().getAsLong();
      long matched = context.metadata().getNumberMatched().orElse(-1);

      long pages = Math.max(transformationContext.getPage(), 0);
      if (returned > 0 && matched > -1) {
        pages =
            Math.max(
                pages,
                matched / transformationContext.getLimit()
                    + (matched % transformationContext.getLimit() > 0 ? 1 : 0));
      }

      LOGGER.debug("numberMatched {}", matched);
      LOGGER.debug("numberReturned {}", returned);
      LOGGER.debug("pageSize {}", transformationContext.getLimit());
      LOGGER.debug("page {}", transformationContext.getPage());
      LOGGER.debug("pages {}", pages);

      boolean includeLimit = !transformationContext.isQueryExpression();

      ImmutableList.Builder<NavigationDTO> pagination = new ImmutableList.Builder<>();
      ImmutableList.Builder<NavigationDTO> metaPagination = new ImmutableList.Builder<>();
      if (transformationContext.getPage() > 1) {
        pagination
            .add(
                new NavigationDTO(
                    "«",
                    includeLimit
                        ? String.format("limit=%d&offset=%d", transformationContext.getLimit(), 0)
                        : String.format("offset=%d", 0)))
            .add(
                new NavigationDTO(
                    "‹",
                    includeLimit
                        ? String.format(
                            "limit=%d&offset=%d",
                            transformationContext.getLimit(),
                            transformationContext.getOffset() - transformationContext.getLimit())
                        : String.format(
                            "offset=%d",
                            transformationContext.getOffset() - transformationContext.getLimit())));
        metaPagination.add(
            new NavigationDTO(
                "prev",
                includeLimit
                    ? String.format(
                        "limit=%d&offset=%d",
                        transformationContext.getLimit(),
                        transformationContext.getOffset() - transformationContext.getLimit())
                    : String.format(
                        "offset=%d",
                        transformationContext.getOffset() - transformationContext.getLimit())));
      } else {
        pagination.add(new NavigationDTO("«")).add(new NavigationDTO("‹"));
      }

      if (matched > -1) {
        long from = Math.max(1, transformationContext.getPage() - 2);
        long to = Math.min(pages, from + 4);
        if (to == pages) {
          from = Math.max(1, to - 4);
        }
        for (long i = from; i <= to; i++) {
          if (i == transformationContext.getPage()) {
            pagination.add(new NavigationDTO(String.valueOf(i), true));
          } else {
            pagination.add(
                new NavigationDTO(
                    String.valueOf(i),
                    includeLimit
                        ? String.format(
                            "limit=%d&offset=%d",
                            transformationContext.getLimit(),
                            (i - 1) * transformationContext.getLimit())
                        : String.format("&offset=%d", (i - 1) * transformationContext.getLimit())));
          }
        }

        if (transformationContext.getPage() < pages) {
          pagination
              .add(
                  new NavigationDTO(
                      "›",
                      includeLimit
                          ? String.format(
                              "limit=%d&offset=%d",
                              transformationContext.getLimit(),
                              transformationContext.getOffset() + transformationContext.getLimit())
                          : String.format(
                              "offset=%d",
                              transformationContext.getOffset()
                                  + transformationContext.getLimit())))
              .add(
                  new NavigationDTO(
                      "»",
                      includeLimit
                          ? String.format(
                              "limit=%d&offset=%d",
                              transformationContext.getLimit(),
                              (pages - 1) * transformationContext.getLimit())
                          : String.format(
                              "offset=%d", (pages - 1) * transformationContext.getLimit())));
          metaPagination.add(
              new NavigationDTO(
                  "next",
                  includeLimit
                      ? String.format(
                          "limit=%d&offset=%d",
                          transformationContext.getLimit(),
                          transformationContext.getOffset() + transformationContext.getLimit())
                      : String.format(
                          "offset=%d",
                          transformationContext.getOffset() + transformationContext.getLimit())));
        } else {
          pagination.add(new NavigationDTO("›")).add(new NavigationDTO("»"));
        }
      } else {
        int from = Math.max(1, transformationContext.getPage() - 2);
        int to = transformationContext.getPage();
        for (int i = from; i <= to; i++) {
          if (i == transformationContext.getPage()) {
            pagination.add(new NavigationDTO(String.valueOf(i), true));
          } else {
            pagination.add(
                new NavigationDTO(
                    String.valueOf(i),
                    String.format(
                        "limit=%d&offset=%d",
                        transformationContext.getLimit(),
                        (i - 1) * transformationContext.getLimit())));
          }
        }
        if (returned >= transformationContext.getLimit()) {
          pagination.add(
              new NavigationDTO(
                  "›",
                  includeLimit
                      ? String.format(
                          "limit=%d&offset=%d",
                          transformationContext.getLimit(),
                          transformationContext.getOffset() + transformationContext.getLimit())
                      : String.format(
                          "offset=%d",
                          transformationContext.getOffset() + transformationContext.getLimit())));
          metaPagination.add(
              new NavigationDTO(
                  "next",
                  includeLimit
                      ? String.format(
                          "limit=%d&offset=%d",
                          transformationContext.getLimit(),
                          transformationContext.getOffset() + transformationContext.getLimit())
                      : String.format(
                          "offset=%d",
                          transformationContext.getOffset() + transformationContext.getLimit())));
        } else {
          pagination.add(new NavigationDTO("›"));
        }
      }

      transformationContext.collectionView().setPagination(pagination.build());
      transformationContext.collectionView().setMetaPagination(metaPagination.build());

    } else if (transformationContext.isFeatureCollection()) {
      LOGGER.error(
          "Pagination not supported by feature provider, the number of matched items was not provided.");
    }
  }

  @Override
  public void onFeature(FeatureHtml feature) {
    transformationContext
        .featuresHtmlConfiguration()
        .getFeatureTitleTemplate()
        .ifPresent(
            template -> {
              Function<String, String> lookup =
                  pathString ->
                      feature
                          .findPropertyByPath(pathString)
                          .map(PropertyHtml::getFirstValue)
                          .orElse(null);
              String name = StringTemplateFilters.applyTemplate(template, lookup);
              if (Objects.nonNull(name) && !name.isEmpty()) {
                feature.name(name);
              }
            });

    // TODO: generalize as value transformer
    if (transformationContext.getI18n().isPresent()) {
      translateBooleans(
          feature.getProperties(),
          transformationContext.getI18n().get(),
          transformationContext.getLanguage());
    }

    if (!transformationContext.isFeatureCollection()) {
      transformationContext.collectionView().setTitle(feature.getName());
      transformationContext
              .collectionView()
              .breadCrumbs()
              .get(transformationContext.collectionView().breadCrumbs().size() - 1)
              .label =
          feature.getName();
    } else {
      feature.inCollection(true);
    }

    if (transformationContext.isSchemaOrgEnabled()) {
      feature.itemType("http://schema.org/Place");
    }

    transformationContext.collectionView().addFeatures(feature);
  }

  @Override
  public void onEnd(ModifiableContext context) {
    if (transformationContext.isFeatureCollection()) {
      renderView(
          new ImmutableFeatureCollectionView.Builder()
              .from(transformationContext.collectionView())
              .build());

    } else {
      renderView(
          new ImmutableFeatureCollectionDetailsView.Builder()
              .from(transformationContext.collectionView())
              .build());
    }
  }

  private void renderView(View view) {
    // TODO: FeatureTokenEncoderBytes.getOutputStream
    OutputStreamWriter writer = new OutputStreamWriter(new OutputStreamToByteConsumer(this::push));
    try {
      ((MustacheRenderer) transformationContext.mustacheRenderer()).render(view, writer);
      writer.flush();
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  private void transformLinks(List<PropertyHtml> properties) {
    for (int i = 0; i < properties.size(); i++) {
      PropertyHtml property = properties.get(i);
      if ((property.isObject() || property.isArray())
          && property
              .getSchema()
              .flatMap(
                  schema ->
                      schema
                          .getObjectType()
                          .filter(objectType -> Objects.equals(objectType, "Link")))
              .isPresent()) {

        String href =
            property.getNestedProperties().stream()
                .filter(
                    valueProperty ->
                        valueProperty
                            .getSchema()
                            .filter(schema -> schema.getName().equals("href"))
                            .isPresent())
                .findFirst()
                .flatMap(valueProperty -> Optional.ofNullable(valueProperty.getValue()))
                .orElse("");
        String title =
            property.getNestedProperties().stream()
                .filter(
                    valueProperty ->
                        valueProperty
                            .getSchema()
                            .filter(schema -> schema.getName().equals("title"))
                            .isPresent())
                .findFirst()
                .flatMap(valueProperty -> Optional.ofNullable(valueProperty.getValue()))
                .orElse("Link");

        property.type(PropertyBase.Type.VALUE);
        property.value(String.format("<a href=\"%s\">%s</a>", href, title));
      } else {
        transformLinks(property.getNestedProperties());
      }
    }
  }

  private void translateBooleans(
      List<PropertyHtml> properties, I18n i18n, Optional<Locale> language) {
    for (PropertyHtml property : properties) {
      if (property.isValue()) {
        if (Objects.nonNull(property.getValue())
            && property
                .getSchema()
                .filter(schema -> schema.getType() == Type.BOOLEAN)
                .isPresent()) {
          if (property.getValue().matches("[fF](alse|ALSE)?|0")) {
            property.value(i18n.get("false", language));
          } else if (property.getValue().matches("[tT](rue|RUE)?|[\\-\\+]?1")) {
            property.value(i18n.get("true", language));
          }
        }
      } else {
        translateBooleans(property.getNestedProperties(), i18n, language);
      }
    }
  }
}
