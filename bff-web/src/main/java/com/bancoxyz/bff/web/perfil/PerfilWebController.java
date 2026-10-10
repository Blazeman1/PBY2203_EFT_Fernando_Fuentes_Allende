package com.bancoxyz.bff.web.perfil;

import com.bancoxyz.bff.web.client.ClientesServiceClient;
import com.bancoxyz.bff.web.client.CuentaInternaDTO;
import com.bancoxyz.bff.web.client.CuentasServiceClient;
import com.bancoxyz.bff.web.exception.AccesoNoAutorizadoException;
import com.bancoxyz.bff.web.security.JwtAuthFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

/**
 * Perfil del titular de la cuenta (via clientes-service): consulta y actualizacion de datos de
 * contacto. El cliente nunca indica un {@code clienteId}: se deriva de SU cuenta autenticada,
 * de modo que es imposible consultar o modificar el perfil de otra persona.
 */
@RestController
@RequestMapping("/api/web/cuentas/{cuentaId}/titular")
public class PerfilWebController {

    private final CuentasServiceClient cuentasServiceClient;
    private final ClientesServiceClient clientesServiceClient;

    public PerfilWebController(CuentasServiceClient cuentasServiceClient, ClientesServiceClient clientesServiceClient) {
        this.cuentasServiceClient = cuentasServiceClient;
        this.clientesServiceClient = clientesServiceClient;
    }

    @GetMapping
    public PerfilWebResponse obtener(@PathVariable long cuentaId, HttpServletRequest httpRequest) {
        long clienteId = clienteDeLaCuenta(cuentaId, httpRequest);
        return PerfilWebResponse.desde(clientesServiceClient.obtenerCliente(clienteId));
    }

    @PutMapping
    public PerfilWebResponse actualizarContacto(@PathVariable long cuentaId, @Valid @RequestBody ContactoWebRequest request,
                                                HttpServletRequest httpRequest) {
        long clienteId = clienteDeLaCuenta(cuentaId, httpRequest);
        return PerfilWebResponse.desde(clientesServiceClient.actualizarContacto(
                clienteId, request.email(), request.telefono(), request.direccion()));
    }

    private long clienteDeLaCuenta(long cuentaId, HttpServletRequest request) {
        long cuentaAutenticada = (long) Optional.ofNullable(request.getAttribute(JwtAuthFilter.ATRIBUTO_CUENTA_ID))
                .orElseThrow(() -> new AccesoNoAutorizadoException("No hay una sesion valida."));
        if (cuentaAutenticada != cuentaId) {
            throw new AccesoNoAutorizadoException("Su sesion no tiene acceso a la cuenta " + cuentaId + ".");
        }
        CuentaInternaDTO cuenta = cuentasServiceClient.obtenerCuenta(cuentaId);
        return cuenta.clienteId();
    }
}
