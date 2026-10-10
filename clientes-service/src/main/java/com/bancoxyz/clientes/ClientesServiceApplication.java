package com.bancoxyz.clientes;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Microservicio de <b>Gestion de Clientes</b> del Banco XYZ (EFT, Parte 3): administra la
 * informacion personal y el perfil de cada cliente (alta, consulta, actualizacion de datos de
 * contacto, desactivacion y reactivacion).
 *
 * <p>Es dueno exclusivo de los datos personales: cuentas-service solo guarda el
 * {@code clienteId}, nunca el nombre ni la edad. Separar "quien es el cliente" de "que cuentas
 * tiene" permite proteger y evolucionar los datos personales de forma independiente del resto
 * del sistema (por ejemplo, exigir un scope OAuth2 propio, {@code clientes.access}, que
 * bff-mobile y bff-atm no poseen).</p>
 */
@SpringBootApplication
public class ClientesServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ClientesServiceApplication.class, args);
    }
}
