import { emitEvent, getUrlParameter } from 'js/helpers';

export const getCookie = (name) => {
    const value = `; ${document.cookie}`;
    const parts = value.split(`; ${name}=`);
    return parts.length < 2
        ? undefined
        : parts
            .pop()
            .split(';')
            .shift();
};

export const setCookie = (name, value, expiryDays, path, domain, secure) => {
    const cookie = [
        `${name}=${value}`,
        `path=${path || '/'}`
    ];

    if (typeof expiryDays === 'number') {
        const expiryDate = new Date();
        expiryDate.setHours(expiryDate.getHours() + (expiryDays * 24));
        cookie.push(`expires=${expiryDate.toUTCString()}`);
    }
    if (domain) {
        cookie.push(`domain=${domain}`);
    }
    if (secure) {
        cookie.push('secure');
    }
    document.cookie = cookie.join(';');
};

export const removeCookie = (name, path, domain, secure) => setCookie(name, null, -1, path, domain, secure);

export const getReferrerCookie = () => getCookie('usfoods_referer');

export const setReferrerCookie = () => setCookie('usfoods_referer', document.referrer, 'session', '/');

export const removeReferrerCookie = () => removeCookie('usfoods_referer', '/');

export const getGoogleCampaignCookie = () => getCookie('_gcl_aw');

export const getGCLID = () => {
    let id = null;
    const urlParameter = getUrlParameter('gclid', true);
    if (urlParameter) {
        id = urlParameter;
    } else {
        const cookie = getGoogleCampaignCookie();
        if (cookie) {
            const cookieParts = cookie.split('.');
            id = cookieParts[cookieParts.length - 1];
        }
    }
    if (!id) {
        id = null;
    }
    return id;
};

export const getBacRegistrationCookie = () => getCookie('usfoods_registration_info');

export const getGoogleAnalyticsCookie = () => getCookie('_gid');

export const getUTMCookie = () => getCookie('bac_campaign');

export const getBacLeadTypeCookie = () => getCookie('bac_leadType');
export const removeBacRegistrationCookie = () => {
    removeCookie('usfoods_registration_info', '/');
};
export const removeBacLeadTypeCookie = () => {
    removeCookie('bac_leadType', '/');
};
export const setBacRegistrationCookie = (jsonStr, time) => {
    let timeAdjusted = 3650;
    if (typeof time !== 'number') {
        try {
            timeAdjusted = parseInt(time, 10);
        } catch { /* do nothing */ }
    }
    setCookie(
        'usfoods_registration_info',
        jsonStr,
        timeAdjusted,
        '/'
    );
};
export const setBacCampaignCookie = (jsonStr, time) => {
    let timeAdjusted = 90;
    if (typeof time !== 'number') {
        try {
            timeAdjusted = parseInt(time, 10);
        } catch { /* do nothing */ }
    }
    setCookie('bac_campaign', jsonStr, timeAdjusted, '/');
};

/** Session key for UTM query params mirrored from the URL (see captureUTMsAsIndividualCookies). */
export const UTM_SESSION_STORAGE_KEY = 'usfoods_utm_params';

/**
 * Reads merged UTM param values from sessionStorage (written by captureUTMsAsIndividualCookies).
 * @returns {Record<string, string>}
 */
export const getUtmParamsFromSessionStorage = () => {
    if (typeof sessionStorage === 'undefined' || !sessionStorage) {
        return {};
    }
    try {
        const usfUtmParams = sessionStorage.getItem(UTM_SESSION_STORAGE_KEY);
        if (!usfUtmParams) {
            return {};
        }
        const parsed = JSON.parse(usfUtmParams);
        if (typeof parsed !== 'object' || parsed === null || Array.isArray(parsed)) {
            return {};
        }
        return parsed;
    } catch {
        return {};
    }
};

