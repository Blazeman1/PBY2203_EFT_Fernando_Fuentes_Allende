package com.bancoxyz.bff.web.perfil;

import jakarta.validation.constraints.Email;

/** Datos de contacto editables desde el canal web. Un campo nulo significa "no modificar". */
public record ContactoWebRequest(@Email String email, String telefono, String direccion) {
}
