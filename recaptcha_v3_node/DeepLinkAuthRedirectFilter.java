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

import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.settings.SlingSettingsService;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Set;

import static org.apache.sling.engine.EngineConstants.FILTER_SCOPE_REQUEST;
import static org.apache.sling.engine.EngineConstants.SLING_FILTER_SCOPE;
import static org.osgi.framework.Constants.SERVICE_DESCRIPTION;
import static org.osgi.framework.Constants.SERVICE_RANKING;

@Component(
    service = Filter.class,
    property = {
        SLING_FILTER_SCOPE + "=" + FILTER_SCOPE_REQUEST,
        SERVICE_DESCRIPTION + "=Redirect anonymous deeplink requests to login and return to original URL",
        SERVICE_RANKING + ":Integer=1100"
    }
)
@Designate(ocd = DeepLinkAuthRedirectFilter.Config.class)
public class DeepLinkAuthRedirectFilter implements Filter {

    @ObjectClassDefinition(
        name = "USF Deep Link Authentication Redirect Filter",
        description = "On publish, redirects anonymous requests containing deeplink=true into the OAuth login flow, returning to the original URL after successful login."
    )
    public @interface Config {
        @AttributeDefinition(
            name = "Enabled",
            description = "Enable/disable deeplink authentication redirects."
        )
        boolean enabled() default true;

        @AttributeDefinition(
            name = "Query parameter name",
            description = "Query parameter that activates deeplink authentication handling."
        )
        String deeplinkParam() default "deeplink";

        @AttributeDefinition(
            name = "Query parameter value",
            description = "Value of the deeplink parameter that activates handling."
        )
        String deeplinkValue() default "true";

        @AttributeDefinition(
            name = "Login entrypoint path",
            description = "Path that starts the OAuth login flow."
        )
        String loginEntrypointPath() default "/usfdce/login/validation/j_security_check";

        @AttributeDefinition(
            name = "OAuth config id",
            description = "OAuth config id passed as configid=... (e.g. azureadb2csite)."
        )
        String oauthConfigId() default "azureadb2csite";
    }

    private static final Logger LOG = LoggerFactory.getLogger(DeepLinkAuthRedirectFilter.class);

    private static final String RUNMODE_AUTHOR = "author";
    private static final String USER_ANONYMOUS = "anonymous";

    @Reference
    private SlingSettingsService slingSettingsService;

    private boolean enabled;
    private String deeplinkParam;
    private String deeplinkValue;
    private String loginEntrypointPath;
    private String oauthConfigId;

    @Activate
    @Modified
    protected void activate(Config config) {
        this.enabled = config.enabled();
        this.deeplinkParam = StringUtils.defaultIfBlank(config.deeplinkParam(), "deeplink");
        this.deeplinkValue = StringUtils.defaultIfBlank(config.deeplinkValue(), "true");
        this.loginEntrypointPath = StringUtils.defaultIfBlank(config.loginEntrypointPath(), "/usfdce/login/validation/j_security_check");
        this.oauthConfigId = StringUtils.defaultIfBlank(config.oauthConfigId(), "azureadb2csite");
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        if (!enabled) {
            chain.doFilter(request, response);
            return;
        }

        if (!(request instanceof SlingHttpServletRequest) || !(response instanceof HttpServletResponse)) {
            chain.doFilter(request, response);
            return;
        }

        SlingHttpServletRequest httpRequest = (SlingHttpServletRequest) request;

        if (isAuthor()) {
            chain.doFilter(request, response);
            return;
        }

        if (!isEligibleRequest(httpRequest)) {
            chain.doFilter(request, response);
            return;
        }

        if (!isDeeplinkRequest(httpRequest)) {
            chain.doFilter(request, response);
            return;
        }

        if (isLoggedIn(httpRequest)) {
            chain.doFilter(request, response);
            return;
        }

        String originalUrl = getOriginalUrl(httpRequest);
        String redirectUrl = buildLoginRedirectUrl(originalUrl);
        LOG.debug("Deeplink detected; redirecting anonymous user to {}", redirectUrl);
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        httpResponse.sendRedirect(redirectUrl);
    }

    private boolean isAuthor() {
        Set<String> runModes = slingSettingsService != null ? slingSettingsService.getRunModes() : Set.of();
        return runModes.contains(RUNMODE_AUTHOR);
    }

    private boolean isEligibleRequest(HttpServletRequest request) {
        if (!"GET".equalsIgnoreCase(request.getMethod())) {
            return false;
        }

        String uri = StringUtils.defaultString(request.getRequestURI());
        if (uri.startsWith(loginEntrypointPath) || uri.startsWith("/usfdce/login/validation")) {
            return false;
        }
        if (uri.startsWith("/system/sling/logout")) {
            return false;
        }

        String lower = uri.toLowerCase();
        return !(lower.endsWith(".js")
            || lower.endsWith(".css")
            || lower.endsWith(".png")
            || lower.endsWith(".jpg")
            || lower.endsWith(".jpeg")
            || lower.endsWith(".gif")
            || lower.endsWith(".svg")
            || lower.endsWith(".webp")
            || lower.endsWith(".ico")
            || lower.endsWith(".json")
            || lower.endsWith(".xml"));
    }

    private boolean isDeeplinkRequest(HttpServletRequest request) {
        String value = request.getParameter(deeplinkParam);
        return value != null && value.equalsIgnoreCase(deeplinkValue);
    }

    private boolean isLoggedIn(SlingHttpServletRequest request) {
        try {
            String userId = request.getResourceResolver() != null ? request.getResourceResolver().getUserID() : null;
            return StringUtils.isNotBlank(userId) && !USER_ANONYMOUS.equalsIgnoreCase(userId);
        } catch (RuntimeException exception) {
            LOG.debug("Unable to determine user login state; treating as anonymous", exception);
            return false;
        }
    }

    private String getOriginalUrl(HttpServletRequest request) {
        StringBuilder sb = new StringBuilder();
        sb.append(request.getRequestURL());
        String qs = request.getQueryString();
        if (StringUtils.isNotBlank(qs)) {
            sb.append('?').append(qs);
        }
        return sb.toString();
    }

    private String buildLoginRedirectUrl(String originalUrl) {
        String encodedState = URLEncoder.encode(StringUtils.defaultString(originalUrl), StandardCharsets.UTF_8);
        return loginEntrypointPath
            + "?configid=" + URLEncoder.encode(oauthConfigId, StandardCharsets.UTF_8)
            + "&state=" + encodedState;
    }
}

