/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.versioned.features.app;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.transactions.domain.CompositeId;
import de.ii.ogcapi.transactions.domain.MutationStrategy;
import de.ii.ogcapi.transactions.domain.TxAction;
import de.ii.ogcapi.transactions.domain.TxActionType;
import de.ii.ogcapi.versioned.features.domain.VersionedFeaturesConfiguration;
import de.ii.ogcapi.versioned.features.domain.VersionedFeaturesConfiguration.MutationTime;
import de.ii.xtraplatform.features.domain.FeatureSchema;
import de.ii.xtraplatform.features.domain.FeatureTransactions.PropertyUpdate;
import de.ii.xtraplatform.features.domain.SchemaBase;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.core.MediaType;
import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAccessor;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;

/**
 * Strategy that governs mutations on collections where the {@code VERSIONED_FEATURES} building
 * block is enabled. The executor selects it via the inherited {@code isEnabledForApi(apiData,
 * collectionId)} contract; otherwise it falls back to {@code PlainMutationStrategy}.
 *
 * <p>Carries the dispatch decision and the per-action versioning semantics: timestamp capture,
 * retire-and-insert on update/replace, retire-only on delete, no-backdating / no-overlap guards,
 * predecessor/successor maintenance.
 */
@Singleton
@AutoBind
public class VersionedMutationStrategy implements MutationStrategy {

  @Inject
  public VersionedMutationStrategy() {}

  @Override
  public Class<? extends ExtensionConfiguration> getBuildingBlockConfigurationType() {
    return VersionedFeaturesConfiguration.class;
  }

  @Override
  public int priority() {
    return 100;
  }

  @Override
  public Instant resolveMutationTimestamp(
      OgcApiDataV2 apiData,
      TxAction action,
      Instant scopeTimestamp,
      Optional<Instant> ogcMutationDatetimeHeader) {
    MutationTime mode =
        apiData
            .getExtension(VersionedFeaturesConfiguration.class, action.getCollectionId())
            .map(VersionedFeaturesConfiguration::getMutationTime)
            .orElse(null);
    if (mode == null || mode == MutationTime.SERVER) {
      return scopeTimestamp;
    }
    // mutationTime: client. Precedence: body-supplied value > header > 400.
    //
    // - Insert / Replace carry the new version's PRIMARY_INTERVAL_START in the payload, which
    //   the encoder writes verbatim because `insertRoleOverrides` is empty in client mode. The
    //   executor only uses the returned timestamp for the chain check, the insert pre-flight,
    //   and predecessor/successor denorm maintenance.
    //   Returning `scopeTimestamp` as a placeholder keeps those checks conservative — they may
    //   over-approximate in edge cases where the body's start is in the past or future — until
    //   body-side extraction is implemented.
    // - Update modifies properties via the payload; the relevant timestamps (end value, etc.)
    //   live in the resolved propertyUpdates list that `chooseUpdateMode` and
    //   `patchOpenVersion` consume directly. The placeholder returned here is unused on the
    //   RETIRE_IN_PLACE path; on CLONE_AND_PATCH it would propagate to the SQL provider, but
    //   that path is still UnsupportedOperationException.
    // - Delete has no body — the OGC-Mutation-Datetime header is the only canonical source,
    //   so a missing header in client mode genuinely should 400.
    if (action.getType() == TxActionType.DELETE) {
      return ogcMutationDatetimeHeader.orElseThrow(
          () ->
              new BadRequestException(
                  "mutationTime is 'client' on collection '"
                      + action.getCollectionId()
                      + "' but the OGC-Mutation-Datetime header is required for Delete (no"
                      + " body-side timestamp source)."));
    }
    return ogcMutationDatetimeHeader.orElse(scopeTimestamp);
  }

