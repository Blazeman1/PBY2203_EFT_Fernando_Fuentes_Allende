package com.bancoxyz.bff.atm.controller;

import com.bancoxyz.bff.atm.client.CuentasServiceClient;
import com.bancoxyz.bff.atm.client.CuentaInternaDTO;
import com.bancoxyz.bff.atm.client.PagoInternoDTO;
import com.bancoxyz.bff.atm.client.PagosServiceClient;
import com.bancoxyz.bff.atm.dto.DepositoRequest;
import com.bancoxyz.bff.atm.dto.DepositoResponse;
import com.bancoxyz.bff.atm.dto.RetiroRequest;
import com.bancoxyz.bff.atm.dto.RetiroResponse;
import com.bancoxyz.bff.atm.dto.SaldoResponse;
import com.bancoxyz.bff.atm.exception.DepositoRechazadoException;
import com.bancoxyz.bff.atm.exception.LimiteRetiroExcedidoException;
import com.bancoxyz.bff.atm.exception.SesionInvalidaException;
import com.bancoxyz.bff.atm.security.SesionAtmFilter;
import com.bancoxyz.bff.atm.security.SesionAtmService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

/**
 * Las UNICAS operaciones que este canal ofrece: las operaciones criticas de un cajero
 * automatico (consulta de saldo, retiro y deposito). No existe, deliberadamente, ningun endpoint
 * de historial de movimientos ni de datos personales en este modulo.
 *
 * <p>El retiro debita directamente en cuentas-service (fuente de verdad del saldo); el deposito
 * se procesa a traves de pagos-service, que deja registro de la operacion y la acredita.</p>
 */
@RestController
@RequestMapping("/api/atm/cuentas")
public class CuentaAtmController {

    private static final Logger log = LoggerFactory.getLogger(CuentaAtmController.class);

    /** Limite de seguridad propio del canal cajero: ningun retiro individual puede superar este monto. */
    private static final double LIMITE_MAXIMO_RETIRO = 500_000.0;

    private final CuentasServiceClient cuentasServiceClient;
    private final PagosServiceClient pagosServiceClient;
    private final SesionAtmService sesionAtmService;

    public CuentaAtmController(CuentasServiceClient cuentasServiceClient, PagosServiceClient pagosServiceClient,
                               SesionAtmService sesionAtmService) {
        this.cuentasServiceClient = cuentasServiceClient;
        this.pagosServiceClient = pagosServiceClient;
        this.sesionAtmService = sesionAtmService;
    }

    @GetMapping("/{cuentaId}/saldo")
    public SaldoResponse consultarSaldo(@PathVariable long cuentaId, HttpServletRequest request) {
        verificarPropietario(cuentaId, request);
        CuentaInternaDTO cuenta = cuentasServiceClient.obtenerCuenta(cuentaId);
        return new SaldoResponse(cuenta.saldo());
    }

    @PostMapping("/{cuentaId}/retiro")
    public RetiroResponse retirar(@PathVariable long cuentaId, @Valid @RequestBody RetiroRequest request,
                                   HttpServletRequest httpRequest) {
        verificarPropietario(cuentaId, httpRequest);
        if (request.monto() > LIMITE_MAXIMO_RETIRO) {
            throw new LimiteRetiroExcedidoException(LIMITE_MAXIMO_RETIRO);
        }

        CuentaInternaDTO cuentaActualizada = cuentasServiceClient.debitarSaldo(cuentaId, request.monto());
        log.info("Retiro confirmado: cuenta={} monto={} nuevoSaldo={}", cuentaId, request.monto(), cuentaActualizada.saldo());

        // Tras un retiro exitoso, la sesion se invalida de inmediato: cualquier operacion
        // adicional (otro retiro, otra consulta) exige validar tarjeta y PIN nuevamente. Ver el
        // razonamiento completo en el javadoc de SesionAtmService.
        String token = (String) httpRequest.getAttribute(SesionAtmFilter.ATRIBUTO_TOKEN_SESION);
        sesionAtmService.invalidar(token);

        return new RetiroResponse(true, request.monto(), cuentaActualizada.saldo());
    }

    /**
     * Deposito en efectivo por cajero, procesado por pagos-service. Igual que el retiro, es una
     * operacion critica: la sesion se invalida al terminar, exitosa o no.
     */
    @PostMapping("/{cuentaId}/deposito")
    public DepositoResponse depositar(@PathVariable long cuentaId, @Valid @RequestBody DepositoRequest request,
                                      HttpServletRequest httpRequest) {
        verificarPropietario(cuentaId, httpRequest);
        if (request.monto() > LIMITE_MAXIMO_RETIRO) {
            throw new LimiteRetiroExcedidoException(LIMITE_MAXIMO_RETIRO);
        }
        String token = (String) httpRequest.getAttribute(SesionAtmFilter.ATRIBUTO_TOKEN_SESION);
        try {
            PagoInternoDTO pago = pagosServiceClient.depositar(cuentaId, request.monto());
            if (!"COMPLETADA".equals(pago.estado())) {
                throw new DepositoRechazadoException("El deposito no pudo realizarse: " + pago.detalle());
            }
            log.info("Deposito confirmado: cuenta={} monto={} pagoId={}", cuentaId, request.monto(), pago.pagoId());
            return new DepositoResponse(true, request.monto(), pago.pagoId());
        } finally {
            sesionAtmService.invalidar(token);
        }
    }

    private void verificarPropietario(long cuentaId, HttpServletRequest request) {
        long cuentaAutenticada = (long) Optional.ofNullable(request.getAttribute(SesionAtmFilter.ATRIBUTO_CUENTA_ID))
                .orElseThrow(() -> new SesionInvalidaException("No hay una sesion de cajero valida."));
        if (cuentaAutenticada != cuentaId) {
            throw new SesionInvalidaException("La tarjeta validada no corresponde a la cuenta " + cuentaId + ".");
        }
    }
}
