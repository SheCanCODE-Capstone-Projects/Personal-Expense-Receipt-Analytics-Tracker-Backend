# Using the API

This guide shows how to call every part of the Expense Tracker API, from the browser and from the command line.

| Environment | Base URL | Interactive docs |
|---|---|---|
| Local | `http://localhost:8080` | http://localhost:8080/swagger-ui.html |
| Staging | `https://<staging domain>` | `https://<staging domain>/swagger-ui.html` |
| Production | `https://<production domain>` | Disabled (Swagger is off in the `prod` profile) |

Conventions:
- **IDs** are UUID strings, e.g. `"3f9c2b1e-8d4a-4c6b-9e0f-1a2b3c4d5e6f"`.
- **Money** is whole RWF as an integer (`30000`, not `"30,000"` or `300.00`). Other currencies are stored separately and never added to RWF totals.
- **Dates** are `YYYY-MM-DD`. **Months** are `YYYY-MM`.
- **Errors** come back as JSON with an HTTP status and a message, e.g. `400` for invalid input, `401` when you are not signed in, `404` for something that isn't yours or doesn't exist.

---

## 1. Authentication

### How sessions work

Tokens are **never returned in a response body**. When you sign in, the server sets two **HttpOnly cookies**, which JavaScript cannot read:

| Cookie | Sent to | Lifetime | Purpose |
|---|---|---|---|
| `access_token` | every `/api/**` call | 15 minutes | Proves who you are |
| `refresh_token` | `/api/auth/**` only | 30 days | Gets a new pair when the access token expires |

Both are signed JWTs. The server also keeps a hash of each token's ID, so logging out takes effect immediately, and each refresh token works only once.

Sign-in responses return the user's **profile** instead:
```json
{ "userId": "3f9c…", "email": "amina@demo.local", "fullName": "Amina Uwase", "role": "MEMBER", "createdAt": "2026-11-26T09:14:03" }
```

### Sign-up and sign-in endpoints (public)

| Method & path | Body | Result |
|---|---|---|
| `POST /api/auth/otp/request` | `{"email"}` | `202`. Emails a 6-digit code valid for 10 minutes. Repeat requests within 60 s don't send another email. |
| `POST /api/auth/otp/verify` | `{"email", "code"}`, plus `fullName`, `password` and an optional `role` the **first** time | `200` + profile + cookies |
| `POST /api/auth/login` | `{"email", "password"}` | `200` + profile + cookies |
| `POST /api/auth/refresh` | none (uses the cookie) | `200` + profile + new cookies, or `401` (cookies cleared) |
| `POST /api/auth/logout` | none | `204`, ends this session and clears cookies |

Signed in:

| Method & path | Body | Result |
|---|---|---|
| `GET /api/auth/me` | | Profile |
| `PUT /api/auth/me` | `{"fullName"}` | Updated profile |
| `POST /api/auth/logout-all` | | `204`, signs out every device |

**Registration rules (first `otp/verify` for a new email):**
- `fullName`: required, up to 100 characters.
- `password`: required, 8–72 characters with at least one letter and one digit. It is stored as a BCrypt hash.
- `role`: optional. One of `MEMBER` (default), `OWNER`, `TREASURER`, `Guest`. `ADMIN` cannot be chosen.
- If the code is correct but `fullName` or `password` is missing, you get `400` and **the code is not used up**, so show the form and resend.
- A wrong code uses one of 5 attempts. After 5, request a new code.

### From a browser (the frontend)

Every request must include credentials so the cookies are sent:

```js
const API = import.meta.env.VITE_API_URL ?? "http://localhost:8080";

async function api(path, options = {}, retried = false) {
  const res = await fetch(API + path, {
    credentials: "include",                       // send and receive the HttpOnly cookies
    headers: { "Content-Type": "application/json", ...options.headers },
    ...options,
  });
  if (res.status === 401 && !retried && !path.startsWith("/api/auth/")) {
    const refreshed = await fetch(API + "/api/auth/refresh", { method: "POST", credentials: "include" });
    if (refreshed.ok) return api(path, options, true);   // retry once with the new cookies
    window.location.assign("/login");
  }
  if (!res.ok) throw new Error((await res.json().catch(() => ({}))).message ?? res.statusText);
  return res.status === 204 ? null : res.json();
}

// Sign up / sign in with a code
await api("/api/auth/otp/request", { method: "POST", body: JSON.stringify({ email }) });
const me = await api("/api/auth/otp/verify", {
  method: "POST",
  body: JSON.stringify({ email, code, fullName, password, role }),   // last three only on first sign-in
});

// Or with a password
const me2 = await api("/api/auth/login", { method: "POST", body: JSON.stringify({ email, password }) });

// On page load: am I signed in?
const current = await api("/api/auth/me").catch(() => null);
```

