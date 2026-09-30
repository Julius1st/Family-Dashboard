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
        assertThat(onTimeConfirmed.platform()).isEqualTo("3");
        assertThat(onTimeConfirmed.scheduledTime()).isEqualTo(LocalDateTime.of(2026, 10, 1, 8, 15));
        assertThat(onTimeConfirmed.expectedTime()).isEqualTo(LocalDateTime.of(2026, 10, 1, 8, 15));
        assertThat(onTimeConfirmed.status()).isEqualTo(DepartureStatus.ON_TIME);

        // 2. On time, with NO real-time data at all - expectedTime stays null
        // (callers are expected to fall back to scheduledTime for display),
        // and the absence of real-time data must not be misread as a delay.
        Departure onTimeNoRealtime = departures.get(1);
        assertThat(onTimeNoRealtime.line()).isEqualTo("4");
        assertThat(onTimeNoRealtime.destination()).isEqualTo("Durlach Bahnhof");
        assertThat(onTimeNoRealtime.platform()).isEqualTo("1");
        assertThat(onTimeNoRealtime.scheduledTime()).isEqualTo(LocalDateTime.of(2026, 10, 1, 8, 18));
        assertThat(onTimeNoRealtime.expectedTime()).isNull();
        assertThat(onTimeNoRealtime.status()).isEqualTo(DepartureStatus.ON_TIME);

        // 3. Delayed by 3 minutes - matches the design handoff's own "+3 Min" example.
        Departure delayed = departures.get(2);
        assertThat(delayed.line()).isEqualTo("5");
        assertThat(delayed.destination()).isEqualTo("Rheinstetten Rathaus");
        assertThat(delayed.platform()).isEqualTo("2");
        assertThat(delayed.scheduledTime()).isEqualTo(LocalDateTime.of(2026, 10, 1, 8, 20));
        assertThat(delayed.expectedTime()).isEqualTo(LocalDateTime.of(2026, 10, 1, 8, 23));
        assertThat(delayed.status()).isEqualTo(DepartureStatus.DELAYED);

        // 4. Cancelled.
        Departure cancelled = departures.get(3);
        assertThat(cancelled.line()).isEqualTo("2");
        assertThat(cancelled.destination()).isEqualTo("Knielingen");
        assertThat(cancelled.platform()).isEqualTo("4");
        assertThat(cancelled.scheduledTime()).isEqualTo(LocalDateTime.of(2026, 10, 1, 8, 25));
        assertThat(cancelled.status()).isEqualTo(DepartureStatus.CANCELLED);
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
}
