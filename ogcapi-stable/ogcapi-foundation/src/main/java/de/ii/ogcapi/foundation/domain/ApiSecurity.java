/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.foundation.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.common.collect.Sets;
import de.ii.ogcapi.foundation.domain.PermissionGroup.Base;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.immutables.value.Value;

/**
 * @langEn Access control for all API operations (combination of endpoint and HTTP method).
 *     <p>#### Permissions
 *     <p>Access control is based on permissions and permission groups.
 *     <p>Permissions are a combination of a group prefix (see below) and an OpenAPI operation id
 *     (without any prefix), for example `data:getItems` or `tiles:getTile`. These can be used if a
 *     more fine-grained control is needed in comparison to permission groups.
 *     <p>#### Permission groups
 *     <p>These are the **predefined main permission groups**, every operation/permission is
 *     contained in exactly one main group:
 *     <p><code>
 * - `noaccess`: no access
 * - `discover`: access API landing pages, conformance declarations and OpenAPI definitions
 * - `collections:read`: access feature collection metadata
 * - `data:read`: access and query features
 * - `data:write`: mutate features
 * - `tiles:read`: access tiles
 * - `styles:read`: access styles and their metadata
 * - `styles:write`: mutate styles and update their metadata
 * - `resources:read`: access file resources
 * - `resources:write`: mutate file resources
 * - `search:read`: access stored queries and their parameters
 * - `search:write`: mutate stored queries
 * - `routes:read`: access stored routes and their definition
 * - `routes:write`: compute and store routes, delete stored routes
 * </code>
 *     <p>These are the **predefined parent permission groups** (convenient unions of main groups):
 *     <p><code>
 * - `collections`: includes `collections:read`
 * - `data`: includes `data:read` and `data:write`
 * - `tiles`: includes `tiles:read`
 * - `styles`: includes `styles:read` and `styles:write`
 * - `resources`: includes `resources:read` and `resources:write`
 * - `search`: includes `search:read` and `search:write`
 * - `routes`: includes `routes:read` and `routes:write`
 * </code>
 *     <p>These are the **predefined base permission groups** (convenient unions of main groups):
 *     <p><code>
 * - `read`: includes `discover`, `collections:read`, `data:read`, `tiles:read`, `styles:read`, `resources:read`, `search:read` and `routes:read`
 * - `write`: includes `data:write`, `styles:write`, `resources:write`, `search:write` and `routes:write`
 * </code>
 *     <p>**Custom permission groups** are defined in `groups`, they may contain permissions and/or
 *     predefined permission groups.
 *     <p>The special **permission group `public`** defines the list of permissions and/or
 *     predefined permission groups that every user possesses, if authenticated or not. The default
 *     for `public` is `[read]`. If `public` should have no permissions at all, keep in mind that an
 *     empty list has no effect in this case. Use `[noaccess]` instead.
 *     <p>#### Data-specific permissions
 *     <p>The permissions groups and permissions described above will permit access to any API and
 *     collection. To restrict the access to specific APIs or collections, a suffix can be added to
 *     permission groups and permissions, for example `read::daraa` or
 *     `data:getItems::daraa:AeronauticSrf`.
 *     <p>#### Scopes
 *     <p>*OAuth2 Scopes* are an optional additional authorization layer. They are typically used
 *     when access to an API is granted to a third-party application on behalf of a user. Scopes
 *     then allow to limit which of the users permissions should be granted to the application. The
 *     application would request the scopes it needs and the user would be presented a consent form
 *     where he can choose the scopes he wishes to grant.
 *     <p>Scopes are disabled by default and can be enabled by setting the `scopes` option. The
 *     value is a list of the permission group types that should be used as scopes. That allows to
 *     set the granularity of scopes, since presenting too many scopes in a consent form might be
 *     overwhelming and all the enabled scopes have to actually exist in the identity provider.
 *     <p>For example setting `scopes` to `[BASIC]` would enable the scopes `read` and `write`. When
 *     a user then grants the `write` scope to an application, that does not automatically mean the
 *     application is allowed to write anything. What the application can write is still defined by
 *     the users permissions. But not granting the `write` scope is an easy way to prohibit any
 *     write access, even if the user has such permissions.
 *     <p>#### Authentication and authorization
 *     <p>To support authenticated users, a bearer token has to be included in the `Authorization`
 *     header in requests to the API. Validation and evaluation of these tokens has to be configured
 *     in the [global configuration](../application/20-configuration/README.md).
 *     <p>To determine if a user is authorized to perform the requested operation, the following
 *     steps are executed:
 *     <p><code>
 * 1. If the operation is covered by the `public` group, authorization is granted, even if no token or an invalid token were provided. (Then jump to 6.)
 * 2. If no token or an invalid token (wrong signature or expired) are provided, authorization is rejected.
 * 3. If `audience` is non-empty and does not intersect the audience claim of the provided token, authorization is rejected.
 * 4. If `scopes` is non-empty and the scope claim of the provided token does not contain at least one permission group that covers
 *    the requested operation, authorization is rejected.
 * 5. If the permissions claim of the provided token does not contain at least one permission, predefined permission group or custom
 *    permission group that covers the requested operation, authorization is rejected.
 * 6. If `policies` is enabled and the PDP returns `Deny`, authorization is rejected.
 * </code>
 * @langDe Absicherung für alle API Operationen (Kombination aus Endpunkt und HTTP-Methode).
 *     <p>#### Berechtigungen
 *     <p>Die Absicherung basiert auf Berechtigungen und Berechtigungsgruppen.
 *     <p>Berechtigungen sind eine Kombination aus Gruppen-Präfix (siehe unten) und einer OpenAPI
 *     Operation-Id (ohne jeglichen Präfix), z.B. `data:getItems` oder `tiles:getTile`. Diese können
 *     verwendet werden, wenn eine fein-granularere Absicherung benötigt wird, als sie mit
 *     Berechtigungsgruppen möglich ist.
 *     <p>#### Berechtigungsgruppen
 *     <p>Das sind die **vordefinierten Main-Berechtigungsgruppen**, jede Operation/Berechtigung ist
 *     in genau einer Main-Gruppe enthalten:
 *     <p><code>
 * - `noaccess`: Kein Zugriff
 * - `discover`: Lesen von API Landing Pages, Conformance Declarations und OpenAPI Definitionen
 * - `collections:read`: Lesen von Metadaten zu Feature Collections
 * - `data:read`: Lesen und Abfragen von Features
 * - `data:write`: Ändern von Features
 * - `tiles:read`: Lesen von Tiles
 * - `styles:read`: Lesen von Styles und deren Metadaten
 * - `styles:write`: Ändern von Styles und deren Metadaten
 * - `resources:read`: Lesen von Dateiressourcen
 * - `resources:write`: Ändern von Dateiressourcen
 * - `search:read`: Lesen von Stored Queries und deren Parameter
 * - `search:write`: Ändern von Stored Queries
 * - `routes:read`: Lesen von gespeicherten Routen und deren Definition
 * - `routes:write`: Berechnen und Speichern von Routen, Löschen gespeicherter Routen
 * </code>
 *     <p>Das sind die **vordefinierten Parent-Berechtigungsgruppen** (komfortable Vereinigungen von
 *     Main-Gruppen):
 *     <p><code>
 * - `collections`: enthält `collections:read`
 * - `data`: enthält `data:read` und `data:write`
 * - `tiles`: enthält `tiles:read`
 * - `styles`: enthält `styles:read` und `styles:write`
 * - `resources`: enthält `resources:read` und `resources:write`
 * - `search`: enthält `search:read` und `search:write`
 * - `routes`: enthält `routes:read` und `routes:write`
 * </code>
 *     <p>Das sind die **vordefinierten Base-Berechtigungsgruppen** (komfortable Vereinigungen von
 *     Main-Gruppen):
 *     <p><code>
 * - `read`: enthält `discover`, `collections:read`, `data:read`, `tiles:read`, `styles:read`, `resources:read`, `search:read` und `routes:read`
 * - `write`: enthält `data:write`, `styles:write`, `resources:write`, `search:write` und `routes:write`
 * </code>
 *     <p>**Benutzerdefinierte Berechtigungsgruppen** werden in `groups` definiert, sie können
 *     Berechtigungen und/oder vordefinierte Berechtigungsgruppen enthalten.
 *     <p>Die spezielle **Berechtigungsgruppe `public`** definiert die Liste der Berechtigungen
 *     und/oder vordefinierten Berechtigungsgruppen, die jeder Benutzer besitzt, ob angemeldet oder
 *     nicht. Der Default für `public` ist `[read]`. Soll `public` überhaupt keine Berechtigungen
 *     haben, ist zu beachten, dass eine leere Liste in diesem Fall keine Wirkung hat. Stattdessen
 *     muss `[noaccess]` verwendet werden.
 *     <p>#### Daten-spezifische Berechtigungen
 *     <p>Die oben beschriebenen Berechtigungen gewähren Zugriff zu jeder API und Collection. Um den
 *     Zugriff auf bestimmte APIs oder Collections einzuschränken, kann ein Suffix zu
 *     Berechtigungsgruppen und Berechtigungen hinzugefügt werden, z.B. `read::daraa` oder
 *     `data:getItems::daraa:AeronauticSrf`.
 *     <p>#### Scopes
 *     <p>*OAuth2 Scopes* sind ein optionaler zusätzlicher Autorisierungs-Layer. Sie werden
 *     typischerweise verwendet wenn einer Fremd-Applikation im Namen eines Users Zugriff auf eine
 *     API gewährt werden soll. Scopes erlauben es dann zu beschränken welche der Berechtigungen des
 *     Users der Applikation gewährt werden sollen. Die Applikation würde die benötigten Scopes
 *     anfragen und dem User würde ein Einwilligungs-Formular präsentiert in dem er die Scopes
 *     auswählen kann, die er gewähren will.
 *     <p>Scopes sind standardmäßig deaktiviert und können durch Setzen der `scopes` Option
 *     aktiviert werden. Der Wert ist eine Liste von Berechtigungsgruppen-Typen die als Scopes
 *     verwendet werden sollen. Das erlaubt die Granularität der Scopes festzulegen, da zu viele
 *     Scopes in einem Einwilligungs-Formular überwältigen können und alle aktivierten Scopes auch
 *     im Identity-Provider existieren müssen.
 *     <p>Zum Beispiel würde das Setzen von `scopes` auf `[BASIC]` die Scopes `read` und `write`
 *     aktivieren. Wenn ein User einer Applikation dann den Scope `write` gewähren würde, heißt das
 *     nicht automatisch, dass die Applikation irgendetwas schreiben darf. Was die Applikation
 *     schreiben darf wird immer noch durch die Berechtigungen des Users beschränkt. Aber den Scope
 *     `write` nicht zu gewähren ist ein einfacher Weg jeglichen Schreibzugriff zu verbieten, auch
 *     wenn der User entsprechende Rechte hat.
 *     <p>#### Authentifizierung and Autorisierung
 *     <p>Um authentifizierte Benutzer zu unterstützen, muss ein Bearer-Token im
 *     `Authorization`-Header in Anfragen an die API inkludiert werden. Die Validierung und
 *     Auswertung dieser Tokens muss in der [globalen
 *     Konfiguration](../application/20-configuration/README.md) konfiguriert werden.
 *     <p>Um zu bestimmen ob ein Benutzer autorisiert ist die angefragte Operation auszuführen,
 *     werden die folgenden Schritte durchgeführt:
 *     <p><code>
 * 1. Wenn die Operation von der `public`-Gruppe abgedeckt ist, wird die Autorisierung gewährt, auch wenn kein Token oder ein invalides
 *    Token bereitgestellt wurden. (Dann Sprung zu 6.)
 * 2. Wenn kein Token oder ein invalides Token (falsche Signatur oder abgelaufen) bereitgestellt wurden, wird die Autorisierung verweigert.
 * 3. Wenn `audience` nicht leer ist und sich nicht mit dem Audience-Claim des gegebenen Tokens überschneidet, wird die Autorisierung verweigert.
 * 4. Wenn `scopes` nicht leer ist und der Scope-Claim des gegebenen Tokens nicht mindestens eine Berechtigungsgruppe enthält, die die Operation
 *    abdeckt, wird die Autorisierung verweigert.
 * 5. Wenn der Permissions-Claim des gegebenen Tokens nicht mindestens eine Berechtigung, vordefinierte
 *    Berechtigungsgruppe oder benutzerdefinierte Berechtigungsgruppe enthält, die die Operation
 *    abdeckt, wird die Autorisierung verweigert.
 * 6. Wenn `policies` aktiviert ist und der PDP `Deny` zurück gibt, wird die Autorisierung verweigert.
 * </code>
 */
