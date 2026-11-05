package com.wealthmax.common.logging;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Checks the facts of logback-spring.xml that the rules depend on. Whether the JSON really prints on stdout
 * needs a started application, so that is checked by hand in step 1h4.
 */
class LogbackConfigFileTest {

    private String rawText;
    private Document config;

    @BeforeEach
    void load() throws Exception {
        try (InputStream in = new ClassPathResource("logback-spring.xml").getInputStream()) {
            rawText = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        config = factory.newDocumentBuilder().parse(new java.io.ByteArrayInputStream(
                rawText.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    @DisplayName("the file is well-formed XML and no longer carries the old 'SaaS' header or the unused JSON pattern")
    void noLeftovers() {
        assertThat(rawText).doesNotContain("SaaS").doesNotContain("JSON_PATTERN");
    }

    @Test
    @DisplayName("the console and file text patterns print the traceId")
    void textPatternsCarryTheTraceId() {
        assertThat(propertyValue("CONSOLE_PATTERN")).contains("%X{traceId");
        assertThat(propertyValue("FILE_PATTERN")).contains("%X{traceId");
    }

    @Test
    @DisplayName("CONSOLE_JSON writes Spring Boot's structured ECS format to the console")
    void jsonAppenderUsesBootsStructuredEncoder() {
        Element appender = appenderNamed("CONSOLE_JSON");

        assertThat(appender.getAttribute("class")).isEqualTo("ch.qos.logback.core.ConsoleAppender");
        Element encoder = (Element) appender.getElementsByTagName("encoder").item(0);
        assertThat(encoder.getAttribute("class"))
                .isEqualTo("org.springframework.boot.logging.logback.StructuredLogEncoder");
        assertThat(encoder.getElementsByTagName("format").item(0).getTextContent().trim()).isEqualTo("ecs");
    }

    @Test
    @DisplayName("staging and prod write JSON to the console (OTH-23)")
    void stagingAndProdLogJson() {
        for (String profile : new String[] {"staging", "prod"}) {
            assertThat(rootAppenderRefs(profile)).as(profile + " root").contains("CONSOLE_JSON");
            assertThat(loggerAppenderRefs(profile, "com.wealthmax")).as(profile + " com.wealthmax")
                    .contains("CONSOLE_JSON");
        }
    }

    @Test
    @DisplayName("local and dev keep readable text on the console")
    void localAndDevKeepReadableText() {
        for (String profile : new String[] {"local", "dev"}) {
            assertThat(rootAppenderRefs(profile)).as(profile + " root").contains("CONSOLE")
                    .doesNotContain("CONSOLE_JSON");
        }
    }

    private String propertyValue(String name) {
        NodeList properties = config.getElementsByTagName("property");
        for (int i = 0; i < properties.getLength(); i++) {
            Element property = (Element) properties.item(i);
            if (name.equals(property.getAttribute("name"))) {
                return property.getAttribute("value");
            }
        }
        throw new AssertionError("no <property name=\"" + name + "\">");
    }

    private Element appenderNamed(String name) {
        NodeList appenders = config.getElementsByTagName("appender");
        for (int i = 0; i < appenders.getLength(); i++) {
            Element appender = (Element) appenders.item(i);
            if (name.equals(appender.getAttribute("name"))) {
                return appender;
            }
        }
        throw new AssertionError("no <appender name=\"" + name + "\">");
    }

    private Element springProfile(String name) {
        NodeList profiles = config.getElementsByTagName("springProfile");
        for (int i = 0; i < profiles.getLength(); i++) {
            Element profile = (Element) profiles.item(i);
            if (name.equals(profile.getAttribute("name"))) {
                return profile;
            }
        }
        throw new AssertionError("no <springProfile name=\"" + name + "\">");
    }

    private java.util.List<String> rootAppenderRefs(String profile) {
        return refsUnder((Element) springProfile(profile).getElementsByTagName("root").item(0));
    }

    private java.util.List<String> loggerAppenderRefs(String profile, String loggerName) {
        NodeList loggers = springProfile(profile).getElementsByTagName("logger");
        for (int i = 0; i < loggers.getLength(); i++) {
            Element logger = (Element) loggers.item(i);
            if (loggerName.equals(logger.getAttribute("name"))) {
                return refsUnder(logger);
            }
        }
        throw new AssertionError("no <logger name=\"" + loggerName + "\"> in " + profile);
    }

    private static java.util.List<String> refsUnder(Element parent) {
        java.util.List<String> refs = new java.util.ArrayList<>();
        for (Node child = parent.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element element && "appender-ref".equals(element.getTagName())) {
                refs.add(element.getAttribute("ref"));
            }
        }
        return refs;
    }
}
