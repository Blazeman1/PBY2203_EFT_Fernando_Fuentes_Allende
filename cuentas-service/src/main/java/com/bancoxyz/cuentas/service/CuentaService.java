package com.bancoxyz.cuentas.service;

import com.bancoxyz.cuentas.dto.ActualizarSaldoRequest;
import com.bancoxyz.cuentas.dto.AperturaCuentaRequest;
import com.bancoxyz.cuentas.dto.CuentaInternalDTO;
import com.bancoxyz.cuentas.dto.MantenimientoCuentaRequest;
import com.bancoxyz.cuentas.exception.CuentaNoEncontradaException;
import com.bancoxyz.cuentas.exception.OperacionNoPermitidaException;
import com.bancoxyz.cuentas.exception.SaldoInsuficienteException;
import com.bancoxyz.cuentas.model.Cuenta;
import com.bancoxyz.cuentas.model.Movimiento;
import com.bancoxyz.cuentas.model.OperacionProcesada;
import com.bancoxyz.cuentas.repository.CuentaRepository;
import com.bancoxyz.cuentas.repository.MovimientoRepository;
import com.bancoxyz.cuentas.repository.OperacionProcesadaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Reglas de negocio de Gestion de Cuentas: apertura, cierre, mantenimiento y movimientos de
 * saldo por REST. Cada metodo publico es una transaccion local de base de datos.
 */
@Service
public class CuentaService {

    private static final Logger log = LoggerFactory.getLogger(CuentaService.class);

    /** "ahorro" y "prestamo" vienen del legacy; "corriente" se agrega como producto del sistema nuevo. */
    public static final Set<String> TIPOS_SOPORTADOS = Set.of("ahorro", "prestamo", "corriente");

    private final CuentaRepository cuentaRepository;
    private final MovimientoRepository movimientoRepository;
    private final OperacionProcesadaRepository operacionRepository;
    private final JdbcTemplate jdbcTemplate;

