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
                            <svg width="16" height="16" viewBox="0 0 14 14" fill="none"><polygon points="4,2 4,12 12,7" fill="currentColor"/></svg>
                        </div>
                    </div>
                </div>
                <button class="card__delete" aria-label="Delete video" title="Delete">
                    <svg width="14" height="14" viewBox="0 0 16 16" fill="none"><path d="M3 4h10M6 4V3a1 1 0 0 1 1-1h2a1 1 0 0 1 1 1v1M5 4v9a1 1 0 0 0 1 1h4a1 1 0 0 0 1-1V4" stroke="currentColor" stroke-width="1.3" stroke-linecap="round" stroke-linejoin="round"/></svg>
                </button>
                <div class="card__body">
                    <p class="card__title">${escHtml(v.title || 'Untitled')}</p>
                    <p class="card__desc">${escHtml(v.description || '')}</p>
                    <div class="card__footer">
                        ${v.contentType ? '<span class="card__type">' + escHtml(v.contentType.split('/')[1] || v.contentType).toUpperCase() + '</span>' : ''}
                    </div>
                </div>
            `;
            card.addEventListener('click', (e) => {
                if (e.target.closest('.card__delete')) return;
                openPlayer(v);
            });
            card.querySelector('.card__delete').addEventListener('click', (e) => {
                e.stopPropagation();
                showDeleteModal(v.videoId, v.title || 'Untitled');
            });
            grid.appendChild(card);
        });
    }

    function escHtml(str) {
        const d = document.createElement('div');
        d.textContent = str;
        return d.innerHTML;
    }

    let currentVideoId = null;

    /* ========================================
       DELETE MODAL
       ======================================== */

    const deleteModal = document.getElementById('delete-modal');
    const deleteModalName = document.getElementById('delete-modal-name');
    const deleteCancelBtn = document.getElementById('delete-cancel');
    const deleteConfirmBtn = document.getElementById('delete-confirm');
    let pendingDeleteId = null;

    function showDeleteModal(videoId, title) {
        pendingDeleteId = videoId;
        deleteModalName.textContent = '"' + title + '"';
        deleteModal.hidden = false;
    }

    function hideDeleteModal() {
        deleteModal.hidden = true;
        pendingDeleteId = null;
    }

    deleteCancelBtn.addEventListener('click', hideDeleteModal);

    deleteModal.addEventListener('click', (e) => {
        if (e.target === deleteModal) hideDeleteModal();
    });

    deleteConfirmBtn.addEventListener('click', () => {
        if (!pendingDeleteId) return;
        const videoId = pendingDeleteId;
        hideDeleteModal();

        fetch(API_BASE + '/' + videoId, { method: 'DELETE' })
            .then(res => {
                if (!res.ok) throw new Error('Delete failed');
                return res.json();
            })
            .then(data => {
                toast(data.message || 'Video deleted', 'success');
                if (currentVideoId === videoId) closePlayer();
                loadLibrary();
            })
            .catch(() => toast('Failed to delete video', 'error'));
    });

    /* ========================================
       CINEMATIC CUSTOM PLAYER
       ======================================== */

    const playerWrap = document.getElementById('player-wrap');
    const vid = document.getElementById('player-video');
    const centerPlay = document.getElementById('center-play');
    const centerPlayIcon = document.getElementById('center-play-icon');
    const centerPauseIcon = document.getElementById('center-pause-icon');
    const ctrlPlay = document.getElementById('ctrl-play');
    const ctrlPlayIcon = document.getElementById('ctrl-play-icon');
    const ctrlPauseIcon = document.getElementById('ctrl-pause-icon');
    const progressBar = document.getElementById('progress-bar');
    const progressFilled = document.getElementById('progress-filled');
    const progressBuffered = document.getElementById('progress-buffered');
    const progressScrubber = document.getElementById('progress-scrubber');
    const ctrlTime = document.getElementById('ctrl-time');
    const ctrlMute = document.getElementById('ctrl-mute');
    const volSlider = document.getElementById('vol-slider');
    const volWave = document.getElementById('vol-wave');
    const ctrlSpeed = document.getElementById('ctrl-speed');
    const ctrlQuality = document.getElementById('ctrl-quality');
    const ctrlFullscreen = document.getElementById('ctrl-fullscreen');

    let activeHls = null;
    let animFrame = null;

    // --- Format time ---
    function fmtTime(s) {
        if (!s || isNaN(s)) return '0:00';
        const m = Math.floor(s / 60);
        const sec = Math.floor(s % 60);
        return m + ':' + (sec < 10 ? '0' : '') + sec;
    }

    // --- Sync play/pause icons ---
    function syncPlayState() {
        const playing = !vid.paused;
        centerPlayIcon.hidden = playing;
        centerPauseIcon.hidden = !playing;
        ctrlPlayIcon.hidden = playing;
        ctrlPauseIcon.hidden = !playing;
        playerEl.classList.toggle('player--playing', playing);
        playerEl.classList.toggle('player--paused', !playing);
    }

    // --- Progress update loop ---
    function updateProgress() {
        if (vid.duration) {
            const pct = (vid.currentTime / vid.duration) * 100;
            progressFilled.style.width = pct + '%';
            progressScrubber.style.left = pct + '%';
            ctrlTime.textContent = fmtTime(vid.currentTime) + ' / ' + fmtTime(vid.duration);

            // Buffered
            if (vid.buffered.length > 0) {
                const buf = (vid.buffered.end(vid.buffered.length - 1) / vid.duration) * 100;
                progressBuffered.style.width = buf + '%';
            }
        }
        animFrame = requestAnimationFrame(updateProgress);
    }

    // --- Toggle play/pause ---
    function togglePlay() {
        if (vid.paused) {
            vid.play().catch(() => { });
        } else {
            vid.pause();
        }
    }

    centerPlay.addEventListener('click', (e) => { e.stopPropagation(); togglePlay(); });
    ctrlPlay.addEventListener('click', (e) => { e.stopPropagation(); togglePlay(); });
    playerWrap.addEventListener('click', (e) => {
        if (e.target.closest('.player__controls') || e.target.closest('.player__center-play')) return;
        togglePlay();
    });

    vid.addEventListener('play', syncPlayState);
    vid.addEventListener('pause', syncPlayState);

    // --- Progress bar scrubbing ---
    let isScrubbing = false;

    function scrub(e) {
        const rect = progressBar.getBoundingClientRect();
        const pct = Math.max(0, Math.min(1, (e.clientX - rect.left) / rect.width));
        if (vid.duration) {
            vid.currentTime = pct * vid.duration;
            progressFilled.style.width = (pct * 100) + '%';
            progressScrubber.style.left = (pct * 100) + '%';
        }
    }

    progressBar.addEventListener('mousedown', (e) => {
        isScrubbing = true;
        scrub(e);
    });

    document.addEventListener('mousemove', (e) => {
        if (isScrubbing) scrub(e);
    });

    document.addEventListener('mouseup', () => {
        isScrubbing = false;
    });

    // --- Volume ---
    volSlider.addEventListener('input', () => {
        vid.volume = parseFloat(volSlider.value);
        vid.muted = vid.volume === 0;
        volWave.style.opacity = vid.volume > 0 ? '1' : '0.2';
    });

    ctrlMute.addEventListener('click', (e) => {
        e.stopPropagation();
        vid.muted = !vid.muted;
        volSlider.value = vid.muted ? 0 : vid.volume || 1;
        volWave.style.opacity = vid.muted ? '0.2' : '1';
    });

    // --- Quality ---
    ctrlQuality.addEventListener('change', () => {
        if (activeHls) {
            activeHls.currentLevel = parseInt(ctrlQuality.value);
        }
    });

    function populateQualityLevels(hls) {
        ctrlQuality.innerHTML = '<option value="-1">Auto</option>';
        hls.levels.forEach((level, i) => {
            const h = level.height;
            const label = h + 'p';
            const opt = document.createElement('option');
            opt.value = i;
            opt.textContent = label;
            ctrlQuality.appendChild(opt);
        });
        ctrlQuality.value = '-1';
        ctrlQuality.hidden = false;
    }

    function resetQuality() {
        ctrlQuality.innerHTML = '<option value="-1" selected>Auto</option>';
        ctrlQuality.hidden = true;
    }

    // --- Speed ---
    ctrlSpeed.addEventListener('change', () => {
        vid.playbackRate = parseFloat(ctrlSpeed.value);
    });

    // --- Fullscreen ---
    ctrlFullscreen.addEventListener('click', (e) => {
        e.stopPropagation();
        if (document.fullscreenElement) {
            document.exitFullscreen();
        } else {
            playerWrap.requestFullscreen().catch(() => { });
        }
    });

    // --- Keyboard controls ---
    document.addEventListener('keydown', (e) => {
        if (playerEl.hidden) return;
        if (e.target.tagName === 'INPUT' || e.target.tagName === 'TEXTAREA' || e.target.tagName === 'SELECT') return;
        switch (e.key) {
            case ' ':
                e.preventDefault();
                togglePlay();
                break;
            case 'ArrowLeft':
                vid.currentTime = Math.max(0, vid.currentTime - 5);
                break;
            case 'ArrowRight':
                vid.currentTime = Math.min(vid.duration || 0, vid.currentTime + 5);
                break;
            case 'm':
            case 'M':
                vid.muted = !vid.muted;
                volSlider.value = vid.muted ? 0 : vid.volume;
                volWave.style.opacity = vid.muted ? '0.2' : '1';
                break;
            case 'f':
            case 'F':
                ctrlFullscreen.click();
                break;
        }
    });

    // --- Open player ---
    function openPlayer(video) {
        // Clean up previous HLS
        if (activeHls) { activeHls.destroy(); activeHls = null; }
        if (animFrame) { cancelAnimationFrame(animFrame); }

        currentVideoId = video.videoId;

        const hlsUrl = API_BASE + '/hls/' + video.videoId + '/master.m3u8';
        const rangeUrl = API_BASE + '/stream/range/' + video.videoId;

        playerTitle.textContent = video.title || 'Untitled';
        playerDesc.textContent = video.description || '';

        // Reset state
        progressFilled.style.width = '0%';
        progressBuffered.style.width = '0%';
        progressScrubber.style.left = '0%';
        ctrlTime.textContent = '0:00 / 0:00';
        ctrlSpeed.value = '1';
        vid.playbackRate = 1;

        function playWithFallback() {
            playerEl.hidden = false;
            playerEl.classList.add('player--paused');
            playerEl.classList.remove('player--playing');
            syncPlayState();
            updateProgress();
            vid.play().catch(() => { });
            playerEl.scrollIntoView({ behavior: 'smooth', block: 'start' });
        }

        // Try HLS first
        if (typeof Hls !== 'undefined' && Hls.isSupported()) {
            const hls = new Hls({ startLevel: -1, capLevelToPlayerSize: true });
            hls.loadSource(hlsUrl);
            hls.attachMedia(vid);
            hls.on(Hls.Events.MANIFEST_PARSED, () => {
                populateQualityLevels(hls);
                playWithFallback();
            });
            hls.on(Hls.Events.ERROR, (_, data) => {
                if (data.fatal) {
                    hls.destroy();
                    activeHls = null;
                    resetQuality();
                    // Fall back to range streaming
                    vid.src = rangeUrl;
                    vid.addEventListener('loadedmetadata', playWithFallback, { once: true });
                }
            });
            activeHls = hls;
        } else if (vid.canPlayType('application/vnd.apple.mpegurl')) {
            resetQuality();
            vid.src = hlsUrl;
            vid.addEventListener('loadedmetadata', playWithFallback, { once: true });
            vid.addEventListener('error', () => {
                vid.src = rangeUrl;
                vid.addEventListener('loadedmetadata', playWithFallback, { once: true });
            }, { once: true });
        } else {
            resetQuality();
            vid.src = rangeUrl;
            vid.addEventListener('loadedmetadata', playWithFallback, { once: true });
        }
    }

    function closePlayer() {
        if (activeHls) { activeHls.destroy(); activeHls = null; }
        if (animFrame) { cancelAnimationFrame(animFrame); animFrame = null; }
        vid.pause();
        vid.removeAttribute('src');
        vid.load();
        playerEl.hidden = true;
        playerEl.classList.remove('player--playing', 'player--paused');
        currentVideoId = null;
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
