package cl.antubank.gateway.graphql;

import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import graphql.schema.DataFetchingEnvironment;
import org.springframework.graphql.execution.DataFetcherExceptionResolverAdapter;
import org.springframework.graphql.execution.ErrorType;
import org.springframework.stereotype.Component;

/**
 * Mapea excepciones del BFF a errores GraphQL con mensaje y tipo útiles (tarea 9.3).
 *
 * <p>Sin este resolver, graphql-java enmascara los mensajes de excepciones no-GraphQLError como
 * {@code INTERNAL_ERROR}. Aquí se traduce {@link IllegalArgumentException} (p. ej. cuando no se
 * puede determinar el RUT del cliente) a un error {@link ErrorType#BAD_REQUEST} con su mensaje
 * original, de modo que el frontend reciba un diagnóstico accionable.
 */
@Component
public class BffExceptionResolver extends DataFetcherExceptionResolverAdapter {

    @Override
    protected GraphQLError resolveToSingleError(Throwable ex, DataFetchingEnvironment env) {
        if (ex instanceof IllegalArgumentException) {
            return GraphqlErrorBuilder.newError(env)
                    .errorType(ErrorType.BAD_REQUEST)
                    .message(ex.getMessage())
                    .build();
        }
        return null; // el resto se maneja con la política por defecto
    }
}
