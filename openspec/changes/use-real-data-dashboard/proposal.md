## Why

During initial development, the frontend and some backend services relied on hardcoded mock data to validate the UI design and component structure (e.g., historical actions, weather forecasts, and diagnostic images). Now that the backend engine, persistence layer, and real API integrations are fully functional, we need to wire the frontend to consume this real data. This solves the problem of the UI showing static, misleading information and ensures the system reflects actual operations and real-time conditions.

## What Changes

- Implement real Open-Meteo API integration in `OpenMeteoWeatherClient.java` to fetch actual weather forecasts instead of returning mock data.
- Update `SectorPage.tsx` and `SectorHistory.tsx` to retrieve and display the real history of autonomous actions for a given sector using the `useHistory` hook, removing static mocks.
- Update `RecentDiagnostics.tsx` to display real capture images (`imagenUrl`) instead of falling back to CSS gradients.
- Add empty states to `SectorHistory` and `ActivityFeed` to gracefully handle cases where no real actions have been executed yet.

## Capabilities

### New Capabilities

### Modified Capabilities
- `dashboard`: The dashboard now consumes real recent diagnostics images and real activity feed data, with appropriate empty states.
- `sector-detail`: The sector detail view now fetches and displays real historical actions specific to the sector, rather than mock entries.
- `pronostico-climatico`: The weather engine now fetches real weather forecasts from the Open-Meteo API instead of generating mock weather data.

## Impact

- **Backend**: `OpenMeteoWeatherClient` no longer returns mock data but performs an HTTP call to the Open-Meteo API. `HistorialService` and `NurseryService` ensure real historical data is mapped properly to the frontend DTOs.
- **Frontend**: The `SectorHistory`, `RecentDiagnostics`, and `ActivityFeed` components are updated to process and render real data or graceful empty states. Mock definitions in `sectorDetail.ts` are removed.
