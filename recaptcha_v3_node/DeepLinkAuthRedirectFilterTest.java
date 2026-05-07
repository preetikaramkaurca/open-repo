/*
 * **********************************************************************
 *  BOUNTEOUS CONFIDENTIAL
 *  ___________________
 *
 *  Copyright 2026 Bounteous
 *  All Rights Reserved.
 *
 *  NOTICE: All information added and modified by Bounteous for AEM
 *  Activate contained herein is, and remains the property
 *  of Bounteous and its suppliers, if any. The intellectual and
 *  technical concepts contained herein are proprietary to Bounteous
 *  and its suppliers and are protected by trade secret or copyright law.
 *  Dissemination of this information or reproduction of this material
 *  is strictly forbidden unless prior written permission is obtained
 *  from Bounteous.
 *  * ***********************************************************************
 */
package com.usfoods.aem.base.core.filters;

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.settings.SlingSettingsService;
import org.apache.sling.testing.mock.osgi.MockOsgi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.servlet.FilterChain;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletException;
import java.io.IOException;
import java.util.HashMap;
import java.util.Set;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class DeepLinkAuthRedirectFilterTest {

    private SlingHttpServletRequest request;
    private HttpServletResponse response;
    private FilterChain filterChain;
    private DeepLinkAuthRedirectFilter filter;
    private SlingSettingsService slingSettingsService;
    private ResourceResolver resourceResolver;

    @BeforeEach
    public void setup() {
        var bundleContext = MockOsgi.newBundleContext();

        request = mock(SlingHttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        filterChain = mock(FilterChain.class);

        slingSettingsService = mock(SlingSettingsService.class);
        when(slingSettingsService.getRunModes()).thenReturn(Set.of("publish"));
        bundleContext.registerService(SlingSettingsService.class, slingSettingsService, null);

        resourceResolver = mock(ResourceResolver.class);
        when(resourceResolver.getUserID()).thenReturn("anonymous");
        when(request.getResourceResolver()).thenReturn(resourceResolver);

        filter = new DeepLinkAuthRedirectFilter();
        MockOsgi.injectServices(filter, bundleContext);
        MockOsgi.activate(filter, bundleContext, new HashMap<String, Object>());
    }

    @Test
    public void testRedirectsAnonymousDeeplinkRequestToOauthEntrypoint() throws IOException, ServletException {
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/products-we-offer");
        when(request.getParameter("deeplink")).thenReturn("true");
        when(request.getRequestURL()).thenReturn(new StringBuffer("https://www.usfoods.com/products-we-offer"));
        when(request.getQueryString()).thenReturn("deeplink=true");

        filter.doFilter(request, response, filterChain);

        verify(response).sendRedirect(org.mockito.ArgumentMatchers.startsWith(
            "/usfdce/login/validation/j_security_check?configid=azureadb2csite&state="
        ));
        verify(filterChain, never()).doFilter(request, response);
    }

    @Test
    public void testDoesNothingOnAuthorRunmode() throws IOException, ServletException {
        when(slingSettingsService.getRunModes()).thenReturn(Set.of("author"));

        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/products-we-offer");
        when(request.getParameter("deeplink")).thenReturn("true");

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }
}

