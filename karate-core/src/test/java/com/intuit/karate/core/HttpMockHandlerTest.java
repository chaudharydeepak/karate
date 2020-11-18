package com.intuit.karate.core;

import com.intuit.karate.http.ApacheHttpClient;
import com.intuit.karate.match.Match;
import com.intuit.karate.match.MatchResult;
import com.intuit.karate.http.HttpConstants;
import com.intuit.karate.http.HttpRequestBuilder;
import com.intuit.karate.http.HttpServer;
import com.intuit.karate.http.Response;

import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.handler.codec.http.cookie.Cookie;
import io.netty.handler.codec.http.cookie.DefaultCookie;


/**
 *
 * @author pthomas3
 */
class HttpMockHandlerTest {

    static final Logger logger = LoggerFactory.getLogger(HttpMockHandlerTest.class);

    MockHandler handler;
    HttpServer server;
    FeatureBuilder mock;
    HttpRequestBuilder http;
    Response response;

    HttpRequestBuilder handle() {
        handler = new MockHandler(mock.build());
        server = new HttpServer(0, handler);
        ScenarioEngine se = ScenarioEngine.forTempUse();
        ApacheHttpClient client = new ApacheHttpClient(se);
        http = new HttpRequestBuilder(client);
        http.url("http://localhost:" + server.getPort());
        return http;
    }

    FeatureBuilder background(String... lines) {
        mock = FeatureBuilder.background(lines);
        return mock;
    }

    private void match(Object actual, Object expected) {
        MatchResult mr = Match.that(actual).isEqualTo(expected);
        assertTrue(mr.pass, mr.message);
    }

    @AfterEach
    void afterEach() {
        server.stop();
    }

    @Test
    void testSimpleGet() {
        background().scenario(
                "pathMatches('/hello')",
                "def response = 'hello world'");
        response = handle().path("/hello").invoke("get");
        match(response.getBodyAsString(), "hello world");
    }

    @Test
    void testUrlWithSpecialCharacters() {
        background().scenario(
                "pathMatches('/hello/{raw}')",
                "def response = { success: true }"
        );
        response = handle().path("/hello/�Ill~Formed@RequiredString!").invoke("get");
        match(response.getBodyConverted(), "{ success: true }");
    }

    @Test
    void testGraalJavaClassLoading() {
        background().scenario(
                "pathMatches('/hello')",
                "def Utils = Java.type('com.intuit.karate.core.MockUtils')",
                "def response = Utils.testBytes"
        );
        response = handle().path("/hello").invoke("get");
        match(response.getBody(), MockUtils.testBytes);
    }

    @Test
    void testEmptyResponse() {
        background().scenario(
                "pathMatches('/hello')",
                "def response = null"
        );
        response = handle().path("/hello").invoke("get");
        match(response.getBody(), HttpConstants.ZERO_BYTES);
    }

    @Test
    void testConfigureResponseHeaders() {
        background("configure responseHeaders = { 'Content-Type': 'text/html' }")
                .scenario(
                        "pathMatches('/hello')",
                        "def response = ''");
        response = handle().path("/hello").invoke("get");
        match(response.getHeader("Content-Type"), "text/html");
    }

    @Test
    void testCookie() {
        background().scenario(
                "pathMatches('/hello')",
                "def response = requestHeaders");
        Cookie cookie = new DefaultCookie("someKey","someValue");
        cookie.setDomain("localhost");
        response = handle().path("/hello").cookie(cookie).invoke("get");
        assertTrue(response.getBodyConverted().toString().contains("someKey=someValue"));
    }
}
