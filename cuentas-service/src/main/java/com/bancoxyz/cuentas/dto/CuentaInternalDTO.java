package com.bancoxyz.cuentas.dto;

import com.bancoxyz.cuentas.model.Cuenta;
import com.bancoxyz.cuentas.model.Movimiento;

import java.time.LocalDate;
import java.util.List;

/**
 * Detalle interno COMPLETO de una cuenta. Cada BFF decide que subconjunto reenviar a su canal.
 *
 * <p>Ya no incluye el nombre ni la edad del titular: esos datos pertenecen a clientes-service.
 * Este servicio solo conoce el {@code clienteId}; quien necesita mostrar el nombre (bff-web) lo
 * compone consultando ambos servicios.</p>
 */
public record CuentaInternalDTO(
        long cuentaId,
        long clienteId,
        String tipo,
        String estado,
        double saldo,
        LocalDate fechaApertura,
        List<MovimientoDTO> movimientos
) {
    public static CuentaInternalDTO desde(Cuenta cuenta, List<Movimiento> movimientos) {
        return new CuentaInternalDTO(
                cuenta.getId(),
                cuenta.getClienteId(),
                cuenta.getTipo(),
                cuenta.getEstado(),
                cuenta.getSaldo(),
                cuenta.getFechaApertura(),
                movimientos.stream().map(MovimientoDTO::desde).toList()
        );
    }

    /** Variante sin historial, para listados. */
    public static CuentaInternalDTO resumen(Cuenta cuenta) {
        return desde(cuenta, List.of());
    }
}
