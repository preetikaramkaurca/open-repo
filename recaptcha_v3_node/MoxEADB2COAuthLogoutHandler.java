/*
 * **********************************************************************
 *  BOUNTEOUS CONFIDENTIAL
 *  ___________________
 *
 *  Copyright 2025 Bounteous
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
package com.usfoods.aem.base.core.services.login.provider;

import com.day.crx.security.token.TokenCookie;
import com.usfoods.aem.base.core.security.dco.DcoJwtSessionService;
import com.usfoods.aem.base.core.services.login.services.MoxEADB2CConfig;
import com.usfoods.aem.base.core.services.login.services.impl.MoxEADB2COAuth2ProviderImpl;
import com.usfoods.aem.base.core.services.login.utils.MoxEADB2COAuth2ProviderUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.sling.auth.core.spi.AuthenticationHandler;
import org.apache.sling.auth.core.spi.AuthenticationInfo;
import org.osgi.framework.Constants;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@Component(name = "MoxE AD B2C - OAuth Logout Handler",
    service = AuthenticationHandler.class,
    property = {
        AuthenticationHandler.PATH_PROPERTY + "=" + "/",
        Constants.SERVICE_RANKING + MoxEADB2COAuthLogoutHandler.SERVICE_RANKING,
    }
)
public class MoxEADB2COAuthLogoutHandler implements AuthenticationHandler {
    private final Logger log = LoggerFactory.getLogger(getClass());

    public static final String SERVICE_RANKING = ":Integer=" + "-5001";

    /**
     * Cookie {@code Domain} for clearing {@code USF_DCO_SESSION} on logout — mirrors login mint logic in
     * {@code AuthenticationServiceImpl} without changing that class.
     */
    private static final String DCO_SESSION_COOKIE_DOMAIN_USFOODS = ".usfoods.com";
    private static final String DCO_SESSION_COOKIE_DOMAIN_USFOOD = ".usfood.com";

    @Reference
    public void setConfigurationAdmin(ConfigurationAdmin configurationAdmin) {
        this.configurationAdmin = configurationAdmin;
    }

    private ConfigurationAdmin configurationAdmin;

    @Reference
    private DcoJwtSessionService dcoJwtSessionService;

    @Reference
    private MoxEADB2COAuth2ProviderImpl b2cOAuth2Provider;

    @Override
    public AuthenticationInfo extractCredentials(HttpServletRequest request, HttpServletResponse response) {
        return null;
    }

    @Override
    public boolean requestCredentials(HttpServletRequest request, HttpServletResponse response) throws IOException {
        return false;
    }

    @Override
    public void dropCredentials(HttpServletRequest request, HttpServletResponse response) throws IOException {

        response.setContentType("text/html");

        log.info("{} executing dropCredentials", MoxEADB2COAuthLogoutHandler.class);
        String operation = request.getParameter("operation");
        String currentPage = request.getParameter("currentPage");

        if (operation != null && operation.equals("b2clogout")) {
            log.info("{} executing b2clogout operation", MoxEADB2COAuthLogoutHandler.class);
            MoxEADRequestHelper.removeConfigId(request, response);
            MoxEADRequestHelper.removeAuthenticatedConfigId(request, response);
            log.info("Start KMLI Cookies delete process ");
            TokenCookie.setCookie(response, "DCE_KMLI", "", 0, "/", null, true, request.isSecure());
            TokenCookie.setCookie(response, "DCE_CUST_KMLI", "", 0, "/", null, true, request.isSecure());
            log.info("DCE_KMLI" + TokenCookie.getCookie(request, "DCE_KMLI"));
            log.info("DCE_CUST_KMLI" + TokenCookie.getCookie(request, "DCE_CUST_KMLI"));

            Configuration providerConfig = configurationAdmin
                .getConfiguration("com.adobe.granite.auth.oauth.provider~azure");
            String consumerKey = providerConfig.getProperties().get("oauth.client.id").toString();

            TokenCookie.setCookie(response, consumerKey, "", 0, "/", null, true, request.isSecure());
            log.info("{} dropped all cookie values", MoxEADB2COAuthLogoutHandler.class);
            MoxEADB2CConfig b2cConfig = b2cOAuth2Provider.getB2Cconfig();
            String loginDomain = b2cConfig.getB2CLoginDomain();
            final String tenant = b2cConfig.getB2CTenantName();
            final String b2cSignInSignUpPolicyName = b2cConfig.getB2CSignInSignUpPolicy();
            log.info("{} redirecting to dropCredentials - {}", MoxEADB2COAuthLogoutHandler.class, MoxEADB2COAuth2ProviderUtils.getHostURL(request));

            //remove single sign-on cookie if it hasn't been validated yet
            removeCookie(request, response, loginDomain);
            if (dcoJwtSessionService != null && dcoJwtSessionService.isEnabled()) {
                dcoJwtSessionService.expireSessionCookieOnLogout(
                    response, resolveDcoSessionCookieDomain(request), request.isSecure());
            }
            response.setHeader("Cache-Control", "no-cache");
            response.setHeader("Expires", "0");
            // Set standard HTTP/1.1 no-cache headers.
            response.setHeader("Cache-Control", "private, no-store, no-cache, must-revalidate");
            // Set standard HTTP/1.0 no-cache header.
            response.setHeader("Pragma", "no-cache");


            response.sendRedirect("https://" + loginDomain
                + "/" + tenant + ".onmicrosoft.com/"
                + b2cSignInSignUpPolicyName + "/oauth2/v2.0/logout?post_logout_redirect_uri="
                + MoxEADB2COAuth2ProviderUtils.getHostURL(request) + currentPage);

        }

    }

    /**
     * Aligns DCO session cookie {@code Domain} on logout with how {@code AuthenticationServiceImpl} sets MOXE / DCO
     * cookies at login (same host checks). Kept local to this handler so login code paths stay untouched.
     */
    static String resolveDcoSessionCookieDomain(HttpServletRequest request) {
        String domain = request.getServerName();
        if (domain.contains(DCO_SESSION_COOKIE_DOMAIN_USFOODS)) {
            domain = DCO_SESSION_COOKIE_DOMAIN_USFOODS;
        } else if (domain.contains(DCO_SESSION_COOKIE_DOMAIN_USFOOD)) {
            domain = DCO_SESSION_COOKIE_DOMAIN_USFOOD;
        }
        return domain;
    }

    void removeCookie(HttpServletRequest request, HttpServletResponse response, String loginDomain) {
        Cookie[] cookies = request.getCookies();
        for (int i = 0; i < cookies.length; i++) {
            String name = cookies[i].getName();
            if (StringUtils.equalsIgnoreCase("DCE_KMLI", name) || StringUtils.equalsIgnoreCase("DCE_CUST_KMLI ", name)) {
                Cookie cookie = new Cookie(name, "");
                cookie.setDomain(loginDomain);
                cookie.setMaxAge(0);
                cookie.setPath("/");
                cookie.setComment("EXPIRING COOKIE at " + System.currentTimeMillis());
                response.addCookie(cookie);
            }
        }
    }
}
