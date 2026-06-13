package dev.streamforge.core.model;

/**
 * Definicion de un campo en un schema de datos
 */
public class FieldDefinition {

    private final String name;
    private final FieldType type;
    private final boolean nullable;

    public FieldDefinition(String name, FieldType type, boolean nullable) {
        this.name     = name;
        this.type     = type;
        this.nullable = nullable;
    }

    public String getName()    { return name;     }
    public FieldType getType() { return type;     }
    public boolean isNullable(){ return nullable; }

    @Override
    public String toString() {
        return name + ":" + type + (nullable ? "?" : "");
    }
}