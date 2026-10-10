package com.bancoxyz.clientes.service;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** Dispara la migracion inicial al arrancar (en un bean aparte para que aplique el proxy transaccional). */
@Component
public class CargaDatosRunner implements ApplicationRunner {

    private final CargaDatosLegacy cargaDatosLegacy;

    public CargaDatosRunner(CargaDatosLegacy cargaDatosLegacy) {
        this.cargaDatosLegacy = cargaDatosLegacy;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        cargaDatosLegacy.cargarSiEstaVacio();
    }
}
