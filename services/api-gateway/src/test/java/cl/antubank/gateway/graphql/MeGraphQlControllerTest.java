package cl.antubank.gateway.graphql;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import cl.antubank.gateway.graphql.dto.AccountDto;
import cl.antubank.gateway.graphql.dto.BalanceDto;
import cl.antubank.gateway.graphql.dto.TransferDto;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.graphql.GraphQlTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.graphql.test.tester.GraphQlTester;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Tests del BFF GraphQL (tarea 9.3, Requisito 9, criterio 5): verifica que la query {@code me}
 * agrega cuentas, saldos (campo anidado) e historial de transferencias, mockeando el cliente de
 * los servicios internos ({@link DownstreamBffClient}). No se levantan los servicios reales: la
 * lógica de agregación y el mapeo del schema se prueban de forma determinista.
 */
@GraphQlTest(MeGraphQlController.class)
@Import({GraphQlScalarConfig.class, BffExceptionResolver.class})
class MeGraphQlControllerTest {

    private static final String RUT = "12.345.678-5";
    private static final String ACCOUNT_ID = "11111111-1111-1111-1111-111111111111";

    @MockBean
    private DownstreamBffClient client;

    @org.springframework.beans.factory.annotation.Autowired
    private GraphQlTester graphQlTester;

    @Test
    void meAgregaCuentasSaldosEHistorial() {
        AccountDto cuenta = new AccountDto(
                ACCOUNT_ID, RUT, "Javiera González",
                "CORRIENTE", "BANCO_DE_CHILE", "Banco de Chile", "CLP", "ACTIVE");
        when(client.accountsByRut(eq(RUT), any())).thenReturn(Flux.just(cuenta));

        when(client.balance(eq(ACCOUNT_ID), any()))
                .thenReturn(Mono.just(new BalanceDto(ACCOUNT_ID, "CLP", 38000L, new BigDecimal("38000"))));

        // Historial real por cuenta (tarea 9.4): el resolver consulta transfersByAccount por cada
        // cuenta del titular. Aquí la única cuenta devuelve un movimiento CONFIRMED.
        TransferDto transfer = new TransferDto(
                "22222222-2222-2222-2222-222222222222", "CONFIRMED",
                ACCOUNT_ID, "33333333-3333-3333-3333-333333333333",
                50000L, "CLP", "2024-01-15T10:30:00Z");
        when(client.transfersByAccount(eq(ACCOUNT_ID), any())).thenReturn(Flux.just(transfer));

        graphQlTester.document("""
                query {
                  me(rut: "12.345.678-5") {
                    rut
                    accounts {
                      id
                      accountType
                      currency
                      balance { amountMinor amount currency }
                    }
                    transfers {
                      id
                      status
                      amountMinor
                      currency
                    }
                  }
                }
                """)
                .execute()
                .path("me.rut").entity(String.class).isEqualTo(RUT)
                .path("me.accounts[0].id").entity(String.class).isEqualTo(ACCOUNT_ID)
                .path("me.accounts[0].accountType").entity(String.class).isEqualTo("CORRIENTE")
                .path("me.accounts[0].balance.amountMinor").entity(Long.class).isEqualTo(38000L)
                .path("me.accounts[0].balance.amount").entity(String.class).isEqualTo("38000")
                .path("me.accounts[0].balance.currency").entity(String.class).isEqualTo("CLP")
                // El historial ahora es real y NO vacío: refleja el movimiento devuelto por el
                // endpoint GET /transfers?accountId= de transfer-service (Requisito 9, criterio 5).
                .path("me.transfers").entityList(Object.class).hasSize(1)
                .path("me.transfers[0].id").entity(String.class)
                .isEqualTo("22222222-2222-2222-2222-222222222222")
                .path("me.transfers[0].status").entity(String.class).isEqualTo("CONFIRMED")
                .path("me.transfers[0].amountMinor").entity(Long.class).isEqualTo(50000L);
    }

