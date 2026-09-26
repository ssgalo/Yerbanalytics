## Context

During the rapid iteration phase, the frontend relied heavily on mock data generation (in `mock/sectorDetail.ts` and `mock/generators.ts`) to validate the dashboard and sector detail UI. The backend was updated to persist historical actions and integrate with external APIs (like Open-Meteo), but the frontend components (`SectorPage`, `SectorHistory`, `RecentDiagnostics`, `ActivityFeed`) were not yet wired to use these real API endpoints.

This change connects these existing frontend components to the real backend data, removing the legacy mock usage.

## Goals / Non-Goals

**Goals:**
- Transition the `RecentDiagnostics` component to use real diagnostic image URLs instead of fallback CSS gradients when available.
- Transition `SectorHistory` and `SectorPage` to fetch real system actions executed by the rules engine instead of displaying static mock entries.
- Ensure the Open-Meteo API is properly queried in `OpenMeteoWeatherClient` rather than returning a mocked JSON string.
- Provide graceful empty states for components when no data is available (e.g., empty history logs).

**Non-Goals:**
- Overhaul the styling or structure of the dashboard components beyond adding empty states.
- Change how the backend persists the diagnostic or historical data; this change is purely about consuming the data that is already correctly persisted.

## Decisions

1. **Leveraging `useHistory` hook in `SectorPage`:**
   Instead of injecting history data deeply via `useSectorDetail`, we will call the global `useHistory` hook at the `SectorPage` level and filter the returned `ActionRecord` list by `sectorId`. This avoids polluting the domain logic of `SectorDetail` with asynchronous API calls that aren't purely derived from real-time nursery state.

2. **Empty states for historical components:**
   We decided to add simple textual empty states (`No hay acciones registradas...`) when arrays are empty, ensuring the UI remains clean instead of displaying empty cards or throwing errors.

3. **Fallback handling for images:**
   `RecentDiagnostics` will conditionally render the real image via `background-image: url(...)` if `d.imagenUrl` is present. If it's null (e.g. diagnostic generated from telemetry, without a photo), it will gracefully fall back to the existing CSS gradient logic (`d.thumb`) and render a generic SVG icon over it.

## Risks / Trade-offs

- **Risk**: Calling the Open-Meteo API for every weather update might result in rate limiting if not cached. 
  - **Mitigation**: The `OpenMeteoWeatherClient` uses a `@Cacheable("weatherForecast")` annotation and is refreshed via `@Scheduled(fixedRateString = "${yerbanalytics.weather.refresh-ms:900000}")` (every 15 minutes), safely staying within the free tier limits.
- **Risk**: History fetching per sector might be slow if the `useHistory` API response grows too large over time.
  - **Mitigation**: Long-term pagination should be considered. For now, the global history array is lightweight enough to be filtered on the client side, and we only display the last 5 records (`.slice(0, 5)`).
