/*******************************************************************************
 * Copyright 2016 Adobe - WCM Core Components
 *
 * Adobe holds the copyright for all the WCM Core Components files and their contents
 * found in this repository, to the extent they remain in their original form as copied
 * from the WCM Core Components project at https://github.com/adobe/aem-core-wcm-components.
 *
 * Licensed under the Apache License, Version 2.0 (the 'License');
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an 'AS IS' BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
 *
 * BOUNTEOUS CONFIDENTIAL
 * ___________________
 *
 * Copyright 2021 Bounteous - AEM Activate
 *
 * All Rights Reserved.
 *
 * NOTICE: All information added and modified by Bounteous for AEM
 * Activate contained herein is, and remains the property
 * of Bounteous and its suppliers, if any. The intellectual and
 * technical concepts contained herein are proprietary to Bounteous
 * and its suppliers and are protected by trade secret or copyright law.
 * Dissemination of this information or reproduction of this material
 * is strictly forbidden unless prior written permission is obtained
 * from Bounteous.
 ******************************************************************************/
/* global jQuery */
((channel) => {
    channel.on('cq-editor-loaded', () => {
        if (window.CQ && window.CQ.CoreComponents && window.CQ.CoreComponents.panelcontainer
            && window.CQ.CoreComponents.panelcontainer.v1 && window.CQ.CoreComponents.panelcontainer.v1.registry) {
            window.CQ.CoreComponents.panelcontainer.v1.registry.register({
                name: 'cmp-tabs',
                selector: '.cmp-tabs',
                // Keep this narrower than .cmp-tabs so Adaptive Form tabs
                // (data-panelcontainer="adaptiveFormTabs") are not claimed as content tabs.
                wrapperSelector: 'cmp-tabs, [data-panelcontainer="tabs"]',
                itemSelector: "[data-cmp-hook-tabs='tabpanel']",
                itemActiveSelector: '.cmp-tabs__tabpanel--active'
            });
        }
    });
})(jQuery(document));
