package com.bancoxyz.bff.web.dto;

import com.bancoxyz.bff.web.client.ClienteInternoDTO;
import com.bancoxyz.bff.web.client.CuentaInternaDTO;
import com.bancoxyz.bff.web.client.MovimientoInternoDTO;

import java.util.List;

/**
 * Personalizacion del canal WEB (cada BFF entrega la informacion segun las necesidades de
 * su frontend): respuesta RICA y COMPLETA -titular, edad, tasa de interes
 * calculada, agregados financieros y el historial COMPLETO de movimientos-, pensada para una
 * interfaz de escritorio con tablas, filtros y graficos. Comparar directamente con
 * {@code CuentaMobileResumenResponse} (bff-mobile) y la ausencia total de historial en bff-atm.
 */
public record CuentaWebResponse(
        long cuentaId,
        String titular,
        String tipoCuenta,
        int edadTitular,
        double saldoActual,
        double tasaInteresMensual,
        double interesProyectadoMensual,
        int totalMovimientos,
        double totalDepositos,
        double totalRetiros,
        double totalCompras,
        List<MovimientoWebResponse> movimientos
) {

    private static final double TASA_AHORRO = 0.0150;
    private static final double TASA_PRESTAMO = 0.0250;

    /** Valor mostrado cuando clientes-service no esta disponible (ver {@code CuentaWebController}). */
    public static final String TITULAR_NO_DISPONIBLE = "(no disponible temporalmente)";

    /**
     * Compone la vista del canal web a partir de DOS microservicios: la cuenta (cuentas-service)
     * y su titular (clientes-service). {@code titular} puede ser {@code null} si clientes-service
     * no respondio: en ese caso se entrega la cuenta igualmente, sin los datos personales.
     */
    public static CuentaWebResponse desde(CuentaInternaDTO cuenta, ClienteInternoDTO titular) {
        double tasa = "ahorro".equals(cuenta.tipo()) ? TASA_AHORRO : TASA_PRESTAMO;
        double totalDepositos = sumar(cuenta.movimientos(), "deposito");
        double totalRetiros = sumar(cuenta.movimientos(), "retiro");
        double totalCompras = sumar(cuenta.movimientos(), "compra");

        List<MovimientoWebResponse> movimientos = cuenta.movimientos().stream()
                .map(MovimientoWebResponse::desde)
                .toList();

        return new CuentaWebResponse(
                cuenta.cuentaId(),
                titular == null ? TITULAR_NO_DISPONIBLE : titular.nombre(),
                cuenta.tipo(),
                titular == null ? 0 : titular.edad(),
                cuenta.saldo(),
                tasa,
                round2(cuenta.saldo() * tasa),
                movimientos.size(),
                totalDepositos,
                totalRetiros,
                totalCompras,
                movimientos
        );
    }

    private static double sumar(List<MovimientoInternoDTO> movimientos, String tipo) {
        return round2(movimientos.stream()
                .filter(m -> tipo.equals(m.tipoMovimiento()))
                .mapToDouble(m -> Math.abs(m.monto()))
                .sum());
    }

    private static double round2(double valor) {
        return Math.round(valor * 100.0) / 100.0;
    }
}
