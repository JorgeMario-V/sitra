package co.coop.sitra.integracion;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Prueba de la arquitectura del corte vertical: atraviesa TODAS las capas reales
 * (filtro JWT → controlador → servicio → repositorio → MariaDB con triggers → simulador del núcleo).
 * Necesita MariaDB encendido y db/instalar_todo.sql ejecutado. Se corre con: mvn test -Pintegracion
 */
@Tag("integracion")
@SpringBootTest
@AutoConfigureMockMvc
class CorteVerticalIntegracionTest {

    private static final String URL = "/api/v1/transferencias";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    private String token(String clientId, String secreto) throws Exception {
        MvcResult r = mvc.perform(post("/api/v1/auth/token").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("clientId", clientId, "secreto", secreto))))
                .andExpect(status().isOk())
                .andReturn();
        return "Bearer " + json.readTree(r.getResponse().getContentAsString()).get("token").asText();
    }

    private String cuerpo(String referencia, long monto) throws Exception {
        return json.writeValueAsString(Map.of("cuentaOrigen", "AH-1001", "cuentaDestino", "BILLETERA-EXTERNA",
                "monto", monto, "referenciaExterna", referencia));
    }

    private long id(MvcResult r) throws Exception {
        return json.readTree(r.getResponse().getContentAsString()).get("id").asLong();
    }

    @Test
    @DisplayName("AC-01: sin token responde 401 y con secreto incorrecto no entrega token")
    void sinTokenNoSeEntra() throws Exception {
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(cuerpo("IT-SIN-TOKEN", 1000)))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/token").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("clientId", "billetera-externa", "secreto", "mal"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("CRUD punta a punta: crear 201, repetir 409, listar, consultar, PUT 409, anular 204 y ya no aparece")
    void crudPuntaAPunta() throws Exception {
        String billetera = token("billetera-externa", "billetera-2026");
        String referencia = "IT-" + UUID.randomUUID();

        MvcResult creada = mvc.perform(post(URL).header("Authorization", billetera)
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo(referencia, 1000)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("CONFIRMADA"))
                .andReturn();
        long id = id(creada);

        mvc.perform(post(URL).header("Authorization", billetera)
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo(referencia, 1000)))
                .andExpect(status().isConflict());

        mvc.perform(get(URL).header("Authorization", billetera))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.referenciaExterna == '" + referencia + "')]").exists());

        mvc.perform(get(URL + "/" + id).header("Authorization", billetera))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.referenciaExterna").value(referencia));

        mvc.perform(put(URL + "/" + id).header("Authorization", billetera).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("cuentaDestino", "BILLETERA-EXTERNA", "monto", 500))))
                .andExpect(status().isConflict());

        mvc.perform(delete(URL + "/" + id).header("Authorization", billetera))
                .andExpect(status().isNoContent());

        mvc.perform(get(URL + "/" + id).header("Authorization", billetera))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("PUT: una transferencia RECHAZADA por el núcleo (422) se corrige y queda CONFIRMADA (200)")
    void rechazadaSeCorrigeYReintenta() throws Exception {
        String billetera = token("billetera-externa", "billetera-2026");
        MvcResult rechazada = mvc.perform(post(URL).header("Authorization", billetera)
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo("IT-" + UUID.randomUUID(), 999999999L)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.estado").value("RECHAZADA"))
                .andReturn();
        long id = id(rechazada);

        mvc.perform(put(URL + "/" + id).header("Authorization", billetera).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("cuentaDestino", "BILLETERA-EXTERNA", "monto", 1000))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CONFIRMADA"));

        // se anula para devolver el saldo y dejar la base como estaba
        mvc.perform(delete(URL + "/" + id).header("Authorization", billetera))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("HU-02 y HU-03: saldo para la billetera; bitácora solo para el auditor (403) y la cadena está íntegra")
    void saldoYAuditoria() throws Exception {
        String billetera = token("billetera-externa", "billetera-2026");
        mvc.perform(get("/api/v1/cuentas/AH-1001/saldo").header("Authorization", billetera))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.saldo").exists());
        mvc.perform(get("/api/v1/auditoria").header("Authorization", billetera))
                .andExpect(status().isForbidden());

        String auditor = token("auditor-cumplimiento", "auditor-2026");
        mvc.perform(get("/api/v1/auditoria").param("cuenta", "AH-1001").header("Authorization", auditor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].hashActual").exists());
        mvc.perform(get("/api/v1/auditoria/verificacion").header("Authorization", auditor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.integra").value(true));
    }
}
