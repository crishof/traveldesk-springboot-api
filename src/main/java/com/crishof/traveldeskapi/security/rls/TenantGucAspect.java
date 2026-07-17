package com.crishof.traveldeskapi.security.rls;

import com.crishof.traveldeskapi.security.principal.SecurityUser;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Fija el GUC {@code app.current_agency} (via {@code set_config(..., is_local => true)})
 * al inicio de cada metodo transaccional, tomando la agencia del principal autenticado.
 * Esto activa las politicas RLS por agencia cuando la app conecta con un rol sin superusuario.
 *
 * <p><b>Inactivo por defecto</b> ({@code app.security.rls.enabled=false}): mientras la app
 * conecte como superusuario, RLS se ignora y este aspecto no aporta nada, por eso se deja
 * apagado para no ejecutar SQL innecesario ni depender de PostgreSQL en tests con H2.</p>
 *
 * <p>Se ejecuta DENTRO de la transaccion gracias al orden definido en {@link RlsTransactionConfig}.</p>
 */
@Aspect
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
@Slf4j
public class TenantGucAspect {

    private final boolean enabled;

    @PersistenceContext
    private EntityManager entityManager;

    public TenantGucAspect(@Value("${app.security.rls.enabled:false}") boolean enabled) {
        this.enabled = enabled;
    }

    @Before("@annotation(org.springframework.transaction.annotation.Transactional) "
            + "|| @within(org.springframework.transaction.annotation.Transactional) "
            + "|| @annotation(jakarta.transaction.Transactional) "
            + "|| @within(jakarta.transaction.Transactional)")
    public void setCurrentAgency() {
        if (!enabled) {
            return;
        }

        UUID agencyId = currentAgencyId();
        if (agencyId == null) {
            return;
        }

        // set_config admite parametros de bind (SET no) y con is_local=true es transaccional.
        entityManager
                .createNativeQuery("SELECT set_config('app.current_agency', :aid, true)")
                .setParameter("aid", agencyId.toString())
                .getSingleResult();
    }

    private UUID currentAgencyId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        if (authentication.getPrincipal() instanceof SecurityUser securityUser) {
            return securityUser.getAgencyId();
        }
        return null;
    }
}
