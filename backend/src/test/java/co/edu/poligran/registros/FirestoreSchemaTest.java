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
    @Test void usesDocumentIdAndStoresAgeAndSemesterAsIntegers() {
        var data = validator.validate(row());
        assertNull(validator.id(data));
        assertEquals(45L, data.get("Edad")); assertEquals(8L, data.get("Semestre"));
        assertEquals("0001234567", data.get("Telefono")); assertEquals("Ana ", data.get("Nombre"));
        assertTrue(validator.schema().generatedId()); assertEquals(10, data.size());
        assertEquals(List.of("Edad", "Semestre"), validator.schema().fields().stream()
            .filter(f -> f.type() == co.edu.poligran.registros.dto.Schema.Type.INTEGER)
            .map(co.edu.poligran.registros.dto.Schema.Field::name).toList());
    }
    @Test void optionalNumbersAreNullAndExplicitIdIsAccepted() {
        var data = validator.validate(Map.of("Nombre", "Ana", "Apellido", "Ejemplo", "_id", "BX2oGqeu7pVD4g7OsVVs"));
        assertNull(data.get("Edad")); assertNull(data.get("Semestre")); assertEquals("", data.get("Telefono")); assertEquals("BX2oGqeu7pVD4g7OsVVs", validator.id(data));
        assertThrows(ApiException.class, () -> validator.validate(Map.of("Nombre", "Ana", "Apellido", "Ejemplo", "_id", 123)));
    }
    @Test void stripsFieldNamesButPreservesValuesAndRejectsAmbiguity() {
        assertEquals("Ana ", validator.validate(Map.of("Nombre ", "Ana ", "Apellido ", "Ejemplo ")).get("Nombre"));
        assertThrows(ApiException.class, () -> validator.validate(Map.of("Nombre", "A", "Nombre ", "B", "Apellido", "C")));
    }
    @Test void acceptsNumericJson() {
        var data = row(); data.put("Edad", 45); data.put("Semestre", 8);
        assertEquals(45L, validator.validate(data).get("Edad"));
        assertEquals(8L, validator.validate(data).get("Semestre"));
    }
    @Test void rejectsInvalidNumbersForBothFields() {
        for (String field : List.of("Edad", "Semestre")) {
            for (Object invalid : List.of("abc", "2.5", 2.5, true, "9007199254740992", "999999999999999999999999")) {
                var data = row(); data.put(field, invalid);
                assertThrows(ApiException.class, () -> validator.validate(data), field + ": " + invalid);
            }
            for (Object empty : Arrays.asList(null, "", "   ")) {
                var data = row(); data.put(field, empty);
                assertNull(validator.validate(data).get(field));
            }
            var data = row(); data.put(field, " 000 ");
            assertEquals(0L, validator.validate(data).get(field));
        }
    }
    @Test void csvSkipsInvalidNumericRows() {
        String header = "Nombre,Apellido,Correo,Direccion,Edad,Jornada,Semestre,Sexo,Telefono,Universidad\n";
        assertEquals(new ImportResultWrapper(0, 2), importCsv(header
            + "Ana,Ejemplo,,,abc,,8,,,\nAna,Ejemplo,,,45,,2.5,,,"));
        assertTrue(repository.rows.isEmpty());
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
        assertEquals(45L, repository.get(id).get("Edad"));
        assertEquals(8L, repository.get(id).get("Semestre"));
        // ID generado por la versión anterior, que guardaba todos los campos como texto.
        assertEquals("csv-b19c33a7275065ee158d7fd5ac6c5fe19bd243ab85985958fc1496e68ea5a93c", id);
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
