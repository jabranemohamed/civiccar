# LinkedIn post (English) — ready to publish

> **How to use**: publish the text below as a *document post* by attaching
> `civiccare-tunis-carousel.pdf` (LinkedIn renders it as a swipeable carousel), **or** as a
> *video post* with `civiccare-report-flow.mp4`. Put your demo/GitHub link in the **first
> comment** rather than in the post body (better reach). 3–5 hashtags max.

---

Reporting a broken streetlight in Tunis should take 30 seconds — not a phone call, a form,
and a week of silence.

So I built **CivicCare Tunis**: a full civic-issue reporting platform, end to end. 🏙️

Citizens pin a problem on the map, pick from 40 issue types, attach photos and submit —
**no account needed**. City teams get a real back office: moderation queue, routing to the
right team, role-based permissions and a full audit trail.

A few things I'm proud of:

🌍 **Trilingual by design** — French, Arabic and English, with a complete right-to-left
Arabic interface (and yes, the map stays geographic).

🗺️ **Geography done honestly** — server-side PostGIS validation against the municipal
boundary, duplicate detection within 50 metres, marker clustering on a MapLibre map.

📬 **Reliability where it hurts** — a transactional outbox means reports are never lost
when the mail server goes down; emails simply catch up when it recovers.

🔭 **Full observability** — every business action is traced with OpenTelemetry: traces in
Tempo, metrics in Prometheus, logs correlated by trace ID in Loki, dashboards in Grafana.

🔌 **Open by default** — a read API compatible with the Open311 GeoReport v2 standard.

⚙️ Stack: Java 25 · Spring Boot 4 · Vaadin Flow 25 · PostgreSQL 18 + PostGIS · Docker —
backed by 49 integration tests running against a real PostGIS database.

Swipe through the carousel to see it in action 👇

💡 **The source code is completely free** — feel free to use it, learn from it or build
on it for your own city (link in the first comment).

*Note: this is a demonstration project — not affiliated with the municipality of Tunis.*

What would YOU add to a civic reporting platform? I'd love to hear your thoughts — and
**don't hesitate to reach out**, my inbox is open. 💬

#Java #SpringBoot #Vaadin #CivicTech #SoftwareEngineering

---

## Alternative short version (for a video post)

From map pin to confirmation e-mail in under a minute — this is **CivicCare Tunis**, a
trilingual (FR/AR/EN) civic-issue reporting platform I built end to end with Java 25,
Spring Boot 4, Vaadin 25 and PostGIS.

No citizen accounts. Real moderation and routing for city teams. Duplicate detection,
Open311 API, full OpenTelemetry observability, 49 integration tests.

The source code is completely free — link in the first comment. Questions, feedback or
just curious? Don't hesitate to contact me. 💬

Demo project — not affiliated with the municipality of Tunis.

#Java #SpringBoot #Vaadin #CivicTech

## First-comment template

🔗 Source code (free & open): <your GitHub link>
▶️ Live demo: <your demo link, if hosted>
📩 Questions or ideas? DM me here on LinkedIn — happy to chat!

> Tip: if you publish the code, add a licence file to the repo so "free" is unambiguous —
> MIT or Apache-2.0 are the usual choices for this kind of project.
