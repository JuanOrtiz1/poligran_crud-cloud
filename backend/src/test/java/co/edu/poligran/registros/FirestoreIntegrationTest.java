package co.edu.poligran.registros;

import co.edu.poligran.registros.repository.RegistroRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.firestore.Firestore;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockMultipartFile;
import java.util.*;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Opt-in: solo emulador localhost y proyecto demo; nunca modifica cloudsync-datalab. */
@EnabledIfEnvironmentVariable(named = "FIRESTORE_EMULATOR_HOST", matches = "127\\.0\\.0\\.1:[0-9]+")
@SpringBootTest(properties = "app.firebase.project-id=demo-cloudu4")
@AutoConfigureMockMvc
class FirestoreIntegrationTest {
    static final String COLLECTION = "registros pruebas " + UUID.randomUUID() + " ";
    @DynamicPropertySource static void configure(DynamicPropertyRegistry properties) {
        properties.add("app.firebase.collection", () -> COLLECTION);
    }
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired RegistroRepository repository;
    @Autowired Firestore firestore;
    @AfterEach void clean() throws Exception {
        // Colección exclusiva de esta ejecución en demo-cloudu4.
        for (var doc : firestore.collection(COLLECTION).get().get().getDocuments()) doc.getReference().delete().get();
    }
    Map<String, Object> row(String id) {
        var data = FirestoreSchemaTest.row(); data.put("_id", id); return data;
    }
    void createRow(String id) throws Exception {
        mvc.perform(post("/api/registros").contentType("application/json").content(json.writeValueAsBytes(row(id))))
            .andExpect(status().isCreated());
    }
    @Test void schemaHasTenTextFieldsAndGeneratedDocumentId() throws Exception {
        mvc.perform(get("/api/esquema")).andExpect(status().isOk())
            .andExpect(jsonPath("$.idField").value("_id")).andExpect(jsonPath("$.generatedId").value(true))
            .andExpect(jsonPath("$.fields.length()").value(10));
    }
    @Test void performsCrudAndDoesNotStoreIdAsAField() throws Exception {
        createRow("crud");
        mvc.perform(get("/api/registros/crud")).andExpect(status().isOk())
            .andExpect(jsonPath("$.Edad").value("045")).andExpect(jsonPath("$.Telefono").value("0001234567"));
        assertFalse(firestore.collection(COLLECTION).document("crud").get().get().contains("_id"));
        var changed = row("crud"); changed.put("Direccion", "Dirección modificada");
        mvc.perform(put("/api/registros/crud").contentType("application/json").content(json.writeValueAsBytes(changed)))
            .andExpect(status().isOk());
        assertEquals("Dirección modificada", repository.get("crud").get("Direccion"));
        mvc.perform(delete("/api/registros/crud")).andExpect(status().isNoContent());
        mvc.perform(get("/api/registros/crud")).andExpect(status().isNotFound());
        mvc.perform(delete("/api/registros/crud")).andExpect(status().isNotFound());
        mvc.perform(put("/api/registros/crud").contentType("application/json").content(json.writeValueAsBytes(changed)))
            .andExpect(status().isNotFound());
    }
    @Test void generatesAnIdWhenTheUiCreatesARecord() throws Exception {
        var result = mvc.perform(post("/api/registros").contentType("application/json")
            .content(json.writeValueAsBytes(FirestoreSchemaTest.row()))).andExpect(status().isCreated()).andReturn();
        String id = json.readTree(result.getResponse().getContentAsString()).get("_id").asText();
        assertEquals(20, id.length()); assertEquals("Ana ", repository.get(id).get("Nombre"));
    }
    @Test void preservesExistingSpacedKeysAndExtraFieldsOnUpdate() throws Exception {
        var original = FirestoreSchemaTest.row();
        original.put("Nombre ", original.remove("Nombre")); original.put("Apellido ", original.remove("Apellido"));
        original.put("datoExterno", 42L);
        var doc = firestore.collection(COLLECTION).document("legacy"); doc.create(original).get();
        assertEquals("Ana ", repository.get("legacy").get("Nombre"));
        var changed = row("legacy"); changed.put("Nombre", "Nuevo nombre"); repository.update("legacy", changed);
        var saved = doc.get().get().getData();
        assertEquals("Nuevo nombre", saved.get("Nombre ")); assertFalse(saved.containsKey("Nombre"));
        assertEquals(42L, saved.get("datoExterno")); assertFalse(saved.containsKey("_id"));
    }
    @Test void pagesByDocumentIdEvenWhenCursorDocumentWasDeleted() throws Exception {
        for (String id : List.of("a", "b", "c", "d")) createRow(id);
        var first = repository.list(2, null); assertEquals("b", first.nextCursor()); assertEquals(2, first.items().size());
        repository.delete("b");
        var second = repository.list(2, first.nextCursor());
        assertEquals(List.of("c", "d"), second.items().stream().map(r -> r.get("_id")).toList()); assertNull(second.nextCursor());
    }
    @Test void csvCreatesValidRowsAndSkipsInvalidAndDuplicateRows() throws Exception {
        String header = "Nombre,Apellido,Correo,Direccion,Edad,Jornada,Semestre,Sexo,Telefono,Universidad\n";
        String valid = "Ana,Ejemplo,a@example.invalid,Calle ficticia,045,Noche,08,Femenino,000123,U de ejemplo\n";
        String invalid = ",SinNombre,a@example.invalid,Calle ficticia,045,Noche,08,Femenino,000123,U de ejemplo\n";
        var file = new MockMultipartFile("file", "prueba.csv", "text/csv", (header + valid + invalid + valid).getBytes(StandardCharsets.UTF_8));
        mvc.perform(multipart("/api/registros/importar").file(file)).andExpect(status().isOk())
            .andExpect(jsonPath("$.read").value(3)).andExpect(jsonPath("$.created").value(1))
            .andExpect(jsonPath("$.skipped").value(2)).andExpect(jsonPath("$.failed").value(0));
        mvc.perform(multipart("/api/registros/importar").file(file)).andExpect(status().isOk())
            .andExpect(jsonPath("$.created").value(0)).andExpect(jsonPath("$.skipped").value(3));
        assertEquals(1, repository.list(10, null).items().size());
    }
    @Test void rejectsDuplicateCreatesAndInvalidRequests() throws Exception {
        createRow("existing");
        mvc.perform(post("/api/registros").contentType("application/json").content(json.writeValueAsBytes(row("existing"))))
            .andExpect(status().isConflict());
        var changed = row("other-id");
        mvc.perform(put("/api/registros/existing").contentType("application/json").content(json.writeValueAsBytes(changed)))
            .andExpect(status().isBadRequest());
        mvc.perform(get("/api/registros?limit=101")).andExpect(status().isBadRequest());
        var bad = new MockMultipartFile("file", "bad.csv", "text/csv", "Nombre,Otro\nA,B".getBytes(StandardCharsets.UTF_8));
        mvc.perform(multipart("/api/registros/importar").file(bad)).andExpect(status().isBadRequest());
        assertEquals(1, repository.list(10, null).items().size());
    }
}
