package com.usfoods.aem.base.core.servlets;

import com.usfoods.aem.base.core.services.AdobeTargetCleanupService;
import com.usfoods.aem.base.core.services.AdobeTargetCleanupService.AdobeTargetCleanupException;
import com.usfoods.aem.base.core.services.AdobeTargetCleanupService.AdobeTargetCleanupResult;
import com.usfoods.aem.base.core.util.ServiceUtil;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import javax.servlet.ServletException;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CleanupAdobeTargetIdsServletTest {

    @Mock
    private SlingHttpServletRequest request;

    @Mock
    private SlingHttpServletResponse response;

    @Mock
    private ResourceResolver resolver;

    @Mock
    private Resource resource;

    @Mock
    private AdobeTargetCleanupService cleanupService;

    private CleanupAdobeTargetIdsServlet servlet;
    private MockedStatic<ServiceUtil> serviceUtilMock;
    private StringWriter body;

    @BeforeEach
    void setUp() throws IOException {
        servlet = new CleanupAdobeTargetIdsServlet();
        servlet.setCleanupService(cleanupService);
        serviceUtilMock = Mockito.mockStatic(ServiceUtil.class);
        serviceUtilMock.when(ServiceUtil::runModeIsAuthor).thenReturn(true);
        body = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(body));
        when(request.getResourceResolver()).thenReturn(resolver);
        when(resolver.getUserID()).thenReturn("author");
        when(cleanupService.isEnabled()).thenReturn(true);
    }

    @AfterEach
    void tearDown() {
        serviceUtilMock.close();
    }

    @Test
    void showsDisabledMessageWhenFeatureIsOff() throws ServletException, IOException {
        when(cleanupService.isEnabled()).thenReturn(false);

        servlet.doPost(request, response);

        verify(response).setStatus(403);
        assertTrue(body.toString().contains(AdobeTargetCleanupService.DISABLED_MESSAGE));
        verify(cleanupService, never()).cleanup(any(), any());
        assertTrue(body.toString().contains("\"enabled\":false"));
    }

    @Test
    void statusCallReportsDisabledWithoutRunningCleanup() throws ServletException, IOException {
        when(cleanupService.isEnabled()).thenReturn(false);

        servlet.doGet(request, response);

        verify(response).setStatus(403);
        assertTrue(body.toString().contains(AdobeTargetCleanupService.DISABLED_MESSAGE));
        assertTrue(body.toString().contains("\"enabled\":false"));
        verify(cleanupService, never()).cleanup(any(), any());
    }

    @Test
    void statusCallReportsEnabled() throws ServletException, IOException {
        servlet.doGet(request, response);

        verify(response).setStatus(200);
        assertTrue(body.toString().contains("\"enabled\":true"));
    }

    @Test
    void reportsMissingConfigurationWithoutRunningCleanup() throws ServletException, IOException {
        when(cleanupService.missingConfiguration()).thenReturn("clientId (USF_TARGET_CLIENT_ID)");

        servlet.doPost(request, response);

        verify(response).setStatus(503);
        assertTrue(body.toString().contains(AdobeTargetCleanupService.INCOMPLETE_CONFIGURATION_PREFIX));
        assertTrue(body.toString().contains("USF_TARGET_CLIENT_ID"));
        assertTrue(body.toString().contains("\"enabled\":true"));
        verify(cleanupService, never()).cleanup(any(), any());
    }

    @Test
    void statusCallReportsMissingConfiguration() throws ServletException, IOException {
        when(cleanupService.missingConfiguration()).thenReturn("clientSecret (USF_TARGET_CLIENT_SECRET)");

        servlet.doGet(request, response);

        verify(response).setStatus(503);
        assertTrue(body.toString().contains("USF_TARGET_CLIENT_SECRET"));
        verify(cleanupService, never()).cleanup(any(), any());
    }

    @Test
    void disabledStatusIgnoresBlankCredentials() throws ServletException, IOException {
        when(cleanupService.isEnabled()).thenReturn(false);
        when(cleanupService.missingConfiguration()).thenReturn("clientId (USF_TARGET_CLIENT_ID)");

        servlet.doGet(request, response);

        verify(response).setStatus(403);
        assertTrue(body.toString().contains(AdobeTargetCleanupService.DISABLED_MESSAGE));
        assertTrue(!body.toString().contains("USF_TARGET_CLIENT_ID"));
    }

    @Test
    void returnsNotFoundWhenNotAuthor() throws ServletException, IOException {
        serviceUtilMock.when(ServiceUtil::runModeIsAuthor).thenReturn(false);

        servlet.doPost(request, response);

        verify(response).setStatus(404);
        verify(cleanupService, never()).cleanup(resolver, "/content/experience-fragments");
    }

    @Test
    void rejectsAnonymousUsers() throws ServletException, IOException {
        when(resolver.getUserID()).thenReturn("anonymous");

        servlet.doPost(request, response);

        verify(response).setStatus(403);
        assertTrue(body.toString().contains("\"success\":false"));
    }

    @Test
    void rejectsPathsOutsideExperienceFragments() throws ServletException, IOException {
        when(request.getParameter("path")).thenReturn("/content/www/home");

        servlet.doPost(request, response);

        verify(response).setStatus(400);
        verify(cleanupService, never()).cleanup(resolver, "/content/www/home");
    }

    @Test
    void returnsNotFoundWhenPathDoesNotExist() throws ServletException, IOException {
        when(request.getParameter("path")).thenReturn("/content/experience-fragments/missing");
        when(resolver.getResource("/content/experience-fragments/missing")).thenReturn(null);

        servlet.doPost(request, response);

        verify(response).setStatus(404);
    }

    @Test
    void returnsCleanupSummary() throws ServletException, IOException {
        when(request.getParameter("path")).thenReturn("/content/experience-fragments/usf/");
        when(resolver.getResource("/content/experience-fragments/usf")).thenReturn(resource);
        AdobeTargetCleanupResult result = new AdobeTargetCleanupResult(
                "Cleanup finished.",
                "/content/experience-fragments/usf",
                2,
                1,
                0,
                1,
                0,
                Collections.singletonList("/content/experience-fragments/usf/xf/jcr:content/cq:targetMetadata"),
                false);
        when(cleanupService.cleanup(resolver, "/content/experience-fragments/usf")).thenReturn(result);

        servlet.doPost(request, response);

        verify(response).setStatus(200);
        assertTrue(body.toString().contains("\"deleted\":1"));
        assertTrue(body.toString().contains("cq:targetMetadata"));
    }

    @Test
    void returnsServerErrorWhenCleanupFails() throws ServletException, IOException {
        when(request.getParameter("path")).thenReturn("/content/experience-fragments/usf");
        when(resolver.getResource("/content/experience-fragments/usf")).thenReturn(resource);
        when(cleanupService.cleanup(resolver, "/content/experience-fragments/usf"))
                .thenThrow(new AdobeTargetCleanupException("Adobe IMS token request failed with status 401"));

        servlet.doPost(request, response);

        verify(response).setStatus(500);
        assertTrue(body.toString().contains("status 401"));
        assertTrue(body.toString().contains("\"success\":false"));
    }
}
