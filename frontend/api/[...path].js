/**
 * Vercel serverless proxy — forwards all /api/* requests to the Spring Boot backend.
 * Set BACKEND_URL in Vercel project environment variables (e.g. https://your-backend.com).
 */
export default async function handler(req, res) {
  const backendUrl = process.env.BACKEND_URL;
  if (!backendUrl) {
    return res.status(500).json({ error: 'BACKEND_URL environment variable is not set.' });
  }

  const segments = Array.isArray(req.query.path) ? req.query.path.join('/') : (req.query.path ?? '');
  const search = new URL(req.url, 'http://localhost').search;
  const targetUrl = `${backendUrl}/api/${segments}${search}`;

  const isSSE = req.headers['accept'] === 'text/event-stream';

  const fetchOptions = {
    method: req.method,
    headers: {
      'content-type': req.headers['content-type'] ?? 'application/json',
      'accept':        req.headers['accept']         ?? '*/*',
    },
  };

  if (!['GET', 'HEAD'].includes(req.method) && req.body) {
    fetchOptions.body = JSON.stringify(req.body);
  }

  const upstream = await fetch(targetUrl, fetchOptions);

  if (isSSE) {
    res.setHeader('Content-Type', 'text/event-stream');
    res.setHeader('Cache-Control', 'no-cache');
    res.setHeader('Connection', 'keep-alive');
    res.setHeader('X-Accel-Buffering', 'no');
    res.status(upstream.status);

    const reader = upstream.body.getReader();
    const decoder = new TextDecoder();
    while (true) {
      const { done, value } = await reader.read();
      if (done) break;
      res.write(decoder.decode(value, { stream: true }));
    }
    res.end();
    return;
  }

  // Copy safe response headers
  for (const [key, value] of upstream.headers.entries()) {
    if (!['transfer-encoding', 'connection', 'keep-alive'].includes(key.toLowerCase())) {
      res.setHeader(key, value);
    }
  }
  res.status(upstream.status).send(await upstream.text());
}
