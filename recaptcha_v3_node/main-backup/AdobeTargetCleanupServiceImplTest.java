package com.usfoods.aem.base.core.services.impl.usftools;

import com.sun.net.httpserver.HttpServer;
import com.usfoods.aem.base.core.services.AdobeTargetCleanupService;
import com.usfoods.aem.base.core.services.AdobeTargetCleanupService.AdobeTargetCleanupException;
import com.usfoods.aem.base.core.services.AdobeTargetCleanupService.AdobeTargetCleanupResult;
import com.usfoods.aem.base.core.services.impl.usftools.AdobeTargetCleanupServiceImpl.Response;
import com.usfoods.aem.base.core.services.impl.usftools.AdobeTargetCleanupServiceImpl.TargetHttp;
import com.usfoods.aem.base.core.utils.AemEnvironmentUtilService;
import io.wcm.testing.mock.aem.junit5.AemContext;
import io.wcm.testing.mock.aem.junit5.AemContextExtension;
import org.apache.sling.api.resource.ModifiableValueMap;
import org.apache.sling.api.resource.PersistenceException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(AemContextExtension.class)
class AdobeTargetCleanupServiceImplTest {

    private final AemContext context = new AemContext();
    private final long[] now = {1_000_000L};

    @Test
    void deletesStaleNodesAfterLoadingOffers() {
        String stale = "/content/experience-fragments/usf/xf/master/jcr:content/cq:targetMetadata";
        context.create().resource(stale, "cq:targetOfferId", 15L);
        markPageContent(stale);
        ScriptedHttp http = scriptedOffers("{\"total\":1,\"offers\":[{\"id\":3}]}");

        AdobeTargetCleanupResult result = service(http).cleanup(context.resourceResolver(),
                "/content/experience-fragments/usf");

        assertEquals(1, result.getDeleted());
        assertNull(context.resourceResolver().getResource(stale));
    }

    @Test
    void doesNotDeleteWhenFeatureIsDisabled() {
        String stale = "/content/experience-fragments/usf/xf/master/jcr:content/cq:targetMetadata";
        context.create().resource(stale, "cq:targetOfferId", 15L);
        AdobeTargetCleanupServiceImpl.Config disabled = config();
        when(disabled.enabled()).thenReturn(false);
        AdobeTargetCleanupServiceImpl service = new AdobeTargetCleanupServiceImpl();
        service.activate(disabled);

        AdobeTargetCleanupException error = assertThrows(AdobeTargetCleanupException.class, () -> service.cleanup(
                context.resourceResolver(),
                "/content/experience-fragments/usf"));

        assertEquals(AdobeTargetCleanupService.DISABLED_MESSAGE, error.getMessage());
        assertNotNull(context.resourceResolver().getResource(stale));
    }

    @Test
    void staysDisabledOnProductionEvenWhenFlagIsOn() {
        String stale = "/content/experience-fragments/usf/xf/master/jcr:content/cq:targetMetadata";
        context.create().resource(stale, "cq:targetOfferId", 15L);
        AemEnvironmentUtilService environment = mock(AemEnvironmentUtilService.class);
        when(environment.getRunModes()).thenReturn(Collections.singleton("prod"));
        when(environment.getEnvironment()).thenReturn("prod");
        AdobeTargetCleanupServiceImpl service = new AdobeTargetCleanupServiceImpl();
        service.setEnvironmentUtilService(environment);
        service.activate(config());

        assertFalse(service.isEnabled());
        AdobeTargetCleanupException error = assertThrows(AdobeTargetCleanupException.class, () -> service.cleanup(
                context.resourceResolver(),
                "/content/experience-fragments/usf"));

        assertEquals(AdobeTargetCleanupService.DISABLED_MESSAGE, error.getMessage());
        assertNotNull(context.resourceResolver().getResource(stale));
    }

