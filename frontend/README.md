# Antu Bank — Frontend (Next.js)

Frontend de Antu Bank. Esta base implementa la **tarea 10.1**: login vía **Keycloak**
usando **OpenID Connect Authorization Code + PKCE (S256)**.

> Las vistas del dashboard (10.2), la i18n es/en (10.3) y el modo guest + tour (10.4) se
> agregan en tareas posteriores.

## Stack

- **Next.js 15** (App Router, TypeScript, ESLint).
- **Auth.js v5** (`next-auth`) con el **proveedor Keycloak**, que maneja el flujo
  Authorization Code + PKCE y el almacenamiento de tokens en la sesión.
- **React 19**.

## Autenticación (cómo está cableada)

El client `antu-bank-frontend` está definido en
[`infra/keycloak/realm-export.json`](../infra/keycloak/realm-export.json) como **client
público** (`publicClient: true`, sin `client secret`) con **standard flow + PKCE S256** y
`redirectUris` a `http://localhost:3000/*`.

En `src/auth.ts` el proveedor Keycloak se configura para un client público:

- `clientSecret` vacío (no hay secreto de client).
- `checks: ["pkce", "state"]` → fuerza Authorization Code + PKCE.
- `token_endpoint_auth_method: "none"` → al canjear el código no se envía secreto de client.

El `access_token` de Keycloak se guarda en el JWT de sesión (`callbacks.jwt`) y se expone
en la sesión del servidor (`callbacks.session`) como `session.accessToken`, para poder
llamar al gateway/BFF más adelante (tarea 10.2).

### Rutas y piezas

- `src/app/api/auth/[...nextauth]/route.ts` — endpoints de Auth.js (signin, callback de
  Keycloak, signout, sesión).
- `src/app/actions.ts` — server actions `iniciarSesion()` / `cerrarSesion()`.
- `src/app/page.tsx` — portada con botón **Iniciar sesión**; si hay sesión, saluda por nombre.
- `src/app/panel/page.tsx` — **página protegida** (requiere login).
- `src/middleware.ts` — protege `/panel` y redirige al login si no hay sesión.

## Variables de entorno

Copia `.env.example` a `.env.local` y ajusta:

| Variable | Descripción | Equivalente clásico |
|---|---|---|
| `AUTH_SECRET` | Secreto para firmar la cookie de sesión de Auth.js | `NEXTAUTH_SECRET` |
| `AUTH_URL` | URL base del frontend (`http://localhost:3000`) | `NEXTAUTH_URL` |
| `AUTH_KEYCLOAK_ISSUER` | Issuer del realm (`http://localhost:8081/realms/antu-bank`) | `KEYCLOAK_ISSUER` |
| `AUTH_KEYCLOAK_ID` | clientId público (`antu-bank-frontend`) | `KEYCLOAK_CLIENT_ID` |
| `AUTH_KEYCLOAK_SECRET` | Vacío (client público, sin secreto) | — |
| `NEXT_PUBLIC_GATEWAY_URL` | URL del gateway/BFF (tareas 10.2+) | — |

> Auth.js v5 usa el prefijo `AUTH_*`. Si prefieres los nombres clásicos, puedes leerlos y
> mapearlos, pero por defecto el código usa `AUTH_*`.

## Desarrollo local

Requisitos previos: tener el stack de Keycloak arriba (ver
[`infra/keycloak/README.md`](../infra/keycloak/README.md)):

```bash
docker compose -f infra/docker-compose.yml up -d keycloak
```

Luego, en `frontend/`:

```bash
npm install
npm run dev      # http://localhost:3000
```

Abre http://localhost:3000, pulsa **Iniciar sesión** y autentícate con un usuario demo del
realm (`cliente.demo` / `demo1234`). Tras el login serás redirigido al panel.

## Build de producción

```bash
npm run build
npm run start
```

El build **no** requiere que Keycloak esté arriba: la autenticación ocurre en tiempo de
ejecución. Sí requiere `AUTH_SECRET` definido (en `.env.local` o como variable de entorno).
