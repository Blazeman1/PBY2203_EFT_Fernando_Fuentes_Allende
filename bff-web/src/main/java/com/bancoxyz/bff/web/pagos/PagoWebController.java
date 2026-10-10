package com.bancoxyz.bff.web.pagos;

import com.bancoxyz.bff.web.client.PagosServiceClient;
import com.bancoxyz.bff.web.exception.AccesoNoAutorizadoException;
import com.bancoxyz.bff.web.security.JwtAuthFilter;
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

import java.util.List;
import java.util.Optional;

/**
 * Pagos de servicios e historial de pagos del canal web (via pagos-service). Funcionalidad
 * propia de una interfaz de escritorio: bff-mobile y bff-atm, deliberadamente, no la ofrecen.
 */
@RestController
@RequestMapping("/api/web/cuentas/{cuentaId}/pagos")
public class PagoWebController {

    private final PagosServiceClient pagosServiceClient;

    public PagoWebController(PagosServiceClient pagosServiceClient) {
        this.pagosServiceClient = pagosServiceClient;
    }

    /** Paga un servicio con cargo a la cuenta. El campo {@code estado} de la respuesta indica el desenlace. */
    @PostMapping
    public ResponseEntity<PagoWebResponse> pagarServicio(@PathVariable long cuentaId,
                                                         @Valid @RequestBody PagoServicioWebRequest request,
                                                         HttpServletRequest httpRequest) {
        verificarPropietario(cuentaId, httpRequest);
        PagoWebResponse pago = PagoWebResponse.desde(
                pagosServiceClient.pagarServicio(cuentaId, request.monto(), request.comercio()));
        return ResponseEntity.status(HttpStatus.CREATED).body(pago);
    }

    @GetMapping
    public List<PagoWebResponse> historial(@PathVariable long cuentaId, HttpServletRequest httpRequest) {
        verificarPropietario(cuentaId, httpRequest);
        return pagosServiceClient.historial(cuentaId).stream().map(PagoWebResponse::desde).toList();
    }

    private void verificarPropietario(long cuentaId, HttpServletRequest request) {
        long cuentaAutenticada = (long) Optional.ofNullable(request.getAttribute(JwtAuthFilter.ATRIBUTO_CUENTA_ID))
                .orElseThrow(() -> new AccesoNoAutorizadoException("No hay una sesion valida."));
        if (cuentaAutenticada != cuentaId) {
            throw new AccesoNoAutorizadoException("Su sesion no tiene acceso a la cuenta " + cuentaId + ".");
        }
    }
}
