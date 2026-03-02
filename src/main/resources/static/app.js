/**
 * Aperture — Upload + Library + Player Client
 * Upload with animations · Video library grid · Inline streaming player
 */
(() => {
    'use strict';

    /* ========================================
       DOM REFERENCES
       ======================================== */

    // Upload
    const dropzone = document.getElementById('dropzone');
    const browseBtn = document.getElementById('browse-btn');
    const fileInput = document.getElementById('file-input');
    const preview = document.getElementById('preview');
    const previewVid = document.getElementById('preview-video');
    const prevName = document.getElementById('preview-name');
    const prevDur = document.getElementById('preview-duration');
    const prevRes = document.getElementById('preview-res');
    const prevSize = document.getElementById('preview-size');
    const prevType = document.getElementById('preview-type');
    const removeBtn = document.getElementById('remove-btn');
    const form = document.getElementById('upload-form');
    const titleInput = document.getElementById('title');
    const descInput = document.getElementById('description');
    const panel = document.getElementById('panel');
    const prog = document.getElementById('prog');
    const progFill = document.getElementById('prog-fill');
    const progPct = document.getElementById('prog-pct');
    const cta = document.getElementById('cta');
    const ctaLabel = document.getElementById('cta-label');
    const ctaText = document.getElementById('cta-text');
    const doneState = document.getElementById('done-state');
    const toastsEl = document.getElementById('toasts');

    // Library
    const grid = document.getElementById('grid');
    const emptyState = document.getElementById('empty-state');
    const refreshBtn = document.getElementById('refresh-btn');

    // Player
    const playerEl = document.getElementById('player');
    const playerVideo = document.getElementById('player-video');
    const playerTitle = document.getElementById('player-title');
    const playerDesc = document.getElementById('player-desc');
    const playerClose = document.getElementById('player-close');

    const API_BASE = '/api/v1/videos';

    let file = null;

    /* ========================================
       HELPERS
       ======================================== */

    function formatBytes(n) {
        if (!n) return '0 B';
        const u = ['B', 'KB', 'MB', 'GB'];
        const i = Math.floor(Math.log(n) / Math.log(1024));
        return parseFloat((n / Math.pow(1024, i)).toFixed(1)) + ' ' + u[i];
    }

    function formatDur(s) {
        const m = Math.floor(s / 60);
        const sec = Math.floor(s % 60);
        return m + ':' + String(sec).padStart(2, '0');
    }

    function toast(msg, type = 'success') {
        const el = document.createElement('div');
        el.className = 'toast toast--' + type;
        el.innerHTML = '<span class="toast__dot"></span><span>' + msg + '</span>';
        toastsEl.appendChild(el);
        setTimeout(() => {
            el.classList.add('toast--out');
            el.addEventListener('animationend', () => el.remove());
        }, 4000);
    }

    function morphCtaLabel(text, icon) {
        ctaLabel.classList.add('cta__label--exit');
        setTimeout(() => {
            ctaText.textContent = text;
            const arrow = ctaLabel.querySelector('.cta__arrow');
            if (icon === null && arrow) arrow.style.display = 'none';
            else if (icon && arrow) arrow.outerHTML = icon;
            else if (arrow) arrow.style.display = '';
            ctaLabel.classList.remove('cta__label--exit');
            ctaLabel.classList.add('cta__label--enter');
            ctaLabel.addEventListener('animationend', () => ctaLabel.classList.remove('cta__label--enter'), { once: true });
        }, 200);
    }

    const ARROW_SVG = '<svg class="cta__arrow" width="16" height="16" viewBox="0 0 16 16" fill="none"><path d="M8 12V4M5 6L8 3L11 6" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/></svg>';

    /* ========================================
       FILE SELECTION & UPLOAD
       ======================================== */

    function pick(f) {
        if (!f) return;
        if (!f.type.startsWith('video/')) { toast('Please select a video file', 'error'); return; }
        if (f.size > 100 * 1024 * 1024) { toast('File exceeds 100 MB limit', 'error'); return; }

        file = f;
        const url = URL.createObjectURL(f);
        previewVid.src = url;
        prevName.textContent = f.name;
        prevSize.textContent = formatBytes(f.size);
        prevType.textContent = f.type.split('/')[1].toUpperCase();
        prevDur.textContent = '…';
        prevRes.textContent = '…';

        previewVid.addEventListener('loadedmetadata', function handler() {
            prevDur.textContent = formatDur(previewVid.duration);
            prevRes.textContent = previewVid.videoWidth + '×' + previewVid.videoHeight;
            previewVid.currentTime = previewVid.duration * 0.25;
            previewVid.removeEventListener('loadedmetadata', handler);
        });

        dropzone.hidden = true;
        preview.hidden = false;
    }

    function clearFile() {
        file = null;
        fileInput.value = '';
        previewVid.src = '';
        dropzone.hidden = false;
        preview.hidden = true;
    }

    function resetAll() {
        clearFile();
        titleInput.value = '';
        descInput.value = '';
        doneState.hidden = true;
        panel.classList.remove('panel--success');
        prog.hidden = true;
        prog.classList.remove('prog--fade');
        progFill.classList.remove('prog__fill--done');
        progPct.classList.remove('prog__pct--done');
        progFill.style.width = '0%';
        progPct.textContent = '0 %';
        cta.classList.remove('cta--done');
        cta.disabled = false;
        cta.type = 'submit';
        morphCtaLabel('Upload', ARROW_SVG);
    }

    // Drag & Drop
    ['dragenter', 'dragover'].forEach(e => dropzone.addEventListener(e, ev => { ev.preventDefault(); dropzone.classList.add('drop--over'); }));
    ['dragleave', 'drop'].forEach(e => dropzone.addEventListener(e, ev => { ev.preventDefault(); dropzone.classList.remove('drop--over'); }));
    dropzone.addEventListener('drop', e => pick(e.dataTransfer.files[0]));
    dropzone.addEventListener('click', () => fileInput.click());
    browseBtn.addEventListener('click', ev => { ev.stopPropagation(); fileInput.click(); });
    fileInput.addEventListener('change', () => pick(fileInput.files[0]));
    removeBtn.addEventListener('click', clearFile);

    // Form submit
    form.addEventListener('submit', e => {
        e.preventDefault();

        if (cta.classList.contains('cta--done')) { resetAll(); return; }
        if (!file) { toast('Select a video first', 'error'); return; }

        const title = titleInput.value.trim();
        if (!title) { toast('Title is required', 'error'); titleInput.focus(); return; }

        const fd = new FormData();
        fd.append('file', file);
        fd.append('title', title);
        fd.append('description', descInput.value.trim());

        cta.disabled = true;
        prog.hidden = false;
        prog.classList.remove('prog--fade');
        progFill.classList.remove('prog__fill--done');
        progPct.classList.remove('prog__pct--done');
        progFill.style.width = '0%';
        progPct.textContent = '0 %';
        morphCtaLabel('Uploading…', null);

        const xhr = new XMLHttpRequest();

        xhr.upload.addEventListener('progress', ev => {
            if (ev.lengthComputable) {
                const p = Math.round(ev.loaded / ev.total * 100);
                progFill.style.width = p + '%';
                progPct.textContent = p + ' %';
            }
        });

        xhr.addEventListener('load', () => {
            if (xhr.status >= 200 && xhr.status < 300) {
                handleSuccess(xhr.responseText);
            } else {
                handleError(xhr.responseText);
            }
        });

        xhr.addEventListener('error', () => {
            cta.disabled = false;
            prog.hidden = true;
            morphCtaLabel('Upload', ARROW_SVG);
            toast('Network error — check your connection', 'error');
        });

        xhr.open('POST', API_BASE);
        xhr.send(fd);
    });

    function handleSuccess(responseText) {
        let msg = 'Video uploaded successfully';
        try { msg = JSON.parse(responseText).message || msg; } catch { }

        progFill.style.width = '100%';
        progFill.classList.add('prog__fill--done');
        progPct.classList.add('prog__pct--done');
        progPct.textContent = '100 %';

        setTimeout(() => { cta.disabled = false; cta.classList.add('cta--done'); morphCtaLabel('Uploaded ✓', null); }, 400);
        setTimeout(() => { prog.classList.add('prog--fade'); }, 600);
        setTimeout(() => {
            panel.classList.add('panel--success');
            prog.hidden = true;
            preview.hidden = true;
            doneState.hidden = false;
            toast(msg, 'success');
            // Auto-refresh library
            loadLibrary();
        }, 800);
        setTimeout(() => { cta.type = 'submit'; morphCtaLabel('New Upload →', null); }, 2200);
    }

    function handleError(responseText) {
        let msg = 'Upload failed — please try again';
        try { msg = JSON.parse(responseText).message || msg; } catch { }
        cta.disabled = false;
        prog.hidden = true;
        morphCtaLabel('Upload', ARROW_SVG);
        toast(msg, 'error');
    }

    /* ========================================
       VIDEO LIBRARY
       ======================================== */

    function loadLibrary() {
        fetch(API_BASE + '/allVideos')
            .then(res => {
                if (!res.ok) throw new Error('Failed to load');
                return res.json();
            })
            .then(videos => renderGrid(videos))
            .catch(() => {
                grid.innerHTML = '';
                emptyState.hidden = false;
            });
    }

    function renderGrid(videos) {
        grid.innerHTML = '';

        if (!videos || videos.length === 0) {
            emptyState.hidden = false;
            return;
        }

        emptyState.hidden = true;

        videos.forEach((v, i) => {
            const card = document.createElement('div');
            card.className = 'card';
            card.style.animationDelay = (i * 0.05) + 's';
            card.innerHTML = `
                <div class="card__thumb">
                    <div class="card__play">
                        <div class="card__play-icon">
                            <svg width="14" height="14" viewBox="0 0 14 14" fill="none"><polygon points="4,2 4,12 12,7" fill="currentColor"/></svg>
                        </div>
                    </div>
                </div>
                <div class="card__body">
                    <p class="card__title">${escHtml(v.title || 'Untitled')}</p>
                    <p class="card__desc">${escHtml(v.description || '')}</p>
                    ${v.contentType ? '<span class="card__type">' + escHtml(v.contentType.split('/')[1] || v.contentType).toUpperCase() + '</span>' : ''}
                </div>
            `;
            card.addEventListener('click', () => openPlayer(v));
            grid.appendChild(card);
        });
    }

    function escHtml(str) {
        const d = document.createElement('div');
        d.textContent = str;
        return d.innerHTML;
    }

    /* ========================================
       INLINE PLAYER
       ======================================== */

    function openPlayer(video) {
        const streamUrl = API_BASE + '/stream/' + video.videoId;
        playerVideo.src = streamUrl;
        if (video.contentType) {
            playerVideo.type = video.contentType;
        }
        playerTitle.textContent = video.title || 'Untitled';
        playerDesc.textContent = video.description || '';
        playerEl.hidden = false;
        playerVideo.play().catch(() => { });

        // Scroll to player
        playerEl.scrollIntoView({ behavior: 'smooth', block: 'start' });
    }

    function closePlayer() {
        playerVideo.pause();
        playerVideo.src = '';
        playerEl.hidden = true;
    }

    playerClose.addEventListener('click', closePlayer);

    refreshBtn.addEventListener('click', () => {
        refreshBtn.classList.add('lib-refresh--spin');
        loadLibrary();
        setTimeout(() => refreshBtn.classList.remove('lib-refresh--spin'), 400);
    });

    /* ========================================
       INIT
       ======================================== */

    loadLibrary();

})();
