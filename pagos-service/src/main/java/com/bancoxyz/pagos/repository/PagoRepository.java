package com.bancoxyz.pagos.repository;

import com.bancoxyz.pagos.model.Pago;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PagoRepository extends JpaRepository<Pago, String> {

    /** Historial de una cuenta: operaciones en las que participa como origen o como destino. */
    @Query("select p from Pago p where p.cuentaOrigen = :cuentaId or p.cuentaDestino = :cuentaId order by p.creadoEn desc")
    List<Pago> historialDeCuenta(@Param("cuentaId") Long cuentaId);
}
