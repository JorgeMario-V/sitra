package co.coop.sitra.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import co.coop.sitra.dto.ActualizarTransferenciaRequest;
import co.coop.sitra.dto.CrearTransferenciaRequest;
import co.coop.sitra.exception.SolicitudRechazadaException;
import co.coop.sitra.integration.NucleoConector;
import co.coop.sitra.integration.NucleoNoDisponibleException;
import co.coop.sitra.integration.ResultadoNucleo;
import co.coop.sitra.model.Asociado;
import co.coop.sitra.model.ClienteApi;
import co.coop.sitra.model.CuentaAutorizada;
import co.coop.sitra.model.EstadoTransferencia;
import co.coop.sitra.model.Operacion;
import co.coop.sitra.model.Resultado;
import co.coop.sitra.model.Rol;
import co.coop.sitra.model.TipoCuenta;
import co.coop.sitra.model.Transferencia;
import co.coop.sitra.repository.CuentaAutorizadaRepository;
import co.coop.sitra.repository.TransferenciaRepository;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/**
 * Reglas de negocio de la HU-01 con el núcleo y la base de datos sustituidos por dobles de prueba
 * (Mockito). AC-03: todas corren sin el núcleo real y sin base de datos.
 */
class TransferenciaServiceTest {

    private static final String IP = "127.0.0.1";

    private TransferenciaRepository transferencias;
    private CuentaAutorizadaRepository cuentas;
    private IdentificacionService identificacion;
    private AuditoriaService auditoria;
    private NucleoConector nucleo;
    private TransferenciaService servicio;

    private final ClienteApi billetera = new ClienteApi("Billetera", "billetera-externa", "hash", Rol.BILLETERA);
    private final CuentaAutorizada ah1001 =
            new CuentaAutorizada(new Asociado("71000001", "Jorge Puerta"), "AH-1001", TipoCuenta.AHORROS);

    @BeforeEach
    void preparar() {
        transferencias = mock(TransferenciaRepository.class);
        cuentas = mock(CuentaAutorizadaRepository.class);
        identificacion = mock(IdentificacionService.class);
        auditoria = mock(AuditoriaService.class);
        nucleo = mock(NucleoConector.class);
        servicio = new TransferenciaService(transferencias, cuentas, identificacion, auditoria, nucleo);

        when(identificacion.exigirRol("billetera-externa", Rol.BILLETERA)).thenReturn(billetera);
        when(cuentas.findByNumeroCuentaAndActivoTrue("AH-1001")).thenReturn(Optional.of(ah1001));
        when(transferencias.save(any(Transferencia.class))).thenAnswer(invocacion -> invocacion.getArgument(0));
    }

    private static CrearTransferenciaRequest pedido(String cuenta, String monto) {
        return new CrearTransferenciaRequest(cuenta, "BILLETERA-EXTERNA", new BigDecimal(monto), "OP-PRUEBA-1");
    }

    private Transferencia existente(String monto) {
        Transferencia t = Transferencia.nueva(billetera, ah1001, "BILLETERA-EXTERNA", new BigDecimal(monto),
                "OP-PRUEBA-7", IP);
        when(transferencias.findByIdAndClienteApi_IdAndActivoTrue(eq(7L), any())).thenReturn(Optional.of(t));
        return t;
    }

    // ------------------------------- CREATE -------------------------------

    @Test
    @DisplayName("Camino feliz: el núcleo aprueba y la transferencia queda CONFIRMADA con su comprobante")
    void confirmaCuandoElNucleoAprueba() {
        when(nucleo.ejecutarTransferencia("AH-1001", new BigDecimal("300000")))
                .thenReturn(ResultadoNucleo.aprobada("NUC-1"));
        Transferencia t = servicio.registrar("billetera-externa", pedido("AH-1001", "300000"), IP);
        assertEquals(EstadoTransferencia.CONFIRMADA, t.getEstado());
        assertEquals("NUC-1", t.getComprobanteNucleo());
        verify(transferencias).save(t);
    }

    @Test
    @DisplayName("Monto negativo: 400, el intento queda RECHAZADO en la bitácora y el núcleo ni se entera")
    void montoNegativoSeRechazaYSeRegistra() {
        SolicitudRechazadaException e = assertThrows(SolicitudRechazadaException.class,
                () -> servicio.registrar("billetera-externa", pedido("AH-1001", "-50000"), IP));
        assertEquals(HttpStatus.BAD_REQUEST, e.getEstado());
        verify(auditoria).registrar(eq(billetera), isNull(), eq("AH-1001"), eq(Operacion.TRANSFERENCIA),
                eq(Resultado.RECHAZADA), eq("OP-PRUEBA-1"), eq(new BigDecimal("-50000")), anyString(), eq(IP));
        verifyNoInteractions(nucleo);
        verify(transferencias, never()).save(any());
    }

    @Test
    @DisplayName("Código de operación repetido: 409 y nunca se duplica la operación (AC-02)")
    void referenciaRepetidaDa409() {
        when(transferencias.existsByReferenciaExterna("OP-PRUEBA-1")).thenReturn(true);
        SolicitudRechazadaException e = assertThrows(SolicitudRechazadaException.class,
                () -> servicio.registrar("billetera-externa", pedido("AH-1001", "300000"), IP));
        assertEquals(HttpStatus.CONFLICT, e.getEstado());
        verifyNoInteractions(nucleo);
    }

