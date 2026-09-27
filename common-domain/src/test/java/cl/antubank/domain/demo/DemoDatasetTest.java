package cl.antubank.domain.demo;

import static org.assertj.core.api.Assertions.assertThat;

import cl.antubank.domain.identity.Rut;
import cl.antubank.domain.money.Currency;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests del dataset del seed demo (tarea 12a.4, Requisito 12, criterio 3).
 *
 * <p>Verifican que los datos sembrados son chilenos realistas y consistentes: RUT válidos
 * (DV módulo 11), identificadores de cuenta únicos y distintos de la cuenta de patrimonio, y
 * saldos de apertura no negativos. Al ejercitar {@link DemoDataset#accounts()} también se fuerza
 * la construcción de cada {@link Rut}, que rechazaría en el acto cualquier RUT inválido.
 */
class DemoDatasetTest {

    @Test
    @DisplayName("Todas las cuentas demo tienen RUT válido (módulo 11)")
    void todasLasCuentasTienenRutValido() {
        List<DemoAccount> cuentas = DemoDataset.accounts();

        assertThat(cuentas).isNotEmpty();
        for (DemoAccount cuenta : cuentas) {
            // Reconstruir desde el formato canónico revalida el DV módulo 11.
            assertThat(Rut.isValid(cuenta.holderRut().toCanonical()))
                    .as("RUT %s debe ser válido", cuenta.holderRut())
                    .isTrue();
        }
    }

    @Test
    @DisplayName("El RUT de patrimonio (contrapartida) es válido")
    void rutDePatrimonioEsValido() {
        assertThat(DemoDataset.openingBalanceRut()).isNotNull();
    }

    @Test
    @DisplayName("Los identificadores de cuenta son únicos y distintos de la cuenta de patrimonio")
    void identificadoresUnicosYDistintosDelPatrimonio() {
        List<UUID> ids = DemoDataset.accounts().stream().map(DemoAccount::id).toList();

        assertThat(ids).doesNotHaveDuplicates();
        assertThat(ids).doesNotContain(DemoDataset.OPENING_BALANCE_ACCOUNT_ID);
    }

    @Test
    @DisplayName("Los saldos de apertura son no negativos y hay al menos una cuenta con saldo")
    void saldosDeAperturaNoNegativos() {
        List<DemoAccount> cuentas = DemoDataset.accounts();

        assertThat(cuentas).allSatisfy(c -> assertThat(c.openingBalanceMinor()).isGreaterThanOrEqualTo(0));
        assertThat(cuentas).anyMatch(DemoAccount::hasOpeningBalance);
    }

    @Test
    @DisplayName("Incluye a los titulares demo cliente.demo y admin.demo (coherentes con Keycloak)")
    void incluyeTitularesDemo() {
        var rutsPorNombre = DemoDataset.accounts().stream()
                .collect(Collectors.toMap(
                        DemoAccount::holderName,
                        c -> c.holderRut().format(),
                        (a, b) -> a));

        assertThat(rutsPorNombre).containsEntry("Javiera González", "12.345.678-5");
        assertThat(rutsPorNombre).containsEntry("Sebastián Muñoz", "16.789.012-1");
    }

    @Test
    @DisplayName("Todas las cuentas demo usan CLP (moneda principal de la demo)")
    void todasEnClp() {
        assertThat(DemoDataset.accounts())
                .allSatisfy(c -> assertThat(c.currency()).isEqualTo(Currency.CLP));
    }
}
