package com.familydashboard.departures;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.io.IOException;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;

import com.github.tomakehurst.wiremock.WireMockServer;

/**
 * WireMock-based test for {@link TriasDepartureProvider}, per {@code
 * CLAUDE.md}'s "Tests for these use WireMock, never the live API"
 * convention.
 *
 * <p><b>Fixture provenance (honesty note, per {@code
 * docs/departures-widget-plan.md}'s Ticket 1 acceptance criteria):</b>
 * GitHub was reachable from this environment (confirmed: {@code
 * api.github.com/repos/VDVde/TRIAS/issues/1} returns 200), and the plan doc
 * describes that issue as containing "a real example StopEventRequest/
 * response". That description doesn't hold up on inspection: the issue
 * ("Example request to trias service") and its comments only demonstrate a
 * {@code LocationInformationRequest} (looking up a stop by name), never a
 * {@code StopEventRequest} or {@code StopEventResponse} — there is no
 * departure-board example to fetch from it. That real example is saved,
 * unused, at {@code departures/github-issue-1-example.xml} purely as
 * provenance for this finding and as a reference for the outer {@code
 * Trias}/{@code ServiceRequest}/{@code siri:RequestTimestamp} envelope shape
 * (which {@link TriasDepartureProvider#buildStopEventRequest()} does follow).
 *
 * <p>{@code departures/trias-stop-event-response.xml} (this test's actual
 * fixture) is instead a hand-constructed, synthetic {@code
 * StopEventResponse}, built from the official {@code Trias_StopEvents.xsd}
 * / {@code Trias_JourneySupport.xsd} / {@code Trias_LocationSupport.xsd} /
 * {@code Trias_Common.xsd} / {@code Trias_ModesSupport.xsd} schema files
 * (also fetched live from github.com/VDVde/TRIAS while implementing this
 * class), which fully and unambiguously specify the element structure used
 * here — it is not a captured real response.
 */
class TriasDepartureProviderTest {

    private WireMockServer wireMockServer;
    private TriasDepartureProvider provider;

    @BeforeEach
    void startWireMockServer() {
        wireMockServer = new WireMockServer(wireMockConfig().dynamicPort());
        wireMockServer.start();

        TransitProperties transitProperties = new TransitProperties(
                wireMockServer.baseUrl() + "/trias", "test-requestor", "de:08212:1001");
        provider = new TriasDepartureProvider(transitProperties);
    }

    @AfterEach
    void stopWireMockServer() {
        wireMockServer.stop();
    }

    @Test
    void nextDeparturesSendsAWellFormedRequestContainingTheConfiguredStopRef() {
        wireMockServer.stubFor(post(urlPathEqualTo("/trias"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "text/xml")
                        .withBody(readFixture("trias-stop-event-response.xml"))));

        provider.nextDepartures();

        wireMockServer.verify(postRequestedFor(urlPathEqualTo("/trias"))
                .withHeader("Content-Type", equalTo("text/xml"))
                .withRequestBody(containing("<StopPointRef>de:08212:1001</StopPointRef>"))
                .withRequestBody(containing("<RequestorRef>test-requestor</RequestorRef>"))
                .withRequestBody(containing("<StopEventRequest>")));

        String sentBody = wireMockServer.getAllServeEvents().get(0).getRequest().getBodyAsString();
        assertThatCode(() -> parseXml(sentBody)).doesNotThrowAnyException();
    }

