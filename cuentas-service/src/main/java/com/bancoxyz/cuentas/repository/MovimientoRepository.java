package com.bancoxyz.cuentas.repository;

import com.bancoxyz.cuentas.model.Movimiento;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MovimientoRepository extends JpaRepository<Movimiento, Long> {

    List<Movimiento> findByCuentaIdOrderByIdAsc(Long cuentaId);
}
