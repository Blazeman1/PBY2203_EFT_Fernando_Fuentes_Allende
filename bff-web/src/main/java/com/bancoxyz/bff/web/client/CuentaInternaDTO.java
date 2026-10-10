package com.bancoxyz.bff.web.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Espejo local del detalle de cuenta que expone cuentas-service. Copia propia (no un JAR de DTOs
 * compartido) de forma deliberada: cada BFF se acopla solo al contrato HTTP/JSON del
 * microservicio, nunca a codigo Java compartido, y puede evolucionar sin coordinar despliegues.
 *
 * <p>Notese que ya no trae el nombre ni la edad del titular: esos datos pertenecen a
 * clientes-service y este BFF los compone a partir de {@code clienteId}.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CuentaInternaDTO(
        long cuentaId,
        long clienteId,
        String tipo,
        String estado,
        double saldo,
        List<MovimientoInternoDTO> movimientos
) {
}
