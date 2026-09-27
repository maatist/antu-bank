package cl.antubank.domain.demo;

import cl.antubank.domain.account.AccountType;
import cl.antubank.domain.account.ChileanBank;
import cl.antubank.domain.identity.Rut;
import cl.antubank.domain.money.Currency;
import java.util.List;
import java.util.UUID;

/**
 * Fuente única de verdad del seed demo (tarea 12a.4, Requisito 12, criterio 3).
 *
 * <p>Define el conjunto de cuentas y usuarios demo con <strong>datos chilenos realistas</strong>:
 * RUT válidos (dígito verificador módulo 11), nombres chilenos y bancos reales de la plaza local
 * ({@link ChileanBank}). Los identificadores de cuenta son UUID fijos para que el
 * {@code account-service} y el {@code ledger-service} siembren de forma coherente e idempotente
 * (misma cuenta ↔ mismo saldo) sin acoplarse en el arranque.
 *
 * <p>Los RUT y nombres coinciden con los usuarios sembrados en Keycloak
 * ({@code infra/keycloak/realm-export.json}) y con el mapa demo del frontend, de modo que el
 * ingreso "invitado" (usuario {@code cliente.demo}, rol CUSTOMER) y el usuario {@code admin.demo}
 * (rol ADMIN) resuelvan a cuentas realmente sembradas:
 * <ul>
 *   <li>{@code cliente.demo} → Javiera González, RUT {@code 12.345.678-5} (CUSTOMER).</li>
 *   <li>{@code admin.demo}  → Sebastián Muñoz, RUT {@code 16.789.012-1} (ADMIN).</li>
 * </ul>
 *
 * <p>Este es dominio puro (sin Spring): los seeders de cada servicio lo consumen y lo activan solo
 * bajo el perfil {@code demo}.
 */
public final class DemoDataset {

    /**
     * Cuenta contable de contrapartida para los saldos de apertura (patrimonio/opening balance).
     *
     * <p>El ledger es de doble entrada con invariante Σ = 0: para dar un saldo inicial positivo a
     * una cuenta de cliente hay que <strong>debitar</strong> la cuenta del cliente (aumenta su
     * saldo) y <strong>acreditar</strong> por igual importe esta cuenta de patrimonio (que queda
     * con saldo negativo, como corresponde a una cuenta de capital). Así cada asiento de apertura
     * cuadra a cero y respeta la inmutabilidad del libro (nunca se modifica, solo se agrega).
     */
    public static final UUID OPENING_BALANCE_ACCOUNT_ID =
            UUID.fromString("00000000-0000-0000-0000-0000000000e0");

    /** RUT "institucional" asociado a la cuenta de patrimonio (banco emisor del capital demo). */
    private static final Rut OPENING_BALANCE_RUT = Rut.of("11.111.111-1");

    private DemoDataset() {
    }

    /**
     * @return las cuentas demo a sembrar, con datos chilenos realistas y saldos de apertura.
     */
    public static List<DemoAccount> accounts() {
        return List.of(
                // Titular del ingreso invitado / botón demo (cliente.demo, rol CUSTOMER).
                new DemoAccount(
                        UUID.fromString("00000000-0000-0000-0000-000000000001"),
                        Rut.of("12.345.678-5"),
                        "Javiera González",
                        AccountType.CORRIENTE,
                        ChileanBank.BANCO_DE_CHILE,
                        Currency.CLP,
                        2_500_000L),
                // Segunda cuenta del mismo titular (cuenta de ahorro), para mostrar múltiples cuentas.
                new DemoAccount(
                        UUID.fromString("00000000-0000-0000-0000-000000000002"),
                        Rut.of("12.345.678-5"),
                        "Javiera González",
                        AccountType.AHORRO,
                        ChileanBank.BANCO_ESTADO,
                        Currency.CLP,
                        800_000L),
                // Usuario administrador demo (admin.demo, rol ADMIN).
                new DemoAccount(
                        UUID.fromString("00000000-0000-0000-0000-000000000003"),
                        Rut.of("16.789.012-1"),
                        "Sebastián Muñoz",
                        AccountType.CORRIENTE,
                        ChileanBank.BCI,
                        Currency.CLP,
                        5_000_000L),
                // Cuentas de contraparte para transferencias demo (otros clientes).
                new DemoAccount(
                        UUID.fromString("00000000-0000-0000-0000-000000000004"),
                        Rut.of("9.876.543-3"),
                        "Camila Rojas",
                        AccountType.VISTA,
                        ChileanBank.SANTANDER_CHILE,
                        Currency.CLP,
                        1_200_000L),
                new DemoAccount(
                        UUID.fromString("00000000-0000-0000-0000-000000000005"),
                        Rut.of("21.484.618-7"),
                        "Matías Fuentes",
                        AccountType.CORRIENTE,
                        ChileanBank.SCOTIABANK_CHILE,
                        Currency.CLP,
                        350_000L),
                new DemoAccount(
                        UUID.fromString("00000000-0000-0000-0000-000000000006"),
                        Rut.of("7.654.321-6"),
                        "Antonia Silva",
                        AccountType.AHORRO,
                        ChileanBank.FALABELLA,
                        Currency.CLP,
                        4_750_000L));
    }

    /**
     * @return el RUT institucional de la cuenta de patrimonio (contrapartida de los saldos demo).
     */
    public static Rut openingBalanceRut() {
        return OPENING_BALANCE_RUT;
    }
}
