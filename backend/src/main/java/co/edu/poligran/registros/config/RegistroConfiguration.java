package co.edu.poligran.registros.config;

import co.edu.poligran.registros.dto.Schema;
import co.edu.poligran.registros.repository.*;
import co.edu.poligran.registros.service.*;
import com.google.cloud.firestore.Firestore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import java.util.List;

@Configuration
public class RegistroConfiguration {
    public static Schema schema() {
        return new Schema("_id", List.of("Nombre", "Apellido", "Correo", "Direccion", "Edad",
            "Jornada", "Semestre", "Sexo", "Telefono", "Universidad").stream()
            .map(name -> new Schema.Field(name, name.equals("Edad") || name.equals("Semestre")
                ? Schema.Type.INTEGER : Schema.Type.TEXT, name.equals("Nombre") || name.equals("Apellido")))
            .toList(), true);
    }
    @Bean
    RegistroValidator validator() { return new RegistroValidator(schema()); }
    @Bean
    RegistroRepository repository(ObjectProvider<Firestore> firestore, RegistroValidator validator,
            @Value("${app.firebase.collection}") String collection) {
        return new FirestoreRegistroRepository(firestore::getObject, collection, validator);
    }
    @Bean
    CsvImportService importer(RegistroValidator validator, RegistroRepository repository,
                              @Value("${app.csv.max-rows}") int maxRows) {
        return new CsvImportService(validator, repository, maxRows);
    }
}
