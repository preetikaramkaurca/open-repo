/* eslint-disable */
document.addEventListener('DOMContentLoaded', () => {
    const EXPERIENCE_FRAGMENTS = '/content/experience-fragments';
    const submitButton = document.getElementById('cleanupTargetSubmit');
    const result = document.querySelector('.cleanup-target-result');
    const mount = document.querySelector('.cleanup-target-progress-mount');
    if (!submitButton || !result || !mount) {
        return;
    }

    const label = document.createElement('div');
    label.id = 'cleanupTargetProgressLabel';
    label.textContent = 'Processing...';
    label.hidden = true;

    const progress = document.createElement('coral-progress');
    progress.id = 'cleanupTargetProgress';
    progress.setAttribute('indeterminate', '');
    progress.setAttribute('size', 'L');
    progress.hidden = true;
    mount.appendChild(label);
    mount.appendChild(progress);

    let featureEnabled = true;

    const setProcessing = (processing) => {
        label.hidden = !processing;
        progress.hidden = !processing;
        submitButton.disabled = processing || !featureEnabled;
    };

    const disableFeature = (message) => {
        featureEnabled = false;
        submitButton.disabled = true;
        const pathInput = document.querySelector('input[name="./path"]');
        if (pathInput) {
            pathInput.disabled = true;
        }
        result.textContent = message;
    };

    const renderResult = (data) => {
        if (!data || data.success !== true) {
            result.textContent = (data && data.message) || 'Cleanup failed.';
            return;
        }
        const lines = [
            data.message || 'Cleanup finished.',
            `Offers loaded: ${data.offerCount}`,
            `Metadata nodes checked: ${data.examined}`,
            `Kept: ${data.kept}`,
            `Deleted: ${data.deleted}`,
        ];
        if (data.skipped) {
            lines.push(`Skipped (offer id was not a number): ${data.skipped}`);
        }
        if (data.deletedPaths && data.deletedPaths.length) {
            lines.push('Deleted paths:');
            data.deletedPaths.forEach((deletedPath) => lines.push(deletedPath));
            if (data.truncated) {
                lines.push('Additional deleted paths were omitted from this list.');
            }
        }
        result.textContent = lines.join('\n');
    };

    fetch('/bin/usfoods/cleanup-adobe-target-ids', {
        credentials: 'same-origin',
        headers: { Accept: 'application/json' },
    })
        .then((response) => response.json())
        .then((data) => {
            if (data && data.enabled === false) {
                disableFeature(data.message || 'This feature is disabled contact Admin team');
            } else if (data && data.success === false && data.message) {
                disableFeature(data.message);
            }
        })
        .catch(() => {
            result.textContent = '';
        });

    submitButton.addEventListener('click', () => {
        if (!featureEnabled) {
            return;
        }
        const pathInput = document.querySelector('input[name="./path"]');
        const path = pathInput ? pathInput.value.trim().replace(/\/$/, '') : '';
        const allowed = path === EXPERIENCE_FRAGMENTS || path.indexOf(`${EXPERIENCE_FRAGMENTS}/`) === 0;
        if (!allowed || path.indexOf('..') !== -1) {
            result.textContent = 'Select a path under /content/experience-fragments.';
            return;
        }

        setProcessing(true);
        result.textContent = '';

        fetch('/libs/granite/csrf/token.json', { credentials: 'same-origin' })
            .then((response) => {
                if (!response.ok) {
                    throw new Error('Unable to read the CSRF token.');
                }
                return response.json();
            })
            .then((tokenPayload) => fetch('/bin/usfoods/cleanup-adobe-target-ids', {
                method: 'POST',
                credentials: 'same-origin',
                headers: {
                    'CSRF-Token': tokenPayload.token,
                    'Content-Type': 'application/x-www-form-urlencoded; charset=UTF-8',
                    Accept: 'application/json',
                },
                body: new URLSearchParams({ path }).toString(),
            }))
            .then((response) => response.json().then((data) => {
                renderResult(data);
            }).catch(() => {
                result.textContent = 'Cleanup failed.';
            }))
            .catch(() => {
                result.textContent = 'Cleanup failed.';
            })
            .finally(() => {
                setProcessing(false);
            });
    });
});
