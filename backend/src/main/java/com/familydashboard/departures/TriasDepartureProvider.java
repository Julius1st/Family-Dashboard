package com.familydashboard.departures;

import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import javax.xml.XMLConstants;
import javax.xml.namespace.NamespaceContext;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * The one real {@link DepartureProvider} implementation: builds a TRIAS
 * (VDV 431-2) {@code StopEventRequest} for {@link
 * TransitProperties#stopPointRef()}, POSTs it to {@link
 * TransitProperties#endpointUrl()} (MobiData BW's shared statewide TRIAS
 * front door for Baden-Württemberg, see {@code
 * docs/departures-widget-plan.md}'s "Data source" section), and maps the
 * {@code StopEventResponse} into our own {@link Departure} list, so the
 * upstream XML shape never leaks past this class.
 *
 * <p><b>No XML-binding dependency (JAXB or otherwise).</b> A {@code
 * StopEventRequest} has only a handful of parameters — not a deep object
 * graph — so the request body is built via plain string templating.
 * Responses are parsed with the JDK's built-in {@code
 * javax.xml.parsers}/DOM/XPath (bundled in the JDK itself), never JAXB
 * (removed from the JDK in Java 11+, would need a new Maven dependency to
 * restore) and never a third-party binding library. More verbose than a
 * proper XML-binding library, but zero new dependencies — the explicit,
 * deliberate tradeoff recorded in {@code docs/departures-widget-plan.md}'s
 * "XML handling, no new dependency" design decision.
 *
 * <p>Uses Spring's {@link RestClient}, built via the plain {@link
 * RestClient#create()} static factory rather than an injected {@code
 * RestClient.Builder} — same reasoning as {@code OpenMeteoWeatherProvider}
 * (this Spring Boot 4.1 project doesn't auto-configure a {@code
 * RestClient.Builder} bean from {@code spring-boot-starter-web} alone,
 * confirmed empirically during the weather widget's implementation).
 */
@Component
class TriasDepartureProvider implements DepartureProvider {

    /**
     * TRIAS namespace (targetNamespace of every {@code Trias_*.xsd} file in
     * github.com/VDVde/TRIAS). Needed both to build a namespace-qualified
     * request body and to resolve elements via XPath on the response, since
     * the response document is namespace-aware.
     */
    private static final String TRIAS_NS = "http://www.vdv.de/trias";

    /**
     * SIRI namespace — TRIAS is layered on top of SIRI's generic
     * request/response envelope (see {@code Trias_RequestSupport.xsd}'s
     * {@code AbstractTriasServiceRequestStructure}/{@code
     * AbstractTriasResponseStructure}, both extending {@code siri:*} base
     * types), hence {@code RequestTimestamp}/{@code ResponseTimestamp} being
     * {@code siri:}-prefixed in real TRIAS messages — confirmed both by the
     * XSD includes and by the real, working example request in
     * <a href="https://github.com/VDVde/TRIAS/issues/1">VDVde/TRIAS#1</a>
     * (comment by user {@code forgemo}), which uses exactly this {@code
     * <Trias xmlns="...trias" xmlns:siri="...siri">} + {@code
     * <siri:RequestTimestamp>} shape.
     */
    private static final String SIRI_NS = "http://www.siri.org.uk/siri";

    /**
     * The TRIAS protocol version this request declares. {@code
     * VDVde/TRIAS#1}'s real working example used {@code 1.2}; {@code
     * Trias.xsd}'s root element currently fixes {@code 1.4}. Pinned to
     * {@code 1.2} here to match the one real, confirmed-working example
     * available — MobiData BW's actual supported version isn't known yet
     * (see {@link TransitProperties}), revisit once their response arrives.
     */
    private static final String TRIAS_VERSION = "1.2";

    /**
     * How many upcoming departures to ask for. Arbitrary, generous-enough
     * default for a single-stop household display; not yet configurable
     * (see {@code docs/departures-widget-plan.md}'s "Out of scope" section
     * — no UI for tuning this).
     */
    private static final int NUMBER_OF_RESULTS = 10;

