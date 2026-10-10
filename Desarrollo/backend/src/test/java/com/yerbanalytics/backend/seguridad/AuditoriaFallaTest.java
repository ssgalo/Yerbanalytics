package com.yerbanalytics.backend.seguridad;

import com.yerbanalytics.backend.seguridad.auditoria.AuditoriaService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

/**
 * Si no se puede escribir la auditoría, el cambio no se aplica. Va sin transacción de test a
 * propósito: lo que se prueba es la transacción real del servicio.
 */
@SpringBootTest
@WithMockUser(username = "admin-de-prueba")
class AuditoriaFallaTest {

    @SpyBean private AuditoriaService auditoria;
    @Autowired private UsuarioService usuarios;
    @Autowired private UsuarioRepository repo;
    @Autowired private TransactionTemplate tx;

    @Test
    void unaFallaAlAuditarRevierteElAlta() {
        // El stub pasa por el proxy transaccional (MANDATORY): se arma dentro de una transacción.
        tx.executeWithoutResult(s -> doThrow(new IllegalStateException("la auditoría no está disponible"))
                .when(auditoria).registrar(any(), any(), any(), any()));
        String username = "no-debe-quedar-" + UUID.randomUUID().toString().substring(0, 8);

        assertThatThrownBy(() -> usuarios.alta(username, "Nadie", "OPERARIO", "temporal-123"))
                .hasMessageContaining("auditoría");

        assertThat(repo.existsByUsername(username)).isFalse();
    }
}
