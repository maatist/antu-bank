package cl.antubank.domain.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class RutTest {

    // RUTs con dígito verificador correcto (verificados con el algoritmo módulo 11).
    // Se incluyen casos con DV numérico y con DV 'K'.
    static final String RUT_DV_NUMERICO = "12.345.678-5";
    static final String RUT_DV_K = "16.000.004-K"; // ejemplo con DV = K
    static final String RUT_SIN_PUNTOS = "12345678-5";
    static final String RUT_SIN_SEPARADORES = "123456785";

    @Nested
    @DisplayName("Construcción y validación")
    class Construccion {

        @ParameterizedTest
        @ValueSource(strings = {RUT_DV_NUMERICO, RUT_SIN_PUNTOS, RUT_SIN_SEPARADORES})
        @DisplayName("Acepta el mismo RUT con distintos formatos de entrada")
        void aceptaDistintosFormatos(String entrada) {
            Rut rut = Rut.of(entrada);
            assertThat(rut.body()).isEqualTo(12345678);
            assertThat(rut.dv()).isEqualTo('5');
        }

        @Test
        @DisplayName("Acepta DV con letra K (mayúscula o minúscula)")
        void aceptaDvK() {
            assertThat(Rut.isValid(RUT_DV_K)).isTrue();
            assertThat(Rut.isValid(RUT_DV_K.toLowerCase())).isTrue();
        }

        @Test
        @DisplayName("Rechaza DV incorrecto")
        void rechazaDvIncorrecto() {
            assertThatThrownBy(() -> Rut.of("12.345.678-9"))
                    .isInstanceOf(InvalidRutException.class);
        }

        @ParameterizedTest
        @ValueSource(strings = {"", " ", "1", "abc-d", "12.345.67A-5"})
        @DisplayName("Rechaza entradas inválidas")
        void rechazaEntradasInvalidas(String entrada) {
            assertThat(Rut.isValid(entrada)).isFalse();
        }

        @Test
        @DisplayName("Rechaza null")
        void rechazaNull() {
            assertThat(Rut.isValid(null)).isFalse();
        }
    }

    @Nested
    @DisplayName("Formateo")
    class Formateo {

        @Test
        @DisplayName("format() entrega formato chileno con puntos y guion")
        void formatoChileno() {
            assertThat(Rut.of(RUT_SIN_SEPARADORES).format()).isEqualTo("12.345.678-5");
        }

        @Test
        @DisplayName("toCanonical() entrega cuerpo-dv sin puntos")
        void canonico() {
            assertThat(Rut.of(RUT_DV_NUMERICO).toCanonical()).isEqualTo("12345678-5");
        }
    }

    @Nested
    @DisplayName("Igualdad por valor")
    class Igualdad {

        @Test
        void igualesIndependienteDelFormato() {
            assertThat(Rut.of(RUT_DV_NUMERICO)).isEqualTo(Rut.of(RUT_SIN_SEPARADORES));
            assertThat(Rut.of(RUT_DV_NUMERICO).hashCode()).isEqualTo(Rut.of(RUT_SIN_SEPARADORES).hashCode());
        }
    }
}