    @Test
    void nextDeparturesParsesOnTimeDelayedAndCancelledDepartures() {
        wireMockServer.stubFor(post(urlPathEqualTo("/trias"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "text/xml")
                        .withBody(readFixture("trias-stop-event-response.xml"))));

        var departures = provider.nextDepartures();

        assertThat(departures).hasSize(4);

        // 1. On time, confirmed by real-time data (EstimatedTime == TimetabledTime).
        Departure onTimeConfirmed = departures.get(0);
        assertThat(onTimeConfirmed.line()).isEqualTo("S2");
        assertThat(onTimeConfirmed.destination()).isEqualTo("Bad Herrenalb");
        assertThat(onTimeConfirmed.platform()).isEqualTo("Gl. 3");
        assertThat(onTimeConfirmed.scheduledTime()).isEqualTo(LocalDateTime.of(2026, 10, 1, 8, 15));
        assertThat(onTimeConfirmed.expectedTime()).isEqualTo(LocalDateTime.of(2026, 10, 1, 8, 15));
        assertThat(onTimeConfirmed.status()).isEqualTo(DepartureStatus.ON_TIME);

        // 2. On time, with NO real-time data at all - expectedTime stays null
        // (callers are expected to fall back to scheduledTime for display),
        // and the absence of real-time data must not be misread as a delay.
        Departure onTimeNoRealtime = departures.get(1);
        assertThat(onTimeNoRealtime.line()).isEqualTo("4");
        assertThat(onTimeNoRealtime.destination()).isEqualTo("Durlach Bahnhof");
        assertThat(onTimeNoRealtime.platform()).isEqualTo("Gl. 1");
        assertThat(onTimeNoRealtime.scheduledTime()).isEqualTo(LocalDateTime.of(2026, 10, 1, 8, 18));
        assertThat(onTimeNoRealtime.expectedTime()).isNull();
        assertThat(onTimeNoRealtime.status()).isEqualTo(DepartureStatus.ON_TIME);

        // 3. Delayed by 3 minutes - matches the design handoff's own "+3 Min" example.
        Departure delayed = departures.get(2);
        assertThat(delayed.line()).isEqualTo("5");
        assertThat(delayed.destination()).isEqualTo("Rheinstetten Rathaus");
        assertThat(delayed.platform()).isEqualTo("Gl. 2");
        assertThat(delayed.scheduledTime()).isEqualTo(LocalDateTime.of(2026, 10, 1, 8, 20));
        assertThat(delayed.expectedTime()).isEqualTo(LocalDateTime.of(2026, 10, 1, 8, 23));
        assertThat(delayed.status()).isEqualTo(DepartureStatus.DELAYED);

        // 4. Cancelled.
        Departure cancelled = departures.get(3);
        assertThat(cancelled.line()).isEqualTo("2");
        assertThat(cancelled.destination()).isEqualTo("Knielingen");
        assertThat(cancelled.platform()).isEqualTo("Gl. 4");
        assertThat(cancelled.scheduledTime()).isEqualTo(LocalDateTime.of(2026, 10, 1, 8, 25));
        assertThat(cancelled.status()).isEqualTo(DepartureStatus.CANCELLED);
    }

    /**
     * Regression test for a real bug seen against live KVV (Karlsruhe)
     * data: KVV's actual {@code PlannedBay}/{@code EstimatedBay} text
     * already spells out the German word "Gleis" in full (e.g. {@code
     * "Gleis 3"}), unlike the bare-number convention this fixture used
     * before this bug was found. An earlier version of {@link
     * Departure#platform()} held only the bare, label-stripped value (e.g.
     * {@code "3"}), with {@link DepartureDto#from} separately prepending its
     * own {@code "Gl. "} label for display - so without stripping "Gleis"
     * first, the two would double up into {@code "Gl. Gleis 3"} rather than
     * the intended {@code "Gl. 3"}. {@link Departure#platform()} now holds
     * the already fully-formatted display label itself (see its updated
     * javadoc), so this test asserts the final {@code "Gl. 3"} string
     * directly off {@link Departure#platform()}, with {@link
     * DepartureDto#from} just confirmed to pass it through unchanged.
     * {@code trias-stop-event-response.xml} deliberately mixes both
     * conventions (entry 1: {@code "Gleis 3"}/{@code "GLEIS 3"} mixed
     * case, entry 3: {@code "gleis 2"}/{@code "gleis2"} lowercase with and
     * without a space) so every one of them is asserted as the final {@code
     * "Gl. N"} string (never {@code "Gleis N"} or {@code "Gl. Gleis N"}) in
     * {@link #nextDeparturesParsesOnTimeDelayedAndCancelledDepartures()}
     * above - this test only re-confirms entry 1 end-to-end through the
     * display-facing {@link DepartureDto} too, not just {@link
     * Departure#platform()}.
     */
    @Test
    void nextDeparturesStripsTheRedundantGleisWordFromARealKvvStylePlatformValue() {
        wireMockServer.stubFor(post(urlPathEqualTo("/trias"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "text/xml")
                        .withBody(readFixture("trias-stop-event-response.xml"))));

        var departures = provider.nextDepartures();

        Departure onTimeConfirmed = departures.get(0);
        assertThat(onTimeConfirmed.platform()).isEqualTo("Gl. 3");

        DepartureDto dto = DepartureDto.from(onTimeConfirmed, onTimeConfirmed.scheduledTime().minusMinutes(5));
        assertThat(dto.platform()).isEqualTo("Gl. 3");
    }

