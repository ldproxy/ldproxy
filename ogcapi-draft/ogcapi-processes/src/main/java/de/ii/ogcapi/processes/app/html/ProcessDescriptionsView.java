/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.processes.app.html;

import com.google.common.base.Splitter;
import de.ii.ogcapi.foundation.domain.I18n;
import de.ii.ogcapi.html.domain.NavigationDTO;
import de.ii.ogcapi.html.domain.OgcApiView;
import de.ii.ogcapi.processes.domain.model.ProcessDescriptionReduced;
import de.ii.xtraplatform.web.domain.URICustomizer;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.apache.hc.core5.http.NameValuePair;
import org.apache.hc.core5.net.URIBuilder;
import org.immutables.value.Value;

@Value.Immutable
public abstract class ProcessDescriptionsView extends OgcApiView {
  public ProcessDescriptionsView() {
    super("processDescriptions.mustache");
  }

  public abstract List<ProcessDescriptionReduced> processDescriptions();

  @Nullable
  public abstract List<NavigationDTO> pagination();

  @Nullable
  public abstract URI uri();

  @Value.Derived
  public String getPath() {
    String path = uri().getPath();
    return path;
  }

  @Value.Derived
  public Function<String, String> getQueryWithout() {
    return without -> {
      List<String> ignore = Splitter.on(',').trimResults().omitEmptyStrings().splitToList(without);

      try {
        List<NameValuePair> query =
            new URIBuilder(RawQuery())
                .getQueryParams().stream()
                    .filter(kvp -> !ignore.contains(kvp.getName().toLowerCase()))
                    .collect(Collectors.toList());

        if (query.isEmpty()) {
          return "?";
        }
        return '?' + new URIBuilder().setParameters(query).build().getRawQuery() + '&';
      } catch (URISyntaxException e) {
        throw new IllegalStateException(
            String.format("Failed to parse query parameters: '%s'", RawQuery()), e);
      }
    };
  }

  @Value.Derived
  public String RawQuery() {

    return "?" + (uri().getRawQuery() != null ? uri().getRawQuery() + "&" : "");
  }

  @Value.Derived
  public String none() {
    return i18n().get("none", language());
  }

  public abstract URICustomizer uriCustomizer();

  public abstract I18n i18n();

  public abstract Optional<Locale> language();
}
