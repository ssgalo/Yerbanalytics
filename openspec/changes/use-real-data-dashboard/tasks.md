## 1. Backend Data Sources

- [x] 1.1 Update `OpenMeteoWeatherClient.java` to fetch data from the real Open-Meteo API instead of returning mocked instances.
- [x] 1.2 Update `WeatherForecast.java` to correctly model the Open-Meteo response DTO.

## 2. Frontend Real Data Binding

- [x] 2.1 Update `SectorPage.tsx` to use the `useHistory` hook to fetch and filter the real historical records.
- [x] 2.2 Update `SectorHistory.tsx` to handle empty states and limit the displayed records to the last 5 actions.
- [x] 2.3 Modify `RecentDiagnostics.tsx` to use the `d.imagenUrl` background instead of CSS generic gradients, and hide the fallback SVG if an image exists.
- [x] 2.4 Add empty state text to `ActivityFeed.tsx` for cases when there are no logged autonomous actions.
- [x] 2.5 Remove mock historical array entries from `mock/sectorDetail.ts`.
