# Correspondencia con la actividad de la unidad 4

La actividad suministrada exige una aplicación Java conectada a Firebase que lea un CSV y permita insertar, consultar, editar y eliminar mediante una interfaz gráfica. La rúbrica también evalúa un documento independiente. El backend de este proyecto está escrito en Java; la interfaz es web y está desarrollada en React.

| Requisito del software | Implementación | Verificación |
| --- | --- | --- |
| Leer un CSV desde la interfaz | Archivo multipart enviado a Java; Apache Commons CSV y validación del esquema real | Casos UTF-8, BOM, comillas, delimitadores, encabezados y límites; importación desde Safari |
| Cargar los campos en Firebase | FirestoreRegistroRepository.create con creación condicional | SDK real contra Firestore Emulator y cuenta de servicio contra la nube |
| Insertar | Formulario de diez campos y POST /api/registros | Prueba API y comprobación en Safari |
| Consultar un registro | Búsqueda por ID y tabla con cursores Firestore | Consulta API, navegación por cursor y búsqueda en Safari |
| Editar | Formulario y PUT; ID inmutable; se conservan claves existentes | Prueba API, claves con espacios, campos adicionales y actualización en Safari |
| Eliminar | Diálogo y DELETE; timestamp como precondición | Prueba API: eliminación confirmada, GET posterior 404 y segunda eliminación 404 |
| Interfaz gráfica | React en español, resumen CSV, carga y errores | Compilación Vite y revisión visual en Safari |

## Resultado de las pruebas del 6 de octubre de 2026

- 14 pruebas originales del importador y del validador genérico: aprobadas.
- 8 pruebas del esquema Firestore suministrado: aprobadas.
- 7 pruebas de integración con el SDK contra Firestore Emulator 1.22.0: aprobadas.
- Build frontend: aprobado.
- Interfaz en Safari: creación, búsqueda y edición de un registro ficticio, e importación de dos registros ficticios: comprobadas.
- Arranque sin credenciales: formulario disponible y GET /api/registros?limit=1 devuelve 503 con instrucciones para configurar la cuenta de servicio.
- Base real cloudsync-datalab: conexión verificada, dos registros ficticios importados y CRUD completo confirmado con un documento temporal. Reimportación omite los dos IDs existentes. Ver docs/verificacion-firebase.md.
- Regresión tras incorporar configuración local de credenciales: 22 pruebas aprobadas; 7 de emulador omitidas en esa ejecución sin emulador.

Los datos de prueba son ficticios. La suite automatizada de integración utiliza el proyecto demo-cloudu4 y una colección aislada. Las comprobaciones reales de la API usaron además cloudsync-datalab, después de recibir autorización y la cuenta de servicio del usuario. Los informes de Maven se generan en backend/target/surefire-reports al ejecutar las pruebas.

## Decisiones aplicadas al esquema entregado

Edad y Semestre se guardan como enteros; los otros ocho campos se conservan como texto. El ID se toma del documento, no de Correo. Nombre y Apellido son obligatorios como regla de esta aplicación; los numéricos opcionales vacíos se guardan como null y los demás campos admiten cadena vacía. La colección predeterminada es "registros", sin espacios finales, según la confirmación del usuario.

El ID del proyecto y el número del proyecto no son credenciales. Se necesita un JSON de cuenta de servicio autorizado, guardado fuera del repositorio, o credenciales ADC válidas. No se requieren claves de Firebase en React.

## Evidencias que faltan para la entrega académica

1. Incorporar capturas de la importación y los documentos u4-demo-ana-20261006 y u4-demo-luis-20261006. La conexión y las operaciones reales ya están comprobadas; consultar docs/verificacion-firebase.md.
2. Completar la bitácora PDF con autor, curso y tutor; objetivo general y objetivos específicos; pasos reales de creación de la base; explicación del código; resultados y capturas; conclusiones y referencias APA. Este archivo Markdown sirve de apoyo técnico y no sustituye ese PDF.
3. Compartir la base con el tutor mediante su cuenta y el permiso adecuado, siguiendo las instrucciones de la institución. No se ha enviado ninguna invitación desde este desarrollo.
4. Adjuntar el ZIP del proyecto con código, ejemplos y guía de ejecución. Conservar las credenciales fuera del ZIP.

El software y la conexión real están verificados. El cumplimiento académico completo requiere también la bitácora y el acceso del tutor.

## Fuentes técnicas para documentar el desarrollo

Google. (s. f.). *Add the Firebase Admin SDK to your server*. Firebase. https://firebase.google.com/docs/admin/setup

Google. (s. f.). *Add data to Cloud Firestore*. Firebase. https://firebase.google.com/docs/firestore/manage-data/add-data

Google. (s. f.). *Paginate data with query cursors*. Firebase. https://firebase.google.com/docs/firestore/query-data/query-cursors

Google. (s. f.). *Connect your app to the Cloud Firestore Emulator*. Firebase. https://firebase.google.com/docs/emulator-suite/connect_firestore

Apache Software Foundation. (s. f.). *Apache Commons CSV*. https://commons.apache.org/proper/commons-csv/

Las referencias corresponden a páginas técnicas de actualización continua. Para el documento final, incorpora las citas en el texto y aplica el formato APA solicitado por el tutor.

## Actualización del 11 de octubre de 2026

Edad y Semestre pasan a INTEGER: creación, edición e importación CSV convierten sus valores a enteros y los vacíos a null. Teléfono conserva su tipo texto y sus ceros iniciales. Se conserva el cálculo anterior de los IDs CSV para evitar duplicados al reimportar el mismo archivo. Los documentos existentes se convierten al editar y guardar; no se ejecutó una migración de la base real.

Verificación local: 24 pruebas aprobadas y compilación frontend aprobada. Las 7 pruebas de integración se omitieron por no disponer de un emulador activo; se actualizaron sus comprobaciones para verificar los tipos numéricos en Firestore.