    @Test
    void meConsolidaHistorialDeVariasCuentasDeduplicandoYOrdenandoDescendente() {
        // Dos cuentas del titular. Una transferencia entre ambas aparece en los dos historiales
        // (como origen en una, como destino en la otra): el resolver debe deduplicarla por id y
        // ordenar por fecha descendente (tarea 9.4).
        String cuentaA = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";
        String cuentaB = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb";
        AccountDto a = new AccountDto(cuentaA, RUT, "Javiera González",
                "CORRIENTE", "BANCO_DE_CHILE", "Banco de Chile", "CLP", "ACTIVE");
        AccountDto b = new AccountDto(cuentaB, RUT, "Javiera González",
                "AHORRO", "BANCO_ESTADO", "BancoEstado", "CLP", "ACTIVE");
        when(client.accountsByRut(eq(RUT), any())).thenReturn(Flux.just(a, b));
        when(client.balance(any(), any()))
                .thenReturn(Mono.just(new BalanceDto(cuentaA, "CLP", 0L, BigDecimal.ZERO)));

        // t-comun: entre A y B, aparece en ambos historiales -> se deduplica. t-antigua: solo en A.
        TransferDto comun = new TransferDto("t-comun", "CONFIRMED", cuentaA, cuentaB,
                50000L, "CLP", "2024-03-10T12:00:00Z");
        TransferDto antigua = new TransferDto("t-antigua", "CONFIRMED", cuentaA, "otra",
                10000L, "CLP", "2024-01-01T08:00:00Z");
        when(client.transfersByAccount(eq(cuentaA), any()))
                .thenReturn(Flux.just(comun, antigua));
        when(client.transfersByAccount(eq(cuentaB), any()))
                .thenReturn(Flux.just(comun));

        graphQlTester.document("""
                query {
                  me(rut: "12.345.678-5") {
                    transfers { id createdAt }
                  }
                }
                """)
                .execute()
                // Deduplicada: t-comun aparece una sola vez pese a estar en ambas cuentas.
                .path("me.transfers").entityList(Object.class).hasSize(2)
                // Orden descendente por fecha: la más reciente (t-comun, marzo) va primero.
                .path("me.transfers[0].id").entity(String.class).isEqualTo("t-comun")
                .path("me.transfers[1].id").entity(String.class).isEqualTo("t-antigua");
    }

    @Test
    void meSinRutNiClaimFallaConErrorClaro() {
        // Sin argumento rut y sin JWT (el @GraphQlTest no inyecta principal): debe fallar.
        graphQlTester.document("query { me { rut } }")
                .execute()
                .errors()
                .expect(error -> error.getMessage() != null
                        && error.getMessage().contains("RUT"));
    }

    @Test
    void meDegradaHistorialVacioSinRomperAgregacion() {
        AccountDto cuenta = new AccountDto(
                ACCOUNT_ID, RUT, "Javiera González",
                "CORRIENTE", "BANCO_DE_CHILE", "Banco de Chile", "CLP", "ACTIVE");
        when(client.accountsByRut(eq(RUT), any())).thenReturn(Flux.just(cuenta));
        when(client.balance(eq(ACCOUNT_ID), any()))
                .thenReturn(Mono.just(new BalanceDto(ACCOUNT_ID, "CLP", 0L, BigDecimal.ZERO)));
        // El historial del downstream degrada a vacío (fallo/sin movimientos): la agregación de
        // cuentas y saldos no se rompe (Requisito 9, criterios 4 y 5).
        when(client.transfersByAccount(eq(ACCOUNT_ID), any())).thenReturn(Flux.empty());

        graphQlTester.document("""
                query {
                  me(rut: "12.345.678-5") {
                    accounts { id balance { amountMinor } }
                    transfers { id }
                  }
                }
                """)
                .execute()
                .path("me.accounts[0].balance.amountMinor").entity(Long.class).isEqualTo(0L)
                .path("me.transfers").entityList(Object.class).hasSize(0);
    }
}
