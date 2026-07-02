/*
 * Copyright 2025 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.styles.app

import com.google.common.io.Resources
import de.ii.ogcapi.foundation.domain.SecureXml
import spock.lang.Specification

class StyleFormatSld10SchemaSpec extends Specification {

    def "the SLD 1.0 schema (which imports xlink.xsd) loads via SecureXml.schemaFactory"() {
        when: "building the schema the same way StyleFormatSld10.init does"
        SecureXml.schemaFactory()
                .newSchema(Resources.getResource(StyleFormatSld10, "/schemas/sld10.xsd"))

        then: "the imported schema resolves (not blocked by accessExternalSchema)"
        noExceptionThrown()
    }
}
