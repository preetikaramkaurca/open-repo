package com.usfoods.aem.base.core.services.impl.usftools;

import com.fasterxml.jackson.databind.JsonNode;
import com.usfoods.aem.base.core.services.AdobeTargetCleanupService;
import com.usfoods.aem.base.core.services.AdobeTargetCleanupService.AdobeTargetCleanupException;
import com.usfoods.aem.base.core.services.AdobeTargetCleanupService.AdobeTargetCleanupResult;
import com.usfoods.aem.base.core.util.JsonUtil;
import com.usfoods.aem.base.core.utils.AemEnvironmentUtilService;
import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.resource.PersistenceException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ValueMap;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.LongSupplier;

@Component(
        immediate = true,
        service = AdobeTargetCleanupService.class,
        name = "com.usfoods.aem.base.core.services.impl.usftools.AdobeTargetCleanupServiceImpl")
@Designate(ocd = AdobeTargetCleanupServiceImpl.Config.class)
public class AdobeTargetCleanupServiceImpl implements AdobeTargetCleanupService {

    public static final String DEFAULT_SCOPES = "openid,AdobeID,target_sdk,additional_info.projectedProductContext,"
            + "read_organizations,additional_info.roles";
    static final String TARGET_ACCEPT = "application/vnd.adobe.target.v2+json";

    private static final Logger LOG = LoggerFactory.getLogger(AdobeTargetCleanupServiceImpl.class);
    private static final String PRODUCTION = "prod";
    private static final String TOKEN_URL = "https://ims-na1.adobelogin.com/ims/token/v3";
    private static final String JCR_CONTENT = "jcr:content";
    private static final String JCR_PRIMARY_TYPE = "jcr:primaryType";
    private static final String PAGE_CONTENT = "cq:PageContent";
    private static final String TARGET_METADATA = "cq:targetMetadata";
    private static final String TARGET_METADATA_JSON = "cq:targetMetadataJson";
    private static final String TARGET_OFFER_ID = "cq:targetOfferId";
    private static final long EXPIRY_BUFFER_MILLIS = 60_000L;
    private static final long DEFAULT_EXPIRES_SECONDS = 300L;
    private static final int PAGE_SIZE = 200;
    private static final int MAX_PAGES = 500;
    private static final int MAX_OFFERS = 100_000;
    private static final int MAX_REPORTED_PATHS = 200;
    private static final int CONNECT_TIMEOUT_MILLIS = 10_000;
    private static final int READ_TIMEOUT_MILLIS = 60_000;

    private final Object tokenLock = new Object();

    private volatile String clientId = "";
    private volatile String clientSecret = "";
    private volatile String scopes = DEFAULT_SCOPES;
    private volatile String tenantId = "";
    private volatile String workspaceId = "";
    private volatile boolean enabled;

    private TargetHttp targetHttp = new UrlConnectionHttp();
    private LongSupplier nowMillis = System::currentTimeMillis;
    private String accessToken;
    private long expiresAtMillis;

    private AemEnvironmentUtilService environmentUtilService;

    @Reference
    protected void setEnvironmentUtilService(AemEnvironmentUtilService environmentUtilService) {
        this.environmentUtilService = environmentUtilService;
    }

    @Activate
    @Modified
    protected void activate(Config config) {
        this.enabled = config.enabled();
        this.clientId = StringUtils.trimToEmpty(config.clientId());
        this.clientSecret = StringUtils.trimToEmpty(config.clientSecret());
        this.scopes = StringUtils.trimToEmpty(config.scopes());
        this.tenantId = StringUtils.trimToEmpty(config.tenantId());
        this.workspaceId = StringUtils.trimToEmpty(config.workspaceId());
        clearToken();
        LOG.info("Adobe Target cleanup configuration updated. enabled: {}, production: {}, clientId configured: {}",
                isEnabled(), isProduction(), StringUtils.isNotBlank(this.clientId));
    }

    void useTargetHttp(TargetHttp targetHttp, LongSupplier nowMillis) {
        this.targetHttp = targetHttp;
        this.nowMillis = nowMillis;
        clearToken();
    }

    @Override
    public boolean isEnabled() {
        return enabled && !isProduction();
    }

    @Override
    public String missingConfiguration() {
        return missingFields(clientId, clientSecret, scopes, tenantId, workspaceId);
    }