    public CuentaService(CuentaRepository cuentaRepository, MovimientoRepository movimientoRepository,
                         OperacionProcesadaRepository operacionRepository, JdbcTemplate jdbcTemplate) {
        this.cuentaRepository = cuentaRepository;
        this.movimientoRepository = movimientoRepository;
        this.operacionRepository = operacionRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(readOnly = true)
    public List<CuentaInternalDTO> listar(Long clienteId) {
        List<Cuenta> cuentas = clienteId == null
                ? cuentaRepository.findAll()
                : cuentaRepository.findByClienteIdOrderByIdAsc(clienteId);
        return cuentas.stream().map(CuentaInternalDTO::resumen).toList();
    }

    @Transactional(readOnly = true)
    public CuentaInternalDTO obtener(long cuentaId) {
        Cuenta cuenta = cuentaRepository.findById(cuentaId)
                .orElseThrow(() -> new CuentaNoEncontradaException(cuentaId));
        return conMovimientos(cuenta);
    }

    /**
     * Apertura. La existencia del cliente ya fue verificada por el controlador contra
     * clientes-service ANTES de abrir esta transaccion, para no retener una conexion de base de
     * datos mientras se espera una llamada de red.
     */
    @Transactional
    public CuentaInternalDTO abrir(AperturaCuentaRequest request) {
        String tipo = normalizarTipo(request.tipo());
        double saldoInicial = request.saldoInicial() == null ? 0.0 : request.saldoInicial();
        Long nuevoId = jdbcTemplate.queryForObject("SELECT nextval('cuenta_id_seq')", Long.class);

        Cuenta cuenta = cuentaRepository.saveAndFlush(new Cuenta(nuevoId, request.clienteId(), tipo, saldoInicial, LocalDate.now()));
        if (saldoInicial > 0) {
            movimientoRepository.save(new Movimiento(nuevoId, LocalDate.now(), "deposito", saldoInicial, "Deposito inicial de apertura"));
        }
        log.info("Cuenta {} abierta para el cliente {} (tipo={}, saldo inicial={})", nuevoId, request.clienteId(), tipo, saldoInicial);
        return conMovimientos(cuenta);
    }

    /** Cierre: solo se permite con saldo en cero, para que nunca "desaparezca" dinero de un cliente. */
    @Transactional
    public CuentaInternalDTO cerrar(long cuentaId) {
        Cuenta cuenta = cuentaRepository.buscarParaActualizar(cuentaId)
                .orElseThrow(() -> new CuentaNoEncontradaException(cuentaId));
        if (!cuenta.estaActiva()) {
            throw new OperacionNoPermitidaException("La cuenta " + cuentaId + " ya se encuentra cerrada.");
        }
        if (cuenta.getSaldo() != 0) {
            throw new OperacionNoPermitidaException("La cuenta " + cuentaId + " tiene saldo pendiente ("
                    + cuenta.getSaldo() + "): debe quedar en cero antes de cerrarla.");
        }
        cuenta.setEstado(Cuenta.ESTADO_CERRADA);
        log.info("Cuenta {} cerrada", cuentaId);
        return conMovimientos(cuenta);
    }

    @Transactional
    public CuentaInternalDTO actualizar(long cuentaId, MantenimientoCuentaRequest request) {
        Cuenta cuenta = cuentaRepository.buscarParaActualizar(cuentaId)
                .orElseThrow(() -> new CuentaNoEncontradaException(cuentaId));
        exigirActiva(cuenta);
        cuenta.setTipo(normalizarTipo(request.tipo()));
        log.info("Cuenta {}: tipo actualizado a {}", cuentaId, cuenta.getTipo());
        return conMovimientos(cuenta);
    }

    @Transactional
    public CuentaInternalDTO debitar(long cuentaId, ActualizarSaldoRequest request) {
        Cuenta cuenta = cuentaRepository.buscarParaActualizar(cuentaId)
                .orElseThrow(() -> new CuentaNoEncontradaException(cuentaId));
        if (yaProcesada(request.idOperacion())) {
            log.info("Cuenta {}: debito con idOperacion={} ya aplicado antes; no se repite", cuentaId, request.idOperacion());
            return conMovimientos(cuenta);
        }
        exigirActiva(cuenta);
        double monto = request.monto();
        if (cuenta.getSaldo() < monto) {
            throw new SaldoInsuficienteException(cuentaId, cuenta.getSaldo(), monto);
        }
        cuenta.setSaldo(cuenta.getSaldo() - monto);
        registrar(cuentaId, valorO(request.tipoMovimiento(), "retiro"), monto,
                valorO(request.descripcion(), "Retiro"), request.idOperacion());
        log.info("Cuenta {}: debito de {} aplicado. Nuevo saldo: {}", cuentaId, monto, cuenta.getSaldo());
        return conMovimientos(cuenta);
    }

    @Transactional
    public CuentaInternalDTO acreditar(long cuentaId, ActualizarSaldoRequest request) {
        Cuenta cuenta = cuentaRepository.buscarParaActualizar(cuentaId)
                .orElseThrow(() -> new CuentaNoEncontradaException(cuentaId));
        if (yaProcesada(request.idOperacion())) {
            log.info("Cuenta {}: credito con idOperacion={} ya aplicado antes; no se repite", cuentaId, request.idOperacion());
            return conMovimientos(cuenta);
        }
        exigirActiva(cuenta);
        double monto = request.monto();
        cuenta.setSaldo(cuenta.getSaldo() + monto);
        registrar(cuentaId, valorO(request.tipoMovimiento(), "deposito"), monto,
                valorO(request.descripcion(), "Deposito"), request.idOperacion());
        log.info("Cuenta {}: credito de {} aplicado. Nuevo saldo: {}", cuentaId, monto, cuenta.getSaldo());
        return conMovimientos(cuenta);
    }

    private boolean yaProcesada(String idOperacion) {
        return idOperacion != null && !idOperacion.isBlank() && operacionRepository.existsById(idOperacion);
    }

    private void registrar(long cuentaId, String tipoMovimiento, double monto, String descripcion, String idOperacion) {
        movimientoRepository.save(new Movimiento(cuentaId, LocalDate.now(), tipoMovimiento, monto, descripcion));
        if (idOperacion != null && !idOperacion.isBlank()) {
            operacionRepository.save(new OperacionProcesada(idOperacion, OperacionProcesada.APLICADA, tipoMovimiento));
        }
    }

    private void exigirActiva(Cuenta cuenta) {
        if (!cuenta.estaActiva()) {
            throw new OperacionNoPermitidaException("La cuenta " + cuenta.getId() + " esta cerrada y no admite operaciones.");
        }
    }

    private String normalizarTipo(String tipoCrudo) {
        String tipo = tipoCrudo == null ? "" : tipoCrudo.trim().toLowerCase(Locale.ROOT);
        if (!TIPOS_SOPORTADOS.contains(tipo)) {
            throw new OperacionNoPermitidaException("Tipo de cuenta no soportado: '" + tipoCrudo
                    + "'. Valores permitidos: " + TIPOS_SOPORTADOS);
        }
        return tipo;
    }

    private static String valorO(String valor, String porDefecto) {
        return valor == null || valor.isBlank() ? porDefecto : valor.trim();
    }

    private CuentaInternalDTO conMovimientos(Cuenta cuenta) {
        return CuentaInternalDTO.desde(cuenta, movimientoRepository.findByCuentaIdOrderByIdAsc(cuenta.getId()));
    }
}
