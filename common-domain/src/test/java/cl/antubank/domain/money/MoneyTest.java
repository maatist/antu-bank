package cl.antubank.domain.money;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class MoneyTest {

    @Nested
    @DisplayName("Escala por moneda")
    class Escala {

        @Test
        @DisplayName("CLP no tiene decimales (scale 0)")
        void clpSinDecimales() {
            Money m = Money.ofMajor("1000.4", Currency.CLP);
            assertThat(m.amount()).isEqualByComparingTo("1000");
            assertThat(m.amount().scale()).isZero();
        }

        @Test
        @DisplayName("USD tiene dos decimales (scale 2)")
        void usdDosDecimales() {
            Money m = Money.ofMajor("10.5", Currency.USD);
            assertThat(m.amount()).isEqualByComparingTo("10.50");
            assertThat(m.amount().scale()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("Redondeo bancario (HALF_EVEN)")
    class Redondeo {

        @Test
        @DisplayName("CLP redondea a entero con HALF_EVEN")
        void clpRedondeoHalfEven() {
            // 0.5 -> 0 (al par más cercano), 1.5 -> 2, 2.5 -> 2
            assertThat(Money.ofMajor("0.5", Currency.CLP).amount()).isEqualByComparingTo("0");
            assertThat(Money.ofMajor("1.5", Currency.CLP).amount()).isEqualByComparingTo("2");
            assertThat(Money.ofMajor("2.5", Currency.CLP).amount()).isEqualByComparingTo("2");
        }

        @Test
        @DisplayName("USD redondea a 2 decimales con HALF_EVEN")
        void usdRedondeoHalfEven() {
            assertThat(Money.ofMajor("10.125", Currency.USD).amount()).isEqualByComparingTo("10.12");
            assertThat(Money.ofMajor("10.135", Currency.USD).amount()).isEqualByComparingTo("10.14");
        }
    }

    @Nested
    @DisplayName("Suma y resta")
    class Operaciones {

        @Test
        @DisplayName("Suma de misma moneda")
        void suma() {
            Money a = Money.ofMajor(1000, Currency.CLP);
            Money b = Money.ofMajor(500, Currency.CLP);
            assertThat(a.plus(b)).isEqualTo(Money.ofMajor(1500, Currency.CLP));
        }

        @Test
        @DisplayName("Resta de misma moneda")
        void resta() {
            Money a = Money.ofMajor(1000, Currency.CLP);
            Money b = Money.ofMajor(300, Currency.CLP);
            assertThat(a.minus(b)).isEqualTo(Money.ofMajor(700, Currency.CLP));
        }

        @Test
        @DisplayName("Sumar monedas distintas falla")
        void sumaDistintaMonedaFalla() {
            Money clp = Money.ofMajor(1000, Currency.CLP);
            Money usd = Money.ofMajor(10, Currency.USD);
            assertThatThrownBy(() -> clp.plus(usd))
                    .isInstanceOf(CurrencyMismatchException.class);
        }

        @Test
        @DisplayName("Restar monedas distintas falla")
        void restaDistintaMonedaFalla() {
            Money clp = Money.ofMajor(1000, Currency.CLP);
            Money uf = Money.ofMajor(1, Currency.UF);
            assertThatThrownBy(() -> clp.minus(uf))
                    .isInstanceOf(CurrencyMismatchException.class);
        }
    }

    @Nested
    @DisplayName("Minor units")
    class MinorUnits {

        @Test
        @DisplayName("ofMinor y toMinorUnits en CLP (scale 0)")
        void clpMinor() {
            Money m = Money.ofMinor(1000, Currency.CLP);
            assertThat(m.amount()).isEqualByComparingTo("1000");
            assertThat(m.toMinorUnits()).isEqualTo(1000L);
        }

        @Test
        @DisplayName("ofMinor y toMinorUnits en USD (scale 2)")
        void usdMinor() {
            Money m = Money.ofMinor(1050, Currency.USD);
            assertThat(m.amount()).isEqualByComparingTo("10.50");
            assertThat(m.toMinorUnits()).isEqualTo(1050L);
        }
    }

    @Nested
    @DisplayName("Signo")
    class Signo {

        @Test
        void negativo() {
            assertThat(Money.ofMajor(-1, Currency.CLP).isNegative()).isTrue();
        }

        @Test
        void cero() {
            assertThat(Money.zero(Currency.CLP).isZero()).isTrue();
        }

        @Test
        void positivo() {
            assertThat(Money.ofMajor(1, Currency.CLP).isPositive()).isTrue();
        }
    }

    @Nested
    @DisplayName("Igualdad por valor")
    class Igualdad {

        @Test
        @DisplayName("Mismo monto y moneda son iguales aunque difiera la escala de entrada")
        void igualesPorValor() {
            Money a = Money.ofMajor(new BigDecimal("1000"), Currency.CLP);
            Money b = Money.ofMajor(new BigDecimal("1000.00"), Currency.CLP);
            assertThat(a).isEqualTo(b);
            assertThat(a.hashCode()).isEqualTo(b.hashCode());
        }

        @Test
        @DisplayName("Distinta moneda no son iguales")
        void distintaMonedaNoIguales() {
            assertThat(Money.ofMajor(10, Currency.USD))
                    .isNotEqualTo(Money.ofMajor(10, Currency.UF));
        }
    }
}
