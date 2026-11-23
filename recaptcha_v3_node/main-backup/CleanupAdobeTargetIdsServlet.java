package com.usfoods.aem.base.core.servlets;

import com.usfoods.aem.base.core.services.AdobeTargetCleanupService;
import com.usfoods.aem.base.core.services.AdobeTargetCleanupService.AdobeTargetCleanupException;
import com.usfoods.aem.base.core.services.AdobeTargetCleanupService.AdobeTargetCleanupResult;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.usfoods.aem.base.core.util.JsonUtil;
import com.usfoods.aem.base.core.util.ServiceUtil;
import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.servlets.ServletResolverConstants;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.Servlet;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Author tool endpoint that deletes stale Adobe Target metadata under an experience fragment path.
 */
@Component(
        service = Servlet.class,
        property = {
            ServletResolverConstants.SLING_SERVLET_PATHS + "=/bin/usfoods/cleanup-adobe-target-ids",
            ServletResolverConstants.SLING_SERVLET_METHODS + "=GET",
            ServletResolverConstants.SLING_SERVLET_METHODS + "=POST"
        }
)
public class CleanupAdobeTargetIdsServlet extends SlingAllMethodsServlet {

    private static final long serialVersionUID = 1L;
    private static final Logger LOG = LoggerFactory.getLogger(CleanupAdobeTargetIdsServlet.class);
    private static final String ANONYMOUS_USER = "anonymous";

    private transient AdobeTargetCleanupService cleanupService;

    @Reference
    public void setCleanupService(AdobeTargetCleanupService cleanupService) {
        this.cleanupService = cleanupService;
    }

    @Override
    protected void doGet(SlingHttpServletRequest request, SlingHttpServletResponse response)
            throws ServletException, IOException {
        if (!ServiceUtil.runModeIsAuthor()) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        if (!cleanupService.isEnabled()) {
            writeStatus(response, false);
            return;
        }
        if (writeConfigurationError(response)) {
            return;
        }
        writeStatus(response, true);
    }

    @Override
    protected void doPost(SlingHttpServletRequest request, SlingHttpServletResponse response)
            throws ServletException, IOException {
        if (!ServiceUtil.runModeIsAuthor()) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        if (!cleanupService.isEnabled()) {
            writeStatus(response, false);
            return;
        }
        if (writeConfigurationError(response)) {
            return;
        }
        ResourceResolver resolver = request.getResourceResolver();
        String userId = resolver.getUserID();
        if (StringUtils.isBlank(userId) || ANONYMOUS_USER.equals(userId)) {
            writeError(response, HttpServletResponse.SC_FORBIDDEN, "Sign in to run this tool.");
            return;
        }
        String path = AdobeTargetCleanupService.normalizePath(request.getParameter("path"));
        if (!AdobeTargetCleanupService.isAllowedPath(path)) {
            writeError(response, HttpServletResponse.SC_BAD_REQUEST,
                    "Select a path under /content/experience-fragments.");
            return;
        }
        if (resolver.getResource(path) == null) {
            writeError(response, HttpServletResponse.SC_NOT_FOUND, "Path does not exist: " + path);
            return;
        }
        try {
            AdobeTargetCleanupResult result = cleanupService.cleanup(resolver, path);
            writeJson(response, HttpServletResponse.SC_OK, result.toJson());
        } catch (AdobeTargetCleanupException ex) {
            LOG.error("Adobe Target cleanup failed for {}", path, ex);
            int status = isConfigurationError(ex.getMessage())
                    ? HttpServletResponse.SC_SERVICE_UNAVAILABLE
                    : HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
            writeError(response, status, ex.getMessage());
        }
    }

    private boolean writeConfigurationError(SlingHttpServletResponse response) throws IOException {
        String missing = cleanupService.missingConfiguration();
        if (StringUtils.isBlank(missing)) {
            return false;
        }
        ObjectNode payload = JsonUtil.getJsonMapper().createObjectNode();
        payload.put("success", false);
        payload.put("enabled", true);
        payload.put("message", AdobeTargetCleanupService.INCOMPLETE_CONFIGURATION_PREFIX + missing);
        writeJson(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                JsonUtil.getJsonMapper().writeValueAsString(payload));
        return true;
    }

    private boolean isConfigurationError(String message) {
        return message != null && message.startsWith(AdobeTargetCleanupService.INCOMPLETE_CONFIGURATION_PREFIX);
    }

    private void writeStatus(SlingHttpServletResponse response, boolean enabled) throws IOException {
        ObjectNode payload = JsonUtil.getJsonMapper().createObjectNode();
        payload.put("success", enabled);
        payload.put("enabled", enabled);
        if (!enabled) {
            payload.put("message", AdobeTargetCleanupService.DISABLED_MESSAGE);
        }
        writeJson(response, enabled ? HttpServletResponse.SC_OK : HttpServletResponse.SC_FORBIDDEN,
                JsonUtil.getJsonMapper().writeValueAsString(payload));
    }

    private void writeError(SlingHttpServletResponse response, int status, String message) throws IOException {
        ObjectNode payload = JsonUtil.getJsonMapper().createObjectNode();
        payload.put("success", false);
        payload.put("message", message);
        writeJson(response, status, JsonUtil.getJsonMapper().writeValueAsString(payload));
    }

    private void writeJson(SlingHttpServletResponse response, int status, String json) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(json);
    }
}
