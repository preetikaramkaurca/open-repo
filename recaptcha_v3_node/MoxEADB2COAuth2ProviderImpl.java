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
package com.usfoods.aem.base.core.services.login.services.impl;

import com.adobe.granite.auth.oauth.Provider;
import com.adobe.granite.auth.oauth.ProviderType;
import com.usfoods.aem.base.core.services.login.Constants;
import com.usfoods.aem.base.core.services.login.provider.MoxEADB2COAuth2Api;
import com.usfoods.aem.base.core.services.login.services.MoxEADB2CConfig;
import org.apache.jackrabbit.api.security.user.Authorizable;
import org.apache.jackrabbit.api.security.user.User;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.json.JSONException;
import org.json.JSONObject;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.scribe.builder.api.Api;
import org.scribe.model.OAuthRequest;
import org.scribe.model.Response;
import org.scribe.model.Verb;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.Session;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

@SuppressWarnings({"deprecation", "AEM Rules:AEM-3"})
@Component(
        immediate = true,
        service = { Provider.class, MoxEADB2COAuth2ProviderImpl.class })
@Designate(ocd = MoxEADB2COAuth2ProviderImpl.Cfg.class)
public class MoxEADB2COAuth2ProviderImpl implements Provider {

    @ObjectClassDefinition(name = "MoxE AD B2C Provider Configuration", description = "MoxE AD B2C Provider Configuration")
    public @interface Cfg {
        @AttributeDefinition(name = "oauth.provider.id", description = "OAuth Provider ID")
        String providerId() default "";

        @AttributeDefinition(
                name = "oauth.b2c.b2clogindomain",
                description = "B2C login host (no scheme). On AEM as a Cloud Service, set per environment in Cloud Manager as USF_MOXE_LOGIN_DOMAIN")
        String b2cLoginDomain() default "";

        @AttributeDefinition(name = "oauth.b2c.b2ctenantname", description = "B2C Tenant Domain")
        String b2cTenantName() default "";

        @AttributeDefinition(name = "oauth.b2c.signinsignup.policy", description = "B2C Signin/SignUp Policy")
        String b2cSignInSignUpPolicyName() default "";

        @AttributeDefinition(name = "oauth.b2c.signup.policy", description = "B2C Sign Up Policy")
        String b2cSignUpPolicyName() default "";

        @AttributeDefinition(name = "oauth.b2c.resetpassword.policy", description = "B2C Reset Password Policy")
        String b2cResetPasswordPolicyName() default "";

        @AttributeDefinition(name = "oauth.aem.default.user.name", description = "Oauth AEM Default User Name")
        String oauthAEMDefaultUserName() default "";

        @AttributeDefinition(name = "oauth.aem.default.user.password", description = "Oauth AEM Default User Password")
        String oauthAEMDefaultUserPassword() default "";

    }
    private static final Logger LOGGER = LoggerFactory.getLogger(MoxEADB2COAuth2ProviderImpl.class);
    private ResourceResolver serviceUserResolver;
    private Session session;
    private String id;
    private String name;
    private MoxEADB2CConfig b2Cconfig = new MoxEADB2CConfig();

    @Override
    public String getId() {
        LOGGER.info("{} id {}", MoxEADB2COAuth2ProviderImpl.class, this.id);
        return this.id;
    }

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public ProviderType getType() {
        return ProviderType.OAUTH2;
    }

    @Override
    public Api getApi() {
        LOGGER.info("{} Getting AzureADB2COAuth2Api", MoxEADB2COAuth2ProviderImpl.class);
        return new MoxEADB2COAuth2Api(b2Cconfig);
    }

    @Override
    public String[] getExtendedDetailsURLs(String scope, String userId, Map<String, Object> props) {
        return new String[0];
    }

    @Override
    public String[] getExtendedDetailsURLs(String scope) {
        return new String[0];
    }

    public String mapProperty(String property) {
        return getPropertyPath(property);
    }

    @Override
    public String mapUserId(final String userId, final Map<String, Object> props) {
        final String userName = (String) props.get(getPropertyPath("id"));
        if (userName != null && userName.length() > 0) {
            return "b2c-" + userName;
        } else {
            return "b2c-" + userId;
        }
    }

    protected String getPropertyPath(final String property) {
        return "profile/" + property;
    }

    @Override
    public String getUserFolderPath(String userId, String clientId, Map<String, Object> props) {
        StringBuilder sb = new StringBuilder(getId());
        if (userId != null) {
            sb.append("/").append(userId, 0, 4);
        }
        return sb.toString();
    }

