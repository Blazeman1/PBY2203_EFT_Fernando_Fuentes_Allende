package com.bancoxyz.pagos.controller;

import com.bancoxyz.pagos.dto.DepositoRequest;
import com.bancoxyz.pagos.dto.PagoDTO;
import com.bancoxyz.pagos.dto.PagoServicioRequest;
import com.bancoxyz.pagos.dto.TransferenciaEstadoDTO;
import com.bancoxyz.pagos.dto.TransferenciaRequest;
import com.bancoxyz.pagos.service.PagoService;
import com.bancoxyz.pagos.service.ProcesadorPagos;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * API interna de Procesamiento de Pagos. Protegida por
 * {@link com.bancoxyz.pagos.config.ResourceServerConfig}: exige un JWT de auth-server con scope
 * {@code pagos.access}.
 *
 * <p>Codigos de respuesta: una transferencia responde {@code 202 Accepted} (la escritura es
 * asincrona; el estado se consulta despues). Un deposito o un pago de servicio responde
 * {@code 201 Created} con el pago registrado, cuyo campo {@code estado} indica el desenlace de
 * negocio (COMPLETADA o RECHAZADA, con su motivo en {@code detalle}). Una falla de
 * infraestructura responde {@code 503}.</p>
 */
@RestController
@RequestMapping("/internal")
public class PagoInternalController {

    private final ProcesadorPagos procesadorPagos;
    private final PagoService pagoService;

    public PagoInternalController(ProcesadorPagos procesadorPagos, PagoService pagoService) {
        this.procesadorPagos = procesadorPagos;
        this.pagoService = pagoService;
    }

    @PostMapping("/pagos/transferencias")
    public ResponseEntity<PagoDTO> solicitarTransferencia(@Valid @RequestBody TransferenciaRequest request) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(procesadorPagos.solicitarTransferencia(request));
    }

    @PostMapping("/pagos/depositos")
    public ResponseEntity<PagoDTO> depositar(@Valid @RequestBody DepositoRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(procesadorPagos.depositar(request));
    }

    @PostMapping("/pagos/servicios")
    public ResponseEntity<PagoDTO> pagarServicio(@Valid @RequestBody PagoServicioRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(procesadorPagos.pagarServicio(request));
    }

    @GetMapping("/pagos/{pagoId}")
    public PagoDTO obtener(@PathVariable String pagoId) {
        return pagoService.obtener(pagoId);
    }

    /** Historial de pagos de una cuenta (como origen o como destino), del mas reciente al mas antiguo. */
    @GetMapping("/pagos")
    public List<PagoDTO> historial(@RequestParam long cuentaId) {
        return pagoService.historialDeCuenta(cuentaId);
    }

    /** Estado de una transferencia. Lectura sincronica de una escritura asincrona. */
    @GetMapping("/transferencias/{transferenciaId}")
    public TransferenciaEstadoDTO estadoTransferencia(@PathVariable String transferenciaId) {
        return pagoService.obtenerTransferencia(transferenciaId);
    }
}
