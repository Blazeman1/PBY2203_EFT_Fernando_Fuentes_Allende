package com.bancoxyz.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Punto de entrada unico del ecosistema (Spring Cloud Gateway).
 *
 * <p>Resuelve el requerimiento de <b>enrutamiento y balanceo de carga</b>: los clientes externos
 * conocen UNA sola direccion; el gateway decide, por el prefijo de la ruta, a que BFF
 * corresponde cada peticion ({@code /api/web/**}, {@code /api/mobile/**}, {@code /api/atm/**}),
 * busca en Eureka las instancias vivas de ese BFF y reparte las peticiones entre ellas
 * ({@code lb://}). Agregar una segunda instancia de un BFF no requiere tocar a ningun cliente ni
 * al propio gateway: basta con que la instancia nueva se registre en Eureka.</p>
 *
 * <p>Todas las rutas se declaran en {@code application.yml}; esta clase no contiene logica. El
 * gateway no autentica usuarios (eso es responsabilidad especifica de cada canal, en su BFF) ni
 * conoce a los microservicios internos, que no son alcanzables desde fuera de la red privada.</p>
 */
@SpringBootApplication
public class ApiGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }
}
