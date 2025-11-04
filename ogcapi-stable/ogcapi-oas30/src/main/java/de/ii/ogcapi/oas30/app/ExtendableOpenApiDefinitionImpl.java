/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.oas30.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import com.google.common.collect.ImmutableList;
import com.google.common.io.Resources;
import de.ii.ogcapi.foundation.domain.ApiMetadata;
import de.ii.ogcapi.foundation.domain.ApiSecurity;
import de.ii.ogcapi.foundation.domain.ApiSecurityInfo;
import de.ii.ogcapi.foundation.domain.ExtensionRegistry;
import de.ii.ogcapi.foundation.domain.ExternalDocumentation;
import de.ii.ogcapi.foundation.domain.FoundationConfiguration;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.oas30.domain.OpenApiExtension;
import de.ii.xtraplatform.auth.domain.Oidc;
import de.ii.xtraplatform.base.domain.AppContext;
import de.ii.xtraplatform.base.domain.AuthConfiguration;
import de.ii.xtraplatform.web.domain.URICustomizer;
import io.swagger.v3.core.util.Json;
import io.swagger.v3.core.util.Yaml;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.Scopes;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.security.SecurityScheme.Type;
import io.swagger.v3.oas.models.servers.Server;
import java.io.IOException;
import java.util.Comparator;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.core.Response;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
@AutoBind
public class ExtendableOpenApiDefinitionImpl implements ExtendableOpenApiDefinition {

  private static Logger LOGGER = LoggerFactory.getLogger(ExtendableOpenApiDefinitionImpl.class);

  private final AuthConfiguration authConfig;
  private final ExtensionRegistry extensionRegistry;
  private final Oidc oidc;
  private final ApiSecurityInfo apiSecurityInfo;

  @Inject
  public ExtendableOpenApiDefinitionImpl(
      AppContext appContext,
      ExtensionRegistry extensionRegistry,
      Oidc oidc,
      ApiSecurityInfo apiSecurityInfo) {
    this.authConfig = appContext.getConfiguration().getAuth();
    this.extensionRegistry = extensionRegistry;
    this.oidc = oidc;
    this.apiSecurityInfo = apiSecurityInfo;
  }

  @Override
  public Response getOpenApi(
      String type, URICustomizer requestUriCustomizer, OgcApiDataV2 apiData) {

    boolean pretty = true;

    try {
      OpenAPI openAPI =
          Json.mapper()
              .readerFor(OpenAPI.class)
              .readValue(
                  Resources.asByteSource(
                          Resources.getResource(
                              ExtendableOpenApiDefinitionImpl.class, "/openapi.json"))
                      .openBufferedStream());

      if (apiData.getAccessControl().filter(ApiSecurity::isEnabled).isPresent()) {
        if (oidc.isEnabled()) {
          Scopes scopes = new Scopes();

          if (!apiData.getAccessControl().get().getScopes().isEmpty()) {
            Map<String, String> activeScopes = apiSecurityInfo.getActiveScopes(apiData);

            activeScopes.forEach(scopes::addString);
          }

          openAPI
              .getComponents()
              .addSecuritySchemes(
                  "Default",
                  new SecurityScheme()
                      .type(Type.OPENIDCONNECT)
                      .openIdConnectUrl(oidc.getConfigurationUri()));
        } else {
          openAPI
              .getComponents()
              .addSecuritySchemes(
                  "Default",
                  new SecurityScheme()
                      .type(SecurityScheme.Type.HTTP)
                      .scheme("bearer")
                      .bearerFormat("JWT"));
        }
      }

      openAPI.servers(
          ImmutableList.of(
              new Server()
                  .url(
                      requestUriCustomizer
                          .copy()
                          .clearParameters()
                          .ensureNoTrailingSlash()
                          .removeLastPathSegment("api")
                          .toString())));

      if (apiData != null) {

        openAPI.getInfo().title(apiData.getLabel());
        if (apiData
            .getExtension(FoundationConfiguration.class)
            .map(FoundationConfiguration::includesSpecificationInformation)
            .orElse(true)) {
          String note =
              "Note: This API is based on API building blocks (e.g., operations, query parameters, or headers) specified in OGC API Standards or drafts of those standards. For more information about OGC API Standards, see [https://ogcapi.ogc.org](https://ogcapi.ogc.org/). Some building blocks of this API can be preliminary and may change in this API, because they are not yet based on a stable specification. The maturity is stated for each building block.";
          openAPI.getInfo().description(apiData.getDescription().orElse(""));
          apiData
              .getDescription()
              .ifPresentOrElse(
                  desc -> openAPI.getInfo().description(String.format("%s\n\n_%s_", desc, note)),
                  () -> openAPI.getInfo().description(note));
        } else {
          apiData.getDescription().ifPresent(desc -> openAPI.getInfo().description(desc));
        }

        if (apiData.getMetadata().isPresent()) {
          ApiMetadata md = apiData.getMetadata().get();
          openAPI.getInfo().version(md.getVersion().orElse("1.0.0"));
          if (md.getContactName().isPresent()
              || md.getContactUrl().isPresent()
              || md.getContactEmail().isPresent()) {
            Contact contact = new Contact();
            md.getContactName().ifPresent(contact::name);
            md.getContactUrl().ifPresent(contact::url);
            md.getContactEmail().ifPresent(contact::email);
            openAPI.getInfo().contact(contact);
          }
          if (md.getEffectiveLicenseName().isPresent()) {
            // license name is required
            License license = new License().name(md.getEffectiveLicenseName().get());
            md.getEffectiveLicenseUrl().ifPresent(license::url);
            openAPI.getInfo().license(license);
          }
        } else {
          // version is required
          openAPI.getInfo().version("1.0.0");
        }

        if (apiData.getExternalDocs().isPresent()) {
          ExternalDocumentation externalDocs = apiData.getExternalDocs().get();
          io.swagger.v3.oas.models.ExternalDocumentation docs =
              new io.swagger.v3.oas.models.ExternalDocumentation().url(externalDocs.getUrl());
          if (externalDocs.getDescription().isPresent())
            docs.description(externalDocs.getDescription().get());
          openAPI.externalDocs(docs);
        }
      }

      // TODO update with examples and details (f enums, lang enums, etc.)

      extensionRegistry.getExtensionsForType(OpenApiExtension.class).stream()
          .sorted(Comparator.comparing(OpenApiExtension::getSortPriority))
          .forEachOrdered(openApiExtension -> openApiExtension.process(openAPI, apiData));

      if (StringUtils.isNotBlank(type) && type.trim().equalsIgnoreCase("yaml")) {
        return Response.ok()
            .entity(pretty ? Yaml.pretty(openAPI) : Yaml.mapper().writeValueAsString(openAPI))
            .type(OpenApiYaml.MEDIA_TYPE.type())
            .build();
      } else {
        return Response.ok()
            .entity(pretty ? Json.pretty(openAPI) : Json.mapper().writeValueAsString(openAPI))
            .type(OpenApiJson.MEDIA_TYPE.type())
            .build();
      }
    } catch (IOException e) {
      throw new RuntimeException("OpenAPI document could not be created", e);
    }
  }
}
