/* eslint-disable no-param-reassign */
/* eslint-disable indent */
/* ==========================================================================
   BOUNTEOUS CONFIDENTIAL
   ___________________

   Copyright 2021 Bounteous - AEM Activate
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
import { Component, registerComponent } from 'js/component';
import {
    buildQueryString,
    dataLayerEvent,
    fetchAPIRequest,
    isEditMode,
    isPartiallyScrolledIntoView,
    parseHashParameters,
    randomMember,
    searchDummyData,
    timeout,
    updateCapitalization,
} from 'js/helpers';
import 'js/TeaserTemplate';
import { pushToAllDataLayers } from 'js/analytics';
import { html, render } from 'lit-html';

// eslint-disable-next-line import/no-extraneous-dependencies
import { map } from 'lit/directives/map.js';
// eslint-disable-next-line import/no-extraneous-dependencies
import { styleMap } from 'lit/directives/style-map.js';
import Swiper, { A11y, Navigation, Pagination } from 'swiper';

const componentName = 'cmp-site-search';

const ACTIVE_FILTER_CLASSNAME = `${componentName}__filter--active`;

const DEFAULT_TILE_POSITION = 4;

/** Hash flag for search source: `recommended`, `autocomplete`, or `search` (typed submit); restored on the results page for analytics. */
const HASH_PARAM_SEARCH_SOURCE = 'st';

/** Hash value for `st` when the query came from the search field submit (typed term). */
const HASH_VALUE_SEARCH_TEXTBOX = 'search';

/** Hash value for `st` when the user picked a type-ahead / autocomplete list item. */
const HASH_VALUE_AUTOCOMPLETE = 'autocomplete';

class SiteSearch extends Component {

    /**
     * How the user first chose this search session (typed submit, recommendation tiles, or autocomplete list).
     * Refinements (e.g. filters) should keep this attribution instead of switching to "recommended".
     */
    getAnalyticsEntrySearchType() {
        return this.initialSearchInteractionType ?? this.lastSearchType ?? 'search textbox';
    }

    async fetchAutocompleteAndRender() {
        const { term } = this;
        const secondaryTags = (this.allowSecondaryFilters && !this.hideForm ? this.activeSecondaryTags : this.secondaryTags).join(',');
        if (!term && !secondaryTags) {
            return;
        }
        const payload = {
            q: term,
            page: 1,
            secondaryTags,
        };
        const url = `${window.location.origin}${this.searchUrl}`;

        const requestKey = `${term}-${1}-${secondaryTags}`;
        this.latestRequestKey = requestKey;

        const data = await fetchAPIRequest({
            payload,
            method: 'GET',
            url,
        });

        if (this.latestRequestKey && requestKey === this.latestRequestKey) {
            this.autocompleteResults = data?.results;
            this.autocompleteTerm = term;
            this.renderAutocomplete();
        }
    }

    renderAutocomplete(hidden = false, results = this.autocompleteResults, term = this.autocompleteTerm) {
        this.autocompleteOpen = !hidden;
        if (this.$els.wrappingDrawer) {
            this.$els.wrappingDrawer.classList.toggle('cmp-drawer__content--no-scrolling', this.autocompleteOpen);
        }
        render(this.autocompleteTemplate(results, term, hidden), this.$els.autocomplete);
    }

    autocompleteTemplate(results, term, hide) {
        return html`
            <ul style=${styleMap({ display: hide ? 'none' : '' })} class="cmp-site-search__autocomplete" @keydown="${this.autocompleteKeydown.bind(this)}">
                ${!results?.length
                    ? html`<li>No results</li>`
                    : map(results, (item, index) => html`
                        <li>
                            <a class="${componentName}__autocomplete-item" data-index="${index}" @click="${this.autocompleteClick.bind(this)}" href="${item.link}" @blur="${this.closeAutocomplete.bind(this)}">${
                                map(item.title.toLowerCase().split(term.toLowerCase()), (part, i) => {
                                    if (i === 0) {
                                        return html`${part}`;
                                    }
                                    return html`<b>${term.toLowerCase()}</b>${part}`;
                                })
                            }</a>
                        </li>`)}
            </ul>`;
    }

