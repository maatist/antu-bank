package cl.antubank.domain.money;

/**
 * Monedas soportadas por Antu Bank con su escala (cantidad de decimales).
 *
 * <p>Contexto banca chilena:
 * <ul>
 *   <li>{@code CLP} (peso chileno): no usa decimales, escala 0. Sus minor units son pesos enteros.</li>
 *   <li>{@code USD} (dólar): escala 2 (centavos).</li>
 *   <li>{@code UF} (Unidad de Fomento): unidad indexada usada en Chile, escala 2.</li>
 * </ul>
 *
 * <p>El {@code scale} determina tanto el redondeo de {@link Money} como la conversión
 * entre unidades mayores y minor units (factor {@code 10^scale}).
 */
public enum Currency {

    CLP(0),
    USD(2),
    UF(2);

    private final int scale;

    Currency(int scale) {
        this.scale = scale;
    }

    /**
     * @return cantidad de decimales de la moneda (0 para CLP).
     */
    public int scale() {
        return scale;
    }
}