    @Override
    public AdobeTargetCleanupResult cleanup(ResourceResolver resolver, String path) {
        if (!isEnabled()) {
            throw new AdobeTargetCleanupException(DISABLED_MESSAGE);
        }
        String normalized = AdobeTargetCleanupService.normalizePath(path);
        if (!AdobeTargetCleanupService.isAllowedPath(normalized)) {
            throw new AdobeTargetCleanupException("Select a path under /content/experience-fragments.");
        }
        if (resolver == null || resolver.getResource(normalized) == null) {
            throw new AdobeTargetCleanupException("Path does not exist: " + normalized);
        }
        Set<Long> offerIds = loadOfferIds();
        if (offerIds.isEmpty()) {
            LOG.warn("Adobe Target returned no offers for cleanup under {}", normalized);
        }
        try {
            return deleteStaleMetadata(resolver, normalized, offerIds);
        } catch (PersistenceException ex) {
            throw new AdobeTargetCleanupException("Failed to delete stale Adobe Target metadata.", ex);
        }
    }

    Set<Long> loadOfferIds() {
        String id = clientId;
        String secret = clientSecret;
        String scope = scopes;
        String tenant = tenantId;
        String workspace = workspaceId;
        String missing = missingFields(id, secret, scope, tenant, workspace);
        if (StringUtils.isNotBlank(missing)) {
            throw new AdobeTargetCleanupException(INCOMPLETE_CONFIGURATION_PREFIX + missing);
        }
        String token = accessToken(id, secret, scope, false);
        return readAllOffers(id, tenant, workspace, token);
    }

    AdobeTargetCleanupResult deleteStaleMetadata(ResourceResolver resolver, String path, Set<Long> offerIds)
            throws PersistenceException {
        String normalized = AdobeTargetCleanupService.normalizePath(path);
        if (!AdobeTargetCleanupService.isAllowedPath(normalized)) {
            throw new AdobeTargetCleanupException("Select a path under /content/experience-fragments.");
        }
        Resource root = resolver.getResource(normalized);
        if (root == null || !normalized.equals(root.getPath())) {
            throw new AdobeTargetCleanupException("Path does not exist: " + normalized);
        }

        DeleteCounts counts = new DeleteCounts();
        visit(root, isUnderPageContent(root), offerIds, counts);

        List<String> reported = new ArrayList<>();
        int deleted = 0;
        counts.deletePaths.sort(Comparator.comparingInt(String::length).reversed());
        try {
            for (String deletePath : counts.deletePaths) {
                Resource resource = resolver.getResource(deletePath);
                if (resource == null) {
                    continue;
                }
                resolver.delete(resource);
                deleted++;
                if (reported.size() < MAX_REPORTED_PATHS) {
                    reported.add(deletePath);
                }
                LOG.debug("Deleted stale Adobe Target metadata at {}", deletePath);
            }
            if (deleted > 0) {
                resolver.commit();
            }
        } catch (PersistenceException ex) {
            resolver.revert();
            throw ex;
        }

        LOG.info("Adobe Target cleanup under {} examined {} kept {} deleted {}",
                normalized, counts.examined, counts.kept, deleted);
        return new AdobeTargetCleanupResult(
                message(offerIds.size(), deleted),
                normalized,
                offerIds.size(),
                counts.examined,
                counts.kept,
                deleted,
                counts.skipped,
                reported,
                deleted > reported.size());
    }

    static Response postForm(String url, String formBody) throws IOException {
        HttpURLConnection connection = open(url, "POST");
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        byte[] payload = formBody.getBytes(StandardCharsets.UTF_8);
        try (OutputStream output = connection.getOutputStream()) {
            output.write(payload);
        }
        return read(connection);
    }

    static Response get(String url, Map<String, String> headers) throws IOException {
        HttpURLConnection connection = open(url, "GET");
        for (Map.Entry<String, String> header : headers.entrySet()) {
            connection.setRequestProperty(header.getKey(), header.getValue());
        }
        return read(connection);
    }

    private boolean isProduction() {
        if (environmentUtilService == null) {
            return false;
        }
        if (environmentUtilService.getRunModes().contains(PRODUCTION)) {
            return true;
        }
        return PRODUCTION.equalsIgnoreCase(environmentUtilService.getEnvironment());
    }

    private void clearToken() {
        synchronized (tokenLock) {
            accessToken = null;
            expiresAtMillis = 0L;
        }
    }