    autocompleteClick(e) {
        const typedTerm = (this.$els.input?.value ?? this.term ?? '').trim();
        const typeAheadRank = parseInt(e.currentTarget.dataset.index, 10);
        this.lastSearchType = 'autoComplete';
        this.initialSearchInteractionType = 'autoComplete';
        const filterLabels = Array.from(this.$els.filters || [])
            .filter((f) => this.activeSecondaryTags.includes(f.dataset?.tagId))
            .map((f) => f.innerText?.trim())
            .filter(Boolean);
        this.searchAllDataLayerEvent({
            name: 'search_submit',
            term: typedTerm,
            type: 'autoComplete',
            typeAheadRank,
            filterLabels,
        });
        const searchUrl = e.currentTarget.href;
        const lastIndex = searchUrl.split('/').filter(Boolean).pop().replace(/\.html$/, '');
        if(lastIndex === 'pronto-inquiry') {
            pushToAllDataLayers({
                event: 'form_start', capability: 'dce forms', formStep: 'dce:pronto:step0:start', formType: 'dce: pronto form'
            });
        } else if (lastIndex === 'personal-data-privacy-request') {
            pushToAllDataLayers({
                event: 'form_start', capability: 'dce forms', formStep: 'dce:privacy:step0:start', formType: 'dce: privacy form'
            });
        } else if (lastIndex === 'start-my-ghost-kitchen') {
            pushToAllDataLayers({
                event: 'form_start', capability: 'dce forms', formStep: 'dce:ghostkitchen:step0:start', formType: 'dce: ghost kitchen form'
            });
        } else if (lastIndex === 'toast-referral') {
            pushToAllDataLayers({
                event: 'form_start', capability: 'dce forms', formStep: 'dce:refer:step0:start', formType: 'dce: refer form'
            });
        }
    }

    closeAutocomplete() {
        setTimeout(() => {
            if (!document.activeElement.classList.contains(`${componentName}__autocomplete-item`)) {
                this.renderAutocomplete(true);
            }
        }, 200);
    }
    autocompleteKeydown(event) {
        if (event.key === 'Escape') {
            event.preventDefault();
            this.renderAutocomplete(true);
            this.$els.input?.focus?.();
            this.$els.input?.select?.();
        }
        if (event.key === 'ArrowDown') {
            event.preventDefault();
            const links = Array.from(this.$els.autocomplete.querySelectorAll('a'));
            const index = links.indexOf(event.target);
            if (index > -1) {
                if (index === links.length - 1) {
                    links[0].focus();
                } else {
                    links[index + 1].focus();
                }
            }
        }
        if (event.key === 'ArrowUp') {
            event.preventDefault();
            const links = Array.from(this.$els.autocomplete.querySelectorAll('a'));
            const index = links.indexOf(event.target);
            if (index > -1) {
                if (index === 0) {
                    links[links.length - 1].focus();
                } else {
                    links[index - 1].focus();
                }
            }
        }
    }

    renderFilters() {
        this.querySelectorAll(`.${componentName}__filter`).forEach((tagEl) => {
            tagEl.classList.toggle(ACTIVE_FILTER_CLASSNAME, this.activeSecondaryTags.includes(tagEl.dataset?.tagId));
        });
    }

    shouldWrapSymbolNode(textNode) {
        const symbolPattern = /[®™]/;
        if (!textNode?.nodeValue || !symbolPattern.test(textNode.nodeValue)) {
            return false;
        }

        const { parentElement } = textNode;
        if (!parentElement || parentElement.closest('sup')) {
            return false;
        }
        // Avoid mutating lit-managed teaser internals; symbols are rendered in TeaserTemplate.
        if (parentElement.closest('cmp-teaser-template')) {
            return false;
        }

        const blockedTags = ['SCRIPT', 'STYLE', 'NOSCRIPT', 'TEXTAREA'];
        return !blockedTags.includes(parentElement.tagName);
    }

    wrapSpecialSymbolsInTextNode(textNode) {
        if (!this.shouldWrapSymbolNode(textNode)) {
            return;
        }

        const ownerDocument = textNode.ownerDocument || document;
        const parts = textNode.nodeValue.split(/([®™])/);
        const fragment = ownerDocument.createDocumentFragment();
        let hasReplacement = false;

        parts.forEach((part) => {
            if (part === '®' || part === '™') {
                const supEl = ownerDocument.createElement('sup');
                supEl.textContent = part;
                fragment.appendChild(supEl);
                hasReplacement = true;
            } else if (part) {
                fragment.appendChild(ownerDocument.createTextNode(part));
            }
        });

        if (hasReplacement) {
            textNode.replaceWith(fragment);
        }
    }

    /**
     * Wraps special symbols (® and ™) with <sup> tags in rendered search result text.
     * Uses text-node replacement to avoid mutating structural markup (carousel/custom elements).
     */
    processSpecialCharacters() {
        if (!this.$els.resultList) {
            return;
        }

        const targetNodeFilter = this.$els.resultList.ownerDocument?.defaultView?.NodeFilter || NodeFilter;
        const textNodes = [];
        const walker = this.$els.resultList.ownerDocument.createTreeWalker(
            this.$els.resultList,
            targetNodeFilter.SHOW_TEXT,
            {
                acceptNode: (node) => (this.shouldWrapSymbolNode(node)
                    ? targetNodeFilter.FILTER_ACCEPT
                    : targetNodeFilter.FILTER_REJECT)
            }
        );

        while (walker.nextNode()) {
            textNodes.push(walker.currentNode);
        }

        textNodes.forEach((node) => {
            this.wrapSpecialSymbolsInTextNode(node);
        });

        // Update capitalization for search results
        updateCapitalization(this.$els.resultList);
    }

