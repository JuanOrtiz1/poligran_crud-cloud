package co.edu.poligran.registros.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.auth.oauth2.AccessToken;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.FirestoreOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Cliente de servidor: las credenciales nunca se envían a React. */
@Configuration
public class FirebaseConfiguration {
    @Bean(destroyMethod = "close")
    @Lazy
    Firestore firestore(@Value("${app.firebase.project-id}") String projectId,
                        @Value("${app.firebase.database-id}") String databaseId,
                        @Value("${app.firebase.credentials-file:}") String credentialsFile,
                        @Value("${FIRESTORE_EMULATOR_HOST:}") String emulatorHost) throws IOException {
        if (projectId.isBlank()) throw new IllegalArgumentException("Falta FIREBASE_PROJECT_ID.");
        var options = FirestoreOptions.newBuilder().setProjectId(projectId).setDatabaseId(databaseId);
        if (!emulatorHost.isBlank()) {
            if (!projectId.startsWith("demo-")) throw new IllegalArgumentException("El emulador requiere FIREBASE_PROJECT_ID=demo-... para aislar las pruebas.");
            options.setEmulatorHost(emulatorHost).setCredentials(GoogleCredentials.create(
                new AccessToken("emulator-only", new java.util.Date(Long.MAX_VALUE))));
        } else {
            GoogleCredentials credentials;
            if (credentialsFile.isBlank()) credentials = GoogleCredentials.getApplicationDefault();
            else {
                try (var stream = Files.newInputStream(Path.of(credentialsFile))) {
                    credentials = ServiceAccountCredentials.fromStream(stream);
                }
            }
            if (credentials instanceof ServiceAccountCredentials account && !projectId.equals(account.getProjectId()))
                throw new IOException("La cuenta de servicio pertenece a otro proyecto.");
            options.setCredentials(credentials);
        }
        return options.build().getService();
    }
}
