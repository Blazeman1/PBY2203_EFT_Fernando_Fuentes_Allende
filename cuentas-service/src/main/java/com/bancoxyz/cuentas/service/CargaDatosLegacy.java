package com.bancoxyz.cuentas.service;

import com.bancoxyz.cuentas.model.Cuenta;
import com.bancoxyz.cuentas.model.Movimiento;
import com.bancoxyz.cuentas.repository.CuentaRepository;
import com.bancoxyz.cuentas.repository.MovimientoRepository;
import com.bancoxyz.cuentas.util.FechaFlexibleParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Migracion inicial de los datos legacy a la base de datos de este microservicio: el maestro de
 * cuentas ({@code intereses_trimestrales.csv}) y el historial de movimientos
 * ({@code estados_financieros_anuales.csv}) del repositorio oficial {@code fin_legacy_data}.
 *
 * <p>Se ejecuta UNA sola vez, aunque arranquen varias replicas al mismo tiempo: toda la carga
 * corre dentro de una transaccion que primero toma un <i>advisory lock</i> de PostgreSQL
 * ({@code pg_advisory_xact_lock}). La primera replica que lo obtiene carga los datos; las demas
 * esperan, encuentran la tabla ya poblada y no hacen nada.</p>
 *
 * <p>Las reglas de validacion son las mismas del modulo batch (Parte 1): tipo de cuenta no
 * soportado, edad fuera de rango, saldo vacio o negativo y fechas irreconocibles se omiten y se
 * contabilizan en el log. Como {@code cuenta_id} se repite en el archivo (50 identificadores
 * para 1000 filas), se aplica "upsert": la ultima fila valida de cada cuenta define su estado.</p>
 *
 * <p>El legacy no tiene un identificador de cliente separado del de la cuenta, por lo que en la
 * migracion se asigna {@code clienteId = cuentaId} (mismo criterio que clientes-service).</p>
 */
@Service
public class CargaDatosLegacy {

    private static final Logger log = LoggerFactory.getLogger(CargaDatosLegacy.class);
    private static final Set<String> TIPOS_LEGACY_SOPORTADOS = Set.of("ahorro", "prestamo");
    private static final long CLAVE_ADVISORY_LOCK = 7201L;
    private static final String ARCHIVO_CUENTAS = "data/intereses_trimestrales.csv";
    private static final String ARCHIVO_MOVIMIENTOS = "data/estados_financieros_anuales.csv";

    private final CuentaRepository cuentaRepository;
    private final MovimientoRepository movimientoRepository;
    private final JdbcTemplate jdbcTemplate;

