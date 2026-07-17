package com.crishof.traveldeskapi.security.rls;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.core.Ordered;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * Ordena el advisor transaccional ligeramente por encima (mas externo) que
 * {@link TenantGucAspect}, de modo que el aspecto que fija el GUC se ejecute
 * DENTRO de la transaccion (necesario para {@code SET LOCAL}).
 *
 * <p>Se mantiene el advisor transaccional casi al final de la cadena
 * (LOWEST_PRECEDENCE - 100) para no alterar su orden relativo respecto a
 * seguridad de metodos, {@code @Async} o cache, que siguen envolviendo a la
 * transaccion.</p>
 */
@Configuration
@EnableAspectJAutoProxy
@EnableTransactionManagement(order = Ordered.LOWEST_PRECEDENCE - 100)
public class RlsTransactionConfig {
}
