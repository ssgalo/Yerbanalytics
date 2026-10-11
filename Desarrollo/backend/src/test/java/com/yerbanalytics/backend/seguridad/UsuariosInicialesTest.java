package com.yerbanalytics.backend.seguridad;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Cuentas declaradas en {@code yerbanalytics.auth.usuarios-iniciales}. */
class UsuariosInicialesTest extends SeguridadIntegracionBase {

    @Autowired UsuarioService usuarioService;

    @Test
    void parseaLaListaYAdmiteDosPuntosEnLaClave() {
        List<SeguridadInicializador.UsuarioInicial> lista = SeguridadInicializador.parsearUsuariosIniciales(
                " sim:servicio:clave-uno , ana:OPERARIO:a:b:c ,");
        assertEquals(2, lista.size());
        assertEquals(new SeguridadInicializador.UsuarioInicial("sim", Rol.SERVICIO, "clave-uno"), lista.get(0));
        assertEquals("a:b:c", lista.get(1).clave());
        assertTrue(SeguridadInicializador.parsearUsuariosIniciales("").isEmpty());
    }

    @Test
    void unaEntradaMalFormadaFrenaElArranque() {
        assertThrows(IllegalStateException.class, () -> SeguridadInicializador.parsearUsuariosIniciales("sim:SERVICIO"));
        assertThrows(IllegalStateException.class, () -> SeguridadInicializador.parsearUsuariosIniciales("sim:JEFE:clave-uno"));
    }

    @Test
    void creaLaCuentaUnaSolaVezYLaDeServicioNoDebeCambiarClave() {
        String nombre = "svc-" + UUID.randomUUID().toString().substring(0, 8);
        assertTrue(usuarioService.crearUsuarioInicialSiFalta(nombre, Rol.SERVICIO, CLAVE).isPresent());
        UsuarioEntity u = usuarios.findByUsername(nombre).orElseThrow();
        assertFalse(u.isDebeCambiarClave());
        assertTrue(encoder.matches(CLAVE, u.getClaveHash()));

        // Segundo arranque: no la pisa aunque la clave configurada haya cambiado.
        assertTrue(usuarioService.crearUsuarioInicialSiFalta(nombre, Rol.SERVICIO, "otra-clave-distinta").isEmpty());
        assertTrue(encoder.matches(CLAVE, usuarios.findByUsername(nombre).orElseThrow().getClaveHash()));
    }

    @Test
    void laCuentaDeUnaPersonaNaceConClaveTemporal() {
        String nombre = "op-" + UUID.randomUUID().toString().substring(0, 8);
        usuarioService.crearUsuarioInicialSiFalta(nombre, Rol.OPERARIO, CLAVE);
        assertTrue(usuarios.findByUsername(nombre).orElseThrow().isDebeCambiarClave());
    }
}