    /**
     * This dashboard only ever targets one German stop (see {@code
     * docs/departures-widget-plan.md}'s "Out of scope" section: no stop
     * picker), so the request/response timestamps are anchored to this fixed
     * zone rather than derived from any per-request context — same reasoning
     * as {@code OpenMeteoWeatherProvider}'s {@code TIMEZONE} constant.
     */
    private static final ZoneId ZONE = ZoneId.of("Europe/Berlin");

    /**
     * Matches the German word "Gleis" ("track", used for trams/trains;
     * case-insensitively) plus any whitespace immediately following it, e.g.
     * in {@code "Gleis 1"} or {@code "GLEIS1"}. Used by {@link
     * #normalizePlatform(String)} to detect this label and strip it out of a
     * raw {@code PlannedBay}/{@code EstimatedBay} value before re-prepending
     * its own {@code "Gl. "} short form.
     */
    private static final Pattern GLEIS_LABEL = Pattern.compile("(?i)gleis\\s*");

    /**
     * Matches the German abbreviation "Bstg." ("Bussteig" - "bus platform/
     * bay"; case-insensitively), with an optional trailing period (real KVV
     * data has been seen both with and without it) plus any whitespace
     * immediately following, e.g. in {@code "Bstg. 3"}, {@code "BSTG. 3"},
     * {@code "bstg 3"} or {@code "bstg3"}. Used by {@link
     * #normalizePlatform(String)} to detect this label and strip it out of a
     * raw {@code PlannedBay}/{@code EstimatedBay} value before re-prepending
     * its own normalized {@code "Bstg. "} form - a bus bay must never be
     * relabelled {@code "Gl. "} (that specifically implies a tram/train
     * track), so this is handled as a distinct label from {@link
     * #GLEIS_LABEL}, not folded into it.
     */
    private static final Pattern BSTG_LABEL = Pattern.compile("(?i)bstg\\.?\\s*");

    private final RestClient restClient;
    private final TransitProperties transitProperties;

    @Autowired
    TriasDepartureProvider(TransitProperties transitProperties) {
        this.restClient = RestClient.create();
        this.transitProperties = transitProperties;
    }

    @Override
    public DepartureBoard nextDepartureBoard() {
        // Fetched as raw bytes, NOT pre-decoded into a String: a real TRIAS
        // endpoint (confirmed with live KVV data) can send UTF-8-encoded
        // German text (e.g. "Wolfartsweierer Straße") in a response whose
        // Content-Type header carries no explicit charset parameter. Spring's
        // StringHttpMessageConverter falls back to ISO-8859-1 in that case,
        // which would silently mangle every non-ASCII character (the
        // classic "ß" -> two-garbled-characters mojibake bug) before the XML
        // parser ever saw it - and a String, once wrongly decoded, can't be
        // un-corrupted downstream. Handing the DocumentBuilder the raw bytes
        // instead lets it determine the real encoding itself, the same way
        // any XML parser is supposed to: from the document's own {@code
        // <?xml ... encoding="UTF-8"?>} declaration (falling back to a
        // detected BOM, then UTF-8) - independent of whatever charset (or
        // lack thereof) the HTTP layer reported.
        byte[] responseBody = restClient.post()
                .uri(transitProperties.endpointUrl())
                .contentType(MediaType.TEXT_XML)
                .body(buildStopEventRequest())
                .retrieve()
                .body(byte[].class);
        return parseStopEventResponse(responseBody);
    }

