package cl.antubank.account.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import cl.antubank.domain.account.AccountType;
import cl.antubank.domain.account.ChileanBank;
import cl.antubank.domain.identity.Rut;
import cl.antubank.domain.money.Currency;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Slice test del repositorio de cuentas contra PostgreSQL real (Testcontainers).
 * Verifica el mapping del value object {@link Rut} vía converter y la búsqueda por RUT.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class AccountRepositoryTest {

    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("account")
                    .withUsername("account")
                    .withPassword("account");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    AccountRepository repository;

    @Test
    void guardaYBuscaPorRut() {
        Rut rut = Rut.of("12.345.678-5");
        AccountEntity entity = new AccountEntity(
                UUID.randomUUID(), rut, "María González",
                AccountType.CORRIENTE, ChileanBank.BANCO_ESTADO, Currency.CLP, AccountStatus.ACTIVE);
        repository.save(entity);

        List<AccountEntity> found = repository.findByHolderRut(rut);
        assertThat(found).hasSize(1);
        assertThat(found.get(0).getHolderRut()).isEqualTo(rut);
        assertThat(found.get(0).getBank()).isEqualTo(ChileanBank.BANCO_ESTADO);
        assertThat(found.get(0).getCreatedAt()).isNotNull();
    }
}