    renderResultLabel(isLoading) {
        render(this.resultLabelTemplate(isLoading), this.$els.resultLabel);
    }

    resultLabelTemplate(isLoading) {
        let label = this.hasResults ? this.resultsLabel || '' : this.noResultsLabel || '';
        label = label
            .replaceAll('{0}', this.term || '')
            .replaceAll('{1}', this.pagination?.totalMatches || '');
        return html`
            <span style=${styleMap({ display: isLoading || this.hideResults || this.hideLabel || !this.paginated ? 'none' : null })}>${label}</span>
        `;
    }

    renderResultList(isLoading, page, term) {
        if (this.hideResults) {
            return;
        }
        if (!this.clearResultListRenderOnce) {
            this.clearResultListRenderOnce = true;
            if (this.$els.resultList) {
                this.$els.resultList.innerHTML = '';
            }
        }

        if (!this.hideForm && !this.hideLabel) {
            this.$els.resultsWrap.style.display = null;
            this.$els?.resultsWrap?.classList.toggle(`${componentName}--no-results`, !this.hasResults && !isLoading);
        } else if (this.$els.resultsWrap) {
            this.$els.resultsWrap.style.display = this.hideResults || (!this.hasResults && !isLoading) ? 'none' : null;
        }

        render(this.resultListTemplate(isLoading, page, term), this.$els.resultList);
        this.injectBannerOrTile();

        if (!this.hideResults && this.paginated && this.isShowMore && this.$els.showMoreButton) {
            const isLastPage = this.pagination && page === this.pagination.totalPages;
            this.$els.showMoreButton.style.visibility = this.hideResults || (!this.hasResults && !isLoading) || isLastPage ? 'hidden' : null;
        }

        if (this.isCarousel) {
            this.initCarousel();
        }

        // Process special characters after DOM is rendered
        // Use setTimeout to ensure custom elements (like cmp-teaser-template) have rendered
        if (!isLoading && this.hasResults) {
            setTimeout(() => {
                this.processSpecialCharacters();
            }, 0);
        }
    }

    injectBannerOrTile() {
        const tilePlacementList = this.$els.resultList.children[0].children;
        // Inject tile
        if (this.$els.tile && tilePlacementList.length > this.tilePosition) {
            if (this.renderTile && !this.hasRenderedTile) {
                this.hasRenderedTile = true;
                this.$els.tile.style.display = null;
                if (this.isCarousel) {
                    this.$els.tile.classList.add('cmp-carousel__item', 'swiper-slide');
                }
                this.$els.resultList.children[0].insertBefore(this.$els.tile, tilePlacementList[this.tilePosition]);
            }
            if (!this.renderTile) {
                this.$els.tile.style.display = 'none';
            }
        }

        // Inject banner
        if (this.$els.banner) {
            this.$els.banner.style.display = this.renderBanner ? null : 'none';
        }
    }

    resultListTemplate(isLoading, page, term) {
        if (this.isCarousel) {
            return html`
            <div class="cmp-carousel__slides swiper-wrapper">
                ${this.renderedResults?.map((item, i) => {
                    if ((!item.loaded && !isLoading) || item.hidden) {
                        return '';
                    }
                    return this.resultItemTemplate(item, i, isLoading, page, term);
                }) || ''}
            </div>`;
        }
        return html`
        <ul class="${componentName}__results-list" role="list">
            ${this.renderedResults?.map((item, i) => this.resultItemTemplate(item, i, isLoading, page, term)) || ''}
        </ul>`;
    }

    resultItemTemplate(item, i, isLoading, page, term) {
        const hide = (!item.loaded && !isLoading) || item.hidden;
        const subtitleFromTags = item.tags?.find?.((tag) => tag.tagId?.startsWith?.('us-foods:content-types/'))?.title;
        const image = item.image || this.fallbackImage;
        return html`
        <cmp-teaser-template
            role="${!this.isCarousel ? 'listitem' : ''}" class="${!this.isCarousel ? 'cmp-site-search__result-item' : 'cmp-carousel__item swiper-slide'}" index="${i}" maintitle="${item.title}" subtitle="${item.subtitle || subtitleFromTags}"
            description="${item.description}" term="${term}" link="${item.link}" loaded="${item.loaded}" tags="${JSON.stringify(item.tags)}"
            image="${image}" loading="${isLoading && !item.loaded}"
            page="${page}" hide=${hide} hascta="${this.hasTeaserCTA}" ctalabel="${this.teaserCTALabel}" ctaicon="${this.teaserCTAIcon}"></cmp-teaser-template>`;
    }

