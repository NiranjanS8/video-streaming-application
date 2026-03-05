(() => {
    'use strict';

    const AUTH_TOKEN_KEY = 'authToken';
    const AUTH_USER_KEY = 'authUsername';
    const AUTH_USER_ID_KEY = 'authUserId';

    const tabLogin = document.getElementById('tab-login');
    const tabRegister = document.getElementById('tab-register');
    const loginForm = document.getElementById('login-form');
    const registerForm = document.getElementById('register-form');
    const msg = document.getElementById('auth-msg');
    const goRegister = document.getElementById('go-register');
    const goLogin = document.getElementById('go-login');
    const loginUsername = document.getElementById('login-username');
    const loginPassword = document.getElementById('login-password');
    const registerUsername = document.getElementById('register-username');
    const registerPassword = document.getElementById('register-password');
    const registerConfirmPassword = document.getElementById('register-confirm-password');
    const pwToggles = document.querySelectorAll('.auth__pw-toggle');
    const tabs = [tabLogin, tabRegister];

    function setMsg(text, isError = false) {
        msg.textContent = text || '';
        msg.classList.remove('auth__msg--error', 'auth__msg--ok');
        if (!text) return;
        msg.classList.add(isError ? 'auth__msg--error' : 'auth__msg--ok');
    }

    function switchTab(type) {
        const loginActive = type === 'login';
        tabLogin.classList.toggle('auth__tab--active', loginActive);
        tabRegister.classList.toggle('auth__tab--active', !loginActive);
        tabLogin.setAttribute('aria-selected', String(loginActive));
        tabRegister.setAttribute('aria-selected', String(!loginActive));
        loginForm.hidden = !loginActive;
        registerForm.hidden = loginActive;
        setMsg('');

        if (loginActive) loginUsername.focus();
        else registerUsername.focus();
    }

    function setSubmitting(form, loadingText, isLoading) {
        const btn = form.querySelector('.auth__btn');
        if (!btn) return;
        const defaultText = btn.getAttribute('data-default-text') || btn.textContent;
        btn.disabled = isLoading;
        btn.textContent = isLoading ? loadingText : defaultText;

        tabs.forEach(t => t.disabled = isLoading);
        pwToggles.forEach(t => t.disabled = isLoading);
        if (goRegister) goRegister.disabled = isLoading;
        if (goLogin) goLogin.disabled = isLoading;
    }

    function readErrorMessage(data, fallback) {
        if (!data) return fallback;
        if (typeof data.message === 'string' && data.message.trim()) return data.message;
        if (typeof data.error === 'string' && data.error.trim()) return data.error;
        return fallback;
    }

    tabLogin.addEventListener('click', () => switchTab('login'));
    tabRegister.addEventListener('click', () => switchTab('register'));
    if (goRegister) goRegister.addEventListener('click', () => switchTab('register'));
    if (goLogin) goLogin.addEventListener('click', () => switchTab('login'));

    pwToggles.forEach(btn => {
        btn.addEventListener('click', () => {
            const targetId = btn.getAttribute('data-target');
            const input = document.getElementById(targetId);
            if (!input) return;
            const reveal = input.type === 'password';
            input.type = reveal ? 'text' : 'password';
            btn.classList.toggle('is-revealed', reveal);
            btn.setAttribute('aria-label', reveal ? 'Hide password' : 'Show password');
        });
    });

    loginForm.addEventListener('submit', async (e) => {
        e.preventDefault();
        setMsg('');

        const username = loginUsername.value.trim();
        const password = loginPassword.value;

        if (username.length < 3) {
            setMsg('Username must be at least 3 characters', true);
            loginUsername.focus();
            return;
        }

        if (password.length < 6) {
            setMsg('Password must be at least 6 characters', true);
            loginPassword.focus();
            return;
        }

        try {
            setSubmitting(loginForm, 'Signing in...', true);
            const res = await fetch('/auth/login', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ username, password })
            });

            if (!res.ok) {
                const data = await res.json().catch(() => ({}));
                setMsg(readErrorMessage(data, 'Invalid username or password'), true);
                return;
            }

            const data = await res.json();
            localStorage.setItem(AUTH_TOKEN_KEY, data.token);
            localStorage.setItem(AUTH_USER_KEY, data.username || username);
            localStorage.setItem(AUTH_USER_ID_KEY, String(data.userId || ''));
            window.location.href = '/';
        } catch {
            setMsg('Login request failed. Check your connection and try again.', true);
        } finally {
            setSubmitting(loginForm, 'Signing in...', false);
        }
    });

    registerForm.addEventListener('submit', async (e) => {
        e.preventDefault();
        setMsg('');

        const username = registerUsername.value.trim();
        const password = registerPassword.value;
        const confirmPassword = registerConfirmPassword.value;

        if (username.length < 3) {
            setMsg('Username must be at least 3 characters', true);
            registerUsername.focus();
            return;
        }

        if (password.length < 6) {
            setMsg('Password must be at least 6 characters', true);
            registerPassword.focus();
            return;
        }

        if (password !== confirmPassword) {
            setMsg('Passwords do not match', true);
            registerConfirmPassword.focus();
            return;
        }

        try {
            setSubmitting(registerForm, 'Creating account...', true);
            const res = await fetch('/auth/register', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ username, password })
            });

            const data = await res.json().catch(() => ({}));
            if (!res.ok) {
                setMsg(readErrorMessage(data, 'Registration failed'), true);
                return;
            }

            switchTab('login');
            loginUsername.value = username;
            setMsg('Registered successfully. Now login.');
            loginPassword.focus();
        } catch {
            setMsg('Registration request failed. Check your connection and try again.', true);
        } finally {
            setSubmitting(registerForm, 'Creating account...', false);
        }
    });

    if (localStorage.getItem(AUTH_TOKEN_KEY)) {
        window.location.href = '/';
    }

    switchTab('login');
})();
