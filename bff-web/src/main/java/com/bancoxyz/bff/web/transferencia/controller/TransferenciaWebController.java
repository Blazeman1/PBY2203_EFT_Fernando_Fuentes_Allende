package com.bancoxyz.bff.web.transferencia.controller;

import com.bancoxyz.bff.web.client.PagoInternoDTO;
import com.bancoxyz.bff.web.client.PagosServiceClient;
import com.bancoxyz.bff.web.exception.AccesoNoAutorizadoException;
import com.bancoxyz.bff.web.security.JwtAuthFilter;
import com.bancoxyz.bff.web.transferencia.dto.TransferenciaEstadoDTO;
import com.bancoxyz.bff.web.transferencia.dto.TransferenciaWebRequest;
import com.bancoxyz.bff.web.transferencia.dto.TransferenciaWebResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

/**
 * Transferencia entre cuentas desde el canal web. La escritura es asincrona: este endpoint
 * responde {@code 202 Accepted} apenas pagos-service registra la solicitud e inicia la saga
 * (coreografia por eventos Kafka entre pagos-service y cuentas-service); el desenlace se
 * consulta despues con {@code GET /api/web/transferencias/{id}}, que si es sincronico.
 */
@RestController
@RequestMapping("/api/web")
public class TransferenciaWebController {

    private final PagosServiceClient pagosServiceClient;

    public TransferenciaWebController(PagosServiceClient pagosServiceClient) {
        this.pagosServiceClient = pagosServiceClient;
    }

    @PostMapping("/cuentas/{cuentaId}/transferencias")
    public ResponseEntity<TransferenciaWebResponse> solicitarTransferencia(
            @PathVariable long cuentaId,
            @Valid @RequestBody TransferenciaWebRequest request,
            HttpServletRequest httpRequest) {
        // La cuenta ORIGEN se toma de la URL, ya verificada contra el JWT: un cliente autenticado
        // solo puede transferir DESDE su propia cuenta.
        verificarPropietario(cuentaId, httpRequest);
        if (request.cuentaDestino() == cuentaId) {
            throw new AccesoNoAutorizadoException("La cuenta destino debe ser distinta de la cuenta origen.");
        }
        PagoInternoDTO pago = pagosServiceClient.solicitarTransferencia(cuentaId, request.cuentaDestino(), request.monto());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(TransferenciaWebResponse.aceptada(pago.pagoId()));
    }

    @GetMapping("/transferencias/{transferenciaId}")
    public TransferenciaEstadoDTO consultarEstado(@PathVariable String transferenciaId, HttpServletRequest httpRequest) {
        long cuentaAutenticada = cuentaDelToken(httpRequest);
        TransferenciaEstadoDTO estado = pagosServiceClient.obtenerEstadoTransferencia(transferenciaId);
        if (estado.cuentaOrigen() != cuentaAutenticada) {
            throw new AccesoNoAutorizadoException("Su sesion no tiene acceso a esta transferencia.");
        }
        return estado;
    }

    private void verificarPropietario(long cuentaId, HttpServletRequest request) {
        if (cuentaDelToken(request) != cuentaId) {
            throw new AccesoNoAutorizadoException("Su sesion no tiene acceso a la cuenta " + cuentaId + ".");
        }
    }

    private long cuentaDelToken(HttpServletRequest request) {
        return (long) Optional.ofNullable(request.getAttribute(JwtAuthFilter.ATRIBUTO_CUENTA_ID))
                .orElseThrow(() -> new AccesoNoAutorizadoException("No hay una sesion valida."));
    }
}
