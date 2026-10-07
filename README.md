# Gestor de registros · Cloud U4

Aplicación local con backend **Java 21 / Spring Boot** e interfaz gráfica **React**. El backend lee CSV y ejecuta inserción, consulta, edición y eliminación en Cloud Firestore.

**Estado:** conexión real a `cloudsync-datalab` verificada. Se importaron dos registros ficticios a `registros` y se comprobaron creación, consulta, edición y eliminación usando un documento temporal de prueba. La interfaz muestra los registros guardados en Firestore.

## Configuración de tu base

| Parámetro | Valor |
| --- | --- |
| Proyecto | `cloudsync-datalab` |
| Número del proyecto | `362615374297` (informativo; no es una credencial) |
| Base Firestore | `(default)` |
| Colección | `registros`, sin espacios finales (confirmado por el usuario) |
| Identificador | ID del documento, por ejemplo `BX2oGqeu7pVD4g7OsVVs` |

Los valores predeterminados están en `backend/src/main/resources/application.properties`. Puedes sustituirlos con `FIREBASE_PROJECT_ID`, `FIREBASE_DATABASE_ID` y `FIREBASE_COLLECTION`. La colección confirmada es `registros`, sin espacios finales.

Todos los campos se almacenan como **string**, incluyendo Edad, Semestre y Telefono:

```text
Nombre,Apellido,Correo,Direccion,Edad,Jornada,Semestre,Sexo,Telefono,Universidad
```

Nombre y Apellido son obligatorios. Los demás pueden quedar vacíos y se guardan como `""`. Este criterio es una decisión de la aplicación, ya que no se indicó otra regla de obligatoriedad. El contenido de los valores, incluidos los espacios y ceros iniciales, se conserva.

Los nombres de campo con espacios iniciales o finales se reconocen al leer. Al editar un documento existente se conservan sus claves originales y los campos adicionales que no forman parte del formulario. Si existen dos claves ambiguas, por ejemplo `Nombre` y `Nombre ` en el mismo documento, se informa del conflicto y se evita editarlas automáticamente.

El ID se expone como `_id` en la API, pero **no se añade como campo a Firestore**. Crear desde el formulario genera un ID automático; buscar, editar y eliminar usan ese ID.

## 1. Credenciales locales