    @Test
    void leavesNodesInPlaceWhenOffersCallFails() {
        String stale = "/content/experience-fragments/usf/xf/master/jcr:content/cq:targetMetadataJson";
        context.create().resource(stale, "cq:targetOfferId", 15L);
        ScriptedHttp http = new ScriptedHttp();
        http.enqueuePost(200, "{\"access_token\":\"abc\",\"expires_in\":3600}");
        http.enqueueGet(500, "failed");

        assertThrows(AdobeTargetCleanupException.class, () -> service(http).cleanup(
                context.resourceResolver(),
                "/content/experience-fragments/usf"));
        assertNotNull(context.resourceResolver().getResource(stale));
    }

    @Test
    void reusesTokenUntilItExpiresAndThenRequestsANewOne() {
        ScriptedHttp http = new ScriptedHttp();
        http.enqueuePost(200, token("one", 3600));
        http.enqueueGet(200, offers(1, "[{\"id\":10}]"));
        http.enqueueGet(200, offers(1, "[{\"id\":10}]"));
        AdobeTargetCleanupServiceImpl service = service(http);

        Set<Long> first = service.loadOfferIds();
        Set<Long> second = service.loadOfferIds();

        assertEquals(Set.of(10L), first);
        assertEquals(first, second);
        assertEquals(1, http.postCount());
        assertEquals(2, http.getCount());
        assertEquals("https://mc.adobe.io/tenant/target/offers?workspace=workspace", http.getUrl(0));
        assertTrue(http.lastPostBody().contains("client_id=client"));
        assertTrue(http.lastPostBody().contains("client_secret=secret"));
        assertTrue(http.lastPostBody().contains("grant_type=client_credentials"));
        assertTrue(http.lastPostBody().contains("scope=openid%2CAdobeID"));
        assertEquals("Bearer one", http.lastHeader("Authorization"));
        assertEquals("client", http.lastHeader("x-api-key"));
        assertEquals(AdobeTargetCleanupServiceImpl.TARGET_ACCEPT, http.lastHeader("Accept"));

        now[0] = now[0] + 3_600_000L;
        http.enqueuePost(200, token("two", 3600));
        http.enqueueGet(200, offers(1, "[{\"id\":11}]"));

        assertEquals(Set.of(11L), service.loadOfferIds());
        assertEquals(2, http.postCount());
        assertEquals("Bearer two", http.lastHeader("Authorization"));
    }

    @Test
    void refreshesTokenOnceWhenOffersCallIsUnauthorized() {
        ScriptedHttp http = new ScriptedHttp();
        http.enqueuePost(200, token("one", 3600));
        http.enqueueGet(401, "");
        http.enqueuePost(200, token("two", 3600));
        http.enqueueGet(200, offers(1, "[{\"id\":5},{\"id\":\"8\"}]"));

        assertEquals(Set.of(5L, 8L), service(http).loadOfferIds());
        assertEquals(2, http.postCount());
        assertEquals("Bearer two", http.lastHeader("Authorization"));
    }

    @Test
    void pagesUntilTotalIsReached() {
        ScriptedHttp http = new ScriptedHttp();
        http.enqueuePost(200, token("one", 3600));
        http.enqueueGet(200, offers(3, "[{\"id\":1},{\"id\":\"2\"}]"));
        http.enqueueGet(200, offers(3, "[{\"id\":3}]"));

        assertEquals(Set.of(1L, 2L, 3L), service(http).loadOfferIds());
        assertEquals(2, http.getCount());
        assertTrue(http.getUrl(1).contains("limit=200"));
        assertTrue(http.getUrl(1).contains("offset=2"));
    }