  @Override
  public Map<SchemaBase.Role, Object> insertRoleOverrides(
      OgcApiDataV2 apiData,
      TxAction action,
      Instant mutationTimestamp,
      Optional<String> predecessorIntervalStart) {
    MutationTime mode =
        apiData
            .getExtension(VersionedFeaturesConfiguration.class, action.getCollectionId())
            .map(VersionedFeaturesConfiguration::getMutationTime)
            .orElse(null);
    Map<SchemaBase.Role, Object> overrides = new HashMap<>();
    if (mode == MutationTime.SERVER) {
      // Server-mode versioned insert: every new feature opens a new version at the resolved
      // mutation timestamp. Any client-supplied PRIMARY_INTERVAL_START is replaced and any
      // PRIMARY_INTERVAL_END is dropped so the column lands as NULL ("+infinity" / current).
      overrides.put(SchemaBase.Role.PRIMARY_INTERVAL_START, mutationTimestamp.toString());
      overrides.put(SchemaBase.Role.PRIMARY_INTERVAL_END, null);
    } else if (action.getType() == TxActionType.REPLACE) {
      // Versioned Replace, any mode: the new version is always born open even when the client's
      // body carries a non-null lzi.end. The trailing Update (or a later Replace/Delete) is what
      // closes the version. Mirrors the server-mode "no client-supplied retirement on Insert"
      // rule, applied to Replace's insert half.
      overrides.put(SchemaBase.Role.PRIMARY_INTERVAL_END, null);
    }
    // Client-mode Insert leaves the body's PRIMARY_INTERVAL_START in place — the encoder writes
    // it verbatim. Insert pre-flight, chain-check and predecessor/successor denorm maintenance
    // use the executor's `scopeTimestamp` placeholder; tighter body-side extraction is a
    // future improvement.

    // Denorm PREDECESSOR_INTERVAL_START: when the caller is mid-retire (i.e. we're
    // inserting the successor of an existing version), surface the retired version's start so
    // the new row's denorm pointer is populated. The SQL provider applies the override only if
    // the role is bound to a column on the type's schema mapping — collections without the
    // denorm column ignore the entry.
    predecessorIntervalStart.ifPresent(
        start -> overrides.put(SchemaBase.Role.PREDECESSOR_INTERVAL_START, start));
    return overrides;
  }

  @Override
  public boolean retiresOnReplace() {
    return true;
  }

  @Override
  public boolean retiresOnDelete() {
    return true;
  }

  @Override
  public boolean requiresInsertPreflight() {
    return true;
  }

  @Override
  public boolean disallowsSameFeatureChain(OgcApiDataV2 apiData, TxAction action) {
    // Only server mode shares one timestamp across actions in an atomic transaction; client mode
    // resolves a fresh timestamp per action so chains are validated action-by-action by the
    // standard no-backdating guards.
    MutationTime mode =
        apiData
            .getExtension(VersionedFeaturesConfiguration.class, action.getCollectionId())
            .map(VersionedFeaturesConfiguration::getMutationTime)
            .orElse(null);
    return mode == MutationTime.SERVER;
  }