@Value.Immutable
@JsonDeserialize(builder = ImmutableApiSecurity.Builder.class)
public interface ApiSecurity {

  /**
   * @langEn ##### Policies
   * @langDe ##### Policies
   */
  @Value.Immutable
  @JsonDeserialize(builder = ImmutablePolicies.Builder.class)
  interface Policies {

    /**
     * @langEn Enable an additional authorization layer using a *Policy Decision Point* defined in
     *     the [global configuration](../application/20-configuration/README.md).
     * @langDe Aktiviert einen zusätzlichen Autorisierungs-Layer mittels eines *Policy Decision
     *     Point*, der in der [globalen Konfiguration](../application/20-configuration/README.md)
     *     definiert wird.
     * @default false
     * @since v3.5
     */
    @Nullable
    Boolean getEnabled();

    /**
     * @langEn Add the given attributes to the request sent to the *Policy Decision Point*. Keys are
     *     attribute ids, values are single key objects using either `constant` for a fixed string ,
     *     `property` for a property path or `parameter` for a query parameter. Attributes using
     *     `property` are only relevant for operations involving features. May be defined per
     *     collection.
     * @langDe Fügt die gegebenen Attribute dem Request an den *Policy Decision Point* hinzu. Keys
     *     sind Attribut-Ids, Werte sind Objekte mit einem einzelnen Key, entweder `constant` für
     *     feste Strings, `property` für Property-Pfade oder `parameter` für Query-Parameter.
     *     Attribute mit `property` sind nur für Operationen relevant die Features involvieren. Kann
     *     pro Collection definiert werden.
     * @default {}
     * @since v3.5
     */
    Map<String, PolicyAttribute> getAttributes();