Requirements for cookies to work:
- The frontend's origin must be listed in `CORS_ALLOWED_ORIGINS`, e.g. `http://localhost:5173`.
- Use the same host name for both: `localhost` with `localhost`. With `SameSite=Strict`, cookies are not shared between `localhost` and `127.0.0.1`.
- If the frontend and API are on **different sites** in production (e.g. `myapp.vercel.app` and `api.example.com`), set `AUTH_COOKIE_SAME_SITE=None` on the server. `Secure` stays on.
- Store nothing about the session in `localStorage`. To know who is signed in, call `/api/auth/me`.

**CSRF protection:** a `POST`, `PUT` or `DELETE` that carries auth cookies must come from an allowed origin. Otherwise it gets `403`. Browsers add the `Origin` header automatically.

### From the command line (curl)

curl keeps the cookies in a file (`-c` saves, `-b` sends). Because the requests carry cookies, writes also need an `Origin` header:

```bash
API=http://localhost:8080
C=(-s -c cookies.txt -b cookies.txt -H "Origin: $API")    # cookies + origin (use for uploads)
J=("${C[@]}" -H "Content-Type: application/json")        # ...plus JSON body

# Password sign-in (demo data)
curl "${J[@]}" -X POST $API/api/auth/login -d '{"email":"amina@demo.local","password":"Demo12345"}'

# Or code sign-in / registration
curl "${J[@]}" -X POST $API/api/auth/otp/request -d '{"email":"you@example.com"}'
curl "${J[@]}" -X POST $API/api/auth/otp/verify \
     -d '{"email":"you@example.com","code":"123456","fullName":"Your Name","password":"Str0ngPass","role":"MEMBER"}'

curl "${J[@]}" $API/api/auth/me
curl "${J[@]}" -X POST $API/api/auth/refresh
curl "${J[@]}" -X POST $API/api/auth/logout
```

Scripts and tests can also send `Authorization: Bearer <access JWT>` instead of the cookie. The header takes priority, and a bad header token always gets `401`.

### From Swagger UI

Open `/swagger-ui.html` and run `POST /api/auth/login` (or the OTP pair). The browser stores the cookies, and every other endpoint then works directly from Swagger. You don't need **Authorize**.

Locally, sign-in codes arrive at http://localhost:8025 (Mailpit) with `compose.dev.yml`, or are printed to the console with the `demo` profile.

---

## 2. Categories

| Method & path | Notes |
|---|---|
| `GET /api/categories?kind=EXPENSE\|INCOME` | Shared defaults plus your own |
| `POST /api/categories` | `{"name":"Pets","kind":"EXPENSE"}` |
| `DELETE /api/categories/{id}` | Only your own categories, and only when no transactions use them |

Shared defaults: Food & Groceries, Transport, Rent & Housing, Utilities, Airtime & Data, Health, Education, Entertainment, Shopping, Other; Salary, Business, Gifts, Other Income.

## 3. Transactions

| Method & path | Notes |
|---|---|
| `POST /api/transactions` | Create |
| `GET /api/transactions?from=&to=&type=&currency=&categoryId=&accountId=&recurring=` | All filters optional; newest first |
| `GET` / `PUT` / `DELETE /api/transactions/{id}` | Read / replace / delete |
| `POST /api/transactions/recurring/generate?upToDate=2026-11-30` | Creates the missing monthly copies of recurring transactions |

```json
POST /api/transactions
{
  "type": "EXPENSE",
  "amount": 4500,
  "currency": "RWF",
  "date": "2026-11-20",
  "categoryId": "…",          // optional: defaults to "Other" / "Other Income"
  "merchant": "Simba Supermarket",
  "paymentMethod": "MOBILE_MONEY",
  "note": "Weekly groceries",
  "recurring": false
}
```
`type` is `EXPENSE` or `INCOME`, and the category must be the same kind. The response includes `possibleDuplicate: true` when an identical transaction already exists.

## 4. Importing statements and receipts

