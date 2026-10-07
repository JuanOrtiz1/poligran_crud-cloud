# Configuración local. El JSON privado debe existir fuera del repositorio.
$env:GOOGLE_APPLICATION_CREDENTIALS = "$env:USERPROFILE\.config\cloudu4\service-account.json"
$env:FIREBASE_PROJECT_ID = "cloudsync-datalab"
$env:FIREBASE_DATABASE_ID = "(default)"
$env:FIREBASE_COLLECTION = "registros"
$env:CSV_MAX_ROWS = "1000"
$env:CSV_MAX_FILE_SIZE = "2MB"
$env:CSV_MAX_REQUEST_SIZE = "3MB"