    /**
     * @langEn Applies the given attributes of obligations returned by the *Policy Decision Point*.
     *     Keys are attribute ids, values are single key objects using `parameter` for a query
     *     parameter. Parameters defined in an obligation will overwrite parameters in the request
     *     with the exception if `filter`, which is merged using `AND`, May be defined per
     *     collection.
     * @langDe Wendet die angegebenen Attribute aus Obligations an, die der *Policy Decision Point*
     *     zurückgibt. Keys sind Attribut-Ids, Werte sind Objekte mit einem einzelnen Key
     *     `parameter` für Query-Parameter. Parameter die in einer Obligation definiert sind
     *     überschreiben Parameter im Request, mit der Ausnahme von `filter`, das mit `AND`
     *     zusammengeführt wird. Kann pro Collection definiert werden.
     * @default {}
     * @since v3.5
     */
    Map<String, PolicyAttribute> getObligations();

    @JsonIgnore
    @Value.Derived
    default boolean isEnabled() {
      return !Objects.equals(getEnabled(), false);
    }
  }

  @Value.Immutable
  @JsonDeserialize(builder = ImmutablePolicyAttribute.Builder.class)
  interface PolicyAttribute {
    Optional<Object> getConstant();

    Optional<String> getProperty();

    Optional<String> getParameter();
  }

