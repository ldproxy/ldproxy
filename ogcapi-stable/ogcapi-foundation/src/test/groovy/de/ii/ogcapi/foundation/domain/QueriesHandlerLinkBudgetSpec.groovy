/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.foundation.domain

import jakarta.ws.rs.core.Link
import jakarta.ws.rs.core.Response
import spock.lang.Specification

/**
 * The Link response header stays within its size budget: alternate links go first, then the
 * paging and version links are kept and the other links fill the remaining budget, the link to
 * the resource itself and to the API description first.
 */
class QueriesHandlerLinkBudgetSpec extends Specification {

    QueriesHandler<QueryIdentifier> handler = new QueriesHandler<QueryIdentifier>() {
        @Override
        Map<QueryIdentifier, QueryHandler<? extends QueryInput>> getQueryHandlers() {
            return [:]
        }
    }

    static Link link(String rel, String path = rel) {
        Link.fromUri("http://localhost/api/${path}").rel(rel).build()
    }

    List<Link> headerLinks(List<Link> links, List<String> templates, int budget) {
        Response.ResponseBuilder builder = Response.ok()
        handler.applyLinks(builder, budget, links, templates)
        Response response = builder.build()
        return (response.getHeaders().get('Link') ?: []).collect { it as Link }
    }

    List<String> headerRels(List<Link> links, List<String> templates, int budget) {
        headerLinks(links, templates, budget)*.rel
    }

    int size(List<Link> links) {
        links.sum { it.toString().length() } as int
    }

    def 'all links and templates are kept within the budget'() {
        given:
        List<Link> links = [link('alternate'), link('conformance'), link('self')]

        when:
        Response.ResponseBuilder builder = Response.ok()
        handler.applyLinks(builder, 2048, links, ['<http://localhost/api/{x}>; rel="item"'])
        Response response = builder.build()

        then:
        response.getHeaders().get('Link').collect { (it as Link).rel } == ['alternate', 'conformance', 'self']
        response.getHeaders().get('Link-Template') == ['<http://localhost/api/{x}>; rel="item"']
    }

    def 'a landing page over budget keeps self and the API description instead of no links at all'() {
        given: 'links in the order of the comparator (by rel), with long describedby links early'
        List<Link> links = [
                link('alternate'),
                link('conformance'),
                link('data'),
                link('describedby', 'a-long-path-to-a-document-that-describes-the-data-1'),
                link('describedby', 'a-long-path-to-a-document-that-describes-the-data-2'),
                link('license'),
                link('self'),
                link('service-desc'),
                link('service-doc')]
        int budget = size(links) - 60

        when:
        List<URI> uris = headerLinks(links, [], budget)*.uri
        List<Link> kept = links.findAll { uris.contains(it.uri) }

        then: 'alternate is dropped, the preferred links are kept, the rest fills the budget'
        !kept*.rel.contains('alternate')
        kept*.rel.containsAll(['self', 'service-desc', 'service-doc'])
        kept.size() > 3
        kept.size() < links.size() - 1
        size(kept) <= budget

        and: 'the links keep their order'
        uris == kept*.uri
    }

    def 'paging links are kept and the others fill the remaining budget, link templates are dropped'() {
        given:
        List<Link> links = [
                link('alternate'),
                link('first'),
                link('license', 'a-very-long-path-to-the-license-of-the-data-that-does-not-fit'),
                link('next'),
                link('prev'),
                link('self')]
        int budget = size(links.findAll { it.rel in ['first', 'next', 'prev', 'self'] })

        when:
        Response.ResponseBuilder builder = Response.ok()
        handler.applyLinks(builder, budget, links, ['<http://localhost/api/{x}>; rel="item"'])
        Response response = builder.build()

        then:
        response.getHeaders().get('Link').collect { (it as Link).rel } == ['first', 'next', 'prev', 'self']
        response.getHeaders().get('Link-Template') == null
    }

    def 'only the not-skippable links remain when the paging links alone exceed the budget'() {
        given:
        List<Link> links = [
                link('next', 'items?offset=10&a-long-query-string-that-exceeds-the-budget'),
                link('prev'),
                link('self')]

        expect:
        headerRels(links, [], 40) == ['next']
    }
}
