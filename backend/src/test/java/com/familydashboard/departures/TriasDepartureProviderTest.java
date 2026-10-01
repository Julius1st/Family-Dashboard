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

        provider.nextDepartureBoard();

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

        var board = provider.nextDepartureBoard();
        var departures = board.departures();

        assertThat(departures).hasSize(4);

        // Every CallAtStop in the fixture names the same stop ("Marktplatz")
        // - see the stop-name-specific tests below for the "first non-blank
        // wins" extraction logic itself.
        assertThat(board.stopName()).isEqualTo("Marktplatz");

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

        var departures = provider.nextDepartureBoard().departures();

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

        var departures = provider.nextDepartureBoard().departures();

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
     * TriasDepartureProvider#nextDepartureBoard()}'s own comment): the response
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

        var departures = provider.nextDepartureBoard().departures();

        assertThat(departures).hasSize(1);
        assertThat(departures.get(0).destination()).isEqualTo("Wolfartsweierer Straße");
    }

    /**
     * Regression test for a real bug reported against live KVV data: every
     * departure's countdown showed "in 0 Min" regardless of how far away it
     * actually was. Root cause (see {@link
     * TriasDepartureProvider#parseDateTime(String)}'s own javadoc): the old
     * code was {@code OffsetDateTime.parse(...).toLocalDateTime()}, which
     * discards the parsed offset and keeps only the raw digits written in
     * the source string - correct only by coincidence when the source's own
     * offset already matches {@code Europe/Berlin}'s real current offset,
     * which every <em>other</em> fixture in this test class happens to use
     * (always {@code "+02:00"}, matching CEST on their shared {@code
     * 2026-10-01} date). That's exactly the gap that let the bug through: a
     * fixture whose offset always happens to match the assumption the buggy
     * code made can never exercise the bug, regardless of how many other
     * cases it covers.
     *
     * <p>{@code trias-stop-event-response-utc-offset.xml} deliberately uses
     * offsets that do NOT match {@code Europe/Berlin} - literal UTC ({@code
     * "Z"}), explicit {@code "+00:00"}, and an arbitrary unrelated {@code
     * "+05:00"} - so a correct, instant-based conversion is the only way to
     * pass this test; the old offset-stripping code would produce the raw
     * (wrong) source digits unchanged instead of the correct Berlin
     * wall-clock time asserted below.
     */
    @Test
    void nextDeparturesConvertsNonBerlinOffsetTimestampsToTheCorrectBerlinWallClockTime() {
        wireMockServer.stubFor(post(urlPathEqualTo("/trias"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "text/xml")
                        .withBody(readFixture("trias-stop-event-response-utc-offset.xml"))));

        var departures = provider.nextDepartureBoard().departures();
        assertThat(departures).hasSize(2);

        // Source: "2026-10-01T06:15:00Z" / "2026-10-01T06:15:00+00:00" - the
        // same UTC instant, which is 2026-10-01T08:15:00 in Europe/Berlin
        // (CEST, +02:00) on this date. The old offset-stripping code would
        // have wrongly kept the literal "06:15" digits instead.
        Departure utcAndExplicitZeroOffset = departures.get(0);
        assertThat(utcAndExplicitZeroOffset.scheduledTime()).isEqualTo(LocalDateTime.of(2026, 10, 1, 8, 15));
        assertThat(utcAndExplicitZeroOffset.expectedTime()).isEqualTo(LocalDateTime.of(2026, 10, 1, 8, 15));

        // Source: "2026-10-01T13:20:00+05:00" - an offset that is neither
        // Europe/Berlin's nor UTC's, to prove the fix genuinely converts via
        // the instant rather than special-casing "Z"/"+00:00". Equivalent to
        // 2026-10-01T08:20:00 UTC, i.e. 2026-10-01T10:20:00 in Europe/Berlin.
        Departure arbitraryOffset = departures.get(1);
        assertThat(arbitraryOffset.scheduledTime()).isEqualTo(LocalDateTime.of(2026, 10, 1, 10, 20));
        assertThat(arbitraryOffset.expectedTime()).isEqualTo(LocalDateTime.of(2026, 10, 1, 10, 20));
    }

    /**
     * End-to-end regression test for the same "in 0 Min" bug, this time
     * through the full raw-XML -> {@link Departure} -> {@link
     * DepartureDto#countdownMinutes()} path with a known reference "now",
     * closing the gap the DTO-level test ({@code DeparturesControllerTest})
     * can't: that test only ever constructs {@link Departure} test doubles
     * directly with real-clock-relative {@link LocalDateTime} values,
     * bypassing XML parsing (and therefore {@link
     * TriasDepartureProvider#parseDateTime(String)}) entirely.
     *
     * <p><b>"now" must be an independent, hardcoded reference time - never
     * derived from the parsed {@code departure.scheduledTime()} itself.</b>
     * An earlier version of this test computed {@code now} as {@code
     * departure.scheduledTime().minusMinutes(10)}, which is tautological and
     * provides zero regression protection: whether {@code parseDateTime} is
     * correct or buggy, {@code now} and the departure's time shift together
     * by the exact same (possibly wrong) offset error, so {@code
     * Duration.between(now, time)} always comes out to exactly 10 minutes
     * regardless of whether the parse was actually correct - confirmed by
     * reverting {@link TriasDepartureProvider#parseDateTime(String)} to the
     * old offset-stripping {@code .toLocalDateTime()} code and rerunning:
     * that version passed even with the bug reintroduced. Using a fixed
     * {@code LocalDateTime.of(2026, 10, 1, 8, 5, 0)} instead - 10 minutes
     * before the fixture's independently-known-correct Berlin-equivalent
     * time (08:15, see the test above and the fixture's own comment) - means
     * the old buggy code's wrongly-parsed 06:15 would make the departure
     * appear already 1 hour 50 minutes in the past relative to this fixed
     * reference, which {@code countdownMinutes}'s {@code Math.max(0, ...)}
     * flooring collapses to {@code 0} - not {@code 10} - exactly the
     * reported symptom. Re-confirmed by reverting {@code parseDateTime}
     * again and rerunning with this fixed-reference version: it now
     * genuinely fails (asserts 10, gets 0) against the old code.
     */
    @Test
    void endToEndCountdownIsCorrectlyNonZeroWhenTheSourceTimestampUsesANonBerlinOffset() {
        wireMockServer.stubFor(post(urlPathEqualTo("/trias"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "text/xml")
                        .withBody(readFixture("trias-stop-event-response-utc-offset.xml"))));

        var departures = provider.nextDepartureBoard().departures();
        Departure departure = departures.get(0);

        // Fixed, independent reference time - 10 minutes before the
        // fixture's known-correct Berlin-equivalent time of 08:15 (see
        // trias-stop-event-response-utc-offset.xml's own comment: its first
        // entry's "2026-10-01T06:15:00Z"/"...+00:00" is that UTC instant,
        // which is 2026-10-01T08:15:00 in Europe/Berlin CEST). Deliberately
        // NOT derived from departure.scheduledTime() - see this test's
        // javadoc for why that would make the assertion tautological.
        LocalDateTime now = LocalDateTime.of(2026, 10, 1, 8, 5, 0);
        DepartureDto dto = DepartureDto.from(departure, now);

        assertThat(dto.countdownMinutes()).isEqualTo(10);
    }

    /**
     * Zero {@code StopEventResult} entries is a genuinely valid TRIAS
     * response ("no departures in the queried window"), not an error - see
     * {@code DeparturesController}'s own handling of this case. With no
     * {@code CallAtStop} anywhere in the response to extract a {@code
     * StopPointName/Text} from (see {@link DepartureBoard}'s javadoc on why
     * the name lives per-departure, not independently), {@link
     * DepartureBoard#stopName()} must be {@code null} rather than throwing
     * or defaulting to some placeholder - that fallback decision belongs to
     * {@code DeparturesController}/the frontend, not this parsing layer.
     */
    @Test
    void nextDepartureBoardHasANullStopNameWhenTheResponseHasNoDepartures() {
        wireMockServer.stubFor(post(urlPathEqualTo("/trias"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "text/xml")
                        .withBody(readFixture("trias-stop-event-response-empty.xml"))));

        var board = provider.nextDepartureBoard();

        assertThat(board.departures()).isEmpty();
        assertThat(board.stopName()).isNull();
    }

    /**
     * {@code StopPointName/Text} is repeated once per {@code CallAtStop}
     * (i.e. once per departure), never carried independently of the
     * departures list - see {@link DepartureBoard}'s javadoc. This dashboard
     * only ever queries one {@code StopPointRef} per request, so every entry
     * in a real response is expected to name the same stop; {@link
     * TriasDepartureProvider#parseStopEventResponse} takes the first
     * non-blank one it finds and ignores the rest. This fixture's first
     * entry deliberately carries a blank {@code <Text></Text>} and its
     * second entry the real stop name, confirming the extraction loop skips
     * past the blank candidate instead of settling for it (which would
     * otherwise wrongly leave {@link DepartureBoard#stopName()} {@code
     * null} despite a usable name being available one entry later).
     */
    @Test
    void nextDepartureBoardTakesTheFirstNonBlankStopNameAcrossMultipleDepartures() {
        wireMockServer.stubFor(post(urlPathEqualTo("/trias"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "text/xml")
                        .withBody(readFixture("trias-stop-event-response-first-stopname-blank.xml"))));

        var board = provider.nextDepartureBoard();

        assertThat(board.departures()).hasSize(2);
        assertThat(board.stopName()).isEqualTo("Wolfartsweierer Straße");
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
