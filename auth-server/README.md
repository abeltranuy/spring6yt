# auth-server

Servidor de autorización OAuth 2 / OpenID Connect (Spring Authorization Server) para la app de
`Part38-Spring Security 6 Validating JWT Token`.

## Quién hace qué

| Pieza | Puerto | Responsabilidad |
|---|---|---|
| `auth-server` (este módulo) | 9000 | Formulario de login, compara la clave con BCrypt, emite y firma los tokens (RS256), refresh tokens con rotación, logout |
| App de Part38 | 8080 | Registro de usuarios (`POST /registrar`), la API (`/students`, `/me`) y las páginas de `static/`. **No ve claves ni firma tokens** |

Las dos leen la misma tabla `users` de la base MySQL `telusko`: Part38 la escribe al registrar y
este servidor solo la lee al hacer login.

## Cómo arrancarlo

Hacen falta MySQL y **los dos servidores**, cada uno en su terminal:

```bash
cd auth-server
mvn spring-boot:run
```

```bash
cd "Part38-Spring Security 6 Validating JWT Token"
mvn spring-boot:run
```

Luego abre **http://127.0.0.1:8080/login.html**.

> Usa `127.0.0.1` y no `localhost`. Spring Authorization Server rechaza `localhost` como dirección de
> retorno (`redirect_uri`), y la cookie de sesión queda ligada al nombre con que abriste la página.
> Si entras por `localhost`, `login.html` te redirige sola.

## Los dos caminos para usar la API

1. **Navegador.** "Iniciar Sesión" lleva a este servidor (`authorization_code` + PKCE). Al volver, la
   app de Part38 guarda los tokens en el servidor y el navegador solo recibe una cookie de sesión
   `HttpOnly`. Las escrituras llevan además el token CSRF (cookie `XSRF-TOKEN` → cabecera `X-XSRF-TOKEN`).
2. **Programas** (Postman, otro servicio). Piden un token con `client_credentials` y lo envían como
   `Authorization: Bearer`:

```bash
curl -u part38-api-client:dev-only-api-client-secret -d grant_type=client_credentials -d scope=students.read http://localhost:9000/oauth2/token
```

## Endpoints útiles

- `http://localhost:9000/.well-known/openid-configuration` — todo lo que ofrece el servidor
- `http://localhost:9000/oauth2/jwks` — la clave **pública** con que cualquiera valida las firmas

## Para producción

- Definir `AUTH_WEB_CLIENT_SECRET`, `AUTH_API_CLIENT_SECRET`, `DB_USERNAME` y `DB_PASSWORD` como
  variables de entorno: los valores de `application.properties` son solo para desarrollo.
- `auth-server-keys/jwk.json` contiene la **clave privada** de firma. Está en `.gitignore`; se genera
  sola la primera vez y se reutiliza para que un reinicio no invalide los tokens emitidos.
- Los clientes registrados y las autorizaciones viven en memoria: al reiniciar este servidor los
  usuarios tienen que volver a iniciar sesión. Lo siguiente sería guardarlos en base de datos
  (`JdbcRegisteredClientRepository`, `JdbcOAuth2AuthorizationService`) y servir todo por HTTPS.