    private Set<Long> readAllOffers(String id, String tenant, String workspace, String token) {
        Set<Long> offerIds = new LinkedHashSet<>();
        int offset = 0;
        boolean firstPage = true;
        boolean refreshed = false;
        int pages = 0;
        while (pages < MAX_PAGES && offerIds.size() < MAX_OFFERS) {
            pages++;
            String url = firstPage
                    ? offersUrl(tenant, workspace)
                    : offersUrl(tenant, workspace) + "&limit=" + PAGE_SIZE + "&offset=" + offset;
            Response page = getOffers(url, id, token);
            if (page.statusCode == HttpURLConnection.HTTP_UNAUTHORIZED && !refreshed) {
                token = accessToken(id, clientSecret, scopes, true);
                refreshed = true;
                page = getOffers(url, id, token);
            }
            if (page.statusCode != HttpURLConnection.HTTP_OK) {
                throw new AdobeTargetCleanupException(
                        "Adobe Target offers request failed with status " + page.statusCode);
            }
            JsonNode root = readTree(page.body);
            JsonNode offers = root.get("offers");
            if (offers == null || !offers.isArray()) {
                throw new AdobeTargetCleanupException("Adobe Target offers response did not include an offers array.");
            }
            int pageCount = 0;
            int sizeBefore = offerIds.size();
            for (JsonNode offer : offers) {
                pageCount++;
                Long offerId = readJsonId(offer.get("id"));
                if (offerId != null) {
                    offerIds.add(offerId);
                }
            }
            int total = root.path("total").asInt(-1);
            if (pageCount == 0 && total > 0) {
                throw new AdobeTargetCleanupException("Adobe Target reported offers but the offers array was empty.");
            }
            int added = offerIds.size() - sizeBefore;
            if (!hasAnotherPage(firstPage, pageCount, total, offset, added)) {
                break;
            }
            offset += pageCount;
            firstPage = false;
        }
        return offerIds;
    }

    private boolean hasAnotherPage(boolean firstPage, int pageCount, int total, int offset, int added) {
        if (pageCount == 0) {
            return false;
        }
        if (!firstPage && added == 0) {
            return false;
        }
        if (total >= 0) {
            return offset + pageCount < total;
        }
        return pageCount >= PAGE_SIZE;
    }

    private Response getOffers(String url, String id, String token) {
        try {
            return targetHttp.get(url, headers(id, token));
        } catch (IOException ex) {
            throw new AdobeTargetCleanupException("Adobe Target offers request failed.", ex);
        }
    }

    private String accessToken(String id, String secret, String scope, boolean forceRefresh) {
        synchronized (tokenLock) {
            long now = nowMillis.getAsLong();
            if (!forceRefresh && StringUtils.isNotBlank(accessToken) && now < expiresAtMillis) {
                return accessToken;
            }
            IssuedToken issued = requestToken(id, secret, scope);
            accessToken = issued.value;
            expiresAtMillis = expiryDeadline(now, issued.expiresInSeconds);
            return accessToken;
        }
    }

    private IssuedToken requestToken(String id, String secret, String scope) {
        Response result;
        try {
            result = targetHttp.postForm(TOKEN_URL, formBody(id, secret, scope));
        } catch (IOException ex) {
            throw new AdobeTargetCleanupException("Adobe IMS token request failed.", ex);
        }
        if (result.statusCode != HttpURLConnection.HTTP_OK) {
            throw new AdobeTargetCleanupException(
                    "Adobe IMS token request failed with status " + result.statusCode);
        }
        JsonNode root = readTree(result.body);
        String token = root.path("access_token").asText("");
        if (StringUtils.isBlank(token)) {
            throw new AdobeTargetCleanupException("Adobe IMS token response did not include an access token.");
        }
        long expiresInSeconds = root.path("expires_in").asLong(DEFAULT_EXPIRES_SECONDS);
        if (expiresInSeconds <= 0) {
            expiresInSeconds = DEFAULT_EXPIRES_SECONDS;
        }
        return new IssuedToken(token, expiresInSeconds);
    }

    private long expiryDeadline(long now, long expiresInSeconds) {
        long lifetimeMillis = expiresInSeconds * 1000L;
        long usableMillis = lifetimeMillis > EXPIRY_BUFFER_MILLIS
                ? lifetimeMillis - EXPIRY_BUFFER_MILLIS
                : lifetimeMillis / 2;
        return now + usableMillis;
    }

