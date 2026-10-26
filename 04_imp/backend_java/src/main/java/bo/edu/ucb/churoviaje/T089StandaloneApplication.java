package bo.edu.ucb.churoviaje;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;

/**
 * Arranque INDEPENDIENTE de T-089 para validar JDBC/Flyway/cobertura.
 * No sustituye la aplicación MVC T-088/HU-01 ni altera application.yml.
 * JPA se mantiene en la aplicación integradora; la entidad HU-01 aún no tiene migración.
 */
@SpringBootApplication(
    scanBasePackages = "bo.edu.ucb.churoviaje",
    exclude = {HibernateJpaAutoConfiguration.class, JpaRepositoriesAutoConfiguration.class}
)
public class T089StandaloneApplication {
    public static void main(String[] args) {
        SpringApplication.run(T089StandaloneApplication.class, args);
    }
}