    initCarousel() {
        if (this.swiper) {
            this.swiper.destroy();
        }
        Swiper.use([A11y, Navigation, Pagination]);

        const dynamicBullets = this.pagination?.totalMatches && this.pagination.totalMatches > 10;

        this.swiper = new Swiper(this.$els.swiper, {
            allowTouchMove: true,
            breakpoints: {
                0: {
                    slidesPerView: 1.11,
                },
                768: {
                    slidesPerView: this.isCampaignTeaser ? 1.11 : 2.11,
                },
                1024: {
                    slidesPerView: this.isCampaignTeaser ? 1.11 : 3,
                }
            },
            navigation: {
                nextEl: '.swiper-button-next',
                prevEl: '.swiper-button-prev'
            },
            pagination: {
                bulletActiveClass: 'cmp-carousel__indicator--active swiper-pagination-bullet-active',
                bulletClass: 'cmp-carousel__indicator swiper-pagination-bullet',
                bulletElement: 'li',
                clickable: true,
                el: this.querySelector('.cmp-carousel__indicators.swiper-pagination'),
                dynamicBullets,
                type: 'bullets'
            },
            slideClass: 'cmp-carousel__item',
            slideActiveClass: 'cmp-carousel__item--active',
            slideVisibleClass: 'swiper-slide-visible',
            slidesPerGroup: 1,
            slidesPerView: 1,
            spaceBetween: 0,
            updateOnWindowResize: true,
            centeredSlidesBounds: true,
            wrapperClass: 'cmp-carousel__slides'
        });

        if (this.previousCarouselPosition !== undefined) {
            this.swiper.slideTo(this.previousCarouselPosition, 0, false);
        }

        this.swiper.on('slideChangeTransitionEnd', () => {
            this.refreshActiveSlide();
            if (this.userInteraction) {
                dataLayerEvent({
                    event: 'slideshow_interaction',
                    eventInfo: {
                        slide: this.swiper.activeIndex
                    }
                }, this);
            }
        });

        this.addEventListener('pointerdown', () => {
            this.userInteraction = true;
            setTimeout(() => {
                this.userInteraction = false;
            }, 1000);
        });
    }

    refreshActiveSlide() {
        [...this.querySelectorAll('.slider-slide')].forEach((slide, i) => {
            slide.toggleAttribute('inert', i !== this.swiper.activeIndex);
            slide.setAttribute('tabindex', '-1');
        });

        if (this.paginated && !this.isShowMore) {
            const isNearEnd = this.swiper.activeIndex + 4 > this.swiper.slides.length;
            if (this.pagination && isNearEnd && this.highestPage < this.pagination.totalPages) {
                this.previousCarouselPosition = this.swiper.activeIndex;
                this.highestPage += 1;
                this.page = this.highestPage;
                this.buildHashParam();
                this.fetchResultsAndRender(true);
            }
        }
    }

    updateResultState(data, isLoading, page, term) {
        if (page === 1) {
            this.hasResults = !!data?.results?.length;
        }

        if (data?.pagination) {
            this.pagination = data?.pagination;
        }

        let newTotalLength = page * this.numPerPage;
        if (this.pagination?.totalMatches && newTotalLength > this.pagination.totalMatches && isLoading) {
            newTotalLength = this.pagination.totalMatches;
        }

        // For first skeleton render
        if (!this.renderedResults) {
            this.renderedResults = Array.from({ length: newTotalLength }).map(() => ({}));
        }
        // For additional page skeleton renders
        if (this.renderedResults.length < newTotalLength) {
            const skeletonResults = Array.from({ length: newTotalLength - this.renderedResults.length }).map(() => ({}));
            this.renderedResults = this.renderedResults.concat(skeletonResults);
        }

        // Add result data to renderedResults state
        const pageOffset = (page - 1) * this.numPerPage;
        if (data?.results) {
            for (let i = 0; i < this.numPerPage; i++) {
                const newResult = data.results[i];
                if (newResult) {
                    newResult.loaded = true;
                    this.renderedResults[pageOffset + i] = newResult;
                }
            }
        }

        const isLastPage = !this.paginated || (this.pagination && page === this.pagination.totalPages);
        this.renderedResults.forEach((result, i) => {
            if (isLoading) {
                const isTileOverflow = this.renderTile && i > this.tilePosition
                    && i > this.renderedResults.length - 3;
                if (isTileOverflow) {
                    result.hidden = true;
                }
                if (isLastPage) {
                    result.hidden = false;
                }
            } else {
                const isTileOverflow = this.renderTile && i > this.tilePosition && !isLastPage
                    && i > this.renderedResults.length - 3;
                result.hidden = isTileOverflow;
            }
        });

        // Render based on the updated renderedResults state
        this.renderResultList(isLoading, page, term);
        this.hideLabel = false;
        this.renderResultLabel(isLoading);
        if (this.pageLoad && !isLoading && page === 1) {
            const pageLoadType = this.getAnalyticsEntrySearchType();
            const filterLabels = Array.from(this.$els.filters || [])
                .filter((f) => this.activeSecondaryTags.includes(f.dataset?.tagId))
                .map((f) => f.innerText?.trim())
                .filter(Boolean);
            const resultsCount = this.pagination?.totalMatches
                ?? (this.hasResults ? (this.renderedResults?.filter((r) => r.loaded)?.length ?? 0) : 0);
            this.searchAllDataLayerEvent({
                name: 'page_load',
                term,
                type: pageLoadType,
                numberOfResults: resultsCount,
                filterLabels,
            });
            if (!this.searchResultPage && pageLoadType === 'recommended') {
                this.searchAllDataLayerEvent({
                    name: 'search_submit',
                    term: this.$els.input.value,
                    type: 'recommended',
                    numberOfResults: resultsCount,
                    filterLabels,
                });
            }
            if (this.lastSearchType !== 'recommended' && this.lastSearchType !== 'autoComplete') {
                this.lastSearchType = 'search textbox';
            }
            if (this.initialSearchInteractionType === undefined) {
                let inferredInitial = 'search textbox';
                if (this.lastSearchType === 'recommended') {
                    inferredInitial = 'recommended';
                } else if (this.lastSearchType === 'autoComplete') {
                    inferredInitial = 'autoComplete';
                }
                this.initialSearchInteractionType = inferredInitial;
            }
            this.pageLoad = false;
        }
    }

