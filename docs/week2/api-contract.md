# Week 2 API Contract

All endpoints are prefixed by `/api/v1`. Successful responses use:

```json
{"success":true,"message":"...","data":{},"timestamp":"..."}
```

Errors use:

```json
{"success":false,"code":"INVALID_CREDENTIALS","message":"...","errors":[],"timestamp":"..."}
```

## Public authentication

| Method | Endpoint | Purpose |
|---|---|---|
| POST | `/auth/register` | Create a `ROLE_USER` account |
| POST | `/auth/login` | Issue access and refresh tokens |
| POST | `/auth/refresh` | Rotate the refresh token |

## Authenticated endpoints

| Method | Endpoint | Purpose |
|---|---|---|
| POST | `/auth/logout` | Revoke the supplied device refresh token |
| POST | `/auth/logout-all` | Revoke every active refresh token |
| GET | `/users/me` | Read the authenticated profile |
| PUT | `/users/me` | Update display name, level and daily goal |

Access tokens are sent as `Authorization: Bearer <token>`. Refresh tokens are random opaque values; only their SHA-256 hashes are stored. A rotated or logged-out refresh token cannot be reused.

## Error codes

- `VALIDATION_ERROR`
- `EMAIL_ALREADY_EXISTS`
- `INVALID_CREDENTIALS`
- `ACCOUNT_DISABLED`
- `TOKEN_EXPIRED`
- `INVALID_TOKEN`
- `UNAUTHORIZED`
- `ACCESS_DENIED`
- `USER_NOT_FOUND`
- `INTERNAL_ERROR`
