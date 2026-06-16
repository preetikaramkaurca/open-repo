/* ==========================================================================
   BOUNTEOUS CONFIDENTIAL
   ___________________
   Copyright 2025 Bounteous - AEM Activate
   All Rights Reserved.
   NOTICE: All information added and modified by Bounteous for AEM
   Activate contained herein is, and remains the property
   of Bounteous and its suppliers, if any. The intellectual and
   technical concepts contained herein are proprietary to Bounteous
   and its suppliers and are protected by trade secret or copyright law.
   Dissemination of this information or reproduction of this material
   is strictly forbidden unless prior written permission is obtained
   from Bounteous.
   ========================================================================== */
/* eslint-disable no-param-reassign */
import { Component, registerComponent } from 'js/component';
import { getCustomerName } from 'js/cookie';
import { isAuthenticated } from 'js/helpers';
const componentName = 'cmp-ribbon';
class Ribbon extends Component {
    isProvisionalUser() {
        try {
            const userProfileJson = sessionStorage.getItem('userProfile');
            if (!userProfileJson || userProfileJson === 'undefined') {
                return false;
            }
            const userProfile = JSON.parse(userProfileJson);
            return userProfile && userProfile.provisional === true;
        } catch (e) {
            return false;
        }
    }

    /**
     * Removing hidden attribute of Ribbon component
     */
    async displayComponent() {
        const firstName = await getCustomerName();
        if (firstName !== null && isAuthenticated() && !this.isProvisionalUser()) {
            this.$els.welcomeTextContainer.closest('.ribbon').hidden = false;
            this.$els.welcomeTextSelector.innerHTML = `${this.$els.welcomeTextSelector.innerHTML} ${firstName}!`;
        }
    }
    /**
     * Initialize Component
     */
    connectedCallback() {
        super.connectedCallback?.();
        // Set Up Elements
        this.$els = {
            welcomeTextSelector: this.querySelector('.cmp-ribbon__content--welcome-message'),
            welcomeTextContainer: this.querySelector('.cmp-ribbon__container.cmp-ribbon__welcome-container')
        };
        // Fire Init Funcs
        if (this.$els.welcomeTextContainer) {
            this.$els.welcomeTextContainer.closest('.ribbon').hidden = true;
            this.displayComponent();
        }
    }
}
registerComponent(componentName, Ribbon);
