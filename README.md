# telemed-ia-identity-and-access-api

API del dominio Identity and Access de TeleMed IA, Grupo 2.
Gobernanza: https://github.com/code-corhuila/telemed-ia-docs

## Módulos

- `identity-and-access-core`: dominio, puertos y casos de uso; sin framework.
- `identity-and-access-adapters`: HTTP, JPA, BCrypt y emisión JWT.
- `identity-and-access-app`: composición, configuración, recursos y ejecutable.

El paquete base `com.telemed.identityaccess` se conserva. La clase de arranque
permanece en ese paquete para descubrir componentes, entidades y repositorios
en los módulos dependientes. Las pruebas del controlador HTTP están en adapters
con configuración explícita; la prueba de arranque permanece en app.

## Verificación

Con JDK 21 y Maven 3.9:

```sh
mvn -B clean verify
```

Esta reorganización conserva Spring Boot 3.3.13 y el nivel de compilación Java 17.
La actualización a la línea 3.5 del Anexo C se debe validar como cambio separado.
CI utiliza JDK 21 conforme al Anexo I. Docker conserva JDK 17 para construcción
y ejecución; el nivel de compilación sigue siendo Java 17.

## Ejecución

Configure las variables de `.env.example` en el entorno. Spring Boot no carga
`.env` automáticamente. La base debe existir con las migraciones del repositorio
`telemed-ia-identity-and-access-db`; Hibernate solo valida el esquema.

```sh
java -jar identity-and-access-app/target/identity-and-access-app.jar
```

API: 8081. Actuator: 9081, ruta `/actuator/health`.
Endpoints existentes: POST `/api/v1/auth/register` y `/api/v1/auth/login`.

## Docker

Desde la raíz:

```sh
docker build -f deploy/Dockerfile -t telemed-identity-api .
docker compose --env-file .env -f deploy/compose.yml up --build
```

Compose consume una base ya provisionada, no crea esquema ni ejecuta migraciones.
DB_URL debe ser accesible desde el contenedor; localhost allí identifica al propio
contenedor. Para integrar el servicio con -infra, use su red y nombre del servicio
de base de datos. Actualice allí la ruta del Dockerfile a `deploy/Dockerfile`.

## Ramas

Trabajar mediante PR a develop desde su rama hija. Promover mediante
`git cherry-pick -x`, nunca fusionando ramas permanentes. Conservar CODEOWNERS.

## Pendientes normativos

Esta entrega corrige la modularización, no acredita cumplimiento integral:
RS256, UUID, sobre de error común y correlación, idempotencia de creación,
`/health`, límites explícitos, OpenAPI publicado y pruebas contra PostgreSQL real
requieren trabajo adicional. No cambiar identificadores sin coordinar el -db y los
consumidores. El dominio de identidad emite tokens; su manejo de clave privada y
rutas públicas de login/registro debe documentarse en el contrato del equipo.
