package com.familydashboard.weather;

import java.util.Map;

/**
 * Static WMO-weather-code-to-German-condition-text lookup (see {@code
 * docs/weather-widget-plan.md}'s "Data source: Open-Meteo" section). Open-
 * Meteo's {@code weather_code} field documents 25 distinct WMO codes; this
 * covers the common, clearly-distinguishable ones (clear sky through
 * thunderstorm) rather than all 25, with a sane fallback for anything else.
 */
final class WeatherConditionTexts {

    private static final Map<Integer, String> TEXTS_BY_CODE = Map.ofEntries(
            Map.entry(0, "Klarer Himmel"),
            Map.entry(1, "Überwiegend klar"),
            Map.entry(2, "Teilweise bewölkt"),
            Map.entry(3, "Bedeckt"),
            Map.entry(45, "Nebel"),
            Map.entry(48, "Reifnebel"),
            Map.entry(51, "Leichter Sprühregen"),
            Map.entry(53, "Mäßiger Sprühregen"),
            Map.entry(55, "Starker Sprühregen"),
            Map.entry(61, "Leichter Regen"),
            Map.entry(63, "Mäßiger Regen"),
            Map.entry(65, "Starker Regen"),
            Map.entry(71, "Leichter Schneefall"),
            Map.entry(73, "Mäßiger Schneefall"),
            Map.entry(75, "Starker Schneefall"),
            Map.entry(80, "Leichte Regenschauer"),
            Map.entry(81, "Mäßige Regenschauer"),
            Map.entry(82, "Starke Regenschauer"),
            Map.entry(95, "Gewitter"),
            Map.entry(96, "Gewitter mit leichtem Hagel"),
            Map.entry(99, "Gewitter mit starkem Hagel"));

    private static final String FALLBACK_TEXT = "Unbekannt";

    private WeatherConditionTexts() {
    }

    /**
     * German condition text for a WMO {@code weather_code}, or {@value
     * #FALLBACK_TEXT} for a code not in this class's mapped subset.
     */
    static String forCode(int weatherCode) {
        return TEXTS_BY_CODE.getOrDefault(weatherCode, FALLBACK_TEXT);
    }
}
