package cl.antubank.notification.content;

import static org.assertj.core.api.Assertions.assertThat;

import cl.antubank.domain.money.Currency;
import java.util.Locale;
import org.junit.jupiter.api.Test;

/**
 * Tests unitarios de {@link MoneyFormatter} (tarea 7.2, Requisito 7, criterio 4; Requisito 10,
 * criterio 7: formateo es-CL de montos).
 *
 * <p>Verifican que CLP (moneda principal, escala 0) se formatea con separador de miles chileno
 * (punto) y sin decimales, y que el separador de miles cambia con el locale.
 */
class MoneyFormatterTest {

    private static final Locale ES_CL = Locale.forLanguageTag("es-CL");

    @Test
    void formateaClpEnEsClConMilesEnPuntoYSinDecimales() {
        // 5.000.000 CLP -> $5.000.000 (miles con punto, sin decimales).
        String formatted = MoneyFormatter.format(5_000_000L, Currency.CLP, ES_CL);
        assertThat(formatted).isEqualTo("$5.000.000");
    }

    @Test
    void formateaClpPequenoSinSeparador() {
        assertThat(MoneyFormatter.format(1_000L, Currency.CLP, ES_CL)).isEqualTo("$1.000");
    }

    @Test
    void formateaClpEnInglesConMilesEnComa() {
        // En locale ingles el separador de miles es la coma.
        String formatted = MoneyFormatter.format(1_000_000L, Currency.CLP, Locale.ENGLISH);
        assertThat(formatted).isEqualTo("$1,000,000");
    }

    @Test
    void formateaUsdConDosDecimales() {
        // USD escala 2: 1050 minor units (centavos) -> US$10,50 en es-CL (decimal con coma).
        String formatted = MoneyFormatter.format(1_050L, Currency.USD, ES_CL);
        assertThat(formatted).isEqualTo("US$10,50");
    }
}
