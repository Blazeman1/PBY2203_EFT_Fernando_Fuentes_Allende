package com.bancoxyz.bff.auth.config;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.authorization.client.InMemoryRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configurers.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.security.web.SecurityFilterChain;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;

/**
 * Configuracion del servidor de autorizacion OAuth2.0 (Spring Authorization Server): pieza
 * central de la <b>seguridad distribuida</b> del ecosistema.
 *
 * <p>Alcance: autenticacion <b>servicio-a-servicio</b>. El unico flujo habilitado es
 * {@code client_credentials}, pensado exactamente para comunicacion maquina-a-maquina sin un
 * usuario humano de por medio (el login de cada canal lo resuelve su propio BFF). Por eso este
 * servidor no define un {@code UserDetailsService} ni una pagina de login: solo expone los
 * endpoints estandar ({@code /oauth2/token}, {@code /oauth2/jwks},
 * {@code /.well-known/oauth-authorization-server}).</p>
 *
 * <p><b>Minimo privilegio por scopes.</b> Hay un scope por microservicio protegido
 * ({@code cuentas.access}, {@code clientes.access}, {@code pagos.access}) y cada cliente
 * registrado recibe solo los que su funcion requiere:</p>
 * <pre>
 *   bff-web-client          cuentas.access  clientes.access  pagos.access   (canal completo)
 *   bff-mobile-client       cuentas.access                                  (solo consulta)
 *   bff-atm-client          cuentas.access                   pagos.access   (retiros y depositos)
 *   pagos-service-client    cuentas.access                                  (mueve saldos)
 *   cuentas-service-client                  clientes.access                 (valida al titular)
 * </pre>
 * <p>Si las credenciales de un canal se filtran, el dano queda acotado a los scopes de ese
 * canal, y se pueden rotar o revocar sin afectar a los demas.</p>
 */
@Configuration
public class AuthorizationServerConfig {

    public static final String SCOPE_CUENTAS = "cuentas.access";
    public static final String SCOPE_CLIENTES = "clientes.access";
    public static final String SCOPE_PAGOS = "pagos.access";

    @Value("${auth-server.issuer-uri}")
    private String issuerUri;

    @Value("${auth-server.clientes.bff-web.secreto}")
    private String secretoBffWeb;

    @Value("${auth-server.clientes.bff-mobile.secreto}")
    private String secretoBffMobile;

    @Value("${auth-server.clientes.bff-atm.secreto}")
    private String secretoBffAtm;
    @Value("${auth-server.clientes.pagos-service.secreto}")
    private String secretoPagosService;
    @Value("${auth-server.clientes.cuentas-service.secreto}")
    private String secretoCuentasService;

    /**
     * Cadena de seguridad especifica de los endpoints OAuth2 (la que provee
     * {@link OAuth2AuthorizationServerConfigurer}), con precedencia sobre la cadena por defecto
     * de mas abajo. La autenticacion del cliente (Basic Auth con client_id/client_secret) la
     * resuelve el propio {@code authorizationServerConfigurer} antes de llegar a
     * {@code authorizeHttpRequests}; por eso "anyRequest().authenticated()" ya alcanza aqui.
     */
    @Bean
    @Order(1)
    public SecurityFilterChain authorizationServerSecurityFilterChain(HttpSecurity http) throws Exception {
        // Se instancia con "new" (no con el metodo estatico OAuth2AuthorizationServerConfigurer
        // .authorizationServer(), agregado recien en una version mas nueva de la libreria que la
        // que gestiona spring-boot-starter-parent:3.3.4): el constructor publico sin argumentos
        // existe desde versiones mucho mas antiguas y produce exactamente el mismo configurer.
        OAuth2AuthorizationServerConfigurer authorizationServerConfigurer =
                new OAuth2AuthorizationServerConfigurer();

        http.securityMatcher(authorizationServerConfigurer.getEndpointsMatcher())
                .with(authorizationServerConfigurer, Customizer.withDefaults())
                .authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated())
                .csrf(csrf -> csrf.ignoringRequestMatchers(authorizationServerConfigurer.getEndpointsMatcher()));

