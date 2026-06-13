package dev.streamforge.core.model;

import java.util.*;

/**
 * Schema de un dataset - lista ordenada de campos con sus tipos
 *
 * Usado para:
 *   - Validar compatibilidad entre pasos del pipeline
 *   - Detectar schema drift al comparar con el schema esperado
 *   - Registrar linaje a nivel de campo
 */
public class DataSchema {

    private final String datasetName;
    private final List<FieldDefinition> fields;
    private final Map<String, FieldDefinition> fieldIndex;

    public DataSchema(String datasetName, List<FieldDefinition> fields) {
        this.datasetName = datasetName;
        this.fields      = List.copyOf(fields);
        this.fieldIndex  = new LinkedHashMap<>();
        for (FieldDefinition f : fields) {
            this.fieldIndex.put(f.getName(), f);
        }
    }

    public String getDatasetName()             { return datasetName;           }
    public List<FieldDefinition> getFields()   { return fields;                }
    public Optional<FieldDefinition> getField(String name) {
        return Optional.ofNullable(fieldIndex.get(name));
    }
    public boolean hasField(String name)       { return fieldIndex.containsKey(name); }
    public int getFieldCount()                 { return fields.size();          }

    /**
     * Detecta diferencias entre este schema y otro (schema drift)
     * Retorna lista de descripciones de los cambios detectados
     */
    public List<String> detectDrift(DataSchema other) {
        List<String> drifts = new ArrayList<>();

        // Campos nuevos en other que no estan en este
        for (FieldDefinition f : other.getFields()) {
            if (!this.hasField(f.getName())) {
                drifts.add("Campo nuevo: " + f.getName() + " (" + f.getType() + ")");
            }
        }

        // Campos eliminados de other que estaban en este
        for (FieldDefinition f : this.getFields()) {
            if (!other.hasField(f.getName())) {
                drifts.add("Campo eliminado: " + f.getName());
            }
        }

        // Cambios de tipo
        for (FieldDefinition f : this.getFields()) {
            other.getField(f.getName()).ifPresent(otherF -> {
                if (f.getType() != otherF.getType()) {
                    drifts.add("Tipo cambiado: " + f.getName()
                            + " (" + f.getType() + " --> " + otherF.getType() + ")");
                }
            });
        }

        return drifts;
    }

    @Override
    public String toString() {
        return "DataSchema{dataset='" + datasetName + "', fields=" + fields + "}";
    }
}