/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.transactions.app;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.azahnen.dagger.annotations.AutoBind;
import com.google.common.collect.ImmutableList;
import de.ii.ogcapi.transactions.domain.ImmutableNameValue;
import de.ii.ogcapi.transactions.domain.ImmutableTxDelete;
import de.ii.ogcapi.transactions.domain.ImmutableTxReplace;
import de.ii.ogcapi.transactions.domain.ImmutableTxUpdate;
import de.ii.ogcapi.transactions.domain.InsertItem;
import de.ii.ogcapi.transactions.domain.Transaction;
import de.ii.ogcapi.transactions.domain.TransactionParser;
import de.ii.ogcapi.transactions.domain.TxAction;
import de.ii.ogcapi.transactions.domain.TxInsert;
import de.ii.ogcapi.transactions.domain.TxSemantic;
import de.ii.ogcapi.transactions.domain.TxUpdate.NameValue;
import de.ii.xtraplatform.cql.domain.Cql2Expression;
import de.ii.xtraplatform.cql.domain.In;
import de.ii.xtraplatform.cql.domain.Scalar;
import de.ii.xtraplatform.cql.domain.ScalarLiteral;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.ws.rs.core.MediaType;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import javax.xml.namespace.QName;
import javax.xml.stream.XMLEventFactory;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLEventWriter;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.Attribute;
import javax.xml.stream.events.EndElement;
import javax.xml.stream.events.Namespace;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;

/**
 * Streaming parser for WFS 2.0 {@code wfs:Transaction} XML payloads. Each {@code wfs:Insert} yields
 * one {@link TxInsert} per consecutive-same-collection group of children, with every feature
 * pre-read into a byte array and exposed via {@code items()} — so the executor can batch-call
 * {@code Session.createFeatures} for the whole group rather than once per feature. {@code
 * wfs:Update}, {@code wfs:Replace} and {@code wfs:Delete} are buffered eagerly (one feature for
 * replace; metadata only for the others).
 *
 * <p>Supports only {@code fes:ResourceId@rid} filters per GeoInfoDok §§ 5.1.5 and 5.4. The local
 * part of {@code typeName} (or root element QName) is stored as the action's {@code collectionId} —
 * for ALKIS/NAS this matches the ldproxy collection id directly.
 */
@Singleton
@AutoBind
public class WfsTransactionParser implements TransactionParser {

  static final String MEDIA_TYPE_XML = "application/xml";
  static final String MEDIA_TYPE_GML = "application/gml+xml";

  static final String NS_WFS = "http://www.opengis.net/wfs/2.0";
  static final String NS_FES = "http://www.opengis.net/fes/2.0";

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final XMLInputFactory inputFactory;
  private final XMLOutputFactory outputFactory;
  private final XMLEventFactory eventFactory;

  @Inject
  public WfsTransactionParser() {
    this.inputFactory = XMLInputFactory.newInstance();
    inputFactory.setProperty(XMLInputFactory.SUPPORT_DTD, Boolean.FALSE);
    inputFactory.setProperty("javax.xml.stream.isSupportingExternalEntities", Boolean.FALSE);
    this.outputFactory = XMLOutputFactory.newInstance();
    this.eventFactory = XMLEventFactory.newInstance();
  }

  @Override
  public boolean canParse(MediaType mediaType) {
    if (mediaType == null) return false;
    String type = mediaType.getType() + "/" + mediaType.getSubtype();
    return MEDIA_TYPE_XML.equalsIgnoreCase(type) || MEDIA_TYPE_GML.equalsIgnoreCase(type);
  }

  @Override
  public Transaction parse(InputStream body, MediaType mediaType) {
    XMLEventReader reader;
    try {
      reader = inputFactory.createXMLEventReader(body);
    } catch (XMLStreamException e) {
      throw new IllegalArgumentException("Could not open transaction body as XML", e);
    }
    try {
      StartElement root = advanceToRoot(reader);
      return new WfsTransactionImpl(reader, body, outputFactory, eventFactory, root);
    } catch (XMLStreamException e) {
      closeQuietly(reader);
      closeQuietly(body);
      throw new IllegalArgumentException("Malformed wfs:Transaction body: " + e.getMessage(), e);
    } catch (RuntimeException e) {
      closeQuietly(reader);
      closeQuietly(body);
      throw e;
    }
  }

