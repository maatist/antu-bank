package cl.antubank.notification.content;

import cl.antubank.domain.money.Currency;
import cl.antubank.domain.money.Money;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * Formatea montos para el contenido de las notificaciones respetando el locale y la escala de la
 * moneda (tarea 7.2, Requisito 7, criterio 4).
 *
 * <p>Para <strong>CLP</strong> (moneda principal, escala 0) el formato chileno agrupa los miles con
 * <em>punto</em> y no muestra decimales: {@code 1000000} → {@code $1.000.000}. En locale inglés se
 * usa el separador de miles inglés (coma): {@code $1,000,000}. El símbolo {@code $} antecede al
 * monto en ambos casos (convención chilena y también usada en inglés para el peso).
 *
 * <p>Para monedas con decimales (USD, UF, escala 2) se muestran los decimales con el separador del
 * locale. El monto de entrada llega en <em>minor units</em> desde el evento; se reconstruye con
 * {@link Money#ofMinor(long, Currency)} para preservar exactitud (sin punto flotante).
 */
public final class MoneyFormatter {

    private MoneyFormatter() {
    }

    /**
     * Formatea un monto en minor units a texto localizado con símbolo de moneda.
     *
     * @param amountMinor monto en minor units (CLP: pesos enteros).
     * @param currency    moneda del monto.
     * @param locale      locale para separadores de miles/decimales.
     * @return el monto formateado, p. ej. {@code $1.000.000} (CLP, es-CL) o {@code US$10,50}.
     */
    public static String format(long amountMinor, Currency currency, Locale locale) {
        BigDecimal amount = Money.ofMinor(amountMinor, currency).amount();

        DecimalFormatSymbols symbols = DecimalFormatSymbols.getInstance(locale);
        DecimalFormat format = new DecimalFormat();
        format.setDecimalFormatSymbols(symbols);
        format.setGroupingUsed(true);
        format.setMinimumFractionDigits(currency.scale());
        format.setMaximumFractionDigits(currency.scale());

        return symbol(currency) + format.format(amount);
    }

    /** Símbolo de la moneda antepuesto al monto. */
    private static String symbol(Currency currency) {
        return switch (currency) {
            case CLP -> "$";
            case USD -> "US$";
            case UF -> "UF ";
        };
    }
}