    /**
     * Regression test for a real bug seen against live KVV data for BUS
     * departures specifically: unlike trams (which use "Gleis"), KVV's real
     * {@code PlannedBay}/{@code EstimatedBay} text for a bus uses the German
     * abbreviation "Bstg." ("Bussteig" - "bus platform/bay"), e.g. {@code
     * "Bstg. 3"}. The pre-fix code only recognized "Gleis" - "Bstg." would
     * pass through {@code normalizePlatform()} untouched and then get
     * unconditionally relabelled by {@code DepartureDto.from()}'s old {@code
     * "Gl. " + value} logic, producing the actively wrong {@code "Gl. Bstg.
     * 3"} (a bus bay mislabelled as a tram track, and doubled besides).
     * {@code trias-stop-event-response-bus-platform.xml} mixes two "Bstg."
     * spelling variants the same way the "Gleis" fixture above does (entry
     * 1: {@code "Bstg. 3"}/{@code "BSTG. 3"} mixed case with the period,
     * entry 2: {@code "bstg 21"}/{@code "bstg21"} lowercase, no period, with
     * and without a space) to exercise {@code TriasDepartureProvider}'s
     * {@code BSTG_LABEL} case/spacing tolerance the same way {@code
     * GLEIS_LABEL}'s is exercised elsewhere. Asserts the final platform is
     * {@code "Bstg. N"} - never {@code "Gl.
     * Bstg. N"}, {@code "Gl. N"}, or {@code "Bstg. Bstg. N"} - both on
     * {@link Departure#platform()} and end-to-end through {@link
     * DepartureDto#from}.
     */
    @Test
    void nextDeparturesNormalizesABusBstgPlatformValueWithoutRelabellingItAsAGleis() {
        wireMockServer.stubFor(post(urlPathEqualTo("/trias"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "text/xml")
                        .withBody(readFixture("trias-stop-event-response-bus-platform.xml"))));

        var departures = provider.nextDepartures();

        assertThat(departures).hasSize(2);

        // Mixed case, with the period: "Bstg. 3" / "BSTG. 3".
        Departure mixedCaseWithPeriod = departures.get(0);
        assertThat(mixedCaseWithPeriod.platform()).isEqualTo("Bstg. 3");
        DepartureDto mixedCaseDto = DepartureDto.from(mixedCaseWithPeriod, mixedCaseWithPeriod.scheduledTime().minusMinutes(5));
        assertThat(mixedCaseDto.platform()).isEqualTo("Bstg. 3");

        // Lowercase, no period, with and without a space: "bstg 21" / "bstg21".
        Departure lowercaseNoPeriod = departures.get(1);
        assertThat(lowercaseNoPeriod.platform()).isEqualTo("Bstg. 21");
        DepartureDto lowercaseDto = DepartureDto.from(lowercaseNoPeriod, lowercaseNoPeriod.scheduledTime().minusMinutes(5));
        assertThat(lowercaseDto.platform()).isEqualTo("Bstg. 21");
    }