  private static StartElement advanceToRoot(XMLEventReader reader) throws XMLStreamException {
    while (reader.hasNext()) {
      XMLEvent event = reader.nextEvent();
      if (!event.isStartElement()) continue;
      StartElement start = event.asStartElement();
      QName name = start.getName();
      if (!NS_WFS.equals(name.getNamespaceURI()) || !"Transaction".equals(name.getLocalPart())) {
        throw new IllegalArgumentException(
            "Root element must be {" + NS_WFS + "}Transaction, got " + name);
      }
      return start;
    }
    throw new IllegalArgumentException("Expected a {" + NS_WFS + "}Transaction root element");
  }

  private static void closeQuietly(XMLEventReader r) {
    if (r == null) return;
    try {
      r.close();
    } catch (XMLStreamException ignore) {
      // best-effort
    }
  }

  private static void closeQuietly(AutoCloseable c) {
    if (c == null) return;
    try {
      c.close();
    } catch (Exception ignore) {
      // best-effort
    }
  }

  /* ---------- Streaming Transaction implementation ---------- */

  private static final class WfsTransactionImpl implements Transaction {

    private final XMLEventReader reader;
    private final InputStream body;
    private final WfsActionIterator iterator;
    private boolean closed;

    WfsTransactionImpl(
        XMLEventReader reader,
        InputStream body,
        XMLOutputFactory outputFactory,
        XMLEventFactory eventFactory,
        StartElement root) {
      this.reader = reader;
      this.body = body;
      this.iterator = new WfsActionIterator(reader, outputFactory, eventFactory, root);
    }

    @Override
    public TxSemantic getSemantic() {
      return TxSemantic.ATOMIC;
    }

    @Override
    public Iterator<TxAction> actions() {
      return iterator;
    }

    @Override
    public void close() {
      if (closed) return;
      closed = true;
      closeQuietly(reader);
      closeQuietly(body);
    }
  }

  private static final class WfsActionIterator implements Iterator<TxAction> {

    private final XMLEventReader reader;
    private final XMLOutputFactory outputFactory;
    private final XMLEventFactory eventFactory;
    private final StartElement root;
    // Inserts buffered out of a single wfs:Insert that contains features of more than one
    // collection (rare but spec-allowed): the iterator returns one BufferedInsert per
    // consecutive-same-collection group, queueing the rest here.
    private final java.util.Deque<TxAction> pendingActions = new java.util.ArrayDeque<>();
    private TxAction pending;
    private boolean exhausted;

    WfsActionIterator(
        XMLEventReader reader,
        XMLOutputFactory outputFactory,
        XMLEventFactory eventFactory,
        StartElement root) {
      this.reader = reader;
      this.outputFactory = outputFactory;
      this.eventFactory = eventFactory;
      this.root = root;
    }

    @Override
    public boolean hasNext() {
      if (pending != null) return true;
      if (exhausted) return false;
      try {
        pending = readNextAction();
      } catch (XMLStreamException e) {
        throw new IllegalArgumentException("Malformed wfs:Transaction body: " + e.getMessage(), e);
      }
      if (pending == null) exhausted = true;
      return pending != null;
    }

    @Override
    public TxAction next() {
      if (!hasNext()) throw new NoSuchElementException();
      TxAction r = pending;
      pending = null;
      return r;
    }

