# LingoLens Admin Console

React/TypeScript console for user status/roles, vocabulary publishing, correction review and audit logs.

## Local development

Requires Node.js 24 and the API at `http://localhost:8080`:

```powershell
npm ci
npm run dev
```

Open <http://localhost:5173>. Port 5173 is fixed to match the default API CORS allowlist. For another API, copy `.env.example` to `.env`, set `VITE_API_BASE_URL` and restart Vite. This variable is public browser configuration, not a secret. Add the console origin to the backend's comma-separated `CORS_ALLOWED_ORIGINS`.

Provision the first admin by setting `ADMIN_EMAIL` and `ADMIN_PASSWORD` in the root `.env` before API startup; the password needs at least 12 characters. Bootstrap updates an existing account on every startup while configured; see [root setup](../README.md).

## Build and preview

```powershell
npm ci
npm run build
npm run preview
```

Build runs TypeScript before Vite and outputs `dist/`. Preview uses port 5173. `VITE_API_BASE_URL` is embedded at build time: configure the HTTPS API before deployment builds.

Dependencies are pinned to the lockfile. The console currently stores access tokens in `sessionStorage`, requires `ROLE_ADMIN` and asks for login again on expiry. Refresh and frontend restructuring are future work.
