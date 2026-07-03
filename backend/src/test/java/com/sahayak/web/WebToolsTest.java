package com.sahayak.web;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class WebToolsTest {

    @Test
    void weatherCodesReadAsEnglish() {
        assertEquals("clear sky", WebTools.describeWeatherCode(0));
        assertEquals("partly cloudy", WebTools.describeWeatherCode(2));
        assertEquals("overcast", WebTools.describeWeatherCode(3));
        assertEquals("foggy", WebTools.describeWeatherCode(45));
        assertEquals("rain", WebTools.describeWeatherCode(63));
        assertEquals("snow", WebTools.describeWeatherCode(73));
        assertEquals("rain showers", WebTools.describeWeatherCode(81));
        assertEquals("thunderstorm", WebTools.describeWeatherCode(95));
    }

    @Test
    void htmlToTextStripsMarkupAndScripts() {
        String html = """
                <html><head><style>body{color:red}</style>
                <script>alert('x')</script></head>
                <body><h1>Title</h1><p>Hello &amp; welcome.</p>
                <!-- comment --><div>Second   line</div></body></html>""";
        String text = WebTools.htmlToText(html);

        assertFalse(text.contains("<"), text);
        assertFalse(text.contains("alert"), text);
        assertFalse(text.contains("color:red"), text);
        assertEquals("Title\nHello & welcome.\nSecond line", text);
    }
}