export const captureUTMsAsIndividualCookies = (options = {}) => {
    const hasGetUrlParameter = typeof getUrlParameter === 'function';
    const hasSessionStorage = typeof sessionStorage !== 'undefined' && sessionStorage;
    if (!hasGetUrlParameter || !hasSessionStorage) {
        try {
            // eslint-disable-next-line no-console
            console.warn('[UTM sessionStorage] Missing helper(s):', {
                getUrlParameter: hasGetUrlParameter,
                sessionStorage: hasSessionStorage,
            });
        } catch (e) {
            /* no-op */
        }
        return;
    }

    const {
        params = [
            'utm_source',
            'utm_medium',
            'utm_campaign',
            'utm_term',
            'utm_content',
        ],
        overwrite = false,
        onlyIfPresent = true,
        normalizeCase = true,
        maxCookieValue = 1024,
        storageKey = UTM_SESSION_STORAGE_KEY,
    } = options || {};

    const safeParams = Array.isArray(params)
        ? params.filter((p) => typeof p === 'string' && p.trim() !== '')
        : [];

    if (safeParams.length === 0) {
        return;
    }

    let existing = {};
    try {
        const usfUtmParams = sessionStorage.getItem(storageKey);
        if (usfUtmParams) {
            const parsed = JSON.parse(usfUtmParams);
            if (typeof parsed === 'object' && parsed !== null && !Array.isArray(parsed)) {
                existing = parsed;
            }
        }
    } catch {
        existing = {};
    }

    const merged = { ...existing };

    safeParams.forEach((paramRaw) => {
        try {
            const param = String(paramRaw);
            let value = getUrlParameter(param);

            const hasValue = value !== undefined
                && value !== null
                && String(value).trim() !== '';

            if (onlyIfPresent && !hasValue) {
                return;
            }

            const storageProp = normalizeCase ? param.toLowerCase() : param;

            if (!overwrite && Object.prototype.hasOwnProperty.call(existing, storageProp)) {
                return;
            }

            value = hasValue ? String(value) : '';
            if (value.length > maxCookieValue) {
                value = value.slice(0, maxCookieValue);
            }

            merged[storageProp] = value;
        } catch (err) {
            try {
                // eslint-disable-next-line no-console
                console.warn('[UTM sessionStorage] Skipped param due to error:', {
                    param: paramRaw,
                    error: err,
                });
            } catch (e) {
                /* no-op */
            }
        }
    });

    try {
        sessionStorage.setItem(storageKey, JSON.stringify(merged));
    } catch (err) {
        try {
            // eslint-disable-next-line no-console
            console.warn('[UTM sessionStorage] Failed to persist:', { error: err });
        } catch (e) {
            /* no-op */
        }
    }
};

export const setBacLeadTypeCookie = (str, time) => {
    let timeAdjusted = 'session';
    if (typeof time !== 'number') {
        try {
            timeAdjusted = parseInt(time, 10);
        } catch { /* do nothing */ }
    }
    setCookie('bac_leadType', str, timeAdjusted, '/');
};

export const getDistributionCenterCookie = () => getCookie('usfoods_local_distribution_center');
export const setDistributionCenterCookie = (jsonStr) => {
    setCookie(
        'usfoods_local_distribution_center',
        jsonStr,
        'session',
        '/'
    );
};

export const getEcomUserInfoCookie = () => {
    const kmliCookie = getCookie('DCE_KMLI_MOXE');
    if (kmliCookie) {
        return decodeURIComponent(getCookie('DCE_CUST_KMLI_MOXE'));
    }

    return null;

};

/**
 * checks if user is logged
 * @returns {boolean} true if either DCE_CUST_KMLI_MOXE or DCE_KMLI_MOXE cookie is present, false otherwise
 */
export const isUserLoggedIn = () => {
    const kmliCookie = getCookie('DCE_KMLI_MOXE');
    const custCookie = getCookie('DCE_CUST_KMLI_MOXE');
    return !!(kmliCookie || custCookie);
};

export const getEcomCustomerProfileCookie = () => {
    const custKMLICookie = getCookie('DCE_CUST_KMLI_MOXE');
    if (custKMLICookie) {
        return decodeURIComponent(custKMLICookie);
    }
    return null;
};

export const getEcomCustomerProfileField = (fieldName) => {
    const customerData = getEcomUserInfoCookie();
    if (customerData) {
        return customerData[fieldName];
    }
    return null;
};