  @Override
  public UpdateMode chooseUpdateMode(
      OgcApiDataV2 apiData,
      FeatureSchema collectionSchema,
      TxAction action,
      List<PropertyUpdate> updates,
      Instant mutationTimestamp) {
    if (updates.isEmpty()) {
      throw new BadRequestException(
          "Update on versioned collection '"
              + action.getCollectionId()
              + "' has no property changes (no-op).");
    }

    String collectionId = action.getCollectionId();
    boolean touchesStart = false;
    boolean touchesEndSet = false;
    boolean touchesEndClear = false;
    boolean touchesOther = false;
    Set<String> otherPaths = new LinkedHashSet<>();
    for (PropertyUpdate u : updates) {
      Optional<SchemaBase.Role> role = roleAtPath(collectionSchema, u.getPath());
      if (role.isPresent() && role.get() == SchemaBase.Role.PRIMARY_INTERVAL_START) {
        touchesStart = true;
        continue;
      }
      if (role.isPresent() && role.get() == SchemaBase.Role.PRIMARY_INTERVAL_END) {
        if (u.getValue().isEmpty()) {
          touchesEndClear = true;
        } else {
          touchesEndSet = true;
        }
        continue;
      }
      touchesOther = true;
      otherPaths.add(String.join(".", u.getPath()));
    }

    if (touchesStart) {
      throw new BadRequestException(
          "Update on versioned collection '"
              + collectionId
              + "' cannot modify the primary-interval-start property.");
    }
    if (touchesEndClear) {
      throw new BadRequestException(
          "Update on versioned collection '"
              + collectionId
              + "' cannot reopen a retired version by clearing primary-interval-end.");
    }

    if (touchesEndSet) {
      if (!touchesOther) {
        return UpdateMode.RETIRE_IN_PLACE;
      }
      // Retirement combined with other modifications: only allowed when every other path is on
      // the strategy's retireWithModifications whitelist. ALKIS configures `anl` there so a
      // retirement can carry a change-reason code.
      List<String> whitelist =
          apiData
              .getExtension(VersionedFeaturesConfiguration.class, collectionId)
              .map(VersionedFeaturesConfiguration::getRetireWithModifications)
              .orElse(List.of());
      Set<String> allowed = new LinkedHashSet<>(whitelist);
      for (String path : otherPaths) {
        if (!allowed.contains(path)) {
          throw new BadRequestException(
              "Update on versioned collection '"
                  + collectionId
                  + "' combines retirement with a modification to '"
                  + path
                  + "', which is not on the `retireWithModifications` whitelist for this"
                  + " collection.");
        }
      }
      return UpdateMode.RETIRE_IN_PLACE;
    }

    return UpdateMode.CLONE_AND_PATCH;
  }

  @Override
  public CompositeId splitCompositeId(OgcApiDataV2 apiData, String collectionId, String rawId) {
    Optional<VersionedFeaturesConfiguration> cfg =
        apiData.getExtension(VersionedFeaturesConfiguration.class, collectionId);
    String pattern = cfg.map(VersionedFeaturesConfiguration::getCompositeIdPattern).orElse(null);
    if (pattern == null || pattern.isBlank()) {
      return CompositeId.passthrough(rawId);
    }
    Pattern compiled = compileCompositePattern(pattern);
    Matcher m = compiled.matcher(rawId);
    if (!m.matches()) {
      // Doesn't match — treat as a plain canonical id (no composite suffix attached).
      return CompositeId.passthrough(rawId);
    }
    String canonical = m.group("id");
    String startSuffix = m.group("start");
    if (canonical == null || startSuffix == null) {
      return CompositeId.passthrough(rawId);
    }
    String configured =
        cfg.map(VersionedFeaturesConfiguration::getCompositeIdTimestampFormat).orElse(null);
    String fmt = Objects.requireNonNullElse(configured, DEFAULT_TIMESTAMP_FORMAT);
    try {
      return new CompositeId(
          canonical, Optional.of(parseSuffix(startSuffix, compileTimestampFormat(fmt))));
    } catch (DateTimeParseException e) {
      if (configured == null) {
        // No explicit format configured: also accept the compact date default used for
        // DATE-typed intervals (a date stays a date in composite ids).
        try {
          return new CompositeId(
              canonical,
              Optional.of(
                  parseSuffix(
                      startSuffix,
                      compileTimestampFormat(CompositeIdFormatter.DEFAULT_DATE_FORMAT))));
        } catch (DateTimeParseException ignore) {
          // fall through to the error for the primary format
        }
      }
      throw new BadRequestException(
          "Composite id '"
              + rawId
              + "' on collection '"
              + collectionId
              + "' has a timestamp suffix '"
              + startSuffix
              + "' that does not parse against the configured format '"
              + fmt
              + "'.");
    }
  }