Importing takes two steps: **preview** (nothing is saved), then **confirm**.

```bash
# 1. Preview a CSV or a text-based PDF
curl "${C[@]}" -F file=@samples/statement-october-2026.csv $API/api/imports/preview
```
(Or send JSON `{"csvContent":"date,amount,…"}`, or `text/plain`.) Optional query parameters: `currency`, `accountId`, `categoryId`.

The preview lists every row with `date`, `amount`, `type`, `merchant`, `description`, plus `duplicate`/`duplicateReason` (e.g. `"Already in your transactions"`) and any `error`.

```json
POST /api/imports/confirm
{
  "fileName": "statement-october-2026.csv",
  "currency": "RWF",
  "skipDuplicates": true,                 // default true
  "rows": [
    { "date": "2026-10-03", "amount": 12000, "type": "EXPENSE",
      "merchant": "MTN", "description": "Airtime", "categoryId": null, "allowDuplicate": false }
  ]
}
```
→ `201 {"batchId", "importedCount", "skippedDuplicates", "createdTransactionIds"}`. Importing the same statement a second time returns `importedCount: 0`. Set `allowDuplicate: true` on a row that is a genuine repeat.

File rules:
- **CSV columns:** `date,amount[,type,merchant,description]`. Dates can be `2026-11-14`, `14/11/2026` or `14 Nov 2026`. Encoding can be UTF-8, UTF-16 or Windows-1252.
- **PDF:** text-based statements and receipts only. For a receipt, the TOTAL line becomes one row.
- **Limits:** scanned PDFs, photos and encrypted files get `415`, and files over 10 MB get `413`.

## 5. Budgets and dashboard

| Method & path | Notes |
|---|---|
| `POST /api/budgets` | `{"month":"2026-11","categoryId":"…" or null for overall,"limitAmount":60000,"warnPercent":80}` |
| `GET /api/budgets?month=2026-11` | Each budget with `spent`, `remaining`, `percentUsed`, and `status` `OK` / `WARNING` / `EXCEEDED` |
| `GET` / `DELETE /api/budgets/{id}` | |
| `GET /api/dashboard?month=2026-11&currency=RWF` | Income, expenses, net, totals per category, budget statuses and the forecast |

Forecast: `projectedTotal = round_half_up(spentSoFar / daysElapsed × daysInMonth)`. The response explains the calculation in words.

## 6. Shared group expenses

```bash
G=$(curl "${J[@]}" -X POST $API/api/groups -d '{"name":"Kigali Flatmates"}' | jq -r .id)
curl "${J[@]}" -X POST $API/api/groups/$G/members -d '{"email":"jean@demo.local","displayName":"Jean"}'
curl "${J[@]}" $API/api/groups/$G/members           # get member ids
```

| Method & path | Notes |
|---|---|
| `POST` / `GET /api/groups` | Create / list your groups |
| `POST` / `GET /api/groups/{id}/members` | Add a member by the email of a registered user |
| `POST /api/groups/{id}/expenses?currency=RWF` | Record a bill (see below) |
| `GET /api/groups/{id}/expenses` | |
| `GET /api/groups/{id}/balances?currency=RWF` | Each member's `paid`, `owed`, `net` |
| `GET /api/groups/{id}/settlements/suggestions?currency=RWF` | The fewest transfers that settle everyone |
| `POST` / `GET /api/groups/{id}/settlements` | `{"fromMemberId","toMemberId","amount","currency"}` |

```json
POST /api/groups/{id}/expenses
{ "description": "Dinner", "totalAmount": 30000, "paidByMemberId": "…", "date": "2026-11-20",
  "participants": [ {"memberId": "…"}, {"memberId": "…"}, {"memberId": "…"} ] }
```

How `participants` sets the split:
- **Omitted, or no `value`s:** an equal split between those members (or between everyone, if omitted).
- **`value`s adding up to 100:** a percentage split.
- **Any other `value`s:** exact amounts that must add up to `totalAmount`. (If `totalAmount` is itself 100, values adding up to 100 are treated as percentages.)

Example: RWF 30,000 paid by Amina and split equally between three people gives balances of +20,000 / −10,000 / −10,000. The suggestions are two transfers of 10,000 to Amina. After you record both, every balance is 0.

## 7. Health

`GET /actuator/health` → `{"status":"UP"}`. It is public, and the deploy pipeline's smoke test uses it.
