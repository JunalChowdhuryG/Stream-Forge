package dev.streamforge.core.chaos;

import dev.streamforge.core.model.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("P4 - Chaos: schema drift detectado correctamente")
class SchemaDriftChaosTest {

    @Test
    @DisplayName("campo nuevo en schema detectado como drift")
    void campaNuevo_detectadoComoDrift() {
        DataSchema original = new DataSchema("orders", List.of(
            new FieldDefinition("order_id",    FieldType.LONG,   false),
            new FieldDefinition("customer_id", FieldType.LONG,   false),
            new FieldDefinition("total",       FieldType.DOUBLE, true)
        ));

        DataSchema evolved = new DataSchema("orders", List.of(
            new FieldDefinition("order_id",    FieldType.LONG,   false),
            new FieldDefinition("customer_id", FieldType.LONG,   false),
            new FieldDefinition("total",       FieldType.DOUBLE, true),
            new FieldDefinition("discount",    FieldType.DOUBLE, true) // NUEVO
        ));

        List<String> drifts = original.detectDrift(evolved);

        assertFalse(drifts.isEmpty(), "Debe detectarse al menos un drift");
        assertTrue(drifts.stream().anyMatch(d -> d.contains("discount")),
            "El drift debe mencionar el campo 'discount'");
        assertTrue(drifts.stream().anyMatch(d -> d.contains("nuevo") || d.contains("Campo")),
            "El drift debe indicar que es un campo nuevo");
    }

    @Test
    @DisplayName("cambio de tipo de campo detectado como drift")
    void cambioTipo_detectadoComoDrift() {
        DataSchema original = new DataSchema("orders", List.of(
            new FieldDefinition("customer_id", FieldType.INTEGER, false)
        ));

        DataSchema evolved = new DataSchema("orders", List.of(
            new FieldDefinition("customer_id", FieldType.STRING, false) // cambio INT→STRING
        ));

        List<String> drifts = original.detectDrift(evolved);

        assertFalse(drifts.isEmpty());
        assertTrue(drifts.stream().anyMatch(d ->
            d.contains("customer_id") && d.contains("INTEGER") && d.contains("STRING")),
            "El drift debe indicar el cambio de tipo: " + drifts);
    }

    @Test
    @DisplayName("campo eliminado detectado como drift")
    void campoEliminado_detectadoComoDrift() {
        DataSchema original = new DataSchema("orders", List.of(
            new FieldDefinition("order_id",    FieldType.LONG,   false),
            new FieldDefinition("deprecated_field", FieldType.STRING, true)
        ));

        DataSchema evolved = new DataSchema("orders", List.of(
            new FieldDefinition("order_id", FieldType.LONG, false)
            // deprecated_field fue eliminado
        ));

        List<String> drifts = original.detectDrift(evolved);

        assertFalse(drifts.isEmpty());
        assertTrue(drifts.stream().anyMatch(d -> d.contains("deprecated_field")),
            "El drift debe mencionar el campo eliminado");
    }

    @Test
    @DisplayName("schemas identicos no generan drifts")
    void schemasIdenticos_sinDrift() {
        DataSchema schema = new DataSchema("orders", List.of(
            new FieldDefinition("id",    FieldType.LONG,   false),
            new FieldDefinition("name",  FieldType.STRING, true),
            new FieldDefinition("total", FieldType.DOUBLE, true)
        ));

        List<String> drifts = schema.detectDrift(schema);

        assertTrue(drifts.isEmpty(),
            "Schemas identicos no deben generar drifts: " + drifts);
    }
}