    private Map<String, String> headers(String id, String token) {
        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Bearer " + token);
        headers.put("x-api-key", id);
        headers.put("Accept", TARGET_ACCEPT);
        return headers;
    }

    private String offersUrl(String tenant, String workspace) {
        return "https://mc.adobe.io/"
                + urlEncode(tenant)
                + "/target/offers?workspace="
                + urlEncode(workspace);
    }

    private String formBody(String id, String secret, String scope) {
        return "client_id=" + formEncode(id)
                + "&client_secret=" + formEncode(secret)
                + "&grant_type=client_credentials"
                + "&scope=" + formEncode(scope);
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String formEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static JsonNode readTree(String body) {
        try {
            return JsonUtil.getJsonMapper().readTree(body);
        } catch (IOException ex) {
            throw new AdobeTargetCleanupException("Adobe response was not valid JSON.", ex);
        }
    }

    private static Long readJsonId(JsonNode idNode) {
        if (idNode == null || idNode.isNull()) {
            return null;
        }
        if (idNode.isIntegralNumber()) {
            return idNode.longValue();
        }
        if (idNode.isTextual() && StringUtils.isNumeric(idNode.asText())) {
            try {
                return Long.valueOf(idNode.asText());
            } catch (NumberFormatException ex) {
                return null;
            }
        }
        return null;
    }

    private static String missingFields(String id, String secret, String scope, String tenant, String workspace) {
        List<String> missing = new ArrayList<>();
        if (StringUtils.isBlank(id)) {
            missing.add("clientId (USF_TARGET_CLIENT_ID)");
        }
        if (StringUtils.isBlank(secret)) {
            missing.add("clientSecret (USF_TARGET_CLIENT_SECRET)");
        }
        if (StringUtils.isBlank(scope)) {
            missing.add("scopes (USF_TARGET_SCOPES)");
        }
        if (StringUtils.isBlank(tenant)) {
            missing.add("tenantId (USF_TARGET_TENANT_ID)");
        }
        if (StringUtils.isBlank(workspace)) {
            missing.add("workspaceId (USF_TARGET_WORKSPACE_ID)");
        }
        return String.join(", ", missing);
    }

    private void visit(Resource resource, boolean underPageContent, Set<Long> offerIds, DeleteCounts counts) {
        boolean pageContentHere = underPageContent || isPageContent(resource);
        if (pageContentHere && isTargetNode(resource.getName())) {
            OfferIdProperty property = OfferIdProperty.read(resource);
            if (property.present && property.value == null) {
                counts.skipped++;
            } else if (property.value != null) {
                counts.examined++;
                if (offerIds.contains(property.value)) {
                    counts.kept++;
                } else {
                    counts.deletePaths.add(resource.getPath());
                    return;
                }
            }
        }
        for (Resource child : resource.getChildren()) {
            visit(child, pageContentHere, offerIds, counts);
        }
    }

    private boolean isUnderPageContent(Resource resource) {
        Resource current = resource;
        while (current != null) {
            if (isPageContent(current)) {
                return true;
            }
            current = current.getParent();
        }
        return false;
    }

    private boolean isPageContent(Resource resource) {
        return JCR_CONTENT.equals(resource.getName())
                && PAGE_CONTENT.equals(resource.getValueMap().get(JCR_PRIMARY_TYPE, String.class));
    }

    private boolean isTargetNode(String name) {
        return TARGET_METADATA.equals(name) || TARGET_METADATA_JSON.equals(name);
    }

    private String message(int offerCount, int deleted) {
        if (offerCount == 0 && deleted == 0) {
            return "Adobe Target returned no offers. No metadata nodes with an offer id were found.";
        }
        if (offerCount == 0) {
            return "Adobe Target returned no offers. Metadata nodes with an offer id were removed.";
        }
        if (deleted == 0) {
            return "Cleanup finished. No stale Adobe Target metadata was found.";
        }
        return "Cleanup finished.";
    }

    private static HttpURLConnection open(String url, String method) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(CONNECT_TIMEOUT_MILLIS);
        connection.setReadTimeout(READ_TIMEOUT_MILLIS);
        connection.setInstanceFollowRedirects(false);
        connection.setRequestMethod(method);
        return connection;
    }