  enum ScopeGranularity {
    BASE,
    PARENT,
    MAIN,
    CUSTOM
  }

  String GROUP_DISCOVER = "discover";
  PermissionGroup GROUP_DISCOVER_READ =
      PermissionGroup.of(
          Base.READ,
          GROUP_DISCOVER,
          "access API landing pages, conformance declarations and OpenAPI definitions");
  String GROUP_PUBLIC = "public";

  /**
   * @langEn Option to disable access control.
   * @langDe Option, um die Absicherung zu deaktivieren.
   * @default true
   * @since v3.3
   */
  @Nullable
  Boolean getEnabled();

  /**
   * @langEn Definition of custom permission groups, the key is the group name, the value a list of
   *     permissions and/or predefined permission groups. The group `public` defines the list of
   *     permissions that every user possesses, if authenticated or not.
   * @langDe Definition von benutzerdefinierten Berechtigungsgruppen, der Key ist der Name der
   *     Gruppe, der Werte eine Liste von Berechtigungen und/oder vordefinierten
   *     Berechtigungsgruppen. Die Gruppe `public` definiert die Liste der Berechtigungen, die jeder
   *     Benutzer besitzt, ob angemeldet oder nicht.
   * @default {public: [read]}
   * @since v3.5
   */
  Map<String, Set<String>> getGroups();