export const getEcomCustomerProfileUserId = () => {
    const customerData = getEcomCustomerProfileCookie();
    if (customerData) {
        return customerData.ecUserId;
    }
    return null;
};

export const getCustomerNumber = () => getEcomCustomerProfileField('customerNumber');
export const getDepartmentNumber = () => getEcomCustomerProfileField('departmentNumber');
export const getMarketingSegmentCode = () => getEcomCustomerProfileField('marketingProfile')?.marketSegmentCode;

export const removeEcomUserData = () => {
    // This is to determine what shared cookie to remove on DCE side (usfood.com vs usfoods.com);
    const loc = window.location.hostname.split('.');
    let domain = window.location.hostname;
    if (loc.length > 2) {
        domain = `${loc[loc.length - 2]}.${loc[loc.length - 1]}`;
    }
    /* Cannot remove httpOnly DCE_KMLI cookie from client-side */
    removeCookie('DCE_CUST_KMLI_MOXE', '/', domain);
    sessionStorage.removeItem('userProfile');
    sessionStorage.removeItem('customerAccountData');
    window.location.reload();
};
export const setCustomerMarketingProfileCookie = (jsonStr) => {
    // This is to determine what shared cookie to set on DCE side (usfood.com vs usfoods.com);
    const loc = window.location.hostname.split('.');
    let domain = window.location.hostname;
    if (loc.length > 2) {
        domain = `${loc[loc.length - 2]}.${loc[loc.length - 1]}`;
    }
    setCookie('DCE_CUST_KMLI_MOXE', jsonStr, 3650, '/', domain);
};

export const applyLoginStateContainerVisibility = () => {
    if (typeof window.usfUpdateLoginStateContainer === 'function') {
        window.usfUpdateLoginStateContainer();
    }
};

export const setUserProfile = (dataToSet) => {
    const customerCookieObj = getEcomCustomerProfileCookie();
    if (customerCookieObj !== null) {
        const customerCookieObjParsed = JSON.parse(customerCookieObj);
        const lastSelectedLocation = {
            customerNumber: customerCookieObjParsed.customerNumber,
            customerName: customerCookieObjParsed.customerName,
            departmentNumber: customerCookieObjParsed.departmentNumber,
            departmentName: customerCookieObjParsed.departmentName,
            market: customerCookieObjParsed.distributorNumber
        };

        // eslint-disable-next-line no-param-reassign
        dataToSet.lastSelectedLocation = lastSelectedLocation;

        sessionStorage.setItem('userProfile', JSON.stringify(dataToSet));
        applyLoginStateContainerVisibility();
    }
};

export const refreshUserProfile = () => {
    const dataRequest = '/bin/usfoods/ecom-user-profile-data.json?nocache=true';

    return fetch(dataRequest)
        .then((response) => response.json())
        .then((responseData) => {
            if (responseData.coreProfile === undefined) {
                setUserProfile(responseData);
                return responseData;
            }
            return null;
        });
};

export const getUserProfile = () => {
    const customerInfoCookie = getEcomCustomerProfileCookie();
    if (customerInfoCookie !== null) {
        const customerCookieData = JSON.parse(customerInfoCookie || '{}');
        const { customerNumber } = customerCookieData;
        const { departmentNumber } = customerCookieData;
        if (sessionStorage.getItem('userProfile') !== null) {
            // We have user profile data in session storage
            const sessionUserProfileData = JSON.parse(sessionStorage.getItem('userProfile'));
            if (customerNumber !== sessionUserProfileData.lastSelectedLocation.customerNumber || departmentNumber !== sessionUserProfileData.lastSelectedLocation.departmentNumber) {
                // Could be user change or customer change to need to refresh data.
                return refreshUserProfile();
            }
            return sessionUserProfileData;

        }
        return refreshUserProfile();
    }
    // Not authenticated, no profile
    return null;
};