    @Test
    void doesNotCallTargetWhenConfigurationIsIncomplete() {
        ScriptedHttp http = new ScriptedHttp();
        AdobeTargetCleanupServiceImpl.Config incomplete = config();
        when(incomplete.clientId()).thenReturn("");
        AdobeTargetCleanupServiceImpl service = new AdobeTargetCleanupServiceImpl();
        service.activate(incomplete);
        service.useTargetHttp(http, () -> now[0]);

        AdobeTargetCleanupException error = assertThrows(AdobeTargetCleanupException.class, service::loadOfferIds);

        assertTrue(error.getMessage().contains("USF_TARGET_CLIENT_ID"));
        assertEquals(0, http.postCount());
    }

    @Test
    void ignoresBlankCredentialsWhenFeatureIsDisabled() {
        String stale = "/content/experience-fragments/usf/xf/master/jcr:content/cq:targetMetadata";
        context.create().resource(stale, "cq:targetOfferId", 15L);
        ScriptedHttp http = new ScriptedHttp();
        AdobeTargetCleanupServiceImpl service = new AdobeTargetCleanupServiceImpl();
        service.activate(blankConfig());
        service.useTargetHttp(http, () -> now[0]);

        AdobeTargetCleanupException error = assertThrows(AdobeTargetCleanupException.class, () -> service.cleanup(
                context.resourceResolver(),
                "/content/experience-fragments/usf"));

        assertEquals(AdobeTargetCleanupService.DISABLED_MESSAGE, error.getMessage());
        assertTrue(service.missingConfiguration().contains("USF_TARGET_CLIENT_SECRET"));
        assertEquals(0, http.postCount());
        assertNotNull(context.resourceResolver().getResource(stale));
    }

    @Test
    void rejectsOffersResponseWithoutAnOffersArray() {
        ScriptedHttp http = new ScriptedHttp();
        http.enqueuePost(200, token("one", 3600));
        http.enqueueGet(200, "{\"total\":1}");

        assertThrows(AdobeTargetCleanupException.class, () -> service(http).loadOfferIds());
    }

    @Test
    void deletesStaleOfferNodesUnderJcrContent() throws PersistenceException {
        String kept = "/content/experience-fragments/usf/xf/master/jcr:content/cq:targetMetadata";
        String stale = "/content/experience-fragments/usf/xf/master/jcr:content/cq:targetMetadataJson";
        String nested = "/content/experience-fragments/usf/xf/master/jcr:content/root/teaser/cq:targetMetadata";
        String outside = "/content/experience-fragments/usf/xf/cq:targetMetadata";
        String numericText = "/content/experience-fragments/usf/xf/master/jcr:content/root/plain/cq:targetMetadataJson";
        String noProperty = "/content/experience-fragments/usf/xf/master/jcr:content/root/note/cq:targetMetadata";
        String notNumeric = "/content/experience-fragments/usf/xf/master/jcr:content/root/bad/cq:targetMetadataJson";

        context.create().resource(kept, "cq:targetOfferId", 10L);
        context.create().resource(stale, "cq:targetOfferId", 99L);
        context.create().resource(nested, "cq:targetOfferId", 50);
        context.create().resource(outside, "cq:targetOfferId", 50L);
        context.create().resource(numericText, "cq:targetOfferId", "77");
        context.create().resource(noProperty, "jcr:title", "no offer");
        context.create().resource(notNumeric, "cq:targetOfferId", "not-a-number");
        markPageContent(kept);

        AdobeTargetCleanupResult result = new AdobeTargetCleanupServiceImpl().deleteStaleMetadata(
                context.resourceResolver(),
                "/content/experience-fragments/usf/",
                Set.of(10L));

        assertNotNull(context.resourceResolver().getResource(kept));
        assertNull(context.resourceResolver().getResource(stale));
        assertNull(context.resourceResolver().getResource(nested));
        assertNotNull(context.resourceResolver().getResource(outside));
        assertNull(context.resourceResolver().getResource(numericText));
        assertNotNull(context.resourceResolver().getResource(noProperty));
        assertNotNull(context.resourceResolver().getResource(notNumeric));
        assertEquals(3, result.getDeleted());
        assertEquals(1, result.getKept());
        assertEquals(1, result.getSkipped());
        assertEquals(4, result.getExamined());
        assertEquals(1, result.getOfferCount());
        assertTrue(result.getDeletedPaths().contains(stale));
        assertTrue(result.toJson().contains("\"success\":true"));
    }