  /**
   * @langEn If non-empty, *OAuth2 Scopes* are added to the OpenAPI definition. Then only tokens
   *     that contain at least one scope that covers the requested operation are accepted. Scopes
   *     reuse permissions groups, values are the types of permission groups that should be used:
   *     `BASE` (e.g. `read`), `PARENT` (e.g. `data`), `MAIN` (e.g. `data:read`) and `CUSTOM`
   *     (everything defined in `groups` besides `public`).
   * @langDe Wenn nicht leer, werden *OAuth2 Scopes* zur OpenAPI Definition hinzugefügt. Dann werden
   *     nur Tokens akzeptiert, die mindestens einen Scope enthalten, der die angeforderte Operation
   *     abdeckt. Scopes verwenden Berechtigungsgruppen, Werte sind die Arten von
   *     Berechtigungsgruppen, die verwendet werden sollen: `BASE` (z.B. `read`), `PARENT` (z.B.
   *     `data`), `MAIN` (z.B. `data:read`) und `CUSTOM` (alles in `groups` definierte außer
   *     `public`) sein.
   * @default []
   * @since v3.5
   */
  Set<ScopeGranularity> getScopes();

  /**
   * @langEn If non-empty, only tokens that contain at least one of the given values in the audience
   *     claim are accepted.
   * @langDe Wenn nicht leer, werden nur Tokens akzeptiert, die mindestens einen der gegebenen Werte
   *     im Audience-Claim enthalten.
   * @default []
   * @since v3.5
   */
  Set<String> getAudience();

  /**
   * @langEn Optional additional authorization layer using a *Policy Decision Point* defined in the
   *     [global configuration](../application/20-configuration/40-auth.md), see
   *     [Policies](#policies).
   * @langDe Optionaler zusätzlicher Autorisierungs-Layer mittels eines *Policy Decision Point*, der
   *     in der [globalen Konfiguration](../application/20-configuration/40-auth.md) definiert wird,
   *     siehe [Policies](#policies).
   * @default null
   * @since v3.5
   */
  Optional<Policies> getPolicies();

  @JsonIgnore
  @Value.Derived
  default boolean isEnabled() {
    return !Objects.equals(getEnabled(), false);
  }

  default boolean isRestricted(Set<String> permissions) {
    return isEnabled()
        && Sets.intersection(getGroups().getOrDefault(GROUP_PUBLIC, Set.of()), permissions)
            .isEmpty();
  }

  default Set<String> getGroupsWith(Set<String> permissions) {
    return getGroups().entrySet().stream()
        .filter(group -> !Sets.intersection(group.getValue(), permissions).isEmpty())
        .map(Map.Entry::getKey)
        .collect(Collectors.toSet());
  }
}
