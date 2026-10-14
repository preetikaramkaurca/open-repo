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
/* global $, Granite, CQ */
((ns) => {

    /**
     * Panel Container Utilities
     *
     * @namespace
     * @alias CQ.CoreComponents.panelcontainer.v1.utils
     * @type {{}}
     */
    CQ.CoreComponents.panelcontainer.v1.utils = {

        /**
         * Checks whether an [Editable]{@link Granite.author.Editable} is a Panel Container
         *
         * @param {Granite.author.Editable} editable The [Editable]{@link Granite.author.Editable} to check
         * @returns {Boolean} True if the Editable is a Panel Container, false otherwise
         */
        isPanelContainer(editable) {
            const panelContainerType = getPanelContainerType(editable);
            return (panelContainerType !== undefined);
        },

        /**
         * Returns the Panel Container definition associated with an [Editable]{@link Granite.author.Editable}
         *
         * @param {Granite.author.Editable} editable The Panel Container [Editable]{@link Granite.author.Editable}
         * @returns {Object} The Panel Container Type definition, undefined if none is associated
         */
        getPanelContainerType(editable) {
            return getPanelContainerType(editable);
        },

        /**
         * Returns the Panel Container HTML element associated with an [Editable]{@link Granite.author.Editable}
         *
         * @param {Granite.author.Editable} editable The Panel Container [Editable]{@link Granite.author.Editable}
         * @returns {HTMLElement} The HTML Element match for the Panel Container, undefined if none is associated
         */
        getPanelContainerHTMLElement(editable) {
            const container = getPanelContainerType(editable);
            let element;

            if (container) {
                const selector = container.wrapperSelector || container.selector;
                if ($(editable.dom[0]).is(selector)) {
                    // eslint-disable-next-line prefer-destructuring
                    element = editable.dom[0];
                } else {
                    const { children } = editable.dom[0];
                    for (let i = 0; i < children.length; i += 1) {
                        const child = children[i];
                        if ($(child).is(selector)) {
                            element = child;
                            break;
                        }
                    }
                }
            }

            return element;
        },

        /**
         * Returns Panel Container [Editable]{@link Granite.author.Editable}'s child items
         *
         * @param {Granite.author.Editable} editable The Panel Container {@link Granite.author.Editable}
         * @returns {Array<Granite.author.editable>} The Panel Container child editables
         */
        getPanelContainerItems(editable) {
            let children = [];
            const container = getPanelContainerType(editable);
            if (container) {
                if (editable.isContainer()) {
                    children = editable.getChildren().filter(isDisplayable)
                        .filter((child) => editable.type !== 'usf-aem-cloud-base/components/structure/header' || child.type === 'usf-aem-cloud-base/components/content/mega-menu')
                        .filter((child) => editable.type !== 'usf-aem-cloud-base/components/form/multipage' || child.type === 'wcm/foundation/components/responsivegrid');
                }
            }
            return children;
        }
    };

    /**
     * Returns the Panel Container definition associated with an [Editable]{@link Granite.author.Editable}
     *
     * @param {Granite.author.Editable} editable The Panel Container [Editable]{@link Granite.author.Editable}
     * @returns {Object} The Panel Container Type definition, undefined if none is associated
     */
    function getPanelContainerType(editable) {
        let panelContainerType;
        const panelContainerTypes = CQ.CoreComponents.panelcontainer.v1.registry.getAll();

        if (editable && editable.dom) {
            for (let i = 0; i < panelContainerTypes.length; i += 1) {
                const container = panelContainerTypes[i];
                // wrapperSelector distinguishes Adaptive Form tabs from content tabs,
                // which both use the .cmp-tabs selector.
                const selector = container.wrapperSelector || container.selector;
                let match = $(editable.dom[0]).is(selector);

                // look for a match at the editable DOM wrapper, if none is found, try its children.
                if (!match) {
                    const { children } = editable.dom[0];
                    for (let j = 0; j < children.length; j += 1) {
                        const child = children[j];
                        match = $(child).is(selector);
                        if (match) {
                            break;
                        }
                    }
                }
                if (match) {
                    panelContainerType = container;
                    break;
                }
            }
        }

        return panelContainerType;
    }

    /**
     * Test whether an [Editable]{@link Granite.author.Editable} is displayable in the panel popover.
     * Ignore [Inspectables]{@link Granite.author.Inspectable} and Placeholders.
     *
     * @param {Granite.author.Editable} editable The [Editable]{@link Granite.author.Editable} to test
     * @returns {Boolean} Whether the [Editable]{@link Granite.author.Editable} is displayed in the panel popover, or not
     */
    function isDisplayable(editable) {
        return (editable instanceof ns.Editable
            && (editable.isContainer() || (editable.hasActionsAvailable() && !editable.isNewSection())));
    }
})(Granite.author);
