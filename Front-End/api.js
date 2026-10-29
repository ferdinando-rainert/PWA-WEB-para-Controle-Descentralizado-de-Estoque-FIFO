(function () {
    const isLocal = ['localhost', '127.0.0.1', '[::1]'].includes(window.location.hostname);
    const defaultBaseUrl = isLocal
        ? 'http://127.0.0.1:8080/api/v1'
        : '/api/v1';
    let csrfToken = '';

    function getBaseUrl() {
        if (!isLocal) return defaultBaseUrl;
        return (localStorage.getItem('fifo_api_base_url') || defaultBaseUrl).replace(/\/+$/, '');
    }

    async function request(path, options = {}) {
        const method = (options.method || 'GET').toUpperCase();
        const headers = new Headers(options.headers || {});
        headers.set('Accept', 'application/json');

        if (options.body !== undefined) {
            headers.set('Content-Type', 'application/json');
        }
        if (!['GET', 'HEAD', 'OPTIONS'].includes(method) && path !== '/auth/login' && csrfToken) {
            headers.set('X-CSRF-Token', csrfToken);
        }

        let response;
        try {
            response = await fetch(`${getBaseUrl()}${path}`, {
                ...options,
                method,
                headers,
                credentials: 'include',
                cache: 'no-store',
                body: options.body === undefined ? undefined : JSON.stringify(options.body)
            });
        } catch (error) {
            error.isNetworkError = true;
            throw error;
        }

        const contentType = response.headers.get('content-type') || '';
        const payload = contentType.includes('application/json') || contentType.includes('+json')
            ? await response.json()
            : await response.text();

        if (!response.ok) {
            if (response.status === 401 && path !== '/auth/login') {
                csrfToken = '';
                window.dispatchEvent(new CustomEvent('inventory-auth-required'));
            }
            const message = typeof payload === 'string'
                ? payload
                : payload.message || payload.mensagem || payload.error || `Erro HTTP ${response.status}`;
            const error = new Error(message);
            error.status = response.status;
            throw error;
        }

        if (path === '/auth/login' || path === '/auth/session') {
            csrfToken = payload.csrfToken;
        } else if (path === '/auth/logout') {
            csrfToken = '';
        }
        return payload;
    }

    window.InventoryApi = {
        get baseUrl() {
            return getBaseUrl();
        },
        request,
        configure(baseUrl) {
            const url = new URL(baseUrl, window.location.origin);
            if (!['http:', 'https:'].includes(url.protocol)) {
                throw new Error('A URL da API deve usar HTTP ou HTTPS.');
            }
            if (url.username || url.password || url.search || url.hash) {
                throw new Error('Informe apenas a URL base da API, sem credenciais, parâmetros ou fragmento.');
            }
            if (!isLocal && (url.origin !== window.location.origin || url.pathname.replace(/\/+$/, '') !== '/api/v1')) {
                throw new Error('Em produção, a API deve usar o proxy seguro da Vercel (/api/v1).');
            }
            if (isLocal && url.protocol === 'http:' &&
                !['localhost', '127.0.0.1', '[::1]'].includes(url.hostname)) {
                throw new Error('Use HTTPS para APIs fora do próprio computador.');
            }
            if (isLocal) {
                localStorage.setItem('fifo_api_base_url', url.toString().replace(/\/+$/, ''));
            }
        }
    };
})();