    /**
     * Regression test for a real bug seen against live KVV data: a
     * destination containing the German "ß" ("Wolfartsweierer Straße")
     * rendered as two garbled characters. Root cause (see {@link
     * TriasDepartureProvider#nextDepartures()}'s own comment): the response
     * body was read into a {@code String} via {@code RestClient}'s default
     * message converter BEFORE being handed to the XML parser, and that
     * converter falls back to ISO-8859-1 whenever the HTTP response's
     * {@code Content-Type} header carries no explicit {@code charset}
     * parameter - exactly what's stubbed below (a bare {@code "text/xml"}
     * header, no {@code ;charset=...}), while the body bytes served are
     * genuinely UTF-8-encoded (this fixture's actual on-disk bytes, served
     * via {@code withBody(byte[])} so WireMock can't silently re-encode
     * them). This fails against the pre-fix code (which read the body as a
     * {@code String} first, mangling it before parsing) and passes once the
     * parser is handed the raw bytes directly.
     */
    @Test
    void nextDeparturesCorrectlyDecodesNonAsciiGermanTextEvenWithNoCharsetInTheContentTypeHeader() {
        wireMockServer.stubFor(post(urlPathEqualTo("/trias"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "text/xml")
                        .withBody(readFixtureBytes("trias-stop-event-response-non-ascii.xml"))));

        var departures = provider.nextDepartures();

        assertThat(departures).hasSize(1);
        assertThat(departures.get(0).destination()).isEqualTo("Wolfartsweierer Straße");
    }

    /**
     * Acceptance criterion 2: the request-building logic must be genuinely
     * testable without any real, non-empty {@code application.yml} values -
     * exactly the state this project is in until MobiData BW grants access
     * (see {@link TransitProperties}). No WireMock server, no network call:
     * this calls {@link TriasDepartureProvider#buildStopEventRequest()}
     * directly and only asserts the result is well-formed XML that still
     * contains a (here, empty) {@code <StopPointRef>} element, and — per a
     * reviewer-caught schema-conformance fix, see {@code
     * buildStopEventRequest()}'s own javadoc — a {@code <RequestorRef>}
     * element that is present with empty content rather than omitted, since
     * {@code RequestorRef} is a required element in every conformant TRIAS
     * request.
     */
    @Test
    void buildStopEventRequestProducesWellFormedXmlFromBlankPlaceholderConfig() {
        TransitProperties blankProperties = new TransitProperties("", "", "");
        TriasDepartureProvider providerWithBlankConfig = new TriasDepartureProvider(blankProperties);

        String requestXml = providerWithBlankConfig.buildStopEventRequest();

        assertThatCode(() -> parseXml(requestXml)).doesNotThrowAnyException();
        assertThat(requestXml).contains("<StopPointRef></StopPointRef>");
        assertThat(requestXml).contains("<StopEventRequest>");
        assertThat(requestXml).contains("<RequestorRef></RequestorRef>");
        assertThat(requestXml).contains("<LocationName>");
    }

    @Test
    void buildStopEventRequestEscapesAndIncludesAConfiguredStopRef() {
        TransitProperties transitProperties = new TransitProperties("", "req&ref", "de:08212:1001");
        TriasDepartureProvider providerWithConfig = new TriasDepartureProvider(transitProperties);

        String requestXml = providerWithConfig.buildStopEventRequest();

        assertThatCode(() -> parseXml(requestXml)).doesNotThrowAnyException();
        assertThat(requestXml).contains("<StopPointRef>de:08212:1001</StopPointRef>");
        assertThat(requestXml).contains("<RequestorRef>req&amp;ref</RequestorRef>");
    }

    private static Document parseXml(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        return factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
    }

    private static String readFixture(String fileName) {
        try {
            Path path = new ClassPathResource("departures/" + fileName).getFile().toPath();
            return Files.readString(path);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Same as {@link #readFixture(String)} but returns the fixture's raw
     * on-disk bytes rather than a decoded {@code String}, so a WireMock stub
     * can serve them byte-for-byte via {@code withBody(byte[])} — needed for
     * {@link #nextDeparturesCorrectlyDecodesNonAsciiGermanTextEvenWithNoCharsetInTheContentTypeHeader()},
     * which must guarantee the bytes actually sent over the wire are
     * genuinely UTF-8-encoded, not re-encoded by some intermediate {@code
     * String} round-trip.
     */
    private static byte[] readFixtureBytes(String fileName) {
        try {
            Path path = new ClassPathResource("departures/" + fileName).getFile().toPath();
            return Files.readAllBytes(path);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
