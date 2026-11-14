package com.churoviaje.backend.config;

import javax.sql.DataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy;

/**
 * Conexiones de los repositorios (D-37, D-39). El pool es el HikariCP que arma Spring Boot con
 * {@code spring.datasource}; los repositorios no lo usan directamente, sino a traves de un
 * {@link TransactionAwareDataSourceProxy}.
 *
 * <p>Asi, dentro de un {@code @Transactional}, cada {@code getConnection()} de un repositorio entrega la conexion de
 * la transaccion, y cerrarla con {@code try-with-resources} no la cierra ni la devuelve al pool antes de tiempo: el
 * bloqueo del conductor al reportar y el del evento al votar duran hasta que la transaccion termina, y si algo falla
 * no queda nada guardado. Fuera de una transaccion, cada operacion pide una conexion al pool y al cerrarla la
 * devuelve.
 */
@Configuration(proxyBeanMethods = false)
public class ConfiguracionAccesoDatos {

    /** Nombre del {@code DataSource} que reciben las implementaciones de {@code infraestructura.pg}. */
    public static final String CONEXIONES_DE_LA_TRANSACCION = "conexionesDeLaTransaccion";

    /**
     * No es candidato por defecto: quien pide un {@code DataSource} sin nombre (Flyway, el gestor de transacciones,
     * las metricas) sigue recibiendo el pool, y solo los repositorios, que lo piden por su nombre, reciben este.
     */
    @Bean(name = CONEXIONES_DE_LA_TRANSACCION, defaultCandidate = false)
    public DataSource conexionesDeLaTransaccion(DataSource pool) {
        return new TransactionAwareDataSourceProxy(pool);
    }
}