    resetResultState() {
        this.renderedResults = null;
        this.pendingResults = {};
        this.pagination = null;
        this.renderTile = this.$els.tile && (isEditMode()
            || (this.allowSecondaryFilters && !this.hideForm && this.activeSecondaryTags.includes('us-foods:content-types/product'))
            || ((!this.allowSecondaryFilters || this.hideForm) && this.secondaryTags.includes('us-foods:content-types/product')));
        this.renderBanner = this.$els.banner && (isEditMode() || !this.renderTile);
        this.hasRenderedTile = false;
        this.highestPage = null;
        this.lowestPage = null;
        this.page = 1;
        this.previousCarouselPosition = undefined;
        this.buildHashParam();
    }

    async fetchResultsAndRender(sameQueryDifferentPage = false, isDummy = false) {
        if (this.hideResults) {
            return;
        }
        if (!sameQueryDifferentPage) {
            this.resetResultState();
        }
        // Limits page
        if (this.pagination && this.page > this.pagination.totalPages) {
            this.page = this.pagination.totalPages;
        }
        if (this.lowestPage === null || this.page < this.lowestPage) {
            this.lowestPage = this.page;
        }
        if (this.highestPage === null || this.page > this.highestPage) {
            this.highestPage = this.page;
        }

        // Confirm search criteria
        const { page, term } = this;
        const secondaryTags = (this.allowSecondaryFilters && !this.hideForm ? this.activeSecondaryTags : this.secondaryTags).sort().join(',');
        if (this.hideInitialResults && !term && !secondaryTags && !isDummy && !this.hideForm) {
            this.hasResults = false;
            this.hideLabel = true;
            this.renderResultList();
            this.renderResultLabel();
            return;
        }

        const payload = {
            q: term,
            page,
            secondaryTags,
        };
        const url = `${window.location.origin}${this.searchUrl}`;

        // // Prevents flicker caused by very fast search requests
        // const skeletonTimer = setTimeout(() => {
        // }, 50);
        // Sets up skeleton data and renders
        this.updateResultState(undefined, true, page, term);

        // Prevent duplicate requests
        if (!this.pendingResults) {
            this.pendingResults = {};
        }
        const requestKey = `${term}-${page}-${secondaryTags}`;
        if (this.pendingResults[requestKey]) {
            return;
        }

        // Respond with dummy data (testing)
        if (isDummy) {
            this.pendingResults[requestKey] = searchDummyData(this.numPerPage);
            // Add random delay
            await timeout(randomMember([1000, 2000, 3000]));
        } else {

            // Make search request
            this.pendingResults[requestKey] = await fetchAPIRequest({
                payload,
                method: 'GET',
                url,
            });
        }
        // clearTimeout(skeletonTimer);
        if (!this.pendingResults[requestKey]) {
            return;
        }
        // Handles result data and renders
        const data = this.pendingResults[requestKey];
        this.updateResultState(data, false, page, term);
    }

    buildHashParam() {
        if (this.allowUrlControl) {
            const params = {
                q: this.term,
                page: this.page,
                tags: this.activeSecondaryTags.join(','),
            };
            if (this.lastSearchType === 'recommended') {
                params[HASH_PARAM_SEARCH_SOURCE] = 'recommended';
            } else if (this.lastSearchType === 'autoComplete') {
                params[HASH_PARAM_SEARCH_SOURCE] = HASH_VALUE_AUTOCOMPLETE;
            } else if (this.lastSearchType === 'search textbox') {
                params[HASH_PARAM_SEARCH_SOURCE] = HASH_VALUE_SEARCH_TEXTBOX;
            }
            const newHash = `${buildQueryString(params)}`;
            window.history.replaceState(null, null, `#${newHash}`);
        }
    }

