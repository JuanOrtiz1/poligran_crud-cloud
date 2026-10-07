package co.edu.poligran.registros;

import co.edu.poligran.registros.config.RegistroConfiguration;
import co.edu.poligran.registros.error.ApiException;
import co.edu.poligran.registros.service.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.io.ByteArrayInputStream;
import static org.junit.jupiter.api.Assertions.*;

class FirestoreSchemaTest {
    final RegistroValidator validator = new RegistroValidator(RegistroConfiguration.schema());
    final ImportValidationTest.MemoryRepository repository = new ImportValidationTest.MemoryRepository();
    final CsvImportService importer = new CsvImportService(validator, repository, 10);
    static Map<String, Object> row() {
        return new LinkedHashMap<>(Map.of("Nombre", "Ana ", "Apellido", "Ejemplo ", "Correo", "ana@example.invalid",
            "Direccion", "Calle ficticia", "Edad", "045", "Jornada", "Nocturna ", "Semestre", "08",
            "Sexo", "Femenino", "Telefono", "0001234567", "Universidad", "Universidad de ejemplo "));
    }
    ImportResultWrapper importCsv(String content) {
        var result = importer.importCsv(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)), ",");
        return new ImportResultWrapper(result.created(), result.skipped());
    }
    record ImportResultWrapper(int created, int skipped) {}
    @Test void usesDocumentIdAndAllTenFieldsAreStrings() {
        var data = validator.validate(row());
        assertNull(validator.id(data));
        assertEquals("045", data.get("Edad")); assertEquals("08", data.get("Semestre"));
        assertEquals("0001234567", data.get("Telefono")); assertEquals("Ana ", data.get("Nombre"));
        assertTrue(validator.schema().generatedId()); assertEquals(10, data.size());
    }
    @Test void optionalValuesRemainStringsAndExplicitIdIsAccepted() {
        var data = validator.validate(Map.of("Nombre", "Ana", "Apellido", "Ejemplo", "_id", "BX2oGqeu7pVD4g7OsVVs"));
        assertEquals("", data.get("Edad")); assertEquals("BX2oGqeu7pVD4g7OsVVs", validator.id(data));
        assertThrows(ApiException.class, () -> validator.validate(Map.of("Nombre", "Ana", "Apellido", "Ejemplo", "_id", 123)));
    }
    @Test void stripsFieldNamesButPreservesValuesAndRejectsAmbiguity() {
        assertEquals("Ana ", validator.validate(Map.of("Nombre ", "Ana ", "Apellido ", "Ejemplo ")).get("Nombre"));
        assertThrows(ApiException.class, () -> validator.validate(Map.of("Nombre", "A", "Nombre ", "B", "Apellido", "C")));
    }
    @Test void numericJsonDoesNotChangeTheFirestoreSchema() {
        var data = row(); data.put("Edad", 45);
        assertThrows(ApiException.class, () -> validator.validate(data));
    }
    @Test void rejectsControlCharactersAndReservedDocumentIds() {
        assertThrows(ApiException.class, () -> validator.validateId("a\nb"));
        assertThrows(ApiException.class, () -> validator.validateId("__reserved__"));
    }
    @Test void importsWithoutIdAndReimportIsIdempotent() {
        String csv = "Nombre,Apellido,Correo,Direccion,Edad,Jornada,Semestre,Sexo,Telefono,Universidad\nAna,Ejemplo,a@example.invalid,Calle ficticia,045,Noche,08,Femenino,000123,U de ejemplo";
        assertEquals(1, importCsv(csv).created());
        String id = repository.rows.firstKey(); assertTrue(id.startsWith("csv-"));
        assertEquals(1, importCsv(csv).skipped()); assertEquals(1, repository.rows.size());
        assertEquals("000123", repository.get(id).get("Telefono"));
    }
    @Test void acceptsSpacesInHeadersAndOptionalCsvDocumentId() {
        String csv = "_id, Nombre ,Apellido,Correo,Direccion,Edad,Jornada,Semestre,Sexo,Telefono,Universidad\nexisting-id,Ana,Ejemplo,,,,,,, ,";
        assertEquals(1, importCsv(csv).created()); assertEquals("existing-id", repository.rows.firstKey());
    }
    @Test void duplicateNormalizedHeadersRejectTheWholeFile() {
        assertThrows(ApiException.class, () -> importCsv("Nombre,Nombre ,Apellido,Correo,Direccion,Edad,Jornada,Semestre,Sexo,Telefono,Universidad\nA,B,C,,,,,,,,"));
        assertTrue(repository.rows.isEmpty());
    }
}
