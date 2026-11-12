import {
  AngularNodeAppEngine,
  createNodeRequestHandler,
  isMainModule,
  writeResponseToNodeResponse,
} from '@angular/ssr/node';
import express from 'express';
import { join } from 'node:path';

const browserDistFolder = join(import.meta.dirname, '../browser');

const app = express();
const angularApp = new AngularNodeAppEngine();

const backendUrl = (process.env['BACKEND_URL'] || 'http://localhost:8081').replace(/\/+$/, '');

/**
 * Proxy /api/* requests to the Spring Boot backend so the browser never needs
 * a hardcoded backend URL or cross-origin requests in production.
 */
app.use('/api', express.raw({ type: '*/*' }), async (req, res) => {
  try {
    const targetUrl = `${backendUrl}${req.originalUrl}`;
    const headers: Record<string, string> = {};
    if (req.headers['content-type']) {
      headers['content-type'] = req.headers['content-type'];
    }
    if (req.headers['accept']) {
      headers['accept'] = req.headers['accept'];
    }

    const hasBody = !['GET', 'HEAD'].includes(req.method.toUpperCase());
    const upstream = await fetch(targetUrl, {
      method: req.method,
      headers,
      body: hasBody && Buffer.isBuffer(req.body) && req.body.length > 0
        ? new Uint8Array(req.body)
        : undefined,
    });

    const contentType = upstream.headers.get('content-type');
    if (contentType) {
      res.setHeader('content-type', contentType);
    }
    const body = Buffer.from(await upstream.arrayBuffer());
    res.status(upstream.status).send(body);
  } catch (err) {
    console.error('API proxy error:', err);
    res.status(502).json({ error: 'Upstream API unavailable' });
  }
});

/**
 * Serve static files from /browser
 */
app.use(
  express.static(browserDistFolder, {
    maxAge: '1y',
    index: false,
    redirect: false,
  }),
);

/**
 * Handle all other requests by rendering the Angular application.
 */
app.use((req, res, next) => {
  angularApp
    .handle(req)
    .then((response) =>
      response ? writeResponseToNodeResponse(response, res) : next(),
    )
    .catch(next);
});

/**
 * Start the server if this module is the main entry point, or it is ran via PM2.
 * The server listens on the port defined by the `PORT` environment variable, or defaults to 4000.
 */
if (isMainModule(import.meta.url) || process.env['pm_id']) {
  const port = process.env['PORT'] || 4000;
  app.listen(port, (error) => {
    if (error) {
      throw error;
    }

    console.log(`Node Express server listening on http://localhost:${port}`);
  });
}

/**
 * Request handler used by the Angular CLI (for dev-server and during build) or Firebase Cloud Functions.
 */
export const reqHandler = createNodeRequestHandler(app);
