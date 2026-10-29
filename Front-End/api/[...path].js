const MAX_BODY_BYTES = 1024 * 1024;
const ALLOWED_METHODS = new Set(['GET', 'POST', 'PUT', 'OPTIONS']);

async function readRequestBody(req) {
    if (req.body !== undefined && req.body !== null) {
        if (Buffer.isBuffer(req.body)) return req.body;
        if (typeof req.body === 'string') return Buffer.from(req.body);
        return Buffer.from(JSON.stringify(req.body));
    }

    const chunks = [];
    let size = 0;
    for await (const chunk of req) {
        size += chunk.length;
        if (size > MAX_BODY_BYTES) {
            const error = new Error('Request body is too large.');
            error.statusCode = 413;
            throw error;
        }
        chunks.push(chunk);
    }
    return Buffer.concat(chunks);
}

function getUpstreamOrigin() {
    const value = process.env.FIFO_API_ORIGIN;
    if (!value) throw new Error('FIFO_API_ORIGIN is not configured.');

    const origin = new URL(value);
    if (origin.protocol !== 'https:' || origin.username || origin.password ||
        origin.search || origin.hash || origin.pathname !== '/') {
        throw new Error('FIFO_API_ORIGIN must be an HTTPS origin without a path or credentials.');
    }
    return origin;
}

module.exports = async function handler(req, res) {
    res.setHeader('Cache-Control', 'no-store');
    res.setHeader('X-Content-Type-Options', 'nosniff');

    if (!ALLOWED_METHODS.has(req.method)) {
        res.setHeader('Allow', [...ALLOWED_METHODS].join(', '));
        return res.status(405).json({ message: 'Method not allowed.' });
    }

    const proxySecret = process.env.FIFO_API_PROXY_SECRET;
    if (!proxySecret || proxySecret.length < 32) {
        return res.status(503).json({ message: 'Secure API proxy is not configured.' });
    }

    try {
        const upstreamOrigin = getUpstreamOrigin();
        const requestUrl = new URL(req.url, 'https://vercel.invalid');
        if (!requestUrl.pathname.startsWith('/api/v1/')) {
            return res.status(404).json({ message: 'Route not found.' });
        }

        const headers = new Headers();
        for (const name of ['accept', 'content-type', 'cookie', 'origin', 'referer', 'x-csrf-token']) {
            const value = req.headers[name];
            if (typeof value === 'string') headers.set(name, value);
        }
        headers.set('x-fifo-proxy-secret', proxySecret);
        headers.set('x-forwarded-for', req.headers['x-forwarded-for'] || req.socket.remoteAddress || '');

        const hasBody = !['GET', 'HEAD', 'OPTIONS'].includes(req.method);
        const body = hasBody ? await readRequestBody(req) : undefined;
        if (body && body.length > MAX_BODY_BYTES) {
            return res.status(413).json({ message: 'Request body is too large.' });
        }

        const upstream = await fetch(new URL(requestUrl.pathname + requestUrl.search, upstreamOrigin), {
            method: req.method,
            headers,
            body,
            signal: AbortSignal.timeout(15000),
            redirect: 'manual'
        });

        res.statusCode = upstream.status;
        for (const name of ['content-type', 'set-cookie']) {
            const value = name === 'set-cookie' && typeof upstream.headers.getSetCookie === 'function'
                ? upstream.headers.getSetCookie()
                : upstream.headers.get(name);
            if (value && value.length) res.setHeader(name, value);
        }
        const responseBody = Buffer.from(await upstream.arrayBuffer());
        res.setHeader('Content-Length', responseBody.length);
        return res.end(responseBody);
    } catch (error) {
        const status = error.statusCode || (error.name === 'TimeoutError' ? 504 : 502);
        console.error('API proxy request failed:', error.message);
        return res.status(status).json({
            message: status === 504 ? 'The API timed out.' : 'The API is temporarily unavailable.'
        });
    }
};