    updateStateFromHash() {
        if (this.allowUrlControl) {
            const hashParams = parseHashParameters();
            // for some reason, these get double encoded on publish servers
            this.term = decodeURIComponent(hashParams.q || '');
            const tagsStr = hashParams.tags || '';
            const tags = tagsStr.split(',')
                .filter((tagStr) => this.allowSecondaryFilters && !this.hideForm && !!this.secondaryTags.includes(tagStr));
            if (tags.length) {
                this.activeSecondaryTags = tags;
                this.renderFilters();
            }
            try {
                this.page = parseInt(hashParams.page || 1, 10);
            } catch {
                this.page = 1;
            }
            if (this.term) {
                this.$els.input.value = this.term;
            }
            const hashSearchSource = hashParams[HASH_PARAM_SEARCH_SOURCE];
            if (hashSearchSource === 'recommended') {
                this.lastSearchType = 'recommended';
                this.initialSearchInteractionType = 'recommended';
            } else if (hashSearchSource === HASH_VALUE_AUTOCOMPLETE) {
                this.lastSearchType = 'autoComplete';
                this.initialSearchInteractionType = 'autoComplete';
            } else if (hashSearchSource === HASH_VALUE_SEARCH_TEXTBOX) {
                this.lastSearchType = 'search textbox';
                this.initialSearchInteractionType = 'search textbox';
            } else {
                this.lastSearchType = undefined;
                this.initialSearchInteractionType = undefined;
            }
        }
    }

