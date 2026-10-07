package co.edu.poligran.registros;

import co.edu.poligran.registros.dto.*;
import co.edu.poligran.registros.error.ApiException;
import co.edu.poligran.registros.repository.RegistroRepository;
import co.edu.poligran.registros.service.*;
import org.junit.jupiter.api.*;
import org.springframework.http.HttpStatus;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ImportValidationTest {
    // Esquema FICTICIO, exclusivo de pruebas; no describe la base del usuario.
    final Schema schema = new Schema("codigo", List.of(
        new Schema.Field("codigo", Schema.Type.TEXT, true),
        new Schema.Field("nombre", Schema.Type.TEXT, true),
        new Schema.Field("cantidad", Schema.Type.INTEGER, true)));
    RegistroValidator validator = new RegistroValidator(schema);
    MemoryRepository repository = new MemoryRepository();
    CsvImportService importer = new CsvImportService(validator, repository, 3);
    ImportResult csv(String content) { return importer.importCsv(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)), ","); }
    @Test void preservesLeadingZerosAndConvertsOnlyDeclaredNumbers() {
        var row = validator.validate(Map.of("codigo", "0007", "nombre", "0010", "cantidad", "10"));
        assertEquals("0007", row.get("codigo")); assertEquals("0010", row.get("nombre")); assertEquals(10L, row.get("cantidad"));
    }
    @Test void identifierCannotBeJsonNumber() {
        assertThrows(ApiException.class, () -> validator.validate(Map.of("codigo", 7, "nombre", "Prueba", "cantidad", 10)));
    }
    @Test void invalidHeadersDoNotWrite() {
        assertThrows(ApiException.class, () -> csv("codigo,otro,cantidad\n0001,Prueba,1")); assertTrue(repository.rows.isEmpty());
    }
    @Test void duplicatedHeadersDoNotWrite() {
        assertThrows(ApiException.class, () -> csv("codigo,codigo,cantidad\n0001,Prueba,1")); assertTrue(repository.rows.isEmpty());
    }
    @Test void bomQuotedDelimiterAndMultilineText() {
        var result = csv("\uFEFFcodigo,nombre,cantidad\n0001,\"Texto, con coma\nsegunda línea\",2");
        assertEquals(1, result.created()); assertEquals("Texto, con coma\nsegunda línea", repository.get("0001").get("nombre"));
    }
    @Test void semicolonIsExplicit() {
        var result = importer.importCsv(new ByteArrayInputStream("codigo;nombre;cantidad\n0001;Prueba;2".getBytes(StandardCharsets.UTF_8)), ";");
        assertEquals(1, result.created());
    }
    @Test void duplicatesWithinFileAndOnReimportAreSkipped() {
        var first = csv("codigo,nombre,cantidad\n0001,Primero,2\n0001,Segundo,3");
        var second = csv("codigo,nombre,cantidad\n0001,Tercero,4");
        assertEquals(1, first.created()); assertEquals(1, first.skipped()); assertEquals(1, second.skipped());
        assertEquals("Primero", repository.get("0001").get("nombre"));
    }
    @Test void invalidRowsAreReportedWhileValidOnesAreCreated() {
        var result = csv("codigo,nombre,cantidad\n0001,Prueba,no\n0002,,2\n0003,Correcto,3");
        assertEquals(3, result.read()); assertEquals(1, result.created()); assertEquals(2, result.skipped());
        assertEquals(2, result.errors().getFirst().row());
    }
    @Test void malformedUtf8IsRejected() {
        assertThrows(ApiException.class, () -> importer.importCsv(new ByteArrayInputStream(new byte[]{(byte)0xc3, 0x28}), ","));
        assertTrue(repository.rows.isEmpty());
    }
    @Test void rowLimitRejectsBeforeWriting() {
        assertThrows(ApiException.class, () -> csv("codigo,nombre,cantidad\n1,A,1\n2,B,2\n3,C,3\n4,D,4"));
        assertTrue(repository.rows.isEmpty());
    }
    @Test void malformedQuotesRejectBeforeWriting() {
        assertThrows(ApiException.class, () -> csv("codigo,nombre,cantidad\n1,A,1\n2,\"Sin cerrar,2"));
        assertTrue(repository.rows.isEmpty());
    }
    @Test void uncertainWriteDoesNotCountAsConfirmed() {
        repository.failId = "2";
        var result = csv("codigo,nombre,cantidad\n1,A,1\n2,B,2\n3,C,3");
        assertEquals(2, result.created()); assertEquals(1, result.failed()); assertEquals(3, result.errors().getFirst().row());
        assertEquals(2, repository.rows.size());
    }
    @Test void missingColumnsAreSkipped() {
        var result = csv("codigo,nombre,cantidad\n1,A\n2,B,2");
        assertEquals(1, result.created()); assertEquals(1, result.skipped());
    }
    @Test void unsafeIdsAndUnknownFieldsAreRejected() {
        assertThrows(ApiException.class, () -> validator.validateId("a/b"));
        assertThrows(ApiException.class, () -> validator.validate(Map.of("codigo","1","nombre","A","cantidad","1","extra","x")));
    }
    static class MemoryRepository implements RegistroRepository {
        final SortedMap<String, Map<String, Object>> rows = new TreeMap<>();
        String failId;
        public PageResult list(int limit, String cursor) {
            var values = rows.entrySet().stream().filter(e -> cursor == null || e.getKey().compareTo(cursor) > 0).limit(limit + 1L).toList();
            var page = values.stream().limit(limit).map(Map.Entry::getValue).toList();
            return new PageResult(page, values.size() > limit ? values.get(limit - 1).getKey() : null);
        }
        public Map<String,Object> get(String id) { if (!rows.containsKey(id)) throw ApiException.missing(); return rows.get(id); }
        public void create(String id, Map<String,Object> data) {
            if (id.equals(failId)) throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "No se confirmó la escritura.");
            if (rows.containsKey(id)) throw ApiException.duplicate(); rows.put(id, data);
        }
        public void update(String id, Map<String,Object> data) { get(id); rows.put(id,data); }
        public void delete(String id) { get(id); rows.remove(id); }
    }
}