    private TxAction readNextAction() throws XMLStreamException {
      if (!pendingActions.isEmpty()) {
        return pendingActions.poll();
      }
      while (true) {
        if (!reader.hasNext()) return null;
        XMLEvent event = reader.nextEvent();
        if (event.isEndElement() && isWfs(event.asEndElement().getName(), "Transaction")) {
          return null;
        }
        if (!event.isStartElement()) continue;
        StartElement start = event.asStartElement();
        QName name = start.getName();
        if (!NS_WFS.equals(name.getNamespaceURI())) {
          skipElement(reader);
          continue;
        }
        switch (name.getLocalPart()) {
          case "Insert":
            // Read every feature child of this wfs:Insert, grouped by element local name.
            // Bundling them into one TxInsert (per collection) is the lever the executor and SQL
            // session use to batch JDBC mains/children across features; without this, each child
            // would land as its own SingleItemInsert (N=1) and the multi-row INSERT path stays
            // idle. Children of different collection types in the same wfs:Insert produce one
            // BufferedInsert per group, queued in pendingActions.
            Optional<String> handle = Optional.ofNullable(attribute(start, "handle"));
            Map<String, List<BufferedItem>> byCollection = new LinkedHashMap<>();
            int featureIndex = 0;
            while (reader.hasNext()) {
              XMLEvent e = reader.nextEvent();
              if (e.isEndElement() && isWfs(e.asEndElement().getName(), "Insert")) break;
              if (!e.isStartElement()) continue;
              StartElement feature = e.asStartElement();
              String collectionId = feature.getName().getLocalPart();
              Optional<String> gmlId = Optional.ofNullable(gmlIdAttribute(feature));
              byte[] payload = copySubtree(reader, feature, root, start);
              featureIndex++;
              byCollection
                  .computeIfAbsent(collectionId, k -> new ArrayList<>())
                  .add(new BufferedItem(gmlId, featureIndex, payload));
            }
            if (byCollection.isEmpty()) {
              continue; // empty wfs:Insert — move on to next sibling
            }
            Iterator<Map.Entry<String, List<BufferedItem>>> entries =
                byCollection.entrySet().iterator();
            Map.Entry<String, List<BufferedItem>> first = entries.next();
            while (entries.hasNext()) {
              Map.Entry<String, List<BufferedItem>> ent = entries.next();
              pendingActions.add(new BufferedInsert(ent.getKey(), handle, ent.getValue()));
            }
            return new BufferedInsert(first.getKey(), handle, first.getValue());
          case "Replace":
            return parseReplace(start);
          case "Update":
            return parseUpdate(start);
          case "Delete":
            return parseDelete(start);
          default:
            skipElement(reader);
            break;
        }
      }
    }

    private TxAction parseReplace(StartElement replaceStart) throws XMLStreamException {
      String handle = attribute(replaceStart, "handle");
      byte[] feature = null;
      String collectionId = null;
      List<String> ids = new ArrayList<>();

      while (reader.hasNext()) {
        XMLEvent event = reader.nextEvent();
        if (event.isEndElement() && isWfs(event.asEndElement().getName(), "Replace")) break;
        if (!event.isStartElement()) continue;
        StartElement start = event.asStartElement();
        QName qn = start.getName();
        if (NS_FES.equals(qn.getNamespaceURI()) && "Filter".equals(qn.getLocalPart())) {
          ids.addAll(parseResourceIds(reader));
        } else if (feature == null) {
          collectionId = qn.getLocalPart();
          feature = copySubtree(reader, start, root, replaceStart);
        } else {
          skipElement(reader);
        }
      }
      if (feature == null) {
        throw new IllegalArgumentException("wfs:Replace requires a feature element child");
      }
      if (collectionId == null) {
        throw new IllegalArgumentException("wfs:Replace feature element has no name");
      }
      return new ImmutableTxReplace.Builder()
          .collectionId(collectionId)
          .actionId(Optional.ofNullable(handle))
          .feature(feature)
          .mediaType(MediaType.valueOf(MEDIA_TYPE_GML))
          .filter(idsFilter(ids))
          .build();
    }

