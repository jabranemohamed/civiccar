import { HttpInterceptorFn, HttpResponse } from '@angular/common/http';
import { tap } from 'rxjs';
import { WebTracerProvider, BatchSpanProcessor } from '@opentelemetry/sdk-trace-web';
import { OTLPTraceExporter } from '@opentelemetry/exporter-trace-otlp-http';
import { resourceFromAttributes } from '@opentelemetry/resources';
import { context, propagation, trace, SpanStatusCode } from '@opentelemetry/api';
import { W3CTraceContextPropagator } from '@opentelemetry/core';

/**
 * Instrumentation navigateur OpenTelemetry, limitée et explicite :
 * - un seul provider, un seul point de capture HTTP (cet intercepteur Angular —
 *   pas d'instrumentation fetch/XHR automatique en plus, donc pas de double capture) ;
 * - propagation traceparent UNIQUEMENT vers les API internes même origine (/api),
 *   jamais vers tuiles/géocodeur/médias tiers ; aucun baggage utilisateur ;
 * - noms de spans = modèles de routes (jamais de référence, jeton ou paramètre) ;
 * - export en batch vers le proxy même origine /api/telemetry/traces (borné côté
 *   serveur) ; toute panne de télémétrie est silencieuse pour le métier ;
 * - compatible zoneless : pas de gestionnaire de contexte Zone.js.
 */

let enabled = false;

export function initTelemetry(): void {
  try {
    const provider = new WebTracerProvider({
      resource: resourceFromAttributes({
        'service.name': 'civiccare-tunis-web',
        'service.version': '1.0.0',
      }),
      spanProcessors: [
        new BatchSpanProcessor(
          new OTLPTraceExporter({ url: '/api/telemetry/traces' }),
          { maxExportBatchSize: 32, scheduledDelayMillis: 5000 },
        ),
      ],
    });
    provider.register({ propagator: new W3CTraceContextPropagator() });
    enabled = true;
  } catch {
    enabled = false; // la télémétrie ne bloque jamais le démarrage
  }
}

const tracer = () => trace.getTracer('civiccare-tunis-web');

/** Modèle d'URL sans identifiants/jetons (références, UUID, tokens → placeholders). */
function routeTemplate(url: string): string {
  return url.split('?')[0]
    .replace(/\d{6}-\d{4}/g, '{reference}')
    .replace(/[0-9a-f]{8}-[0-9a-f-]{27,}/gi, '{id}')
    .replace(/(confirm|unsubscribe)\/[A-Za-z0-9_-]{20,}/g, '$1/{token}');
}

/** Intercepteur HTTP : span client + traceparent vers /api uniquement. */
export const telemetryInterceptor: HttpInterceptorFn = (req, next) => {
  const isInternalApi = req.url.startsWith('/api/');
  if (!enabled || !isInternalApi || req.url.startsWith('/api/telemetry')) {
    return next(req);
  }
  const span = tracer().startSpan(`HTTP ${req.method} ${routeTemplate(req.url)}`, {
    attributes: { 'http.request.method': req.method, 'url.template': routeTemplate(req.url) },
  });
  const headers: Record<string, string> = {};
  propagation.inject(trace.setSpan(context.active(), span), headers);
  let clone = req;
  if (headers['traceparent']) {
    clone = req.clone({ setHeaders: headers });
  }
  return next(clone).pipe(tap({
    next: (event) => {
      if (event instanceof HttpResponse) {
        span.setAttribute('http.response.status_code', event.status);
      }
    },
    error: (err: { status?: number }) => {
      span.setAttribute('http.response.status_code', err?.status ?? 0);
      span.setStatus({ code: SpanStatusCode.ERROR });
      span.end();
    },
    complete: () => span.end(),
  }));
};

/** Span d'action métier (dépôt, recherche…) sans donnée personnelle. */
export function businessSpan<T>(name: string, run: () => Promise<T>): Promise<T> {
  if (!enabled) {
    return run();
  }
  const span = tracer().startSpan(name);
  return context.with(trace.setSpan(context.active(), span), run)
    .then((value) => {
      span.end();
      return value;
    })
    .catch((err) => {
      span.setStatus({ code: SpanStatusCode.ERROR });
      span.end();
      throw err;
    });
}
