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
import org.apache.sling.auth.core.spi.AuthenticationInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Dictionary;
import java.util.Hashtable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class MoxEADB2COAuthLogoutHandlerTest {

    @Mock
    private ConfigurationAdmin configurationAdmin;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private Configuration oauthProviderConfig;

    @Mock
    private MoxEADB2COAuth2ProviderImpl b2cOAuth2Provider;

    @Mock
    private MoxEADB2CConfig b2cConfig;

    @Mock
    private DcoJwtSessionService dcoJwtSessionService;

    private MoxEADB2COAuthLogoutHandler logoutHandler;

    /**
     * Helper method to set private fields using reflection
     */
    private void setPrivateField(Object targetObject, String fieldName, Object fieldValue) throws Exception {
        Field field = targetObject.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(targetObject, fieldValue);
    }

    @BeforeEach
    public void setUp() throws Exception {
        logoutHandler = new MoxEADB2COAuthLogoutHandler();

        // Set private field using reflection
        setPrivateField(logoutHandler, "configurationAdmin", configurationAdmin);
        setPrivateField(logoutHandler, "dcoJwtSessionService", dcoJwtSessionService);
        setPrivateField(logoutHandler, "b2cOAuth2Provider", b2cOAuth2Provider);
        when(request.getServerName()).thenReturn("localhost");
    }

    private void setupB2cConfig(String loginDomain, String tenant, String signInSignUpPolicy) {
        when(b2cOAuth2Provider.getB2Cconfig()).thenReturn(b2cConfig);
        when(b2cConfig.getB2CLoginDomain()).thenReturn(loginDomain);
        when(b2cConfig.getB2CTenantName()).thenReturn(tenant);
        when(b2cConfig.getB2CSignInSignUpPolicy()).thenReturn(signInSignUpPolicy);
    }

    @Test
    public void testExtractCredentials() {
        // Test that extractCredentials always returns null
        AuthenticationInfo result = logoutHandler.extractCredentials(request, response);
        assertNull(result);
    }

    @Test
    public void testRequestCredentials() throws IOException {
        // Test that requestCredentials always returns false
        boolean result = logoutHandler.requestCredentials(request, response);
        assertFalse(result);
    }

    @Test
    public void resolveDcoSessionCookieDomain_usfoodsHost_returnsParentDomain() {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getServerName()).thenReturn("www.usfoods.com");
        assertEquals(".usfoods.com", MoxEADB2COAuthLogoutHandler.resolveDcoSessionCookieDomain(req));
    }

    @Test
    public void resolveDcoSessionCookieDomain_usfoodHost_returnsParentDomain() {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getServerName()).thenReturn("app.usfood.com");
        assertEquals(".usfood.com", MoxEADB2COAuthLogoutHandler.resolveDcoSessionCookieDomain(req));
    }

    @Test
    public void resolveDcoSessionCookieDomain_localhost_returnsHostOnly() {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getServerName()).thenReturn("localhost");
        assertEquals("localhost", MoxEADB2COAuthLogoutHandler.resolveDcoSessionCookieDomain(req));
    }

    @Test
    public void testDropCredentials_B2CLogout() throws Exception {
        // Set up request parameters
        when(request.getParameter("operation")).thenReturn("b2clogout");
        when(request.getParameter("currentPage")).thenReturn("/content/home");
        when(request.isSecure()).thenReturn(true);
        when(request.getServerName()).thenReturn("www.usfoods.com");
        when(dcoJwtSessionService.isEnabled()).thenReturn(true);

        // Set up OAuth provider configuration
        Dictionary<String, Object> oauthProps = new Hashtable<>();
        oauthProps.put("oauth.client.id", "test-client-id");
        when(configurationAdmin.getConfiguration("com.adobe.granite.auth.oauth.provider~azure"))
            .thenReturn(oauthProviderConfig);
        when(oauthProviderConfig.getProperties()).thenReturn(oauthProps);

setupB2cConfig("test-domain.b2clogin.com", "testtenant", "B2C_1_signin_signup");

        // Set up cookies
        Cookie[] cookies = {
            new Cookie("DCE_KMLI", "test-value"),
            new Cookie("DCE_CUST_KMLI", "test-value2"),
            new Cookie("other-cookie", "other-value")
        };
        when(request.getCookies()).thenReturn(cookies);

        try (MockedStatic<MoxEADRequestHelper> mockedHelper = Mockito.mockStatic(MoxEADRequestHelper.class);
             MockedStatic<TokenCookie> mockedTokenCookie = Mockito.mockStatic(TokenCookie.class);
             MockedStatic<MoxEADB2COAuth2ProviderUtils> mockedUtils = Mockito.mockStatic(MoxEADB2COAuth2ProviderUtils.class)) {

            mockedUtils.when(() -> MoxEADB2COAuth2ProviderUtils.getHostURL(request))
                .thenReturn("https://example.com");

            // Execute
            logoutHandler.dropCredentials(request, response);

            // Verify content type was set
            verify(response).setContentType("text/html");

            // Verify config IDs were removed
            mockedHelper.verify(() -> MoxEADRequestHelper.removeConfigId(request, response));
            mockedHelper.verify(() -> MoxEADRequestHelper.removeAuthenticatedConfigId(request, response));

            // Verify KMLI cookies were cleared
            mockedTokenCookie.verify(() -> TokenCookie.setCookie(response, "DCE_KMLI", "", 0, "/", null, true, true));
            mockedTokenCookie.verify(() -> TokenCookie.setCookie(response, "DCE_CUST_KMLI", "", 0, "/", null, true, true));

            // Verify client ID cookie was cleared
            mockedTokenCookie.verify(() -> TokenCookie.setCookie(response, "test-client-id", "", 0, "/", null, true, true));

            verify(dcoJwtSessionService).expireSessionCookieOnLogout(response, ".usfoods.com", true);

            // Verify cache headers were set
            verify(response).setHeader("Cache-Control", "no-cache");
            verify(response).setHeader("Expires", "0");
            verify(response).setHeader("Cache-Control", "private, no-store, no-cache, must-revalidate");
            verify(response).setHeader("Pragma", "no-cache");

            // Verify redirect URL
            String expectedRedirectUrl = "https://test-domain.b2clogin.com/testtenant.onmicrosoft.com/B2C_1_signin_signup/oauth2/v2.0/logout?post_logout_redirect_uri=https://example.com/content/home";
            verify(response).sendRedirect(expectedRedirectUrl);
        }
    }

    @Test
    public void testDropCredentials_B2CLogout_skipsDcoSessionClearWhenJwtDisabled() throws Exception {
        when(request.getParameter("operation")).thenReturn("b2clogout");
        when(request.getParameter("currentPage")).thenReturn("/content/home");
        when(request.isSecure()).thenReturn(true);
        when(request.getServerName()).thenReturn("www.usfoods.com");
        when(dcoJwtSessionService.isEnabled()).thenReturn(false);

        Dictionary<String, Object> oauthProps = new Hashtable<>();
        oauthProps.put("oauth.client.id", "test-client-id");
        when(configurationAdmin.getConfiguration("com.adobe.granite.auth.oauth.provider~azure"))
            .thenReturn(oauthProviderConfig);
        when(oauthProviderConfig.getProperties()).thenReturn(oauthProps);

setupB2cConfig("test-domain.b2clogin.com", "testtenant", "B2C_1_signin_signup");

        when(request.getCookies()).thenReturn(new Cookie[]{});

        try (MockedStatic<MoxEADRequestHelper> mockedHelper = Mockito.mockStatic(MoxEADRequestHelper.class);
             MockedStatic<TokenCookie> mockedTokenCookie = Mockito.mockStatic(TokenCookie.class);
             MockedStatic<MoxEADB2COAuth2ProviderUtils> mockedUtils = Mockito.mockStatic(MoxEADB2COAuth2ProviderUtils.class)) {

            mockedUtils.when(() -> MoxEADB2COAuth2ProviderUtils.getHostURL(request))
                .thenReturn("https://example.com");

            logoutHandler.dropCredentials(request, response);

            verify(dcoJwtSessionService, never()).expireSessionCookieOnLogout(any(), any(), anyBoolean());
            verify(response).sendRedirect(anyString());
        }
    }

    @Test
    public void testDropCredentials_NotB2CLogout() throws IOException {
        // Set up request parameters with different operation
        when(request.getParameter("operation")).thenReturn("regular-logout");

        // Execute
        logoutHandler.dropCredentials(request, response);

        // Verify only content type was set, no other operations
        verify(response).setContentType("text/html");
        verify(response, never()).sendRedirect(anyString());
        verify(response, never()).setHeader(anyString(), anyString());
    }

    @Test
    public void testDropCredentials_NullOperation() throws IOException {
        // Set up request parameters with null operation
        when(request.getParameter("operation")).thenReturn(null);

        // Execute
        logoutHandler.dropCredentials(request, response);

        // Verify only content type was set, no other operations
        verify(response).setContentType("text/html");
        verify(response, never()).sendRedirect(anyString());
        verify(response, never()).setHeader(anyString(), anyString());
    }

    @Test
    public void testDropCredentials_B2CLogoutWithInsecureRequest() throws Exception {
        // Set up request parameters
        when(request.getParameter("operation")).thenReturn("b2clogout");
        when(request.getParameter("currentPage")).thenReturn("/content/home");
        when(request.isSecure()).thenReturn(false); // Insecure request

        // Set up OAuth provider configuration
        Dictionary<String, Object> oauthProps = new Hashtable<>();
        oauthProps.put("oauth.client.id", "test-client-id");
        when(configurationAdmin.getConfiguration("com.adobe.granite.auth.oauth.provider~azure"))
            .thenReturn(oauthProviderConfig);
        when(oauthProviderConfig.getProperties()).thenReturn(oauthProps);

setupB2cConfig("test-domain.b2clogin.com", "testtenant", "B2C_1_signin_signup");

        // Set up empty cookies
        when(request.getCookies()).thenReturn(new Cookie[]{});

        try (MockedStatic<MoxEADRequestHelper> mockedHelper = Mockito.mockStatic(MoxEADRequestHelper.class);
             MockedStatic<TokenCookie> mockedTokenCookie = Mockito.mockStatic(TokenCookie.class);
             MockedStatic<MoxEADB2COAuth2ProviderUtils> mockedUtils = Mockito.mockStatic(MoxEADB2COAuth2ProviderUtils.class)) {

            mockedUtils.when(() -> MoxEADB2COAuth2ProviderUtils.getHostURL(request))
                .thenReturn("http://example.com");

            // Execute
            logoutHandler.dropCredentials(request, response);

            // Verify cookies were cleared with secure=false
            mockedTokenCookie.verify(() -> TokenCookie.setCookie(response, "DCE_KMLI", "", 0, "/", null, true, false));
            mockedTokenCookie.verify(() -> TokenCookie.setCookie(response, "DCE_CUST_KMLI", "", 0, "/", null, true, false));
            mockedTokenCookie.verify(() -> TokenCookie.setCookie(response, "test-client-id", "", 0, "/", null, true, false));

            // Verify redirect with HTTP URL
            String expectedRedirectUrl = "https://test-domain.b2clogin.com/testtenant.onmicrosoft.com/B2C_1_signin_signup/oauth2/v2.0/logout?post_logout_redirect_uri=http://example.com/content/home";
            verify(response).sendRedirect(expectedRedirectUrl);
        }
    }

    @Test
    public void testDropCredentials_NullCurrentPage() throws Exception {
        // Set up request parameters with null current page
        when(request.getParameter("operation")).thenReturn("b2clogout");
        when(request.getParameter("currentPage")).thenReturn(null);
        when(request.isSecure()).thenReturn(true);

        // Set up configurations
        Dictionary<String, Object> oauthProps = new Hashtable<>();
        oauthProps.put("oauth.client.id", "test-client-id");
        when(configurationAdmin.getConfiguration("com.adobe.granite.auth.oauth.provider~azure"))
            .thenReturn(oauthProviderConfig);
        when(oauthProviderConfig.getProperties()).thenReturn(oauthProps);

setupB2cConfig("test-domain.b2clogin.com", "testtenant", "B2C_1_signin_signup");

        when(request.getCookies()).thenReturn(new Cookie[]{});

        try (MockedStatic<MoxEADRequestHelper> mockedHelper = Mockito.mockStatic(MoxEADRequestHelper.class);
             MockedStatic<TokenCookie> mockedTokenCookie = Mockito.mockStatic(TokenCookie.class);
             MockedStatic<MoxEADB2COAuth2ProviderUtils> mockedUtils = Mockito.mockStatic(MoxEADB2COAuth2ProviderUtils.class)) {

            mockedUtils.when(() -> MoxEADB2COAuth2ProviderUtils.getHostURL(request))
                .thenReturn("https://example.com");

            // Execute
            logoutHandler.dropCredentials(request, response);

            // Verify redirect with null current page (should append null)
            String expectedRedirectUrl = "https://test-domain.b2clogin.com/testtenant.onmicrosoft.com/B2C_1_signin_signup/oauth2/v2.0/logout?post_logout_redirect_uri=https://example.comnull";
            verify(response).sendRedirect(expectedRedirectUrl);
        }
    }

    @Test
    public void testRemoveCookie_NoKMLICookies() {
        // Set up cookies without KMLI cookies
        Cookie[] cookies = {
            new Cookie("regular-cookie1", "value1"),
            new Cookie("regular-cookie2", "value2"),
            new Cookie("other-cookie", "other-value")
        };
        when(request.getCookies()).thenReturn(cookies);

        String loginDomain = "test-domain.b2clogin.com";

        // Execute
        logoutHandler.removeCookie(request, response, loginDomain);

        // Verify that no cookies were removed
        verify(response, never()).addCookie(any(Cookie.class));
    }

    @Test
    public void testRemoveCookie_NullCookies() {
        // Set up null cookies
        when(request.getCookies()).thenReturn(null);

        String loginDomain = "test-domain.b2clogin.com";

        // Execute - should handle null gracefully
        assertThrows(NullPointerException.class, () -> {
            logoutHandler.removeCookie(request, response, loginDomain);
        });
    }

    @Test
    public void testRemoveCookie_EmptyCookies() {
        // Set up empty cookies array
        when(request.getCookies()).thenReturn(new Cookie[]{});

        String loginDomain = "test-domain.b2clogin.com";

        // Execute
        logoutHandler.removeCookie(request, response, loginDomain);

        // Verify that no cookies were removed
        verify(response, never()).addCookie(any(Cookie.class));
    }

    @Test
    public void testSetConfigurationAdmin() throws Exception {
        // Test the setter method
        ConfigurationAdmin newConfigAdmin = mock(ConfigurationAdmin.class);

        logoutHandler.setConfigurationAdmin(newConfigAdmin);

        // Verify the field was set using reflection
        Field configAdminField = MoxEADB2COAuthLogoutHandler.class.getDeclaredField("configurationAdmin");
        configAdminField.setAccessible(true);
        ConfigurationAdmin actualConfigAdmin = (ConfigurationAdmin) configAdminField.get(logoutHandler);

        assertEquals(newConfigAdmin, actualConfigAdmin);
    }

    @Test
    public void testServiceRanking() {
        // Test that the service ranking constant is correct
        assertEquals(":Integer=-5001", MoxEADB2COAuthLogoutHandler.SERVICE_RANKING);
    }

    @Test
    public void testDropCredentials_ConfigurationException() throws Exception {
        // Set up request parameters
        when(request.getParameter("operation")).thenReturn("b2clogout");
        when(request.getParameter("currentPage")).thenReturn("/content/home");

        // Mock configuration admin to throw exception
        when(configurationAdmin.getConfiguration("com.adobe.granite.auth.oauth.provider~azure"))
            .thenThrow(new IOException("Configuration error"));

        try (MockedStatic<MoxEADRequestHelper> mockedHelper = Mockito.mockStatic(MoxEADRequestHelper.class);
             MockedStatic<TokenCookie> mockedTokenCookie = Mockito.mockStatic(TokenCookie.class)) {

            // Execute - should handle exception gracefully
            assertThrows(IOException.class, () -> {
                logoutHandler.dropCredentials(request, response);
            });

            // Verify that helper methods were still called before exception
            mockedHelper.verify(() -> MoxEADRequestHelper.removeConfigId(request, response));
            mockedHelper.verify(() -> MoxEADRequestHelper.removeAuthenticatedConfigId(request, response));
        }
    }

    @Test
    public void testDropCredentials_MissingConfigurationProperties() throws Exception {
        // Set up request parameters
        when(request.getParameter("operation")).thenReturn("b2clogout");
        when(request.getParameter("currentPage")).thenReturn("/content/home");
        when(request.isSecure()).thenReturn(true);

        // Set up OAuth provider configuration with missing properties
        Dictionary<String, Object> oauthProps = new Hashtable<>();
        // Missing oauth.client.id property
        when(configurationAdmin.getConfiguration("com.adobe.granite.auth.oauth.provider~azure"))
            .thenReturn(oauthProviderConfig);
        when(oauthProviderConfig.getProperties()).thenReturn(oauthProps);

        when(b2cOAuth2Provider.getB2Cconfig()).thenReturn(b2cConfig);
        when(b2cConfig.getB2CLoginDomain()).thenReturn("test-domain.b2clogin.com");
        when(b2cConfig.getB2CTenantName()).thenReturn("testtenant");
        when(b2cConfig.getB2CSignInSignUpPolicy()).thenReturn("B2C_1_signin_signup");

        try (MockedStatic<MoxEADRequestHelper> mockedHelper = Mockito.mockStatic(MoxEADRequestHelper.class);
             MockedStatic<TokenCookie> mockedTokenCookie = Mockito.mockStatic(TokenCookie.class)) {

            when(request.getCookies()).thenReturn(new Cookie[]{});

            // Execute - should handle missing properties gracefully
            assertThrows(NullPointerException.class, () -> {
                logoutHandler.dropCredentials(request, response);
            });

            // Verify that cleanup methods were still called
            mockedHelper.verify(() -> MoxEADRequestHelper.removeConfigId(request, response));
            mockedHelper.verify(() -> MoxEADRequestHelper.removeAuthenticatedConfigId(request, response));
        }
    }

    @Test
    public void testDropCredentials_CompleteWorkflow() throws Exception {
        // Test complete logout workflow with all components
        when(request.getParameter("operation")).thenReturn("b2clogout");
        when(request.getParameter("currentPage")).thenReturn("/content/dashboard");
        when(request.isSecure()).thenReturn(true);

        // Set up complete configurations
        Dictionary<String, Object> oauthProps = new Hashtable<>();
        oauthProps.put("oauth.client.id", "my-client-id-123");
        when(configurationAdmin.getConfiguration("com.adobe.granite.auth.oauth.provider~azure"))
            .thenReturn(oauthProviderConfig);
        when(oauthProviderConfig.getProperties()).thenReturn(oauthProps);

setupB2cConfig("mycompany.b2clogin.com", "mycompany", "B2C_1_SignUpOrSignIn");

        // Set up cookies with various types
        Cookie[] cookies = {
            new Cookie("DCE_KMLI", "user-session-123"),
            new Cookie("DCE_CUST_KMLI", "customer-session-456"),
            new Cookie("JSESSIONID", "java-session"),
            new Cookie("other-app-cookie", "other-value")
        };
        when(request.getCookies()).thenReturn(cookies);

        try (MockedStatic<MoxEADRequestHelper> mockedHelper = Mockito.mockStatic(MoxEADRequestHelper.class);
             MockedStatic<TokenCookie> mockedTokenCookie = Mockito.mockStatic(TokenCookie.class);
             MockedStatic<MoxEADB2COAuth2ProviderUtils> mockedUtils = Mockito.mockStatic(MoxEADB2COAuth2ProviderUtils.class)) {

            mockedUtils.when(() -> MoxEADB2COAuth2ProviderUtils.getHostURL(request))
                .thenReturn("https://mycompany.com");

            // Execute complete logout workflow
            logoutHandler.dropCredentials(request, response);

            // Verify complete cleanup sequence
            verify(response).setContentType("text/html");

            // Config cleanup
            mockedHelper.verify(() -> MoxEADRequestHelper.removeConfigId(request, response));
            mockedHelper.verify(() -> MoxEADRequestHelper.removeAuthenticatedConfigId(request, response));

            // Cookie cleanup
            mockedTokenCookie.verify(() -> TokenCookie.setCookie(response, "DCE_KMLI", "", 0, "/", null, true, true));
            mockedTokenCookie.verify(() -> TokenCookie.setCookie(response, "DCE_CUST_KMLI", "", 0, "/", null, true, true));
            mockedTokenCookie.verify(() -> TokenCookie.setCookie(response, "my-client-id-123", "", 0, "/", null, true, true));

            // Cache headers
            verify(response).setHeader("Cache-Control", "no-cache");
            verify(response).setHeader("Expires", "0");
            verify(response).setHeader("Cache-Control", "private, no-store, no-cache, must-revalidate");
            verify(response).setHeader("Pragma", "no-cache");

            // Verify domain-specific cookie removal
            verify(response, times(1)).addCookie(argThat(cookie ->
                "mycompany.b2clogin.com".equals(cookie.getDomain()) &&
                    cookie.getMaxAge() == 0
            ));

            // Final redirect
            String expectedRedirectUrl = "https://mycompany.b2clogin.com/mycompany.onmicrosoft.com/B2C_1_SignUpOrSignIn/oauth2/v2.0/logout?post_logout_redirect_uri=https://mycompany.com/content/dashboard";
            verify(response).sendRedirect(expectedRedirectUrl);
        }
    }
}