export const getCustomerName = async () => {
    const userProfile = await getUserProfile();
    if (userProfile) {
        if (userProfile.firstName) {
            return userProfile.firstName;
        }
        if (userProfile.loginUsername) {
            return userProfile.loginUsername;
        }
        return 'User';
    }
    const KMLICookie = getCookie('DCE_KMLI_MOXE');
    if (KMLICookie) {
        const decodedKMLICookie = decodeURIComponent(KMLICookie);
        const objKMLI = JSON.parse(decodedKMLICookie);
        const usernameKMLI = window.atob(objKMLI.Username);
        return usernameKMLI;
    }
    return 'User';
};

export const getFavoritesCookie = () => getCookie('usfoods_favorited_items');
export const setFavoritesCookie = (jsonStr) => {
    setCookie(
        'usfoods_favorited_items',
        jsonStr,
        3650,
        '/'
    );
};
export const getMarketInfoCookie = () => getCookie('usfoods_local_market_info');
export const setMarketInfoCookie = (jsonStr) => {
    setCookie(
        'usfoods_local_market_info',
        jsonStr,
        'session',
        '/'
    );
};
export const getChefStoreFormCookie = () => getCookie('usfoods_form_chef_store');
export const getChefStoreCookie = () => getCookie('usfoods_chef_store');
export const setChefStoreCookie = (jsonStr) => {
    setCookie('usfoods_chef_store', jsonStr, 'session', '/');
    emitEvent('setChefStoreCookie');

    const event = new CustomEvent('chefStoreCookieSet');
    document.dispatchEvent(event);
};
export const queryNearestChefStoreCookie = (lat, long, zipCode, customCookieName, distance = 100) => {
    if (!lat && !long && !zipCode) {
        return null;
    }
    return fetch(
        `/bin/usfoods/location-query?data=${encodeURIComponent(JSON.stringify({
            locationTypes: 'us-foods:location-types/chef-store',
            latitude: lat,
            longitude: long,
            zip: zipCode,
            distance
        }))}`,
        {
            method: 'GET',
            headers: {
                'Content-Type': 'application/json',
            }
        }
    )
        .then((response) => response.json())
        .then((data) => {
            if (data.message && data.message.length > 0) {
                if (!customCookieName) {
                    setChefStoreCookie(JSON.stringify(data.message[0]));
                } else {
                    setCookie(customCookieName, JSON.stringify(data.message[0]), 'session', '/');
                }
            } else if (!customCookieName) {
                setChefStoreCookie(JSON.stringify({}));
            } else {
                setCookie(customCookieName, JSON.stringify({}), 'session', '/');
            }
        });
};
export const getDistributionCenterSupportCookie = () => getCookie('usfoods_local_distribution_center_support');
export const setDistributionCenterSupportCookie = (jsonStr) => {
    setCookie('usfoods_local_distribution_center_support', jsonStr, 7, '/');
    if (jsonStr) {
        const json = JSON.parse(jsonStr);
        if (json && json.postalCode) {
            queryNearestChefStoreCookie(undefined, undefined, json.postalCode);
            emitEvent('distributionCenterSupportCookieUpdate', json.postalCode);
        }
    }
};
export const getUserRoleCookie = () => getCookie('usfoods_user_role');
export const setUserRoleCookie = (jsonStr) => {
    setCookie(
        'usfoods_user_role',
        jsonStr,
        3650,
        '/'
    );
};
export const getUserZipCookie = () => getCookie('usfoods_user_zip');
export const setUserZipCookie = (jsonStr) => {
    setCookie(
        'usfoods_user_zip',
        jsonStr,
        3650,
        '/'
    );
};
export const updateUTMCookie = () => {
    const bacCampaignCookieData = {
        utmSource: getUrlParameter('utm_source'),
        utmMedium: getUrlParameter('utm_medium'),
        utmCampaign: getUrlParameter('utm_campaign'),
        utmTerm: getUrlParameter('utm_term'),
        utmContent: getUrlParameter('utm_content'),
        utmPlatform: getUrlParameter('utm_source_platform'),
        utmId: getUrlParameter('utm_id'),
    };

    setBacCampaignCookie(JSON.stringify(bacCampaignCookieData), '2');
    captureUTMsAsIndividualCookies();
};
