package com.bancoxyz.bff.notificaciones.dto;

/**
 * Copia local (deserializada desde JSON) del evento final que publica la saga de transferencias
 * en cuentas-service. Se duplica el record en vez de compartir una libreria comun, siguiendo la
 * misma decision arquitectonica aplicada a los DTO REST de los BFF:
 * cada microservicio es independiente y solo conoce el CONTRATO (en este caso, el JSON del
 * evento), nunca una clase Java compartida con otro modulo.
 *
 * @param transferenciaId identificador unico de la transferencia (UUID)
 * @param cuentaOrigen    cuenta desde la que se transfirio
 * @param cuentaDestino   cuenta que debia recibir el monto
 * @param monto           monto solicitado
 * @param estado          COMPLETADA | RECHAZADA | COMPENSADA
 * @param detalle         motivo legible (solo relevante si no es COMPLETADA)
 */
public record TransferenciaResultadoEvent(
        String transferenciaId,
        long cuentaOrigen,
        long cuentaDestino,
        double monto,
        String estado,
        String detalle
) {
}
