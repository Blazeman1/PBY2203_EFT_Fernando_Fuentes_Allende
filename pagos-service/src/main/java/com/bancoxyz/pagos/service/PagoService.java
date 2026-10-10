package com.bancoxyz.pagos.service;

import com.bancoxyz.pagos.dto.PagoDTO;
import com.bancoxyz.pagos.dto.TransferenciaEstadoDTO;
import com.bancoxyz.pagos.exception.PagoNoEncontradoException;
import com.bancoxyz.pagos.model.Pago;
import com.bancoxyz.pagos.repository.PagoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistencia del registro de pagos. Cada metodo es una transaccion local CORTA: deliberadamente
 * ninguna de ellas abarca una llamada de red (REST o Kafka). Quien coordina el flujo completo de
 * cada operacion es {@link ProcesadorPagos}, que invoca estos metodos antes y despues de hablar
 * con otros sistemas, sin retener una conexion de base de datos mientras espera una respuesta.
 */
@Service
public class PagoService {

    private static final Logger log = LoggerFactory.getLogger(PagoService.class);

    private final PagoRepository pagoRepository;

    public PagoService(PagoRepository pagoRepository) {
        this.pagoRepository = pagoRepository;
    }

    @Transactional
    public PagoDTO registrar(String tipo, Long cuentaOrigen, Long cuentaDestino, double monto, String glosa) {
        Pago pago = pagoRepository.save(new Pago(UUID.randomUUID().toString(), tipo, cuentaOrigen, cuentaDestino, monto, glosa));
        return PagoDTO.desde(pago);
    }

    @Transactional
    public PagoDTO cambiarEstado(String pagoId, String estado, String detalle) {
        Pago pago = pagoRepository.findById(pagoId).orElseThrow(() -> new PagoNoEncontradoException(pagoId));
        pago.cambiarEstado(estado, detalle);
        log.info("Pago {} ({}): estado -> {}{}", pagoId, pago.getTipo(), estado, detalle == null ? "" : " (" + detalle + ")");
        return PagoDTO.desde(pago);
    }

    /**
     * Aplica el resultado final de la saga. Es idempotente: si Kafka entrega el mismo resultado
     * dos veces, la segunda encuentra el pago ya en su estado final y no cambia nada.
     */
    @Transactional
    public void aplicarResultadoTransferencia(String transferenciaId, String estado, String detalle) {
        Optional<Pago> pagoOpt = pagoRepository.findById(transferenciaId);
        if (pagoOpt.isEmpty()) {
            log.warn("Resultado de transferencia {} recibido, pero no existe un pago con ese id; se ignora.", transferenciaId);
            return;
        }
        Pago pago = pagoOpt.get();
        // FALLIDA tambien admite el resultado: si la confirmacion del broker tardo mas que la
        // espera de pagos-service, el pago quedo FALLIDA pero el evento pudo salir igual; lo que
        // informa la saga (lo que realmente ocurrio con el dinero) es lo que debe prevalecer.
        if (!pago.estaEnProceso() && !Pago.FALLIDA.equals(pago.getEstado())) {
            log.info("Transferencia {}: resultado {} recibido, pero el pago ya estaba en estado {}; no se modifica.",
                    transferenciaId, estado, pago.getEstado());
            return;
        }
        pago.cambiarEstado(estado, detalle);
        log.info("Transferencia {}: estado final -> {}", transferenciaId, estado);
    }

    @Transactional(readOnly = true)
    public PagoDTO obtener(String pagoId) {
        return pagoRepository.findById(pagoId).map(PagoDTO::desde).orElseThrow(() -> new PagoNoEncontradoException(pagoId));
    }

    @Transactional(readOnly = true)
    public TransferenciaEstadoDTO obtenerTransferencia(String transferenciaId) {
        return pagoRepository.findById(transferenciaId)
                .filter(pago -> Pago.TIPO_TRANSFERENCIA.equals(pago.getTipo()))
                .map(TransferenciaEstadoDTO::desde)
                .orElseThrow(() -> new PagoNoEncontradoException(transferenciaId));
    }

    @Transactional(readOnly = true)
    public List<PagoDTO> historialDeCuenta(long cuentaId) {
        return pagoRepository.historialDeCuenta(cuentaId).stream().map(PagoDTO::desde).toList();
    }
}