    private TxAction parseUpdate(StartElement updateStart) throws XMLStreamException {
      String handle = attribute(updateStart, "handle");
      String typeName = attribute(updateStart, "typeName");
      String collectionId = stripPrefix(typeName);
      List<NameValue> modify = new ArrayList<>();
      List<String> deletes = new ArrayList<>();
      List<String> ids = new ArrayList<>();

      while (reader.hasNext()) {
        XMLEvent event = reader.nextEvent();
        if (event.isEndElement() && isWfs(event.asEndElement().getName(), "Update")) break;
        if (!event.isStartElement()) continue;
        StartElement start = event.asStartElement();
        QName qn = start.getName();
        if (NS_WFS.equals(qn.getNamespaceURI()) && "Property".equals(qn.getLocalPart())) {
          readProperty(reader, modify, deletes);
        } else if (NS_FES.equals(qn.getNamespaceURI()) && "Filter".equals(qn.getLocalPart())) {
          ids.addAll(parseResourceIds(reader));
        } else {
          skipElement(reader);
        }
      }

      if (modify.isEmpty() && deletes.isEmpty()) {
        throw new IllegalArgumentException(
            "wfs:Update requires at least one wfs:Property child with a Value or null indicator");
      }
      if (collectionId == null) {
        throw new IllegalArgumentException("wfs:Update is missing the 'typeName' attribute");
      }
      return new ImmutableTxUpdate.Builder()
          .collectionId(collectionId)
          .actionId(Optional.ofNullable(handle))
          .modify(modify)
          .deleteProperties(deletes)
          .filter(idsFilter(ids))
          .build();
    }

    private TxAction parseDelete(StartElement deleteStart) throws XMLStreamException {
      String handle = attribute(deleteStart, "handle");
      String typeName = attribute(deleteStart, "typeName");
      String collectionId = stripPrefix(typeName);
      List<String> ids = new ArrayList<>();

      while (reader.hasNext()) {
        XMLEvent event = reader.nextEvent();
        if (event.isEndElement() && isWfs(event.asEndElement().getName(), "Delete")) break;
        if (!event.isStartElement()) continue;
        StartElement start = event.asStartElement();
        QName qn = start.getName();
        if (NS_FES.equals(qn.getNamespaceURI()) && "Filter".equals(qn.getLocalPart())) {
          ids.addAll(parseResourceIds(reader));
        } else {
          skipElement(reader);
        }
      }
      if (collectionId == null) {
        throw new IllegalArgumentException("wfs:Delete is missing the 'typeName' attribute");
      }
      if (ids.isEmpty()) {
        throw new IllegalArgumentException(
            "wfs:Delete requires an fes:Filter with at least one fes:ResourceId/@rid");
      }
      return new ImmutableTxDelete.Builder()
          .collectionId(collectionId)
          .actionId(Optional.ofNullable(handle))
          .filter(idsFilter(ids))
          .build();
    }

    private void readProperty(XMLEventReader reader, List<NameValue> modify, List<String> deletes)
        throws XMLStreamException {
      String name = null;
      JsonNode value = null;
      boolean hasValueElement = false;
      while (reader.hasNext()) {
        XMLEvent event = reader.nextEvent();
        if (event.isEndElement() && isWfs(event.asEndElement().getName(), "Property")) break;
        if (!event.isStartElement()) continue;
        StartElement start = event.asStartElement();
        QName qn = start.getName();
        if (NS_WFS.equals(qn.getNamespaceURI()) && "ValueReference".equals(qn.getLocalPart())) {
          name = readText(reader, "ValueReference");
        } else if (NS_WFS.equals(qn.getNamespaceURI()) && "Value".equals(qn.getLocalPart())) {
          hasValueElement = true;
          String text = readMixedText(reader, "Value");
          value = text == null ? null : MAPPER.getNodeFactory().textNode(text);
        } else {
          skipElement(reader);
        }
      }
      if (name == null) {
        throw new IllegalArgumentException(
            "wfs:Property requires a wfs:ValueReference child holding the property name");
      }
      if (!hasValueElement || value == null) {
        deletes.add(name);
      } else {
        modify.add(new ImmutableNameValue.Builder().name(name).value(value).build());
      }
    }

