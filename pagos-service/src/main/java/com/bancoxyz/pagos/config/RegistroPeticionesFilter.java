package com.bancoxyz.pagos.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Registro de auditoria de cada peticion atendida por este microservicio: metodo, ruta, codigo
 * de respuesta, duracion y QUIEN la hizo (el {@code client_id} OAuth2, tomado del subject del
 * JWT). Cubre el requerimiento de monitorizar y registrar las operaciones de los microservicios.
 *
 * <p>Como cada replica escribe en su propio log, estas lineas son tambien la evidencia directa
 * del balanceo de carga: al escalar el servicio se ve como las peticiones se reparten entre los
 * contenedores. En AWS, el mismo log llega a CloudWatch Logs sin cambiar el codigo.</p>
 *
 * <p>Se ejecuta DESPUES de la cadena de Spring Security, por lo que solo registra peticiones ya
 * autenticadas; los rechazos 401/403 los registra el propio Spring Security.</p>
 */
@Component
public class RegistroPeticionesFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RegistroPeticionesFilter.class);

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // El healthcheck de docker-compose consulta /actuator/health cada 5 s: no aporta al registro.
        return request.getRequestURI().startsWith("/actuator");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        long inicio = System.currentTimeMillis();
        try {
            filterChain.doFilter(request, response);
        } finally {
            Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
            String cliente = autenticacion == null ? "anonimo" : autenticacion.getName();
            log.info("PETICION {} {} -> HTTP {} (cliente={}, {} ms)", request.getMethod(), request.getRequestURI(),
                    response.getStatus(), cliente, System.currentTimeMillis() - inicio);
        }
    }
}