    addEventListeners() {
        this.$els.form.addEventListener('submit', async(e) => {
            e.preventDefault();

            const filterLabels = Array.from(this.$els.filters || [])
                .filter((f) => this.activeSecondaryTags.includes(f.dataset?.tagId))
                .map((f) => f.innerText?.trim())
                .filter(Boolean);

            if (this.searchResultPage && this.$els.input.value) {
                const submitHash = buildQueryString({
                    q: this.$els.input.value.trim(),
                    [HASH_PARAM_SEARCH_SOURCE]: HASH_VALUE_SEARCH_TEXTBOX,
                });
                window.location = `${this.searchResultPage}.html#${submitHash}`;
            } else {
                this.term = (this.$els.input.value ?? '').trim();
                this.lastSearchType = 'search textbox';
                this.initialSearchInteractionType = 'search textbox';
                this.buildHashParam();
                await this.fetchResultsAndRender();
                this.searchAllDataLayerEvent({
                    name: 'search_submit',
                    term: this.$els.input.value,
                    type: 'search textbox',
                    numberOfResults: this.pagination?.totalMatches ?? 0,
                    filterLabels,
                });
            }
        });

        this.$els.input.addEventListener('focus', () => {
            if (this.isAutocomplete && this.$els.input.value) {
                this.fetchAutocompleteAndRender();
            }
        });

        this.$els.autocomplete.addEventListener('focus', () => {
            if (this.isAutocomplete && this.$els.input.value) {
                this.renderAutocomplete(false);
            }
        });

        this.$els.input.addEventListener('blur', () => {
            this.closeAutocomplete();
        });

        this.$els.autocomplete.addEventListener('blur', () => {
            this.renderAutocomplete(true);
        });

        this.$els.clearButton.addEventListener('click', (e) => {
            e.preventDefault();
            this.$els.input.value = '';
            this.term = '';
            this.activeSecondaryTags = [];
            this.renderFilters();
            this.lastSearchType = undefined;
            this.initialSearchInteractionType = undefined;
            this.buildHashParam();
            this.hasResults = false;
            this.hideLabel = true;
            this.renderResultList();
            this.renderResultLabel();
            if (this.isAutocomplete) {
                this.renderAutocomplete(true);
            }
            if (!this.hideInitialResults) {
                this.fetchResultsAndRender();
            }
        });

        this.$els.input.addEventListener('keyup', (event) => {
            if (event.key === 'Enter') {
                event.preventDefault();
                this.renderAutocomplete(true);
                return;
            }
            if (event.key === 'Escape') {
                if (this.autocompleteOpen) {
                    event.preventDefault();
                    event.stopPropagation();
                }
                this.renderAutocomplete(true);
                this.$els.input.focus();
                return;
            }
            if (event.key === 'ArrowDown') {
                if (this.autocompleteOpen) {
                    event.preventDefault();
                    this.$els.autocomplete?.children?.[0]?.querySelector('a')?.focus();
                }
                return;
            }

            this.term = event.target.value;

            if (this.isAutocomplete) {
                if (!this.term) {
                    this.renderAutocomplete(true);
                } else {
                    this.fetchAutocompleteAndRender();
                }
            }
        });

        this.$els.filters.forEach((filterEl) => {
            filterEl.addEventListener('click', async (e) => {
                e.preventDefault();
                const tagId = filterEl?.dataset?.tagId;
                if (!tagId) return;

                if (!this.activeSecondaryTags) {
                    this.activeSecondaryTags = [];
                }
                const wasActive = this.activeSecondaryTags.includes(tagId);
                if (wasActive) {
                    this.activeSecondaryTags = this.activeSecondaryTags.filter((id) => id !== tagId);
                } else {
                    this.activeSecondaryTags.push(tagId);
                }

                // Keep st= / searchType aligned with how the user first reached results (textbox vs recommended), not the filter UI.
                const filterAnalyticsType = this.getAnalyticsEntrySearchType();
                this.buildHashParam();
                this.renderFilters(tagId);
                await this.fetchResultsAndRender();
                const filterLabels = Array.from(this.$els.filters || [])
                    .filter((f) => this.activeSecondaryTags.includes(f.dataset?.tagId))
                    .map((f) => f.innerText?.trim())
                    .filter(Boolean);
                const resultsCount = this.pagination?.totalMatches ?? 0;
                if (!wasActive) {
                    this.searchAllDataLayerEvent({
                        name: 'search_filters_applied',
                        term: (this.$els.input?.value ?? this.term ?? '').trim(),
                        type: filterAnalyticsType,
                        numberOfResults: resultsCount,
                        filterLabels,
                    });
                }
            });
        });

        if (!this.hideResults && this.paginated && !this.isCarousel && !this.isShowMore) {
            document.addEventListener('scroll', () => {
                const isNextInView = isPartiallyScrolledIntoView(this.$els.nextMarker);
                if (this.pagination && isNextInView && this.highestPage < this.pagination.totalPages) {
                    this.highestPage += 1;
                    this.page = this.highestPage;
                    this.buildHashParam();
                    this.fetchResultsAndRender(true);
                }
                const isPrevInView = isPartiallyScrolledIntoView(this.$els.previousMarker);
                if (isPrevInView && this.lowestPage > 1) {
                    this.lowestPage -= 1;
                    this.page = this.lowestPage;
                    this.buildHashParam();
                    this.fetchResultsAndRender(true);
                }
            });
        }

        if (!this.hideResults && this.paginated && this.isShowMore && this.$els.showMoreButton) {
            this.$els.showMoreButton.addEventListener('click', () => {
                if (this.pagination && this.highestPage < this.pagination.totalPages) {
                    this.highestPage += 1;
                    this.previousCarouselPosition = this.swiper?.activeIndex;
                    this.page = this.highestPage;
                    this.buildHashParam();
                    this.fetchResultsAndRender(true);
                }
            });
        }

        [...this.$els.searchBoxSuggestions].forEach((suggestionEl) => {
            if (suggestionEl) {
                if (this.allowUrlControl && !this.hideForm) {
                    suggestionEl.addEventListener('click', () => {
                        setTimeout(() => {
                            document.querySelector('cmp-header cmp-drawer')?.close?.();
                            this.updateStateFromHash();
                            this.lastSearchType = 'recommended';
                            this.initialSearchInteractionType = 'recommended';
                            this.fetchResultsAndRender();
                        }, 300);
                    });
                } else {
                    suggestionEl.addEventListener('click', () => {
                        this.lastSearchType = 'recommended';
                        this.initialSearchInteractionType = 'recommended';
                    });
                }
            }
        });

        [...this.$els.featuredContent].forEach((featuredEL) => {
            if (featuredEL) {
                featuredEL.addEventListener('click', () => {
                    setTimeout(() => {
                        const url = new URL(window.location.href);
                        url.searchParams.set('lnksrc', 'dce:search:featurecontent');
                        window.location.href = url.toString();
                    }, 300);
                });
            }
        });
    }

    searchAllDataLayerEvent({
        name,
        typeAheadRank,
        resultRank,
        numberOfResults,
        term,
        type,
        filterLabels,
    }) {

        const trackSearchEvent = {
            event: name,
            capability: 'search',
            page: {
                pageInfo: {
                    pageName: 'us foods:dce:search results',
                }
            },
            search: {
                searchEngine: 'AEM-Search Results',
            }
        };
        if(term !== undefined) {
            trackSearchEvent.search.term = term;
        }
        if(type !== undefined) {
            trackSearchEvent.search.searchType = type;
        }

        if (numberOfResults !== undefined) {
            trackSearchEvent.search.numberOfResults = numberOfResults;
        }
        if (resultRank !== undefined) {
            trackSearchEvent.search.resultRank = resultRank;
        }
        if (filterLabels !== undefined) {
            trackSearchEvent.search.filters = Array.isArray(filterLabels)
                ? filterLabels.join(', ')
                : filterLabels;
        } else if (this.activeSecondaryTags?.length && this.$els?.filters) {
            const labelList = Array.from(this.$els.filters)
                .filter((f) => this.activeSecondaryTags.includes(f.dataset?.tagId))
                .map((f) => f.innerText?.trim())
                .filter(Boolean);
            if (labelList.length) {
                trackSearchEvent.search.filters = labelList.join(', ');
            }
        }
        if (typeAheadRank !== undefined) {
            trackSearchEvent.search.typeAheadRank = typeAheadRank;
        }
        pushToAllDataLayers(trackSearchEvent);
    }

