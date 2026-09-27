package cl.antubank.notification.content;

import static org.assertj.core.api.Assertions.assertThat;

import cl.antubank.domain.money.Currency;
import cl.antubank.notification.NotificationProperties;
import cl.antubank.notification.channel.Notification;
import cl.antubank.notification.messaging.FraudReason;
import cl.antubank.notification.messaging.TransferConfirmedEvent;
import cl.antubank.notification.messaging.TransferFlaggedEvent;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;

/**
 * Tests unitarios de {@link NotificationContentRenderer} (tarea 7.2, Requisito 7, criterio 4).
 *
 * <p>Verifican que el contenido de las notificaciones se renderiza localizado (es-CL por defecto e
 * ingles), interpolando el monto formateado y —para las alertas— el motivo del riesgo, usando el
 * mismo {@code MessageSource} (basename {@code messages}) que en produccion.
 */
class NotificationContentRendererTest {

    private static final UUID TRANSFER = UUID.randomUUID();
    private static final UUID CUENTA = UUID.randomUUID();

    private ResourceBundleMessageSource messageSource() {
        ResourceBundleMessageSource ms = new ResourceBundleMessageSource();
        ms.setBasename("messages");
        ms.setDefaultEncoding("ISO-8859-1");
        // Igual que en produccion (spring.messages.fallback-to-system-locale=false): un locale sin
        // bundle propio (es-CL/en sin match exacto) cae al bundle base (messages.properties, es),
        // no al locale del sistema.
        ms.setFallbackToSystemLocale(false);
        return ms;
    }

    private NotificationContentRenderer rendererConLocale(Locale locale) {
        NotificationProperties props = new NotificationProperties(
                locale, new NotificationProperties.Events("transfer.events", "fraud.events"));
        return new NotificationContentRenderer(messageSource(), props);
    }

    @Test
    void renderizaTransferenciaConfirmadaEnEspanolConMontoEsCl() {
        NotificationContentRenderer renderer =
                rendererConLocale(Locale.forLanguageTag("es-CL"));

        Notification n = renderer.renderTransferConfirmed(new TransferConfirmedEvent(
                TRANSFER, CUENTA, UUID.randomUUID(), 5_000_000L, Currency.CLP));

        assertThat(n.subject()).isEqualTo("Transferencia realizada con exito");
        assertThat(n.body()).contains("$5.000.000");
        assertThat(n.locale().getLanguage()).isEqualTo("es");
        assertThat(n.recipient()).contains(CUENTA.toString());
    }

    @Test
    void renderizaTransferenciaConfirmadaEnIngles() {
        NotificationContentRenderer renderer = rendererConLocale(Locale.ENGLISH);

        Notification n = renderer.renderTransferConfirmed(new TransferConfirmedEvent(
                TRANSFER, CUENTA, UUID.randomUUID(), 1_000_000L, Currency.CLP));

        assertThat(n.subject()).isEqualTo("Transfer completed successfully");
        assertThat(n.body()).contains("$1,000,000");
    }

    @Test
    void renderizaTransferenciaSospechosaEnEspanolConMotivoLocalizado() {
        NotificationContentRenderer renderer =
                rendererConLocale(Locale.forLanguageTag("es-CL"));

        Notification n = renderer.renderTransferFlagged(new TransferFlaggedEvent(
                TRANSFER, CUENTA, FraudReason.HIGH_AMOUNT, 8_000_000L, Currency.CLP));

        assertThat(n.subject()).isEqualTo("Alerta: transferencia sospechosa detectada");
        assertThat(n.body()).contains("$8.000.000");
        assertThat(n.body()).contains("monto elevado que supera el umbral de riesgo");
    }

    @Test
    void renderizaTransferenciaSospechosaEnInglesConMotivoLocalizado() {
        NotificationContentRenderer renderer = rendererConLocale(Locale.ENGLISH);

        Notification n = renderer.renderTransferFlagged(new TransferFlaggedEvent(
                TRANSFER, CUENTA, FraudReason.VELOCITY_COUNT, 2_000_000L, Currency.CLP));

        assertThat(n.subject()).isEqualTo("Alert: suspicious transfer detected");
        assertThat(n.body()).contains("too many transfers in a short period");
    }
}
