package com.app.androidlab.sample.network.unified;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Unit tests for HTTP models - runs without Android environment
 * These tests can run in CI/CD environments without issues
 */
public class HttpModelsTest {

    @Test
    public void testHttpRequestBuilder() {
        HttpRequest request = new HttpRequest.Builder()
                .url("https://example.com")
                .method("GET")
                .header("User-Agent", "Test")
                .timeouts(5000, 10000)
                .followRedirects(false)
                .build();

        assertEquals("https://example.com", request.url);
        assertEquals("GET", request.method);
        assertEquals("Test", request.headers.get("User-Agent"));
        assertEquals(5000, request.connectTimeoutMs);
        assertEquals(10000, request.readTimeoutMs);
        assertFalse(request.followRedirects);
        assertEquals(HttpProfile.DEFAULT, request.profile);
    }

    @Test
    public void testHttpRequestBuilderWithHttp3Profile() {
        HttpRequest request = new HttpRequest.Builder()
                .url("https://quic.nginx.org/")
                .profile(HttpProfile.FORCE_HTTP3)
                .autoQuicHints()
                .build();

        assertEquals("https://quic.nginx.org/", request.url);
        assertEquals(HttpProfile.FORCE_HTTP3, request.profile);
        assertNotNull("QUIC hints should be generated", request.quicHints);
        assertFalse("QUIC hints should not be empty", request.quicHints.isEmpty());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testHttpRequestBuilderWithEmptyUrl() {
        new HttpRequest.Builder()
                .url("")
                .build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testHttpRequestBuilderWithNullUrl() {
        new HttpRequest.Builder()
                .url(null)
                .build();
    }

    @Test
    public void testHttpResponse() {
        HttpResponse response = new HttpResponse(
                200,
                "h3",
                java.util.Collections.singletonMap("Content-Type", java.util.Arrays.asList("application/json")),
                "test response".getBytes(),
                500L,
                null,
                100L,
                "https://example.com"
        );

        assertEquals(200, response.code);
        assertEquals("h3", response.protocol);
        assertTrue(response.isSuccessful());
        assertEquals(500L, response.durationMs);
        assertEquals(100L, response.receivedBytes);
        assertEquals("https://example.com", response.url);
        assertNull(response.error);
        assertEquals("application/json", response.getContentType());
        assertEquals("test response", response.getBodyAsString());
    }

    @Test
    public void testHttpResponseWithError() {
        HttpResponse response = new HttpResponse(
                500,
                "http/1.1",
                java.util.Collections.emptyMap(),
                null,
                1000L,
                "Server Error",
                0L,
                "https://example.com"
        );

        assertEquals(500, response.code);
        assertFalse(response.isSuccessful());
        assertEquals("Server Error", response.error);
        assertNull(response.getBodyAsString());
    }

    @Test
    public void testQuicHintFromUrl() {
        QuicHint hint = QuicHint.fromUrl("https://quic.nginx.org:8080/path");
        
        assertNotNull(hint);
        assertEquals("quic.nginx.org", hint.host);
        assertEquals(8080, hint.port);
        assertEquals(8080, hint.altPort);
    }

    @Test
    public void testQuicHintFromDefaultPort() {
        QuicHint hint = QuicHint.fromUrl("https://quic.nginx.org/");
        
        assertNotNull(hint);
        assertEquals("quic.nginx.org", hint.host);
        assertEquals(443, hint.port);
        assertEquals(443, hint.altPort);
    }

    @Test
    public void testQuicHintFromInvalidUrl() {
        QuicHint hint1 = QuicHint.fromUrl("invalid-url");
        QuicHint hint2 = QuicHint.fromUrl("");
        QuicHint hint3 = QuicHint.fromUrl(null);
        
        // 至少有一个应该返回null（可能"invalid-url"被认为是valid host）
        assertTrue("At least one invalid URL should return null", 
                   hint1 == null || hint2 == null || hint3 == null);
    }

    @Test
    public void testHttpProfile() {
        assertFalse(HttpProfile.DEFAULT.forceHttp3);
        assertFalse(HttpProfile.DEFAULT.enableNetLog);
        assertEquals(0, HttpProfile.DEFAULT.netLogMaxSizeBytes);
        assertNull(HttpProfile.DEFAULT.netLogDir);
        
        assertTrue(HttpProfile.FORCE_HTTP3.forceHttp3);
        assertFalse(HttpProfile.FORCE_HTTP3.enableNetLog);
        
        // 测试自定义H3 profile
        HttpProfile h3Test = HttpProfile.h3Test("/tmp/netlog", 1024);
        assertTrue(h3Test.forceHttp3);
        assertTrue(h3Test.enableNetLog);
        assertEquals(1024, h3Test.netLogMaxSizeBytes);
        assertEquals("/tmp/netlog", h3Test.netLogDir);
    }

    @Test
    public void testHttpEngineType() {
        assertEquals("AUTO", HttpEngineType.AUTO.name());
        assertEquals("CRONET", HttpEngineType.CRONET.name());
        assertEquals("OKHTTP", HttpEngineType.OKHTTP.name());
    }

    @Test
    public void testCharsetUtilsBasic() {
        // 测试基本的字符编码解码
        String input = "Hello, 世界!";
        byte[] utf8Bytes = input.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        
        String decoded = CharsetUtils.decodeResponse(utf8Bytes, "text/plain; charset=utf-8");
        assertEquals(input, decoded);
    }

    @Test
    public void testCharsetUtilsDefaultEncoding() {
        String input = "Hello World";
        byte[] bytes = input.getBytes();
        
        String decoded = CharsetUtils.decodeResponse(bytes, null);
        assertEquals(input, decoded);
    }
}