    public CargaDatosLegacy(CuentaRepository cuentaRepository, MovimientoRepository movimientoRepository,
                            JdbcTemplate jdbcTemplate) {
        this.cuentaRepository = cuentaRepository;
        this.movimientoRepository = movimientoRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public void cargarSiEstaVacio() throws IOException {
        jdbcTemplate.execute("SELECT pg_advisory_xact_lock(" + CLAVE_ADVISORY_LOCK + ")");
        long existentes = cuentaRepository.count();
        if (existentes > 0) {
            log.info("cuentas-service: la base ya contiene {} cuentas; se omite la migracion inicial del legacy.", existentes);
            return;
        }

        int[] omitidas = new int[1];
        Map<Long, Cuenta> cuentas = leerCuentas(omitidas);
        cuentaRepository.saveAllAndFlush(cuentas.values());

        int[] movimientosOmitidos = new int[1];
        List<Movimiento> movimientos = leerMovimientos(cuentas.keySet(), movimientosOmitidos);
        movimientoRepository.saveAll(movimientos);

        log.info("############################################################");
        log.info("cuentas-service: migracion inicial del legacy completada.");
        log.info("Cuentas validas cargadas: {} (filas omitidas por datos invalidos: {})", cuentas.size(), omitidas[0]);
        log.info("Movimientos validos cargados: {} (filas omitidas por datos invalidos: {})", movimientos.size(), movimientosOmitidos[0]);
        log.info("############################################################");
    }

    private Map<Long, Cuenta> leerCuentas(int[] omitidas) throws IOException {
        Map<Long, Cuenta> cuentas = new LinkedHashMap<>();
        for (String linea : leerLineas(ARCHIVO_CUENTAS)) {
            String[] campos = linea.split(",", -1);
            Optional<Cuenta> cuenta = campos.length < 5 ? Optional.empty() : validarCuenta(campos);
            if (cuenta.isPresent()) {
                cuentas.put(cuenta.get().getId(), cuenta.get());
            } else {
                omitidas[0]++;
            }
        }
        return cuentas;
    }

    private Optional<Cuenta> validarCuenta(String[] campos) {
        try {
            long cuentaId = Long.parseLong(campos[0].trim());
            String nombre = campos[1].trim();
            String saldoTexto = campos[2].trim();
            String edadTexto = campos[3].trim();
            String tipo = campos[4].trim().toLowerCase(Locale.ROOT);

            if (nombre.isBlank() || nombre.equalsIgnoreCase("unknown")) {
                return Optional.empty();
            }
            if (!TIPOS_LEGACY_SOPORTADOS.contains(tipo) || edadTexto.isBlank() || saldoTexto.isBlank()) {
                return Optional.empty();
            }
            int edad = Integer.parseInt(edadTexto);
            if (edad < 18 || edad > 90) {
                return Optional.empty();
            }
            double saldo = Double.parseDouble(saldoTexto);
            if (saldo < 0) {
                return Optional.empty();
            }
            return Optional.of(new Cuenta(cuentaId, cuentaId, tipo, saldo, null));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private List<Movimiento> leerMovimientos(Set<Long> cuentasValidas, int[] omitidos) throws IOException {
        List<Movimiento> movimientos = new ArrayList<>();
        for (String linea : leerLineas(ARCHIVO_MOVIMIENTOS)) {
            String[] campos = linea.split(",", -1);
            Optional<Movimiento> movimiento = campos.length < 5 ? Optional.empty() : validarMovimiento(campos);
            // Un movimiento "huerfano" (cuenta sin maestro valido) no puede exponerse de forma consistente.
            if (movimiento.isPresent() && cuentasValidas.contains(movimiento.get().getCuentaId())) {
                movimientos.add(movimiento.get());
            } else {
                omitidos[0]++;
            }
        }
        return movimientos;
    }

    private Optional<Movimiento> validarMovimiento(String[] campos) {
        try {
            long cuentaId = Long.parseLong(campos[0].trim());
            Optional<LocalDate> fecha = FechaFlexibleParser.parsear(campos[1]);
            String tipoMovimiento = sinAcentos(campos[2].trim().toLowerCase(Locale.ROOT));
            String montoTexto = campos[3].trim();
            if (fecha.isEmpty() || tipoMovimiento.isBlank() || montoTexto.isBlank()) {
                return Optional.empty();
            }
            double monto = Double.parseDouble(montoTexto);
            if (monto == 0) {
                return Optional.empty();
            }
            String descripcion = campos[4].isBlank() ? "Movimiento sin descripcion registrada" : campos[4].trim();
            return Optional.of(new Movimiento(cuentaId, fecha.get(), tipoMovimiento, monto, descripcion));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    /** El legacy mezcla "deposito" y "depósito" para el mismo concepto: se unifican en la migracion. */
    private static String sinAcentos(String texto) {
        return Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }

    private List<String> leerLineas(String recurso) throws IOException {
        try (BufferedReader lector = new BufferedReader(new InputStreamReader(
                new ClassPathResource(recurso).getInputStream(), StandardCharsets.UTF_8))) {
            return lector.lines().skip(1).filter(linea -> !linea.isBlank()).toList();
        }
    }
}