    private static Response read(HttpURLConnection connection) throws IOException {
        try {
            int statusCode = connection.getResponseCode();
            InputStream stream = statusCode >= HttpURLConnection.HTTP_BAD_REQUEST
                    ? connection.getErrorStream()
                    : connection.getInputStream();
            String body = "";
            if (stream != null) {
                try (InputStream input = stream) {
                    body = new String(input.readAllBytes(), StandardCharsets.UTF_8);
                }
            }
            return new Response(statusCode, body);
        } finally {
            connection.disconnect();
        }
    }

    interface TargetHttp {

        Response postForm(String url, String formBody) throws IOException;

        Response get(String url, Map<String, String> headers) throws IOException;
    }

    static final class Response {

        private final int statusCode;
        private final String body;

        Response(int statusCode, String body) {
            this.statusCode = statusCode;
            this.body = body == null ? "" : body;
        }

        int getStatusCode() {
            return statusCode;
        }

        String getBody() {
            return body;
        }
    }

    private static final class UrlConnectionHttp implements TargetHttp {

        @Override
        public Response postForm(String url, String formBody) throws IOException {
            return AdobeTargetCleanupServiceImpl.postForm(url, formBody);
        }

        @Override
        public Response get(String url, Map<String, String> headers) throws IOException {
            return AdobeTargetCleanupServiceImpl.get(url, headers);
        }
    }

    private static final class IssuedToken {

        private final String value;
        private final long expiresInSeconds;

        private IssuedToken(String value, long expiresInSeconds) {
            this.value = value;
            this.expiresInSeconds = expiresInSeconds;
        }
    }

    private static final class DeleteCounts {

        private int examined;
        private int kept;
        private int skipped;
        private final List<String> deletePaths = new ArrayList<>();
    }

    private static final class OfferIdProperty {

        private final boolean present;
        private final Long value;

        private OfferIdProperty(boolean present, Long value) {
            this.present = present;
            this.value = value;
        }

        private static OfferIdProperty read(Resource resource) {
            ValueMap properties = resource.getValueMap();
            if (!properties.containsKey(TARGET_OFFER_ID)) {
                return new OfferIdProperty(false, null);
            }
            Object raw = properties.get(TARGET_OFFER_ID);
            Long value = null;
            if (raw instanceof Number) {
                value = ((Number) raw).longValue();
            } else if (raw instanceof String && StringUtils.isNumeric((String) raw)) {
                try {
                    value = Long.valueOf((String) raw);
                } catch (NumberFormatException ex) {
                    value = null;
                }
            }
            return new OfferIdProperty(true, value);
        }
    }

    @ObjectClassDefinition(
            name = "US Foods - Adobe Target Cleanup",
            description = "Credentials and feature flag for Cleanup Adobe Target Ids. "
                    + "Set USF_TARGET_CLEANUP_ENABLED, USF_TARGET_CLIENT_ID, USF_TARGET_CLIENT_SECRET, "
                    + "USF_TARGET_SCOPES, USF_TARGET_TENANT_ID, and USF_TARGET_WORKSPACE_ID.")
    public @interface Config {

        @AttributeDefinition(
                name = "Enabled",
                description = "Enables Cleanup Adobe Target Ids. "
                        + "Cloud Manager: $[env:USF_TARGET_CLEANUP_ENABLED;default=false]")
        boolean enabled() default false;

        @AttributeDefinition(
                name = "Client ID",
                description = "IMS client id. Cloud Manager: $[env:USF_TARGET_CLIENT_ID;default=]")
        String clientId() default "";

        @AttributeDefinition(
                name = "Client secret",
                description = "IMS client secret. Cloud Manager: $[env:USF_TARGET_CLIENT_SECRET;default=]")
        String clientSecret() default "";

        @AttributeDefinition(
                name = "Scopes",
                description = "IMS scopes. Cloud Manager: $[env:USF_TARGET_SCOPES]")
        String scopes() default DEFAULT_SCOPES;

        @AttributeDefinition(
                name = "Tenant ID",
                description = "Adobe Target tenant id. Cloud Manager: $[env:USF_TARGET_TENANT_ID;default=]")
        String tenantId() default "";

        @AttributeDefinition(
                name = "Workspace ID",
                description = "Adobe Target workspace id. Cloud Manager: $[env:USF_TARGET_WORKSPACE_ID;default=]")
        String workspaceId() default "";
    }
}