    private List<String> parseResourceIds(XMLEventReader reader) throws XMLStreamException {
      List<String> ids = new ArrayList<>();
      while (reader.hasNext()) {
        XMLEvent event = reader.nextEvent();
        if (event.isEndElement() && isFes(event.asEndElement().getName(), "Filter")) break;
        if (!event.isStartElement()) continue;
        StartElement start = event.asStartElement();
        if (isFes(start.getName(), "ResourceId")) {
          String rid = attribute(start, "rid");
          if (rid != null) ids.add(rid);
          skipElement(reader);
        } else {
          throw new IllegalArgumentException(
              "Only fes:ResourceId/@rid filters are supported in wfs:Transaction; got "
                  + start.getName());
        }
      }
      return ids;
    }

    private Optional<Cql2Expression> idsFilter(List<String> ids) {
      if (ids.isEmpty()) return Optional.empty();
      List<Scalar> literals = new ArrayList<>(ids.size());
      for (String id : ids) literals.add(ScalarLiteral.of(id));
      return Optional.of(In.of(literals));
    }

    private byte[] copySubtree(XMLEventReader reader, StartElement start, StartElement... ancestors)
        throws XMLStreamException {
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      XMLEventWriter writer =
          outputFactory.createXMLEventWriter(new OutputStreamWriter(baos, StandardCharsets.UTF_8));
      int depth = 1;
      writer.add(withInheritedNamespaces(start, ancestors));
      while (reader.hasNext() && depth > 0) {
        XMLEvent event = reader.nextEvent();
        writer.add(event);
        if (event.isStartElement()) depth++;
        else if (event.isEndElement()) depth--;
      }
      writer.flush();
      writer.close();
      return baos.toByteArray();
    }

    // Rebuild the feature's start element so its serialized form carries every namespace
    // declaration that was in scope at parse time. StAX events only expose the namespaces
    // declared on each element, so without this the feature payload would lose any prefix
    // bound on an ancestor (e.g. xmlns:gml on wfs:Transaction) and the downstream GML
    // decoder would fail with "Unbound namespace prefix".
    private StartElement withInheritedNamespaces(
        StartElement feature, StartElement... outerToInner) {
      Map<String, Namespace> byPrefix = new LinkedHashMap<>();
      for (StartElement anc : outerToInner) {
        if (anc == null) continue;
        for (Iterator<Namespace> it = anc.getNamespaces(); it.hasNext(); ) {
          Namespace ns = it.next();
          byPrefix.put(prefixKey(ns), ns);
        }
      }
      for (Iterator<Namespace> it = feature.getNamespaces(); it.hasNext(); ) {
        Namespace ns = it.next();
        byPrefix.put(prefixKey(ns), ns);
      }
      Iterator<Attribute> attrs = feature.getAttributes();
      return eventFactory.createStartElement(
          feature.getName(), attrs, byPrefix.values().iterator());
    }

    private static String prefixKey(Namespace ns) {
      return ns.getPrefix() == null ? "" : ns.getPrefix();
    }

    private void skipElement(XMLEventReader reader) throws XMLStreamException {
      int depth = 1;
      while (reader.hasNext() && depth > 0) {
        XMLEvent event = reader.nextEvent();
        if (event.isStartElement()) depth++;
        else if (event.isEndElement()) depth--;
      }
    }

    private String readText(XMLEventReader reader, String localName) throws XMLStreamException {
      StringBuilder sb = new StringBuilder();
      while (reader.hasNext()) {
        XMLEvent event = reader.nextEvent();
        if (event.isEndElement()
            && NS_WFS.equals(event.asEndElement().getName().getNamespaceURI())
            && localName.equals(event.asEndElement().getName().getLocalPart())) {
          break;
        }
        if (event.isCharacters()) {
          sb.append(event.asCharacters().getData());
        } else if (event.isStartElement()) {
          skipElement(reader);
        }
      }
      String text = sb.toString().trim();
      return text.isEmpty() ? null : text;
    }