        return http.build();
    }

    /**
     * Cadena de seguridad para todo lo que NO es un endpoint OAuth2 (en la practica, solo
     * {@code /actuator/health}, usado por scripts/generar_evidencia.sh para verificar que este
     * servicio arranco correctamente).
     */
    @Bean
    @Order(2)
    public SecurityFilterChain defaultSecurityFilterChain(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/actuator/health").permitAll()
                        .anyRequest().authenticated())
                .csrf(csrf -> csrf.disable());
        return http.build();
    }

    /**
     * Clientes OAuth2 registrados: los 3 BFF y los 2 microservicios que llaman a otro
     * microservicio. Cada uno con su propio client_id/client_secret y su propio conjunto de
     * scopes (ver la tabla del Javadoc de la clase). El subject del JWT es el client_id, lo que
     * deja trazabilidad de que componente origino cada llamada.
     */
    @Bean
    public RegisteredClientRepository registeredClientRepository(PasswordEncoder passwordEncoder) {
        return new InMemoryRegisteredClientRepository(
                clienteServicioAServicio("bff-web-client", secretoBffWeb, passwordEncoder,
                        SCOPE_CUENTAS, SCOPE_CLIENTES, SCOPE_PAGOS),
                clienteServicioAServicio("bff-mobile-client", secretoBffMobile, passwordEncoder,
                        SCOPE_CUENTAS),
                clienteServicioAServicio("bff-atm-client", secretoBffAtm, passwordEncoder,
                        SCOPE_CUENTAS, SCOPE_PAGOS),
                clienteServicioAServicio("pagos-service-client", secretoPagosService, passwordEncoder,
                        SCOPE_CUENTAS),
                clienteServicioAServicio("cuentas-service-client", secretoCuentasService, passwordEncoder,
                        SCOPE_CLIENTES));
    }

    private RegisteredClient clienteServicioAServicio(String clientId, String secreto, PasswordEncoder passwordEncoder,
                                                      String... scopes) {
        return RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId(clientId)
                .clientSecret(passwordEncoder.encode(secreto))
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .scopes(registrados -> registrados.addAll(Set.of(scopes)))
                // 10 minutos: suficientemente corto para acotar la vida util de un token robado,
                // suficientemente largo para no forzar una renovacion en cada peticion (cada
                // cliente cachea su token hasta 10 s antes de la expiracion).
                .tokenSettings(TokenSettings.builder()
                        .accessTokenTimeToLive(Duration.ofMinutes(10))
                        .build())
                .build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Par de claves RSA generado en memoria al arrancar (se pierde y se regenera en cada
     * reinicio del proceso). Suficiente para el alcance academico: en un despliegue real, esta
     * clave viviria en un almacen persistente (HSM, KMS) para que los tokens ya emitidos sigan
     * siendo validables tras un reinicio del auth-server.
     */
    @Bean
    public JWKSource<SecurityContext> jwkSource() {
        KeyPair keyPair = generarParDeClaves();
        RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();
        RSAPrivateKey privateKey = (RSAPrivateKey) keyPair.getPrivate();
        RSAKey rsaKey = new RSAKey.Builder(publicKey)
                .privateKey(privateKey)
                .keyID(UUID.randomUUID().toString())
                .build();
        JWKSet jwkSet = new JWKSet(rsaKey);
        return new ImmutableJWKSet<>(jwkSet);
    }

    private static KeyPair generarParDeClaves() {
        try {
            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
            keyPairGenerator.initialize(2048);
            return keyPairGenerator.generateKeyPair();
        } catch (Exception ex) {
            throw new IllegalStateException("No se pudo generar el par de claves RSA del auth-server.", ex);
        }
    }

    @Bean
    public JwtDecoder jwtDecoder(JWKSource<SecurityContext> jwkSource) {
        return OAuth2AuthorizationServerConfiguration.jwtDecoder(jwkSource);
    }

    /**
     * El "issuer" es el identificador publico de este servidor, incrustado en cada token emitido
     * (claim {@code iss}) y usado por cada microservicio (Resource Server) para descubrir
     * automaticamente el endpoint de JWK Set (via {@code /.well-known/oauth-authorization-server})
     * y para validar que el token efectivamente proviene de este auth-server. Se resuelve por
     * configuracion (localhost en ejecucion local, nombre de contenedor en docker-compose) en
     * vez de hardcodearse - ver application.yml y application-docker.yml.
     */
    @Bean
    public AuthorizationServerSettings authorizationServerSettings() {
        return AuthorizationServerSettings.builder()
                .issuer(issuerUri)
                .build();
    }
}