    /**
     * Builds a {@code StopEventRequest} body for {@link
     * TransitProperties#stopPointRef()}, per {@code Trias_StopEvents.xsd}'s
     * {@code StopEventRequestStructure} (fetched live from
     * github.com/VDVde/TRIAS while implementing this class). Package-private
     * (not {@code private}) so {@code TriasDepartureProviderTest} can assert
     * this produces well-formed XML directly from placeholder/empty {@link
     * TransitProperties} values, with no network call and no real
     * credentials needed.
     *
     * <p><b>{@code <RequestorRef>} is always emitted, even with empty
     * content.</b> An earlier version of this method omitted the element
     * entirely when {@link TransitProperties#requestorRef()} was blank,
     * reasoning that an empty element might be rejected outright by a real
     * endpoint. That reasoning was wrong: tracing the actual type graph
     * (fetched live from {@code github.com/VDVde/TRIAS/siri-1.4/siri/}) shows
     * every TRIAS {@code ServiceRequest} extends {@code
     * AbstractTriasServiceRequestStructure} → {@code
     * siri:ContextualisedRequestStructure}, whose {@code
     * RequestorEndpointGroup} declares {@code <xsd:element ref="RequestorRef"
     * />} with no {@code minOccurs} — i.e. {@code RequestorRef} is a
     * <em>required</em> element in every conformant TRIAS request. Omitting
     * it outright fails schema validation unconditionally, which is strictly
     * worse than sending it with empty content. Note this doesn't make an
     * empty {@code <RequestorRef>} fully schema-valid either: {@code
     * RequestorRef}'s type ({@code ParticipantRefStructure} → {@code
     * ParticipantCodeType}, a restriction of {@code xsd:NMTOKEN} in {@code
     * siri_participant-v1.1.xsd}) requires at least one NameChar, so an
     * empty string is not a valid {@code NMTOKEN} value either. Sending it
     * empty only satisfies the required-<em>element</em> cardinality; a
     * genuinely valid, non-blank {@code NMTOKEN} value is still needed once
     * real credentials arrive from MobiData BW.
     *
     * <p><b>{@code <LocationName>} is likewise always emitted (with empty
     * content) inside {@code <LocationRef>}.</b> {@code
     * Trias_LocationSupport.xsd}'s {@code LocationRefStructure} requires a
     * {@code LocationName} element (no {@code minOccurs="0"}) alongside the
     * {@code StopPointRef} choice — structurally required even though this
     * dashboard only tracks a stop ref, not a human-readable stop name, in
     * {@link TransitProperties}. Real TRIAS servers resolve the location via
     * {@code StopPointRef} regardless of this element's content, so an empty
     * {@code <Text>} is used rather than fabricating a fake name.
     */
    String buildStopEventRequest() {
        String timestamp = OffsetDateTime.now(ZONE).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);

        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <Trias version="%s" xmlns="%s" xmlns:siri="%s">
                    <ServiceRequest>
                        <siri:RequestTimestamp>%s</siri:RequestTimestamp>
                        <RequestorRef>%s</RequestorRef>
                        <RequestPayload>
                            <StopEventRequest>
                                <Location>
                                    <LocationRef>
                                        <StopPointRef>%s</StopPointRef>
                                        <LocationName>
                                            <Text></Text>
                                        </LocationName>
                                    </LocationRef>
                                    <DepArrTime>%s</DepArrTime>
                                </Location>
                                <Params>
                                    <NumberOfResults>%d</NumberOfResults>
                                    <StopEventType>departure</StopEventType>
                                    <IncludeRealtimeData>true</IncludeRealtimeData>
                                </Params>
                            </StopEventRequest>
                        </RequestPayload>
                    </ServiceRequest>
                </Trias>
                """.formatted(
                TRIAS_VERSION, TRIAS_NS, SIRI_NS,
                timestamp,
                escapeXml(transitProperties.requestorRef()),
                escapeXml(transitProperties.stopPointRef()),
                timestamp,
                NUMBER_OF_RESULTS);
    }

    /**
     * Parses a {@code StopEventResponse} body into our own {@link
     * DepartureBoard} via DOM/XPath, per this class's javadoc ("no
     * XML-binding dependency"). Structure per {@code Trias_StopEvents.xsd}/
     * {@code Trias_JourneySupport.xsd}/{@code Trias_LocationSupport.xsd}
     * (fetched live from github.com/VDVde/TRIAS): one {@code
     * StopEventResult} per departure, each wrapping a {@code StopEvent}
     * whose {@code ThisCall/CallAtStop} carries the stop-specific timing/
     * platform/stop name, and whose {@code Service} carries the
     * line/destination/cancellation status.
     *
     * <p>Takes the raw response bytes (never a pre-decoded {@code String} —
     * see {@link #nextDepartureBoard()}'s comment on why) and hands them to
     * {@link DocumentBuilder#parse(java.io.InputStream)} directly, so the
     * parser itself resolves the correct character encoding from the
     * document.
     *
     * <p><b>Stop name extraction, "first non-blank wins".</b> {@code
     * StopPointName/Text} lives inside each {@code CallAtStop} — i.e. it is
     * repeated once per departure, not carried once for the whole response
     * (see {@link DepartureBoard}'s javadoc for why that shape pushed the
     * name out into a wrapper type rather than a per-{@link Departure}
     * field). This dashboard only ever queries one {@code StopPointRef} per
     * request (see {@code docs/departures-widget-plan.md}'s "Out of scope"
     * section — no stop picker), so every {@code CallAtStop} in a given
     * response is expected to name the exact same stop; this loop simply
     * takes the first non-blank {@code StopPointName/Text} it finds across
     * all {@code StopEventResult}s and ignores the rest; a response with no
     * departures at all (or where every entry happens to omit the element)
     * leaves {@link DepartureBoard#stopName()} {@code null} — a real,
     * expected state (see {@link DeparturesController} for how this
     * ultimately renders), not a parsing error.
     */
    private DepartureBoard parseStopEventResponse(byte[] responseXml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            // TRIAS responses never legitimately contain a DOCTYPE - reject
            // one outright rather than resolve external entities (XXE
            // hardening for a document body we don't fully control, since it
            // ultimately comes from an external HTTP endpoint).
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            org.w3c.dom.Document document = builder.parse(new ByteArrayInputStream(responseXml));

            XPath xpath = XPathFactory.newInstance().newXPath();
            xpath.setNamespaceContext(triasNamespaceContext());

            NodeList resultNodes = (NodeList) xpath.evaluate(
                    "//trias:StopEventResult", document, XPathConstants.NODESET);

            List<Departure> departures = new ArrayList<>();
            String stopName = null;
            for (int i = 0; i < resultNodes.getLength(); i++) {
                Element stopEventResult = (Element) resultNodes.item(i);
                departures.add(toDeparture(stopEventResult, xpath));

                if (stopName == null) {
                    String candidate = textAt(xpath, stopEventResult,
                            "trias:StopEvent/trias:ThisCall/trias:CallAtStop/trias:StopPointName/trias:Text");
                    if (!isBlank(candidate)) {
                        stopName = candidate;
                    }
                }
            }
            return new DepartureBoard(stopName, departures);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse TRIAS StopEventResponse", e);
        }
    }

    private static Departure toDeparture(Element stopEventResult, XPath xpath) throws Exception {
        String platform = firstNonBlank(
                normalizePlatform(textAt(xpath, stopEventResult, "trias:StopEvent/trias:ThisCall/trias:CallAtStop/trias:EstimatedBay/trias:Text")),
                normalizePlatform(textAt(xpath, stopEventResult, "trias:StopEvent/trias:ThisCall/trias:CallAtStop/trias:PlannedBay/trias:Text")));

        LocalDateTime scheduledTime = parseDateTime(textAt(xpath, stopEventResult,
                "trias:StopEvent/trias:ThisCall/trias:CallAtStop/trias:ServiceDeparture/trias:TimetabledTime"));
        String estimatedTimeText = textAt(xpath, stopEventResult,
                "trias:StopEvent/trias:ThisCall/trias:CallAtStop/trias:ServiceDeparture/trias:EstimatedTime");
        LocalDateTime expectedTime = isBlank(estimatedTimeText) ? null : parseDateTime(estimatedTimeText);

        String line = firstNonBlank(
                textAt(xpath, stopEventResult, "trias:StopEvent/trias:Service/trias:ServiceSection/trias:PublishedLineName/trias:Text"),
                textAt(xpath, stopEventResult, "trias:StopEvent/trias:Service/trias:ServiceSection/trias:LineRef"));
        String destination = textAt(xpath, stopEventResult, "trias:StopEvent/trias:Service/trias:DestinationText/trias:Text");
        boolean cancelled = Boolean.parseBoolean(
                textAt(xpath, stopEventResult, "trias:StopEvent/trias:Service/trias:Cancelled"));

        DepartureStatus status = toStatus(cancelled, scheduledTime, expectedTime);
        return new Departure(line, destination, platform, scheduledTime, expectedTime, status);
    }

    private static DepartureStatus toStatus(boolean cancelled, LocalDateTime scheduledTime, LocalDateTime expectedTime) {
        if (cancelled) {
            return DepartureStatus.CANCELLED;
        }
        if (expectedTime != null && expectedTime.isAfter(scheduledTime)) {
            return DepartureStatus.DELAYED;
        }
        return DepartureStatus.ON_TIME;
    }

    /**
     * Parses a TRIAS {@code TimetabledTime}/{@code EstimatedTime} value (an
     * ISO-8601 offset date-time, e.g. {@code "2026-10-01T08:15:00+02:00"})
     * into the {@link #ZONE}-local wall-clock time.
     *
     * <p><b>Must convert via the instant, never just strip the offset.</b> An
     * earlier version of this method was {@code
     * OffsetDateTime.parse(isoOffsetDateTime).toLocalDateTime()} — which
     * discards the parsed offset entirely and keeps only the raw year/month/
     * day/hour/minute/second digits exactly as written in the source string,
     * regardless of what that offset was. That's only correct by coincidence
     * when the source's own offset already equals {@link #ZONE}'s real
     * current offset ({@code +02:00} in summer/CEST, {@code +01:00} in
     * winter/CET) — exactly true of every example this class's own test
     * fixtures use, which is why the old code passed every test while still
     * being wrong. A real backend that serializes its timestamps in a
     * different offset convention (e.g. UTC, {@code "...Z"} or {@code
     * "+00:00"} — common for systems that store everything in UTC regardless
     * of what a particular protocol's examples assume) would have every
     * parsed time silently shifted by whatever the offset difference is (1-2
     * hours for UTC vs. Europe/Berlin), with no error — confirmed as the root
     * cause of a live-data bug report where every departure's countdown
     * showed "in 0 Min" regardless of how far away it actually was: a
     * backend emitting UTC timestamps would make every parsed {@code
     * scheduledTime}/{@code expectedTime} appear 1-2 hours further in the
     * past than it really is relative to the correctly Europe/Berlin-
     * computed "now", which {@link DepartureDto}'s countdown flooring then
     * renders as a uniform {@code 0} for every departure.
     *
     * <p>{@code atZoneSameInstant(ZONE)} instead resolves the parsed value to
     * the actual instant it represents (using its own offset, whatever that
     * is) and then re-expresses that same instant in {@link #ZONE} — correct
     * regardless of what offset convention the source uses, and identical to
     * the old behavior in the already-covered case where the source offset
     * happens to equal {@link #ZONE}'s current real offset.
     */
    private static LocalDateTime parseDateTime(String isoOffsetDateTime) {
        return OffsetDateTime.parse(isoOffsetDateTime).atZoneSameInstant(ZONE).toLocalDateTime();
    }

    private static String textAt(XPath xpath, Element context, String expression) throws Exception {
        return (String) xpath.evaluate(expression, context, XPathConstants.STRING);
    }

    private static String firstNonBlank(String preferred, String fallback) {
        if (!isBlank(preferred)) {
            return preferred;
        }
        return isBlank(fallback) ? null : fallback;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /**
     * Normalizes a raw {@code PlannedBay}/{@code EstimatedBay} value into the
     * already fully-<em>formatted</em> display label (see {@link
     * Departure#platform()}'s javadoc for that contract) - not a bare value
     * for some later step to re-label, since which short label is correct
     * ({@code "Gl. "} vs {@code "Bstg. "}) depends entirely on what, if any,
     * label word the raw TRIAS text itself already carries.
     *
     * <p>Confirmed against real KVV (Karlsruhe) data: unlike this class's
     * synthetic WireMock test fixture — which, matching some other TRIAS
     * operators' convention, carries a bare number (e.g. {@code "3"}) — KVV's
     * real {@code PlannedBay}/{@code EstimatedBay} text always spells a label
     * out in full, one of two observed so far:
     * <ul>
     *   <li>{@code "Gleis 1"} (German "track", used for trams/trains) →
     *       normalized to {@code "Gl. 1"}.
     *   <li>{@code "Bstg. 3"} (German "Bussteig", "bus platform/bay"; used
     *       for buses) → normalized to {@code "Bstg. 3"}, i.e. re-formatted
     *       but <em>not</em> relabelled {@code "Gl. "} - that short form
     *       specifically implies a tram/train track, which would be actively
     *       wrong for a bus departure.
     * </ul>
     *
     * <p>Leaving either raw label word in place and letting a later step
     * (e.g. {@link DepartureDto#from}, in an earlier version of this method)
     * unconditionally prepend {@code "Gl. "} is exactly the bug class this
     * method guards against: it doubled {@code "Gleis"} into {@code "Gl.
     * Gleis 1"} before, and would equally mislabel a bus bay as {@code "Gl.
     * Bstg. 3"} or {@code "Gl. 3"} if a caller re-added a blanket {@code "Gl.
     * "} prefix here. Normalizing fully in the adapter - producing the exact
     * display string once - keeps this TRIAS/operator-specific quirk from
     * leaking past this class, per this project's provider/DTO convention
     * (see {@code CLAUDE.md}): the DTO layer should format a value, not
     * reinterpret source quirks.
     *
     * <p>If the raw text carries neither recognized label word, it's treated
     * as this fixture's bare-number convention (never actually observed live
     * - only ever used in tests, per the note above) and defaults to the
     * {@code "Gl. "} label, matching this method's original, pre-"Bstg."
     * behavior so that convention's existing callers/tests don't change.
     */
    private static String normalizePlatform(String rawBay) {
        if (isBlank(rawBay)) {
            return null;
        }
        String trimmed = rawBay.trim();

        if (BSTG_LABEL.matcher(trimmed).find()) {
            String value = BSTG_LABEL.matcher(trimmed).replaceAll("").trim();
            return value.isEmpty() ? null : "Bstg. %s".formatted(value);
        }
        if (GLEIS_LABEL.matcher(trimmed).find()) {
            String value = GLEIS_LABEL.matcher(trimmed).replaceAll("").trim();
            return value.isEmpty() ? null : "Gl. %s".formatted(value);
        }
        return "Gl. %s".formatted(trimmed);
    }

    private static String escapeXml(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    /**
     * Maps the {@code trias}/{@code siri} prefixes used in XPath expressions
     * above to their real namespace URIs — required because the response
     * {@link org.w3c.dom.Document} is parsed namespace-aware, so plain
     * unprefixed XPath steps (e.g. {@code //StopEventResult}) would silently
     * match nothing.
     */
    private static NamespaceContext triasNamespaceContext() {
        return new NamespaceContext() {
            @Override
            public String getNamespaceURI(String prefix) {
                return switch (prefix) {
                    case "trias" -> TRIAS_NS;
                    case "siri" -> SIRI_NS;
                    default -> XMLConstants.NULL_NS_URI;
                };
            }

            @Override
            public String getPrefix(String namespaceURI) {
                return null;
            }

            @Override
            public java.util.Iterator<String> getPrefixes(String namespaceURI) {
                return null;
            }
        };
    }
}
