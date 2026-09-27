package cl.antubank.gateway.graphql;

import graphql.language.IntValue;
import graphql.language.StringValue;
import graphql.schema.Coercing;
import graphql.schema.CoercingParseLiteralException;
import graphql.schema.CoercingParseValueException;
import graphql.schema.CoercingSerializeException;
import graphql.schema.GraphQLScalarType;
import java.math.BigInteger;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.graphql.execution.RuntimeWiringConfigurer;

/**
 * Registra el escalar {@code Long} usado por el schema del BFF (tarea 9.3) para exponer montos en
 * minor units sin arriesgar el desbordamiento del {@code Int} de 32 bits de GraphQL.
 *
 * <p>Se define un escalar propio (en vez de depender de graphql-java-extended-scalars) que
 * serializa/parsea enteros de 64 bits ({@code java.lang.Long}). Coerce desde {@link Number},
 * literales enteros y cadenas numéricas.
 */
@Configuration
public class GraphQlScalarConfig {

    /** Escalar {@code Long} (entero de 64 bits) para minor units y epochs. */
    static final GraphQLScalarType LONG = GraphQLScalarType.newScalar()
            .name("Long")
            .description("Entero con signo de 64 bits (Long).")
            .coercing(new Coercing<Long, Long>() {

                @Override
                public Long serialize(Object dataFetcherResult) {
                    if (dataFetcherResult instanceof Number number) {
                        return number.longValue();
                    }
                    if (dataFetcherResult instanceof String s) {
                        try {
                            return Long.parseLong(s.trim());
                        } catch (NumberFormatException ex) {
                            throw new CoercingSerializeException("No es un Long válido: " + s, ex);
                        }
                    }
                    throw new CoercingSerializeException(
                            "Se esperaba un Number o String, se recibió: "
                                    + (dataFetcherResult == null ? "null"
                                            : dataFetcherResult.getClass().getName()));
                }

                @Override
                public Long parseValue(Object input) {
                    if (input instanceof Number number) {
                        return number.longValue();
                    }
                    if (input instanceof String s) {
                        try {
                            return Long.parseLong(s.trim());
                        } catch (NumberFormatException ex) {
                            throw new CoercingParseValueException("No es un Long válido: " + s, ex);
                        }
                    }
                    throw new CoercingParseValueException(
                            "Se esperaba un Number o String para Long.");
                }

                @Override
                public Long parseLiteral(Object input) {
                    if (input instanceof IntValue intValue) {
                        BigInteger value = intValue.getValue();
                        if (value.bitLength() > 63) {
                            throw new CoercingParseLiteralException(
                                    "El literal excede el rango de un Long de 64 bits: " + value);
                        }
                        return value.longValue();
                    }
                    if (input instanceof StringValue stringValue) {
                        try {
                            return Long.parseLong(stringValue.getValue().trim());
                        } catch (NumberFormatException ex) {
                            throw new CoercingParseLiteralException(
                                    "No es un Long válido: " + stringValue.getValue(), ex);
                        }
                    }
                    throw new CoercingParseLiteralException(
                            "Se esperaba un literal entero o cadena para Long.");
                }
            })
            .build();

    @Bean
    RuntimeWiringConfigurer longScalarConfigurer() {
        return wiringBuilder -> wiringBuilder.scalar(LONG);
    }
}
