# License Expiry — Email Backend

Minimal Node/Express server that receives expiry alerts from the Android app
and forwards them as email via SMTP (works with Gmail App Passwords, SendGrid,
Mailgun, or any SMTP relay).

## Setup

```bash
cd backend
npm install
cp .env.example .env
# edit .env with your SMTP credentials and a random API_KEY
npm start
```

Server starts on `http://localhost:3000` by default.

## Endpoint

```
POST /notify-expiry
Authorization: Bearer <API_KEY>
Content-Type: application/json

{
  "toEmail": "user@example.com",
  "vehicleNickname": "My Toyota",
  "licenseType": "Motor License",
  "expiryDate": "2026-10-15"
}
```

Returns `200 { "status": "sent" }` on success.

## Testing locally

```bash
curl -X POST http://localhost:3000/notify-expiry \
  -H "Authorization: Bearer change-me-to-a-long-random-string" \
  -H "Content-Type: application/json" \
  -d '{"toEmail":"you@example.com","vehicleNickname":"My Toyota","licenseType":"Motor License","expiryDate":"2026-10-15"}'
```

## Deploying

Any Node host works: Render, Railway, Fly.io, a small VPS, or a serverless
adapter (e.g. wrapping this in a Firebase Cloud Function or AWS Lambda handler
if you'd rather go serverless). Whatever you use:

1. Set the same environment variables from `.env.example`.
2. Get the deployed HTTPS URL.
3. Put that URL in `LicenseExpiryApp.kt`'s `KEY_BACKEND_URL`, and the same
   `API_KEY` value in `KEY_API_KEY` — they must match on both sides.

## Security notes

- Never commit `.env` — it holds SMTP credentials.
- The `API_KEY` bearer check keeps random internet traffic from using your
  server to send email. Rotate it if it ever leaks.
- For production, consider adding rate limiting (e.g. `express-rate-limit`) so
  a bug in the app can't spam a user's inbox or exhaust your SMTP quota.

- For production, consider adding rate limiting (e.g. `express-rate-limit`) so
  a bug in the app can't spam a user's inbox or exhaust your SMTP quota.