  // The suffix is a local date-time for timestamp formats and a local date for date-only
  // formats (DATE-typed primary intervals); both are interpreted in UTC.
  private static Instant parseSuffix(String suffix, DateTimeFormatter formatter) {
    TemporalAccessor ta = formatter.parseBest(suffix, LocalDateTime::from, LocalDate::from);
    return ta instanceof LocalDateTime
        ? ((LocalDateTime) ta).toInstant(ZoneOffset.UTC)
        : ((LocalDate) ta).atStartOfDay(ZoneOffset.UTC).toInstant();
  }

  // Compact ISO-8601 basic-format with explicit T separator and Z marker:
  // 2024-02-15T12:11:56Z → 20240215T121156Z. Matches the NAS-style example
  // DEHE86202002BHuV20240215T121156Z.
  private static final String DEFAULT_TIMESTAMP_FORMAT = "yyyyMMdd'T'HHmmss'Z'";

  private static final ObjectMapper JSON_MAPPER = new ObjectMapper();
  private static final XMLInputFactory XML_INPUT_FACTORY = newSecureXmlInputFactory();

  private static XMLInputFactory newSecureXmlInputFactory() {
    XMLInputFactory f = XMLInputFactory.newInstance();
    // Defensive: disable DTD/external-entity processing on the read-only scan.
    f.setProperty(XMLInputFactory.SUPPORT_DTD, Boolean.FALSE);
    f.setProperty("javax.xml.stream.isSupportingExternalEntities", Boolean.FALSE);
    return f;
  }

  @Override
  public Optional<Instant> extractPrimaryIntervalStart(
      OgcApiDataV2 apiData, FeatureSchema collectionSchema, MediaType mediaType, byte[] body) {
    if (body == null || body.length == 0 || collectionSchema == null) {
      return Optional.empty();
    }
    // Find the property carrying PRIMARY_INTERVAL_START. The strategy reaches it by walking the
    // canonical schema tree top-down; we return the leaf's `alias` (falling back to `name`) as
    // the element / property key to look for in the wire payload.
    Optional<String> aliasOpt = findPrimaryIntervalStartLeafKey(collectionSchema);
    if (aliasOpt.isEmpty()) {
      return Optional.empty();
    }
    String key = aliasOpt.get();
    String raw;
    if (mediaType != null && isXmlLike(mediaType)) {
      // application/xml, plus application/*+xml (notably application/gml+xml which the
      // WfsTransactionParser stamps on Replace actions).
      raw = scanXmlForFirstElement(body, key);
    } else if (mediaType != null && isJsonLike(mediaType)) {
      // application/json, plus application/*+json (e.g. application/ogc-tx+json).
      raw = scanJsonForFirstProperty(body, key);
    } else {
      return Optional.empty();
    }
    if (raw == null || raw.isBlank()) {
      return Optional.empty();
    }
    try {
      return Optional.of(Instant.parse(raw.trim()));
    } catch (DateTimeParseException e) {
      return Optional.empty();
    }
  }

  // Walk the schema's leaf properties for the first one bearing PRIMARY_INTERVAL_START. Returns
  // its alias (or its name if no alias is set) — the element / property key the wire payload
  // uses. Returns empty if no such property exists on the type.
  private static Optional<String> findPrimaryIntervalStartLeafKey(FeatureSchema root) {
    for (FeatureSchema p : root.getProperties()) {
      Optional<String> nested = findPrimaryIntervalStartLeafKey(p);
      if (nested.isPresent()) return nested;
      if (p.getRole().filter(r -> r == SchemaBase.Role.PRIMARY_INTERVAL_START).isPresent()) {
        return Optional.of(p.getAlias().orElseGet(p::getName));
      }
    }
    return Optional.empty();
  }