    @Test
    void findsMetadataWhenTheSelectedPathIsAlreadyUnderJcrContent() throws PersistenceException {
        String stale = "/content/experience-fragments/usf/xf/master/jcr:content/root/teaser/cq:targetMetadataJson";
        context.create().resource(stale, "cq:targetOfferId", 4L);
        markPageContent(stale);

        AdobeTargetCleanupResult result = new AdobeTargetCleanupServiceImpl().deleteStaleMetadata(
                context.resourceResolver(),
                "/content/experience-fragments/usf/xf/master/jcr:content/root",
                Set.of(9L));

        assertNull(context.resourceResolver().getResource(stale));
        assertEquals(1, result.getDeleted());
    }

    @Test
    void rejectsPathsOutsideExperienceFragments() {
        context.create().resource("/content/www/home/jcr:content/cq:targetMetadata", "cq:targetOfferId", 1L);

        assertThrows(AdobeTargetCleanupException.class, () -> new AdobeTargetCleanupServiceImpl().deleteStaleMetadata(
                context.resourceResolver(),
                "/content/www/home",
                Set.of()));
        assertNotNull(context.resourceResolver().getResource(
                "/content/www/home/jcr:content/cq:targetMetadata"));
    }

    @Test
    void leavesMetadataUnderJcrContentThatIsNotPageContent() throws PersistenceException {
        String stale = "/content/experience-fragments/usf/xf/master/jcr:content/cq:targetMetadata";
        context.create().resource(stale, "cq:targetOfferId", 15L);
        setPrimaryType("/content/experience-fragments/usf/xf/master/jcr:content", "nt:unstructured");

        AdobeTargetCleanupResult result = new AdobeTargetCleanupServiceImpl().deleteStaleMetadata(
                context.resourceResolver(),
                "/content/experience-fragments/usf",
                Set.of());

        assertNotNull(context.resourceResolver().getResource(stale));
        assertEquals(0, result.getDeleted());
        assertEquals(0, result.getExamined());
    }