    /**
     * Initialize Component
     */
    connectedCallback() {
        super.connectedCallback?.();
        // Set Up Elements
        this.$els = {
            form: this.querySelector(`.${componentName}__form`),
            input: this.querySelector(`.${componentName} input`),
            button: this.querySelector(`.${componentName}__button`),
            resultList: this.querySelector(`.${componentName}__results-list-wrap`),
            resultsWrap: this.querySelector(`.${componentName}__results-wrap`),
            resultLabel: this.querySelector(`.${componentName}__results-label`),
            autocomplete: this.querySelector(`.${componentName}__autocomplete-container`),
            clearButton: this.querySelector(`.${componentName}__clear`),
            tile: this.querySelector(`.${componentName}__tile`),
            banner: this.querySelector(`.${componentName}__banner`),
            filters: this.querySelectorAll(`.${componentName}__filter`),
            previousMarker: this.querySelector(`.${componentName}__previous-marker`),
            nextMarker: this.querySelector(`.${componentName}__next-marker`),
            wrappingDrawer: this.closest('.cmp-drawer__content'),
            searchBoxSuggestions: document.querySelectorAll('.cmp-search-box__suggestion'),
            featuredContent: document.querySelectorAll('.cmp-search-box__featured-content .cmp-teaser__title-link'),
            swiper: this.querySelector('.swiper'),
            showMoreButton: this.querySelector(`.${componentName}__show-more`),
        };
        if (this.$els.tile) {
            this.$els.tile.classList.add('cmp-site-search__result-item', 'cmp-site-search__result-item--tile');
            this.$els.tile.style.display = 'none';
        }

        // Set Up Data
        this.isDataLayerEnabled = document.body.hasAttribute('data-cmp-data-layer-enabled');
        this.dataLayer = window.appEventData || [];

        // Component configs
        this.searchUrl = this.dataset.searchurl;
        this.numPerPage = parseInt(this.dataset.numPerPage, 10);
        this.secondaryTags = (this.dataset.secondaryTags || '').split(',');
        this.hideForm = this.dataset.hideForm === 'true';
        this.allowSecondaryFilters = this.dataset.allowSecondaryFilters === 'true';
        this.isAutocomplete = this.dataset.isAutocomplete === 'true' && !this.hideForm;
        this.hideResults = this.dataset.hideResults === 'true' && this.isAutocomplete;
        this.allowUrlControl = this.dataset.allowUrlControl === 'true';
        this.paginated = this.dataset.paginated === 'true';
        this.tilePosition = parseInt(this.dataset.tilePosition !== undefined ? this.dataset.tilePosition : DEFAULT_TILE_POSITION, 10);
        if (isEditMode() && this.tilePosition > this.numPerPage - 1) {
            this.tilePosition = 0;
        }
        this.fallbackImage = this.dataset.fallbackImage;
        this.noResultsLabel = this.dataset.noResultsLabel;
        this.resultsLabel = this.dataset.resultsLabel;
        this.searchResultPage = this.dataset.searchResultPage;
        this.isCarousel = this.dataset.isCarousel === 'true';
        this.hasTeaserCTA = this.dataset.hasTeaserCta === 'true';
        this.teaserCTALabel = this.dataset.teaserCtaLabel;
        this.teaserCTAIcon = this.dataset.teaserCtaIcon;
        this.isCampaignTeaser = !!this.closest('.cmp-teaser--type-campaign');
        this.isShowMore = this.dataset.isShowMore === 'true';
        this.hideInitialResults = this.dataset.hideInitialResults === 'true';
        this.pageLoad = true;

        // Search criteria
        this.term = '';
        this.page = 1;
        this.activeSecondaryTags = [];
        this.renderedResults = null;//will be array []
        this.pagination = null;//will be object {}

        // Fire Init Funcs
        this.updateStateFromHash();
        this.addEventListeners();

        if (isEditMode()) {
            this.fetchResultsAndRender(false, true);
        } else if (this.hideForm || !this.hideInitialResults || this.term !== '' || (this.allowSecondaryFilters && this.activeSecondaryTags.length)) {
            this.fetchResultsAndRender();
        }
    }
}

registerComponent(componentName, SiteSearch);