El ID y el enlace de la consola identifican la base, pero no autentican el backend. Sigue la [configuración oficial de cuentas de servicio](https://firebase.google.com/docs/admin/setup#initialize_the_sdk_in_non-google_environments):

1. En Firebase Console, abre el proyecto `cloudsync-datalab`.
2. Ve a Configuración del proyecto → Cuentas de servicio → Firebase Admin SDK.
3. Genera una clave privada si tu cuenta tiene permiso y guárdala fuera de este repositorio, por ejemplo en `~/.config/cloudu4/service-account.json`.
4. En la terminal donde arrancarás el backend:

macOS/Linux:

```sh
export GOOGLE_APPLICATION_CREDENTIALS="$HOME/.config/cloudu4/service-account.json"
export FIREBASE_PROJECT_ID="cloudsync-datalab"
export FIREBASE_DATABASE_ID="(default)"
export FIREBASE_COLLECTION="registros"
```

PowerShell:

```powershell
$env:GOOGLE_APPLICATION_CREDENTIALS = "$env:USERPROFILE\.config\cloudu4\service-account.json"
$env:FIREBASE_PROJECT_ID = "cloudsync-datalab"
$env:FIREBASE_DATABASE_ID = "(default)"
$env:FIREBASE_COLLECTION = "registros"
```

Puedes adaptar los archivos `backend/config/variables.example.sh` y `.ps1`. Como alternativa, copia `backend/config/local.example.properties` a `backend/config/local.properties` y configura `app.firebase.credentials-file` con la ruta absoluta del JSON. El servidor carga ese archivo al ejecutarse desde `backend/`; la ruta local prevalece sobre el valor predeterminado tomado de GOOGLE_APPLICATION_CREDENTIALS. `local.properties` está ignorado por Git y se excluye del ZIP. En este computador ya quedó configurado, por lo que puedes arrancar el backend directamente. Spring Boot no carga un archivo `.env` automáticamente. No pongas la clave en React ni en la entrega.

La cuenta de servicio debe tener permisos de lectura y escritura sobre Firestore. El backend usa credenciales del servidor, como describe la [documentación oficial de clientes de servidor](https://firebase.google.com/docs/firestore/security/overview).

Sin credenciales, el servidor permite cargar el formulario y devuelve un error 503 explicando lo que falta al intentar consultar o escribir. No utiliza una base ficticia como sustituto de tu nube.

## 2. Ejecutar

Necesitas JDK **21**, Node **20.19+ o 22.12+** compatible con Vite y acceso a Internet para la primera instalación. Se incluyen Maven Wrapper y el archivo de bloqueo de npm.

Abre la carpeta `poligran_crud-cloud` en VS Code. Primera terminal, después de configurar las credenciales:

```sh
cd backend
./mvnw spring-boot:run
```

En Windows: `.\mvnw.cmd spring-boot:run`.

Segunda terminal:

```sh
cd frontend
npm ci
npm run dev
```

Abre **http://127.0.0.1:5173**. El backend escucha en `127.0.0.1:8080`; Vite reenvía las solicitudes `/api`. Ambos servidores se limitan a tu computador.

La primera consulta carga diez registros y permite comprobar la conexión sin modificar la base. Para comprobar específicamente el documento del enlace, busca `BX2oGqeu7pVD4g7OsVVs`.

Para detener, pulsa Ctrl+C en cada terminal. Al abrir otra terminal vuelve a configurar las variables de entorno.

## 3. Usar la aplicación

1. **Importar:** descarga la plantilla desde la interfaz o usa `examples/registros.csv`, que contiene dos registros ficticios compatibles. Selecciona coma o punto y coma y revisa el resumen.
2. **Crear:** completa Nombre, Apellido y los campos que necesites. El ID se genera automáticamente.
3. **Consultar:** usa la tabla paginada o busca un ID exacto.
4. **Editar:** pulsa Editar en el registro y guarda los cambios.
5. **Eliminar:** pulsa Eliminar y confirma en el diálogo.

No se presume que Correo sea único. Los documentos existentes con IDs automáticos se administran directamente sin migrarlos.

### CSV

El archivo debe ser UTF-8, con los diez encabezados anteriores, en cualquier orden. También admite una columna opcional `_id` para conservar IDs explícitos; sin esa columna no necesitas inventar identificadores. Se permiten BOM, campos entre comillas, comas y saltos de línea dentro de un campo entre comillas, y espacios alrededor de los nombres de encabezado.

Las filas sin ID reciben `csv-` más un SHA-256 de sus diez valores. Reimportar exactamente el mismo contenido genera el mismo ID y omite el documento existente. Una fila cuyo contenido cambió se considera nueva: para cambiar un registro existente utiliza Editar. Este mecanismo no detecta como duplicado un documento previamente creado con un ID automático distinto.

La creación es condicional y no sobrescribe documentos. Un ID repetido dentro del archivo se omite. Encabezados inválidos, sintaxis inválida, UTF-8 inválido o exceso de filas rechazan el archivo antes de escribir. Los errores individuales omiten la fila y permiten cargar las válidas.

El resumen contiene `read`, `created`, `skipped`, `failed` y errores por fila. Las filas son registros lógicos (encabezado = fila 1). Las escrituras son individuales, no una transacción global. Si se pierde la confirmación, no se cuenta como creada; reimportar el mismo contenido o ID evita sobrescribir una escritura que sí haya llegado.

Límites configurables: `CSV_MAX_ROWS=1000`, `CSV_MAX_FILE_SIZE=2MB`, `CSV_MAX_REQUEST_SIZE=3MB`.

## 4. API

| Método y ruta | Resultado |
| --- | --- |
| GET /api/esquema | Diez campos TEXT, identificador _id y generatedId=true |
| GET /api/registros?limit=20&cursor=ID | items y nextCursor; límite de 1 a 100 |
| GET /api/registros/{id} | Registro por ID del documento |
| POST /api/registros | Crea; 201 y registro con _id |
| PUT /api/registros/{id} | Actualiza los diez campos; conserva el ID |
| DELETE /api/registros/{id} | Elimina; 204 |
| POST /api/registros/importar | Multipart: file y delimiter; resumen por fila |

Errores JSON: `{"message":"Explicación"}`. Códigos: 400 solicitud inválida, 404 documento inexistente, 409 duplicado/conflicto, 413 archivo grande y 503 credenciales, permisos o conexión.

La paginación usa orden por ID y [cursores nativos de Firestore](https://firebase.google.com/docs/firestore/query-data/query-cursors), con `limit+1`; no descarga toda la colección. Edición y eliminación leen el documento y usan su timestamp como precondición para detectar cambios durante la operación. La edición evita recrear documentos eliminados y conserva datos externos al esquema.

## 5. Verificación

Pruebas de validación, sin Firebase:

```sh
cd backend
./mvnw test
```

Para ejecutar también la integración, inicia el [emulador oficial de Firestore](https://firebase.google.com/docs/emulator-suite/connect_firestore) en localhost. Se incluye `firebase.json` para quien tenga Firebase CLI:

```sh
firebase emulators:start --only firestore --project demo-cloudu4
```

En otra terminal, desde `backend/`:

```sh
FIRESTORE_EMULATOR_HOST=127.0.0.1:8189 ./mvnw test
```

PowerShell:

```powershell
$env:FIRESTORE_EMULATOR_HOST = "127.0.0.1:8189"
.\mvnw.cmd test
Remove-Item Env:FIRESTORE_EMULATOR_HOST
```

Las pruebas de integración se activan solo con un emulador en `127.0.0.1` y fijan el proyecto `demo-cloudu4`, con una colección única de prueba. El cliente exige un proyecto `demo-` en modo emulador. No se ejecutan sobre `cloudsync-datalab`. Sin emulador se aprueban 22 pruebas y se omiten 7 de integración.

Para probar la interfaz con datos locales, configura además `FIREBASE_PROJECT_ID=demo-cloudu4` al arrancar el backend. Para volver a tu nube elimina `FIRESTORE_EMULATOR_HOST`, restaura `FIREBASE_PROJECT_ID=cloudsync-datalab` y reinicia el backend.

Frontend:

```sh
cd frontend
npm run build
```

**Verificación realizada el 6 de octubre de 2026:** 29 pruebas aprobadas (22 de validación y 7 de integración con Firestore Emulator 1.22.0); compilación frontend aprobada. En Safari se comprobó creación, búsqueda, edición e importación de dos filas mediante la interfaz contra el emulador. La eliminación se comprobó mediante la API en las pruebas de integración. **Verificación adicional contra la nube real:** cuenta de servicio autenticada, importación de dos filas confirmada, lecturas posteriores correctas, CRUD completo comprobado con un documento temporal y reimportación sin duplicados. En Safari se verificó que los dos documentos aparecen en la lista. Detalles en `docs/verificacion-firebase.md`. Después del cambio para cargar la ruta desde local.properties se repitieron las 22 pruebas de validación, todas aprobadas; las 7 de emulador se omitieron en esa ejecución sin emulador.

## 6. Actividad y entrega

Consulta `docs/actividad-y-verificacion.md` para relacionar la implementación con el PDF y revisar lo pendiente: bitácora PDF con objetivos, desarrollo, pruebas, conclusiones y referencias APA; capturas para la entrega; e invitación del tutor. La conexión y las operaciones en la nube ya se verificaron; los resultados están en `docs/verificacion-firebase.md`.

El archivo ZIP de fuentes debe incluir backend, frontend, ejemplos y documentación. No incluye credenciales, dependencias descargadas ni datos personales de la base. Maven/npm descargan las dependencias según sus archivos de configuración.

## Problemas habituales

- **503 al consultar:** revisa app.firebase.credentials-file en config/local.properties o GOOGLE_APPLICATION_CREDENTIALS, que el JSON corresponda al proyecto y que la cuenta tenga permisos. Reinicia el backend después de cambiar las variables.
- **Lista vacía inesperada:** comprueba que FIREBASE_COLLECTION sea registros, sin espacios finales, y que hayas eliminado FIRESTORE_EMULATOR_HOST al volver a tu nube.
- **404 al buscar:** usa el ID del documento, no el correo ni el número del proyecto.
- **409 al editar:** actualiza la lista y revisa si cambió el documento o hay claves duplicadas por espacios.
- **Puerto ocupado:** detén otra instancia que use 8080, 5173 o 8189. Si cambias el puerto del backend, ajusta el proxy de Vite.
- **Java incorrecto:** java y javac deben ser versión 21.
- **CSV rechazado:** comprueba los diez encabezados, UTF-8, delimitador y comillas. Exporta los valores como texto para conservar ceros iniciales.

Esta aplicación está preparada para uso local. Publicarla requiere añadir autenticación y autorización a la API. Los permisos de Firebase Admin corresponden al servidor; las reglas del cliente web no sustituyen esos permisos.

## Organización

```text
backend/      API Java, configuración Firestore, validación, importación y pruebas
frontend/     Interfaz React y plantilla CSV descargable
examples/     Datos ficticios compatibles y ejemplos antiguos separados
docs/         Requisitos y resultados de pruebas locales y Firebase real
firebase.json Configuración del emulador para pruebas locales
```