    @Test
    void postsTheImsFormAndReadsTheTokenBody() throws IOException {
        AtomicReference<String> contentType = new AtomicReference<>();
        AtomicReference<String> requestBody = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/ims/token/v3", exchange -> {
            contentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = "{\"access_token\":\"abc\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(response);
            }
        });
        server.start();
        try {
            String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/ims/token/v3";
            Response result = AdobeTargetCleanupServiceImpl.postForm(
                    url,
                    "client_id=client&client_secret=secret&grant_type=client_credentials&scope=openid");

            assertEquals(200, result.getStatusCode());
            assertTrue(result.getBody().contains("abc"));
            assertEquals("application/x-www-form-urlencoded", contentType.get());
            assertTrue(requestBody.get().contains("grant_type=client_credentials"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void sendsTargetOfferHeaders() throws IOException {
        AtomicReference<String> authorization = new AtomicReference<>();
        AtomicReference<String> apiKey = new AtomicReference<>();
        AtomicReference<String> accept = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/tenant/target/offers", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            apiKey.set(exchange.getRequestHeaders().getFirst("x-api-key"));
            accept.set(exchange.getRequestHeaders().getFirst("Accept"));
            byte[] response = "{\"offers\":[]}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(response);
            }
        });
        server.start();
        try {
            String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/tenant/target/offers?workspace=1";
            Response result = AdobeTargetCleanupServiceImpl.get(url, Map.of(
                    "Authorization", "Bearer token",
                    "x-api-key", "client",
                    "Accept", AdobeTargetCleanupServiceImpl.TARGET_ACCEPT));

            assertEquals(200, result.getStatusCode());
            assertEquals("Bearer token", authorization.get());
            assertEquals("client", apiKey.get());
            assertEquals(AdobeTargetCleanupServiceImpl.TARGET_ACCEPT, accept.get());
        } finally {
            server.stop(0);
        }
    }

    private void markPageContent(String path) {
        int content = path.indexOf("/jcr:content");
        String contentPath = path.substring(0, content + "/jcr:content".length());
        setPrimaryType(contentPath, "cq:PageContent");
    }

    private void setPrimaryType(String path, String primaryType) {
        ModifiableValueMap properties = context.resourceResolver().getResource(path).adaptTo(ModifiableValueMap.class);
        properties.put("jcr:primaryType", primaryType);
    }

    private AdobeTargetCleanupServiceImpl service(ScriptedHttp http) {
        AdobeTargetCleanupServiceImpl service = new AdobeTargetCleanupServiceImpl();
        service.activate(config());
        service.useTargetHttp(http, () -> now[0]);
        return service;
    }

    private ScriptedHttp scriptedOffers(String offersJson) {
        ScriptedHttp http = new ScriptedHttp();
        http.enqueuePost(200, "{\"access_token\":\"abc\",\"expires_in\":3600}");
        http.enqueueGet(200, offersJson);
        return http;
    }

    private AdobeTargetCleanupServiceImpl.Config blankConfig() {
        AdobeTargetCleanupServiceImpl.Config configuration = mock(AdobeTargetCleanupServiceImpl.Config.class);
        when(configuration.enabled()).thenReturn(false);
        when(configuration.clientId()).thenReturn("");
        when(configuration.clientSecret()).thenReturn(null);
        when(configuration.scopes()).thenReturn("  ");
        when(configuration.tenantId()).thenReturn("");
        when(configuration.workspaceId()).thenReturn("");
        return configuration;
    }

    private AdobeTargetCleanupServiceImpl.Config config() {
        AdobeTargetCleanupServiceImpl.Config configuration = mock(AdobeTargetCleanupServiceImpl.Config.class);
        when(configuration.enabled()).thenReturn(true);
        when(configuration.clientId()).thenReturn("client");
        when(configuration.clientSecret()).thenReturn("secret");
        when(configuration.scopes()).thenReturn("openid,AdobeID");
        when(configuration.tenantId()).thenReturn("tenant");
        when(configuration.workspaceId()).thenReturn("workspace");
        return configuration;
    }

    private String token(String value, int expiresIn) {
        return "{\"access_token\":\"" + value + "\",\"expires_in\":" + expiresIn + "}";
    }

    private String offers(int total, String offersJson) {
        return "{\"total\":" + total + ",\"offers\":" + offersJson + "}";
    }

    private static final class ScriptedHttp implements TargetHttp {

        private final Queue<Response> posts = new ArrayDeque<>();
        private final Queue<Response> gets = new ArrayDeque<>();
        private final List<String> postBodies = new ArrayList<>();
        private final List<String> getUrls = new ArrayList<>();
        private final List<Map<String, String>> getHeaders = new ArrayList<>();

        private void enqueuePost(int statusCode, String body) {
            posts.add(new Response(statusCode, body));
        }

        private void enqueueGet(int statusCode, String body) {
            gets.add(new Response(statusCode, body));
        }

        private int postCount() {
            return postBodies.size();
        }

        private int getCount() {
            return getUrls.size();
        }

        private String lastPostBody() {
            return postBodies.get(postBodies.size() - 1);
        }

        private String getUrl(int index) {
            return getUrls.get(index);
        }

        private String lastHeader(String name) {
            return getHeaders.get(getHeaders.size() - 1).get(name);
        }

        @Override
        public Response postForm(String url, String formBody) {
            postBodies.add(formBody);
            return posts.remove();
        }

        @Override
        public Response get(String url, Map<String, String> headers) {
            getUrls.add(url);
            getHeaders.add(headers);
            return gets.remove();
        }
    }
}