    @Test
    @DisplayName("Cuenta fuera de la lista blanca: 404")
    void cuentaNoAutorizadaDa404() {
        SolicitudRechazadaException e = assertThrows(SolicitudRechazadaException.class,
                () -> servicio.registrar("billetera-externa", pedido("AH-9999", "300000"), IP));
        assertEquals(HttpStatus.NOT_FOUND, e.getEstado());
        verifyNoInteractions(nucleo);
    }

    @Test
    @DisplayName("El núcleo rechaza por saldo: la transferencia se guarda RECHAZADA con el motivo")
    void nucleoRechaza() {
        when(nucleo.ejecutarTransferencia("AH-1001", new BigDecimal("5000000")))
                .thenReturn(ResultadoNucleo.rechazada("Saldo insuficiente en el núcleo"));
        Transferencia t = servicio.registrar("billetera-externa", pedido("AH-1001", "5000000"), IP);
        assertEquals(EstadoTransferencia.RECHAZADA, t.getEstado());
        assertEquals("Saldo insuficiente en el núcleo", t.getMotivoRechazo());
        verify(transferencias).save(t);
    }

    @Test
    @DisplayName("AC-02: si el núcleo no responde, la transferencia queda FALLIDA, nunca a medias")
    void nucleoSinRespuestaQuedaFallida() {
        when(nucleo.ejecutarTransferencia("AH-1001", new BigDecimal("300000")))
                .thenThrow(new NucleoNoDisponibleException("El núcleo no respondió en 5 segundos"));
        Transferencia t = servicio.registrar("billetera-externa", pedido("AH-1001", "300000"), IP);
        assertEquals(EstadoTransferencia.FALLIDA, t.getEstado());
        verify(transferencias).save(t);
    }

    // ------------------------------- UPDATE -------------------------------

    @Test
    @DisplayName("PUT sobre una CONFIRMADA: 409 (ya movió dinero, no se reintenta)")
    void noSeActualizaUnaConfirmada() {
        Transferencia t = existente("300000");
        t.confirmar("NUC-1");
        SolicitudRechazadaException e = assertThrows(SolicitudRechazadaException.class,
                () -> servicio.actualizar("billetera-externa", 7L,
                        new ActualizarTransferenciaRequest("BILLETERA-EXTERNA", new BigDecimal("100000")), IP));
        assertEquals(HttpStatus.CONFLICT, e.getEstado());
        verify(nucleo, never()).ejecutarTransferencia(anyString(), any());
    }

    @Test
    @DisplayName("PUT sobre una RECHAZADA: se corrige el monto, se reintenta con el mismo código y queda CONFIRMADA")
    void corregirYReintentar() {
        Transferencia t = existente("5000000");
        t.rechazar("Saldo insuficiente en el núcleo");
        when(nucleo.ejecutarTransferencia("AH-1001", new BigDecimal("100000")))
                .thenReturn(ResultadoNucleo.aprobada("NUC-2"));
        Transferencia r = servicio.actualizar("billetera-externa", 7L,
                new ActualizarTransferenciaRequest("BILLETERA-EXTERNA", new BigDecimal("100000")), IP);
        assertEquals(EstadoTransferencia.CONFIRMADA, r.getEstado());
        assertEquals(new BigDecimal("100000"), r.getMonto());
        assertEquals("OP-PRUEBA-7", r.getReferenciaExterna());
    }

    // ------------------------------- DELETE -------------------------------

    @Test
    @DisplayName("DELETE de una CONFIRMADA: primero el reverso en el núcleo, luego ANULADA y activo = false")
    void anularConfirmadaPideReverso() {
        Transferencia t = existente("300000");
        t.confirmar("NUC-1");
        when(nucleo.reversarTransferencia("AH-1001", new BigDecimal("300000"), "NUC-1"))
                .thenReturn(ResultadoNucleo.aprobada("REV-NUC-1"));
        servicio.anular("billetera-externa", 7L, IP);
        assertEquals(EstadoTransferencia.ANULADA, t.getEstado());
        assertFalse(t.getActivo());
        verify(nucleo).reversarTransferencia("AH-1001", new BigDecimal("300000"), "NUC-1");
    }

    @Test
    @DisplayName("DELETE sin respuesta del núcleo: 503 y la transferencia sigue CONFIRMADA")
    void anularSinNucleoNoAnula() {
        Transferencia t = existente("300000");
        t.confirmar("NUC-1");
        when(nucleo.reversarTransferencia(anyString(), any(), anyString()))
                .thenThrow(new NucleoNoDisponibleException("El núcleo no respondió en 5 segundos"));
        SolicitudRechazadaException e = assertThrows(SolicitudRechazadaException.class,
                () -> servicio.anular("billetera-externa", 7L, IP));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, e.getEstado());
        assertEquals(EstadoTransferencia.CONFIRMADA, t.getEstado());
        verify(transferencias, never()).save(any());
    }

    @Test
    @DisplayName("DELETE de una RECHAZADA: no movió dinero, así que no se llama al núcleo")
    void anularRechazadaNoLlamaAlNucleo() {
        Transferencia t = existente("5000000");
        t.rechazar("Saldo insuficiente en el núcleo");
        servicio.anular("billetera-externa", 7L, IP);
        assertEquals(EstadoTransferencia.ANULADA, t.getEstado());
        verifyNoInteractions(nucleo);
    }
}