  // Scan the XML payload for the first element whose local name matches `localName`; return its
  // text content. Returns null when the element is absent or its content is not a single text
  // node. Defensive against malformed XML — any parse error yields null.
  private static String scanXmlForFirstElement(byte[] body, String localName) {
    XMLStreamReader reader = null;
    try {
      reader = XML_INPUT_FACTORY.createXMLStreamReader(new ByteArrayInputStream(body));
      while (reader.hasNext()) {
        int event = reader.next();
        if (event == XMLStreamConstants.START_ELEMENT && localName.equals(reader.getLocalName())) {
          // Collect the element's text — adjacent CHARACTERS/CDATA events until END_ELEMENT.
          StringBuilder sb = new StringBuilder();
          while (reader.hasNext()) {
            int t = reader.next();
            if (t == XMLStreamConstants.CHARACTERS || t == XMLStreamConstants.CDATA) {
              sb.append(reader.getText());
            } else if (t == XMLStreamConstants.END_ELEMENT) {
              return sb.toString();
            } else if (t == XMLStreamConstants.START_ELEMENT) {
              // Unexpected child element: not a pure text leaf — bail out.
              return null;
            }
          }
        }
      }
      return null;
    } catch (Exception e) {
      return null;
    } finally {
      if (reader != null) {
        try {
          reader.close();
        } catch (Exception ignored) {
          // best-effort
        }
      }
    }
  }

  // Scan the JSON payload for the first property whose key matches `propertyName`; return its
  // value as a String. Recurses through arrays/objects depth-first. Returns null on miss or
  // when the value isn't a textual node.
  private static boolean isXmlLike(MediaType mediaType) {
    if (!"application".equalsIgnoreCase(mediaType.getType())) return false;
    String sub = mediaType.getSubtype();
    return "xml".equals(sub) || (sub != null && sub.endsWith("+xml"));
  }

  private static boolean isJsonLike(MediaType mediaType) {
    if (!"application".equalsIgnoreCase(mediaType.getType())) return false;
    String sub = mediaType.getSubtype();
    return "json".equals(sub) || (sub != null && sub.endsWith("+json"));
  }

  private static String scanJsonForFirstProperty(byte[] body, String propertyName) {
    try {
      JsonNode root = JSON_MAPPER.readTree(body);
      return findInJson(root, propertyName);
    } catch (Exception e) {
      return null;
    }
  }

  private static String findInJson(JsonNode node, String key) {
    if (node == null) return null;
    if (node.isObject()) {
      JsonNode v = node.get(key);
      if (v != null && v.isTextual()) return v.asText();
      Iterator<String> fields = node.fieldNames();
      while (fields.hasNext()) {
        String r = findInJson(node.get(fields.next()), key);
        if (r != null) return r;
      }
    } else if (node.isArray()) {
      for (JsonNode child : node) {
        String r = findInJson(child, key);
        if (r != null) return r;
      }
    }
    return null;
  }

  // Compile-cache the regex per pattern string; patterns are configured at API-setup time and
  // rarely change at runtime, so a tiny cache on the strategy is fine.
  private final ConcurrentMap<String, Pattern> compiledPatterns = new ConcurrentHashMap<>();

  private Pattern compileCompositePattern(String pattern) {
    return compiledPatterns.computeIfAbsent(pattern, Pattern::compile);
  }

  private final ConcurrentMap<String, DateTimeFormatter> compiledFormats =
      new ConcurrentHashMap<>();

  private DateTimeFormatter compileTimestampFormat(String format) {
    return compiledFormats.computeIfAbsent(format, DateTimeFormatter::ofPattern);
  }

  // Walk the canonical schema-id path to find the role on the leaf property. Returns empty when
  // any segment cannot be resolved (a defensive fallback — the executor's whitelist check should
  // already have rejected unknown paths).
  private static Optional<SchemaBase.Role> roleAtPath(FeatureSchema root, List<String> path) {
    FeatureSchema current = root;
    for (String segment : path) {
      FeatureSchema next = null;
      for (FeatureSchema p : current.getProperties()) {
        if (segment.equals(p.getName())) {
          next = p;
          break;
        }
      }
      if (next == null) {
        return Optional.empty();
      }
      current = next;
    }
    return current.getRole();
  }
}
