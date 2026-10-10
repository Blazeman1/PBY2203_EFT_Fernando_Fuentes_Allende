package com.bancoxyz.cuentas.repository;

import com.bancoxyz.cuentas.model.Cuenta;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CuentaRepository extends JpaRepository<Cuenta, Long> {

    /**
     * Lee la cuenta tomando un bloqueo de fila ({@code SELECT ... FOR UPDATE}) que se mantiene
     * hasta el fin de la transaccion. Reemplaza al {@code synchronized (cuenta)} de la version en
     * memoria, que solo protegia dentro de UNA JVM: con varias replicas de este servicio, el
     * unico punto comun donde se puede serializar el acceso a un saldo es la base de datos.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Cuenta c where c.id = :id")
    Optional<Cuenta> buscarParaActualizar(@Param("id") Long id);

    List<Cuenta> findByClienteIdOrderByIdAsc(Long clienteId);
}