    @Override
    public Map<String, Object> mapProperties(String srcUrl, String clientId, Map<String, Object> existing,
                                             Map<String, String> newProperties) {
        Map<String, Object> mapped = new HashMap<>();
        mapped.putAll(existing);
        for (Map.Entry<String, String> prop : newProperties.entrySet()) {
            mapped.put(mapProperty(prop.getKey()), prop.getValue());
        }
        return mapped;
    }

    @Override
    public String getAccessTokenPropertyPath(String clientId) {
        return "profile/app-" + clientId;
    }

    @Override
    public User getCurrentUser(SlingHttpServletRequest request) {
        Authorizable authorizable = request.adaptTo(Authorizable.class);
        if (authorizable != null && !authorizable.isGroup()) {
            return (User) authorizable;
        }
        return null;
    }

    @Override
    public void onUserCreate(User user) {
    }

    @Override
    public void onUserUpdate(User user) {
    }

    @Override
    public OAuthRequest getProtectedDataRequest(String url) {
        return new OAuthRequest(Verb.GET, url);
    }

    @Override
    public Map<String, String> parseProfileDataResponse(Response response) {
        String body = null;
        body = response.getBody();
        JSONObject json = null;
        try {
            json = new JSONObject(body);
        } catch (JSONException exception) {
            LOGGER.error(exception.getMessage(), exception);
        }
        Map<String, String> newProps = new HashMap<>();
        if (json != null) {
            for (Iterator<String> keys = json.keys(); keys.hasNext(); ) {
                String key = keys.next();
                newProps.put(key, json.optString(key));
            }
        }
        return newProps;
    }

    @Override
    public String getUserIdProperty() {
        return "email";
    }

    @Override
    public String getOAuthIdPropertyPath(String clientId) {
        return "oauth/oauthid-" + clientId;
    }

    @Override
    public String getValidateTokenUrl(String clientId, String token) {
        this.LOGGER.info(Constants.NOT_SUPPORTED_MESSAGE);
        return null;
    }

    @Override
    public boolean isValidToken(String responseBody, String clientId, String tokenType) {
        this.LOGGER.info(Constants.NOT_SUPPORTED_MESSAGE);
        return false;
    }

    @Override
    public String getUserIdFromValidateTokenResponseBody(String responseBody) {
        this.LOGGER.info(Constants.NOT_SUPPORTED_MESSAGE);
        return null;
    }

    @Override
    public String getErrorDescriptionFromValidateTokenResponseBody(String responseBody) {
        this.LOGGER.info(Constants.NOT_SUPPORTED_MESSAGE);
        return null;
    }

    @Activate
    @Modified
    protected void activate(final Cfg config) throws LoginException  {
        LOGGER.debug("{} Executing activate method", MoxEADB2COAuth2ProviderImpl.class);
        name = getClass().getSimpleName();
        id = config.providerId();

        b2Cconfig = new MoxEADB2CConfig();
        b2Cconfig.setB2CLoginDomain(config.b2cLoginDomain());
        b2Cconfig.setB2CTenantName(config.b2cTenantName());
        b2Cconfig.setB2CSignInSignUpPolicy(config.b2cSignInSignUpPolicyName());
        b2Cconfig.setB2CSignUpPolicy(config.b2cSignUpPolicyName());
        b2Cconfig.setB2CResetPasswordPolicy(config.b2cResetPasswordPolicyName());
        b2Cconfig.setOauthAEMDefaultUserName(config.oauthAEMDefaultUserName());
        b2Cconfig.setOauthAEMDefaultUserPassword(config.oauthAEMDefaultUserPassword());
        Map<String, Object> serviceParams = new HashMap<>();
        serviceParams.put(ResourceResolverFactory.SUBSERVICE, Constants.USER_ADMIN);
    }

    @Deactivate
    protected void deactivate(final ComponentContext componentContext) {
        LOGGER.debug("deactivating provider id {}", id);
        if (session != null && session.isLive()) {
            session.logout();
            session = null;
        }
        if (serviceUserResolver != null) {
            serviceUserResolver.close();
        }
    }

    @Override
    public String getDetailsURL() {
        return "https://"
                + this.b2Cconfig.getB2CLoginDomain()
                + "/" + this.b2Cconfig.getB2CTenantName()
                + ".onmicrosoft.com/"
                + this.b2Cconfig.getB2CSignInSignUpPolicy()
                + "/openid/v2.0/userinfo";
    }

    public MoxEADB2CConfig getB2Cconfig() {
        return b2Cconfig;
    }
}
