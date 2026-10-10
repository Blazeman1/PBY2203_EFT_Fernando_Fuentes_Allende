package com.bancoxyz.clientes.service;

import com.bancoxyz.clientes.model.Cliente;
import com.bancoxyz.clientes.repository.ClienteRepository;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Migracion inicial de los titulares del legacy ({@code intereses_trimestrales.csv}, repositorio
 * oficial {@code fin_legacy_data}) a la base de datos de este microservicio.
 *
 * <p>Se ejecuta una sola vez aunque arranquen varias replicas a la vez: la carga completa corre
 * dentro de una transaccion que primero toma un <i>advisory lock</i> de PostgreSQL; las replicas
 * que llegan despues encuentran la tabla ya poblada y no hacen nada.</p>
 *
 * <p>El legacy no distingue cliente de cuenta: cada fila trae el nombre y la edad del titular
 * junto al numero de cuenta, y un mismo nombre aparece con edades distintas en cuentas
 * distintas, por lo que el nombre no sirve como identificador. En la migracion se crea un
 * cliente por cada cuenta valida, con {@code clienteId = cuentaId}. Se aplican exactamente los
 * mismos filtros que cuentas-service y el modulo batch, de modo que los tres parten del mismo
 * universo de registros validos.</p>
 */
@Service
public class CargaDatosLegacy {

    private static final Logger log = LoggerFactory.getLogger(CargaDatosLegacy.class);
    private static final Set<String> TIPOS_LEGACY_SOPORTADOS = Set.of("ahorro", "prestamo");
    private static final long CLAVE_ADVISORY_LOCK = 7101L;
    private static final String ARCHIVO = "data/intereses_trimestrales.csv";

    private final ClienteRepository clienteRepository;
    private final JdbcTemplate jdbcTemplate;

    public CargaDatosLegacy(ClienteRepository clienteRepository, JdbcTemplate jdbcTemplate) {
        this.clienteRepository = clienteRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public void cargarSiEstaVacio() throws IOException {
        jdbcTemplate.execute("SELECT pg_advisory_xact_lock(" + CLAVE_ADVISORY_LOCK + ")");
        long existentes = clienteRepository.count();
        if (existentes > 0) {
            log.info("clientes-service: la base ya contiene {} clientes; se omite la migracion inicial del legacy.", existentes);
            return;
        }

        Map<Long, Cliente> clientes = new LinkedHashMap<>();
        int omitidas = 0;
        for (String linea : leerLineas()) {
            String[] campos = linea.split(",", -1);
            Optional<Cliente> cliente = campos.length < 5 ? Optional.empty() : validar(campos);
            if (cliente.isPresent()) {
                clientes.put(cliente.get().getId(), cliente.get());
            } else {
                omitidas++;
            }
        }
        clienteRepository.saveAll(clientes.values());

        log.info("############################################################");
        log.info("clientes-service: migracion inicial del legacy completada.");
        log.info("Clientes validos cargados: {} (filas omitidas por datos invalidos: {})", clientes.size(), omitidas);
        log.info("############################################################");
    }

    private Optional<Cliente> validar(String[] campos) {
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
            if (Double.parseDouble(saldoTexto) < 0) {
                return Optional.empty();
            }
            // El legacy no trae datos de contacto: se genera un correo ficticio de demostracion.
            String email = nombre.toLowerCase(Locale.ROOT).replace(' ', '.') + "." + cuentaId + "@clientes.bancoxyz.example";
            return Optional.of(new Cliente(cuentaId, nombre, edad, email, null, null));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private List<String> leerLineas() throws IOException {
        try (BufferedReader lector = new BufferedReader(new InputStreamReader(
                new ClassPathResource(ARCHIVO).getInputStream(), StandardCharsets.UTF_8))) {
            return lector.lines().skip(1).filter(linea -> !linea.isBlank()).toList();
        }
    }
}
