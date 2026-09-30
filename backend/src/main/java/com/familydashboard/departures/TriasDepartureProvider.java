package com.familydashboard.departures;

import java.io.StringReader;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

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
import org.xml.sax.InputSource;

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

    private final RestClient restClient;
    private final TransitProperties transitProperties;

    @Autowired
    TriasDepartureProvider(TransitProperties transitProperties) {
        this.restClient = RestClient.create();
        this.transitProperties = transitProperties;
    }

    @Override
    public List<Departure> nextDepartures() {
        String responseBody = restClient.post()
                .uri(transitProperties.endpointUrl())
                .contentType(MediaType.TEXT_XML)
                .body(buildStopEventRequest())
                .retrieve()
                .body(String.class);
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
     * worse than sending it with empty content (which at least round-trips
     * as a structurally valid, if unauthenticated, request against a real
     * endpoint).
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
     * Parses a {@code StopEventResponse} body into our own {@link Departure}
     * list via DOM/XPath, per this class's javadoc ("no XML-binding
     * dependency"). Structure per {@code Trias_StopEvents.xsd}/{@code
     * Trias_JourneySupport.xsd}/{@code Trias_LocationSupport.xsd} (fetched
     * live from github.com/VDVde/TRIAS): one {@code StopEventResult} per
     * departure, each wrapping a {@code StopEvent} whose {@code ThisCall/
     * CallAtStop} carries the stop-specific timing/platform, and whose
     * {@code Service} carries the line/destination/cancellation status.
     */
    private List<Departure> parseStopEventResponse(String responseXml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            // TRIAS responses never legitimately contain a DOCTYPE - reject
            // one outright rather than resolve external entities (XXE
            // hardening for a document body we don't fully control, since it
            // ultimately comes from an external HTTP endpoint).
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            org.w3c.dom.Document document = builder.parse(new InputSource(new StringReader(responseXml)));

            XPath xpath = XPathFactory.newInstance().newXPath();
            xpath.setNamespaceContext(triasNamespaceContext());

            NodeList resultNodes = (NodeList) xpath.evaluate(
                    "//trias:StopEventResult", document, XPathConstants.NODESET);

            List<Departure> departures = new ArrayList<>();
            for (int i = 0; i < resultNodes.getLength(); i++) {
                departures.add(toDeparture((Element) resultNodes.item(i), xpath));
            }
            return departures;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse TRIAS StopEventResponse", e);
        }
    }

    private static Departure toDeparture(Element stopEventResult, XPath xpath) throws Exception {
        String platform = firstNonBlank(
                textAt(xpath, stopEventResult, "trias:StopEvent/trias:ThisCall/trias:CallAtStop/trias:EstimatedBay/trias:Text"),
                textAt(xpath, stopEventResult, "trias:StopEvent/trias:ThisCall/trias:CallAtStop/trias:PlannedBay/trias:Text"));

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

    private static LocalDateTime parseDateTime(String isoOffsetDateTime) {
        return OffsetDateTime.parse(isoOffsetDateTime).toLocalDateTime();
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
