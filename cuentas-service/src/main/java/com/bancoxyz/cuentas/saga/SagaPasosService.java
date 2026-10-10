package com.bancoxyz.cuentas.saga;

import com.bancoxyz.cuentas.model.Cuenta;
import com.bancoxyz.cuentas.model.Movimiento;
import com.bancoxyz.cuentas.model.OperacionProcesada;
import com.bancoxyz.cuentas.repository.CuentaRepository;
import com.bancoxyz.cuentas.repository.MovimientoRepository;
import com.bancoxyz.cuentas.repository.OperacionProcesadaRepository;
import com.bancoxyz.cuentas.saga.dto.DebitoAplicadoEvent;
import com.bancoxyz.cuentas.saga.dto.TransferenciaSolicitadaEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Optional;

/**
 * Los pasos de base de datos de la saga de transferencias, cada uno como una transaccion local
 * e <b>idempotente</b>.
 *
 * <p>Vive en un bean separado de {@link TransferenciaSagaListener} por dos razones. La primera es
 * tecnica: {@code @Transactional} se aplica mediante un proxy, y solo actua cuando el metodo se
 * invoca desde OTRO bean. La segunda es de diseno: asi la transaccion se confirma ANTES de
 * publicar el siguiente evento, de modo que nunca se anuncia por Kafka un debito que la base de
 * datos todavia podria deshacer.</p>
 *
 * <p>Cada paso deja una fila en {@code operaciones_procesadas} dentro de la misma transaccion
 * que modifica el saldo. Si Kafka vuelve a entregar el mismo evento (entrega "al menos una vez":
 * un rebalanceo del grupo, una replica que cae antes de confirmar el offset), el paso encuentra
 * esa fila y devuelve el resultado ya conocido sin volver a mover dinero.</p>
 */
@Service
public class SagaPasosService {

    private static final Logger log = LoggerFactory.getLogger(SagaPasosService.class);

    private final CuentaRepository cuentaRepository;
    private final MovimientoRepository movimientoRepository;
    private final OperacionProcesadaRepository operacionRepository;

    public SagaPasosService(CuentaRepository cuentaRepository, MovimientoRepository movimientoRepository,
                            OperacionProcesadaRepository operacionRepository) {
        this.cuentaRepository = cuentaRepository;
        this.movimientoRepository = movimientoRepository;
        this.operacionRepository = operacionRepository;
    }

    /** Paso 1: debita la cuenta origen, o rechaza la transferencia si no se puede. */
    @Transactional
    public OperacionProcesada debitarOrigen(TransferenciaSolicitadaEvent evento) {
        String idPaso = evento.transferenciaId() + ":DEBITO";
        Optional<OperacionProcesada> previa = operacionRepository.findById(idPaso);
        if (previa.isPresent()) {
            log.info("Saga transferencia {}: paso DEBITO ya procesado antes ({}); no se repite",
                    evento.transferenciaId(), previa.get().getResultado());
            return previa.get();
        }

        if (evento.cuentaOrigen() == evento.cuentaDestino()) {
            return rechazar(idPaso, evento, "La cuenta origen y la cuenta destino no pueden ser la misma.");
        }
        Optional<Cuenta> origenOpt = cuentaRepository.buscarParaActualizar(evento.cuentaOrigen());
        if (origenOpt.isEmpty()) {
            return rechazar(idPaso, evento, "La cuenta origen " + evento.cuentaOrigen() + " no existe.");
        }
        Cuenta origen = origenOpt.get();
        if (!origen.estaActiva()) {
            return rechazar(idPaso, evento, "La cuenta origen " + evento.cuentaOrigen() + " esta cerrada.");
        }
        if (origen.getSaldo() < evento.monto()) {
            return rechazar(idPaso, evento, "Saldo insuficiente en la cuenta origen (saldo=" + origen.getSaldo()
                    + ", solicitado=" + evento.monto() + ").");
        }

        origen.setSaldo(origen.getSaldo() - evento.monto());
        movimientoRepository.save(new Movimiento(origen.getId(), LocalDate.now(), "transferencia_enviada",
                evento.monto(), "Transferencia a la cuenta " + evento.cuentaDestino()));
        log.info("Saga transferencia {}: debito aplicado en cuenta {} (nuevo saldo={})",
                evento.transferenciaId(), evento.cuentaOrigen(), origen.getSaldo());
        return operacionRepository.save(new OperacionProcesada(idPaso, OperacionProcesada.APLICADA, null));
    }

    /**
     * Paso 2: acredita la cuenta destino. Si no existe o esta cerrada, ejecuta la COMPENSACION:
     * devuelve a la cuenta origen el monto debitado en el paso 1, dejando el sistema como si la
     * transferencia nunca se hubiese solicitado.
     */
    @Transactional
    public OperacionProcesada acreditarDestino(DebitoAplicadoEvent evento) {
        String idPaso = evento.transferenciaId() + ":CREDITO";
        Optional<OperacionProcesada> previa = operacionRepository.findById(idPaso);
        if (previa.isPresent()) {
            log.info("Saga transferencia {}: paso CREDITO ya procesado antes ({}); no se repite",
                    evento.transferenciaId(), previa.get().getResultado());
            return previa.get();
        }

        Optional<Cuenta> destinoOpt = cuentaRepository.buscarParaActualizar(evento.cuentaDestino());
        if (destinoOpt.isPresent() && destinoOpt.get().estaActiva()) {
            Cuenta destino = destinoOpt.get();
            destino.setSaldo(destino.getSaldo() + evento.monto());
            movimientoRepository.save(new Movimiento(destino.getId(), LocalDate.now(), "transferencia_recibida",
                    evento.monto(), "Transferencia desde la cuenta " + evento.cuentaOrigen()));
            log.info("Saga transferencia {}: credito aplicado en cuenta {} (nuevo saldo={}) - TRANSFERENCIA COMPLETADA",
                    evento.transferenciaId(), evento.cuentaDestino(), destino.getSaldo());
            return operacionRepository.save(new OperacionProcesada(idPaso, OperacionProcesada.APLICADA, null));
        }

        String motivo = destinoOpt.isEmpty()
                ? "La cuenta destino " + evento.cuentaDestino() + " no existe; se revirtio el debito aplicado en la cuenta origen."
                : "La cuenta destino " + evento.cuentaDestino() + " esta cerrada; se revirtio el debito aplicado en la cuenta origen.";
        log.warn("Saga transferencia {}: COMPENSANDO - {}", evento.transferenciaId(), motivo);
        cuentaRepository.buscarParaActualizar(evento.cuentaOrigen()).ifPresent(origen -> {
            origen.setSaldo(origen.getSaldo() + evento.monto());
            movimientoRepository.save(new Movimiento(origen.getId(), LocalDate.now(), "reverso_transferencia",
                    evento.monto(), "Reverso de transferencia a la cuenta " + evento.cuentaDestino()));
            log.info("Saga transferencia {}: debito compensado en cuenta {} (saldo restaurado a {})",
                    evento.transferenciaId(), evento.cuentaOrigen(), origen.getSaldo());
        });
        return operacionRepository.save(new OperacionProcesada(idPaso, OperacionProcesada.COMPENSADA, motivo));
    }

    private OperacionProcesada rechazar(String idPaso, TransferenciaSolicitadaEvent evento, String motivo) {
        log.warn("Saga transferencia {}: RECHAZADA - {}", evento.transferenciaId(), motivo);
        return operacionRepository.save(new OperacionProcesada(idPaso, OperacionProcesada.RECHAZADA, motivo));
    }
}
