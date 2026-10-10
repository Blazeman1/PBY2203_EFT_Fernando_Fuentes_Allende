package com.bancoxyz.cuentas.repository;

import com.bancoxyz.cuentas.model.OperacionProcesada;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OperacionProcesadaRepository extends JpaRepository<OperacionProcesada, String> {
}