    private String readMixedText(XMLEventReader reader, String localName)
        throws XMLStreamException {
      StringBuilder sb = new StringBuilder();
      int depth = 1;
      while (reader.hasNext() && depth > 0) {
        XMLEvent event = reader.nextEvent();
        if (event.isEndElement()) {
          EndElement end = event.asEndElement();
          if (depth == 1
              && NS_WFS.equals(end.getName().getNamespaceURI())
              && localName.equals(end.getName().getLocalPart())) {
            depth--;
            break;
          }
          depth--;
          continue;
        }
        if (event.isStartElement()) {
          depth++;
          continue;
        }
        if (event.isCharacters()) {
          sb.append(event.asCharacters().getData());
        }
      }
      String text = sb.toString().trim();
      return text.isEmpty() ? null : text;
    }
  }

  /**
   * Buffered insert: holds every feature child of a {@code wfs:Insert} (for one collection) as a
   * pre-read byte array, returned one at a time from {@link #items()}. Buffering occurs once per
   * wfs:Insert in {@link WfsActionIterator#readNextAction()} — memory peaks at the size of that
   * single wfs:Insert, which is already in memory as the request body anyway. Bundling features
   * into one TxInsert is what lets the executor batch-call {@code Session.createFeatures} and the
   * SQL session fold multiple main INSERTs into one multi-row statement.
   */
  private static final class BufferedInsert implements TxInsert {

    private final String collectionId;
    private final Optional<String> actionId;
    private final List<BufferedItem> items;

    BufferedInsert(String collectionId, Optional<String> actionId, List<BufferedItem> items) {
      this.collectionId = collectionId;
      this.actionId = actionId;
      this.items = items;
    }

    @Override
    public String getCollectionId() {
      return collectionId;
    }

    @Override
    public Optional<String> getActionId() {
      return actionId;
    }

    @Override
    public Optional<String> getTitle() {
      return Optional.empty();
    }

    @Override
    public Optional<String> getDescription() {
      return Optional.empty();
    }

    @Override
    public MediaType getMediaType() {
      return MediaType.valueOf(MEDIA_TYPE_GML);
    }

    @Override
    public Iterator<InsertItem> items() {
      Iterator<BufferedItem> src = items.iterator();
      return new Iterator<>() {
        @Override
        public boolean hasNext() {
          return src.hasNext();
        }

        @Override
        public InsertItem next() {
          if (!src.hasNext()) throw new NoSuchElementException();
          BufferedItem b = src.next();
          return new InsertItem(b.gmlId, b.indexInInsert, new ByteArrayInputStream(b.payload));
        }
      };
    }
  }

  /** Internal triple held by {@link BufferedInsert} — one feature child of a wfs:Insert. */
  private static final class BufferedItem {
    final Optional<String> gmlId;
    final int indexInInsert;
    final byte[] payload;

    BufferedItem(Optional<String> gmlId, int indexInInsert, byte[] payload) {
      this.gmlId = gmlId;
      this.indexInInsert = indexInInsert;
      this.payload = payload;
    }
  }

  private static String attribute(StartElement element, String localName) {
    Attribute attr = element.getAttributeByName(new QName(localName));
    return attr == null ? null : attr.getValue();
  }

  /** Returns the {@code gml:id} attribute value, regardless of which prefix was used. */
  private static String gmlIdAttribute(StartElement element) {
    Attribute attr = element.getAttributeByName(new QName("http://www.opengis.net/gml/3.2", "id"));
    return attr == null ? null : attr.getValue();
  }

  private static String stripPrefix(String qname) {
    if (qname == null) return null;
    int idx = qname.indexOf(':');
    return idx < 0 ? qname : qname.substring(idx + 1);
  }

  private static boolean isWfs(QName name, String localName) {
    return NS_WFS.equals(name.getNamespaceURI()) && localName.equals(name.getLocalPart());
  }

  private static boolean isFes(QName name, String localName) {
    return NS_FES.equals(name.getNamespaceURI()) && localName.equals(name.getLocalPart());
  }

  /** Convenience for tests / static use that don't go through DI; otherwise unused. */
  @SuppressWarnings("unused")
  private static ImmutableList<TxAction> noActions() {
    return ImmutableList.of();
  }
}
